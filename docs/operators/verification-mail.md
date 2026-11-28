# Verification-mail adapter

**Internal implementation candidate #341 under #338 / accepted [ADR-0021](../architecture/decisions/ADR-0021-verified-email-activation.md).** The [verification state](../architecture/verification-state.md) merged through #339/#340. This adapter adds no public request/activation endpoint or automatic delivery worker. Direct signup remains HTTP 410. Configuring SMTP does not reopen signup or send mail on startup, health checks or its own schedule. Existing owners retain password sign-in and Vault access without mail.

## Supported addresses and links

New verification addresses use Jakarta Mail's maintained `InternetAddress` parser, followed by the accepted explicit policy: printable ASCII, one bare mailbox with a DNS-label domain, local part at most 64 octets, complete mailbox at most 254, labels at most 63. Lists, groups, display names, comments, address literals, controls and internationalized addresses are refused. Valid quoted local parts are supported; a quoted character is not treated as a comment. Leading/trailing ASCII spaces are trimmed only after rejecting controls. No recipient probing, provider dot/plus rewriting or Unicode folding occurs.

The complete trimmed mailbox is lowercased with `Locale.ROOT` for the established application identity. Original validated spelling is retained for delivery. H2/PostgreSQL comparison/uniqueness checks cover that supported ASCII contract on the test databases; arbitrary locale/collation changes are not certified. This parser does not change legacy sign-in acceptance, rewrite old identifiers, merge accounts or implement recovery. An unsupported stored legacy address must retain access and receive unavailable-verification guidance in the later UI; it cannot be silently corrected here.

Links use only the validated operator HTTPS origin, fixed `/verify-email` path and a fragment containing purpose/token. No Host/forwarded/client-return URL or recipient domain participates. The template has one fixed subject/sender, no attachments/tracking/third-party resources and no Vault/password content. New-account instructions require choosing a password followed by ordinary sign-in; owner-purpose instructions require the initiating tab/session and promise no login. The Web landing page and fragment-removal behavior remain unimplemented; no real recipient should receive a link through this staged adapter.

## Optional operator settings

Use the existing deployment secret mechanism. Every setting is required together; there is no plaintext mode, trust-all setting or unverified-signup switch. Nothing is required to keep existing-owner sign-in/Vault available.

| Environment variable | Spring property | Meaning |
| --- | --- | --- |
| `KEYNEST_VERIFICATION_MAIL_HOST` | `keynest.verification-mail.host` | Fixed installation SMTP host, never selected by a user |
| `KEYNEST_VERIFICATION_MAIL_PORT` | `keynest.verification-mail.port` | Explicit port, 1–65535 |
| `KEYNEST_VERIFICATION_MAIL_USERNAME` | `keynest.verification-mail.username` | SMTP submission username |
| `KEYNEST_VERIFICATION_MAIL_PASSWORD` | `keynest.verification-mail.password` | Submission credential; keep out of diagnostics/repository |
| `KEYNEST_VERIFICATION_MAIL_SENDER` | `keynest.verification-mail.sender` | One validated bare sender mailbox |
| `KEYNEST_VERIFICATION_MAIL_WEB_ORIGIN` | `keynest.verification-mail.web-origin` | Fixed absolute HTTPS origin, optional trailing slash; no credentials, path, query or fragment |
| `KEYNEST_VERIFICATION_MAIL_TLS_MODE` | `keynest.verification-mail.tls-mode` | Exactly `STARTTLS` or `IMPLICIT_TLS` |

Settings load into a private immutable/redacted object. Missing settings yield `UNCONFIGURED`; partial/invalid values yield `INVALID`; syntactically complete settings yield `READY`. These are internal configuration states, not connection, inbox-delivery or activation-readiness claims. No settings/provider credentials are returned by a health/config endpoint. No DNS, SMTP probe or message is performed during loading. Invalid mail values do not fail core startup. Credentials necessarily remain in process memory for SMTP authentication; no JVM-wide erasure guarantee is made.

Authenticate with PLAIN or LOGIN only inside verified TLS. Required STARTTLS refuses a server that does not advertise/complete TLS before AUTH. Implicit TLS handshakes before reading the SMTP greeting. Both require TLS 1.2 or 1.3, default JVM certificate trust and hostname checking against the configured host. A server advertising no AUTH is refused even though the provider itself permits such a connection. There is no fallback to plaintext, a different SMTP host, anonymous delivery or console mail. Private PKI uses the normal reviewed JVM trust-store mechanism; there is no per-recipient trust exception.

## Attempt bounds and failure semantics

Each adapter instance has two workers, a zero-capacity handoff and no queued third task. Future worker wiring must first obtain a committed [database claim](../architecture/verification-state.md) and supply a trusted eligibility check against that exact generation/lease. The adapter checks that authority before connecting and immediately before submission, outside any account transaction. This class alone does not grant a claim or establish installation-wide physical connection limits. Caller-side account transactions are refused.

TCP connect/read/write each have a five-second ceiling. A fifteen-second whole-attempt timer closes the already-registered **raw socket**, including TLS handshake/read/write stalls; caller timeout, interruption and application shutdown also close it. The provider's write-timeout guard uses the same bounded scheduled executor, with cancelled tasks removed. Closing raw transport precedes SMTP/TLS cleanup so cleanup cannot wait indefinitely for a peer's QUIT response. Isolated tests shorten bounds to demonstrate blocked writes, stalled greeting/handshake, cumulative slow greeting, missing acknowledgement, interruption and shutdown closure.

DNS resolution or an arbitrary eligibility callback can outlive cancellation in its worker. Caller wait is bounded, the raw socket is already closed, and a late result cannot open/send through it. The occupied worker slot stays bounded and unavailable until that code returns. This is a fail-closed availability limit, not a guarantee that native DNS, an arbitrarily paused process, or every JVM operation can be forcibly stopped at an exact wall-clock deadline. The future coordinated worker must retain the accepted thirty-second lease and fifteen-second attempt budget; leases are not proof of physically closed sockets on other replicas.

Results are fixed enums: `SENT` means SMTP accepted submission, `FAILED` means no successful submission was established before sending, `AMBIGUOUS` means submission began and may have arrived, `UNAVAILABLE` means configuration/capacity/shutdown, and `SUPPRESSED` means current eligibility/material refused the attempt. Conservative `AMBIGUOUS` also covers provider rejection after submission starts. No provider exception, response text, recipient, username, credential or link is returned/logged. Never automatically retry a claimed generation for any result; explicit rate-limited resend is the recovery path. Transport outcomes never activate or verify an account.

Messages/raw links exist transiently in worker/Jakarta Mail memory, not in durable jobs or logs. Owned token copies are cleared by the delivery-material callback; JVM, MIME/driver copies and provider retention cannot be promised erased. Keep SMTP debug, JavaMail protocol/JUL tracing, SQL bind tracing, request bodies and exception payload capture off at application/ingress/APM boundaries. Ordinary diagnostics are fixed states/outcomes only. Configure sender-domain authentication, deliverability, provider access/retention and mailbox backups as operator responsibilities; submission acceptance is not inbox receipt or ownership.

## Evidence and remaining enablement

The disposable loopback fixture generates a temporary localhost certificate/key at runtime and supplies a test-only trust root through a package-scoped constructor. No private key/trust-all flag is committed or exposed as production configuration. Tests exercise both TLS modes, original envelope spelling, fixed fragment/template, missing/rejected TLS/AUTH, untrusted/wrong-host certificates, old-protocol refusal, lost acknowledgement without retry, bounded workers and actual connection closure. They do not contact a provider, prove real mailbox receipt or exercise a public activation journey.

Before enabling public requests or automatic claims under #338, complete strict DTO/route/ingress admission, fresh password/owner-session ordering, atomic account activation/confirmation, the accessible inert Web landing flow, and restored-generation/job/session invalidation with retained-key recovery rehearsal. Real disposable mailbox delivery requires separately selected/authorized service and recipient. Keep H1/T01/M1 open; no release/image publication or hosting decision follows from these isolated tests.

Implementation references: [Jakarta Mail address API](https://jakarta.ee/specifications/mail/2.1/apidocs/jakarta.mail/jakarta/mail/internet/InternetAddress.html), [Angus SMTP API](https://eclipse-ee4j.github.io/angus-mail/docs/api/org.eclipse.angus.mail/org/eclipse/angus/mail/smtp/package-summary.html). The dependency versions follow the pinned Spring Boot BOM; the adapter uses the public preconnected-socket and write-timeout APIs so it can own raw-socket cancellation.

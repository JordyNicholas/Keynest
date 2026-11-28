# H7 source-reviewed Java static analysis

Roadmap M1 H7 / #315, focused slice #321. SpotBugs is a bytecode regression tool, not an independent security audit or proof of a secure deployment. Architecture, coverage and formatting gates remain separate requirements. Normal application/library builds do not activate this profile.

## Version, scope and baseline

SpotBugs Maven **4.10.5.0** runs in the explicit `static-analysis` profile, at **Max effort / Medium threshold** (priority 1 and 2 findings). This threshold was selected before inspecting output and is not lowered after results. The [official plugin goals](https://spotbugs.github.io/spotbugs-maven-plugin/spotbugs-mojo.html) and [upstream release history](https://github.com/spotbugs/spotbugs/releases) describe the tool; actual Java 21/25 execution is still required. The gate requires the reported engine version **4.10.5**. Priority is an analyzer heuristic, not a vulnerability severity rating. Lower-priority findings, test classes and defects the analyzer cannot model are outside this gate; no zero-defects claim follows.

**[verified]** Reporting source `6807de83a2fefb057139d7787115f272d583da2c` in [CI 46012783623](https://github.com/JordyNicholas/Keynest/actions/runs/46012783623) produced identical fingerprints on Java 21/25, with zero analysis errors/missing classes:

| Module | Analyzed production classes | Initial findings |
| --- | --- | --- |
| Policy | 14 | 2 |
| Generate | 1 | 0 |
| Seal | 4 | 0 |
| Manager | 166 | 48 |

The initial 50 consist of 11 exposed-reference reads, 27 retained-reference writes, six throwing-constructor notices, three broad-catch notices, one single-use-random heuristic, one monitor heuristic and one boxing roundtrip. Each was source-reviewed; reporting alone does not accept it.

The boxing expression in `VaultService.create` is changed from a primitive `14` branch to `Integer.valueOf(14)`, avoiding an unbox/rebox while retaining the 14-day default and supplied notification value. This is a small nonfunctional cleanup, not a password/expiry-policy change. Applying the already accepted formatter adopts that one touched file; token/literal comparison verifies the boxing expression is the only non-whitespace change. Existing create/export/restore metadata checks and full application tests remain required; no mirrored one-line test is added.

## Reviewed ownership and lifecycle cases

The remaining **49 instances / 48 fingerprints** are individually enumerated in `config/spotbugs-reviewed.json`. Each entry identifies module, exact class, exact method/field, type, priority, occurrence count and rationale, bound to SHA-256 of that class's source file (UTF-8 with universal newline normalization). Nested types bind to their containing source. There are no wildcard/category/module exclusions, suppressed analyzer detectors or missing-class allowances. Raw findings remain present in local XML and in gate counts; a passing gate means zero **unreviewed** findings, not zero findings.

| Rationale | Reviewed behavior and boundary |
| --- | --- |
| `managed-dependency` | Specific constructor fields retain Spring services, transaction collaborators, repositories, EntityManager or operator property beans. Their identity/lifecycle is container-owned; copying those objects would break transactions/configuration rather than establish a useful isolation boundary. These are not references supplied by HTTP callers. Exact members are listed, not every constructor or bean. |
| `mutable-policy-document` | `PasswordPolicyDocument` is an intentionally mutable Jackson/library configuration bean, with public setters and mutable Rules fields. Runtime `PasswordPolicy` constructs immutable list snapshots. Retain this existing public configuration API rather than silently changing it to immutable collections. |
| `operator-configuration-binding` | CORS/trusted-proxy JavaBean lists are installation configuration, not user-editable API data. Setter copies and existing ingress/CORS tests remain; default bean mutability is not claimed as an immutable contract. Changing configuration exposure requires re-review. |
| `managed-ciphertext-entity` | `VaultItem` is a mutable JPA entity with explicit ciphertext getter/setter/constructor ownership. Controllers return bounded views, not this entity or its array. The array contains the existing encrypted envelope; raw passwords are not stored in that field. This does not claim defensive-copy protection for arbitrary in-process callers. |
| `unmodifiable-stream-selection` | `VaultExportSelection` validates IDs then stores `stream().sorted().toList()`, an unmodifiable result; the analyzer still reports its accessor. No caller list is retained. |
| `request-policy-transport` | Account Rules is a per-request/serialization DTO. Effective policy construction snapshots lists into immutable `PasswordPolicy`; stored account policy is serialized under its existing lock. No immutable DTO API or adversarial in-process mutation guarantee is inferred. |
| `owner-metadata-transport` | Overview/SearchPage are owner-scoped response DTOs created from bounded queries. Their lists are not authorization/session state or persistent policy caches. Preserve the current transport contract without pretending the records enforce deep immutability. |
| `restore-ticket-transport` | Tickets expose preview entries; the producer's Preview snapshots lists. Single-use server Plan separately snapshots decisions and binds identity/session/credential/file state. Ticket list mutation is not an authorization path. |
| `startup-validation` | The named constructor warnings arise from rejecting invalid configuration or demo-key setup before serving. Manager does not load untrusted subclasses/plugins as a trust boundary. This is not a general finalizer-attack defense; new in-process extension boundaries need separate review. |
| `sanitized-failure-boundary` | OpenAiProvider preserves specific failure/timeout/interrupt handling before mapping other exceptions without secret diagnostics; export parsing preserves typed failures and clears owned buffers; portable-schema loading fails closed with a fixed startup error. These exact broad catches are deliberate, not a blanket catch-all recommendation. |
| `one-time-csprng-process-key` | Login admission uses SecureRandom once to create a fresh process-only HMAC key, then clears scratch bytes. This is intentional one-time cryptographic key generation, not repeated predictable `Random` seeding or vault-key reuse. |
| `shared-admission-monitor` | Attempt completion intentionally locks the same outer admission monitor used by begin, coordinating a private bounded map. Existing concurrency/release/expiry tests remain. Arbitrary adversarial Java code sharing the service monitor is outside the current server boundary, not a new distributed/fairness guarantee. |

These are proposed reviewed exceptions until the maintainer approves this PR. They do not accept unrelated threat-model risks or excuse future uses of these classes. A source digest binds the reviewed file, not the entire call graph/deployment: new callers, caches, exports or extension boundaries still need security review. The exact-member inventory below and source locations make each proposed treatment reviewable.

## Enforcement and commands

From the repository root, with the supported JDK, Maven and Docker for existing PostgreSQL tests:

```bash
python3 scripts/java_formatting.py check
python3 -m unittest discover -s scripts/tests -p 'test_java_analysis.py'
mvn -B -Pcoverage,static-analysis clean test spotbugs:spotbugs
python3 scripts/check_java_analysis.py
python3 scripts/verify_java_analysis.py
python3 scripts/check_java_coverage.py
```

SpotBugs reporting can return success with findings; **the Python gate is mandatory**. It reads every direct reactor module, requires compiled production classes and a bounded nonempty report, checks version/class/finding/error metadata and rejects missing classes or analyzer errors. New/changed findings, excess occurrence counts, missing former findings, duplicate/broad entries, unknown rationales and changed reviewed source all fail. Source edits, including formatting, require re-review/update of the affected digest or removal of obsolete exceptions; do not refresh hashes blindly. A new module without real analysis fails. `clean` prevents stale files from substituting for current output. No includes/excludes or skip properties are configured for analyzed production code.

Eight local Python test groups cover clean/exact-review success, new/changed/additional/stale findings, source edits, missing/empty/incomplete output, analyzer errors/missing dependencies, invalid/broad/duplicate review entries, an unreported reactor module and suppression of malformed payload diagnostics.

The real probe operates on committed HEAD in an owned detached temporary worktree. It compiles/analyzes an always-null dereference in the policy module, requires a real NP finding and rejection for that finding, then recompiles a repaired control and requires acceptance. Deleting the resulting report must fail. It uses the same review/inspection functions as the full gate; no defect is committed into application code or executed against a Vault. Maven/compiler output is captured without source/literal uploads. Timeouts terminate owned process trees before private-worktree cleanup; only actual CI OS/JDK execution is claimed.

CI retains all seven check names, formatter/history/probes, architecture tests, full tests/coverage floors, real PostgreSQL and both Compose/browser/consumer paths. Runtime/library dependencies, keys, envelopes, authentication, schema, profile defaults, pinned actions/read-only credentials and opt-in publication are retained. Raw analyzer XML remains local (may contain source/literal annotations); no new reporting service or raw-analysis artifact upload is added to the final workflow. The runtime/test dependency SBOM does not establish complete build-plugin transitive supply-chain coverage; plugin pins/upstream and actual execution were reviewed separately.

## Evidence and rollback

Final exact-source results, each probe outcome and explicit reviewed approval belong on #321/the PR. Local parser/unit checks are not a successful local Maven execution when plugin dependencies cannot resolve. A clean analyzer gate does not certify assertion quality, tenant isolation, cryptography, AI egress or production availability. H7/M1 exit and release decisions remain separate evidence checks.

Rollback removes the explicit profile, analysis-only CI steps, guard/tests/probe/review inventory and this guide. Revert the optional boxing cleanup/its formatting separately if desired. Preserve all prior application/formatting/architecture/coverage/scanner gates; no runtime-data migration is needed. Do not use rollback to imply that the reviewed behaviors or other security risks disappeared.

## Exact reviewed members

The inventory is generated here for readability from the committed entries; the JSON plus bound source is authoritative.

| Class | Type and member (field) | Count | Rationale |
| --- | --- | --- | --- |
| `dev.keynest.manager.ai.AiExplanationService` | `EI_EXPOSE_REP2`: `constructor (properties)` | 1 | `managed-dependency` |
| `dev.keynest.manager.ai.OpenAiProvider` | `REC_CATCH_EXCEPTION`: `explain` | 1 | `sanitized-failure-boundary` |
| `dev.keynest.manager.api.RateLimitFilter` | `CT_CONSTRUCTOR_THROW`: `constructor` | 1 | `startup-validation` |
| `dev.keynest.manager.api.RateLimitFilter` | `EI_EXPOSE_REP2`: `constructor (properties)` | 1 | `managed-dependency` |
| `dev.keynest.manager.api.VaultExportRequest` | `REC_CATCH_EXCEPTION`: `read` | 1 | `sanitized-failure-boundary` |
| `dev.keynest.manager.auth.AuthService` | `EI_EXPOSE_REP2`: `constructor (em)` | 1 | `managed-dependency` |
| `dev.keynest.manager.auth.AuthService` | `EI_EXPOSE_REP2`: `constructor (sessions)` | 1 | `managed-dependency` |
| `dev.keynest.manager.auth.ExpiredSessionCleanup` | `EI_EXPOSE_REP2`: `constructor (sessions)` | 1 | `managed-dependency` |
| `dev.keynest.manager.auth.LoginAttemptLimiter` | `CT_CONSTRUCTOR_THROW`: `constructor` | 2 | `startup-validation` |
| `dev.keynest.manager.auth.LoginAttemptLimiter` | `DMI_RANDOM_USED_ONLY_ONCE`: `constructor` | 1 | `one-time-csprng-process-key` |
| `dev.keynest.manager.auth.LoginAttemptLimiter$Attempt` | `USO_UNSAFE_OBJECT_SYNCHRONIZATION`: `complete (this$0)` | 1 | `shared-admission-monitor` |
| `dev.keynest.manager.auth.PasswordChangeService` | `EI_EXPOSE_REP2`: `constructor (transactions)` | 1 | `managed-dependency` |
| `dev.keynest.manager.auth.PasswordChangeTransactions` | `EI_EXPOSE_REP2`: `constructor (em)` | 1 | `managed-dependency` |
| `dev.keynest.manager.auth.PasswordChangeTransactions` | `EI_EXPOSE_REP2`: `constructor (sessions)` | 1 | `managed-dependency` |
| `dev.keynest.manager.auth.SessionRevocationTransactions` | `EI_EXPOSE_REP2`: `constructor (em)` | 1 | `managed-dependency` |
| `dev.keynest.manager.auth.SessionRevocationTransactions` | `EI_EXPOSE_REP2`: `constructor (sessions)` | 1 | `managed-dependency` |
| `dev.keynest.manager.config.CorsProperties` | `EI_EXPOSE_REP`: `getAllowedOrigins (allowedOrigins)` | 1 | `operator-configuration-binding` |
| `dev.keynest.manager.config.RateLimitProperties` | `EI_EXPOSE_REP`: `getTrustedProxies (trustedProxies)` | 1 | `operator-configuration-binding` |
| `dev.keynest.manager.config.VaultKeyValidator` | `EI_EXPOSE_REP2`: `constructor (properties)` | 1 | `managed-dependency` |
| `dev.keynest.manager.policy.AccountPasswordPolicyService$Rules` | `EI_EXPOSE_REP`: `bannedRegexes (bannedRegexes)` | 1 | `request-policy-transport` |
| `dev.keynest.manager.policy.AccountPasswordPolicyService$Rules` | `EI_EXPOSE_REP`: `bannedSubstrings (bannedSubstrings)` | 1 | `request-policy-transport` |
| `dev.keynest.manager.policy.AccountPasswordPolicyService$Rules` | `EI_EXPOSE_REP2`: `constructor (bannedRegexes)` | 1 | `request-policy-transport` |
| `dev.keynest.manager.policy.AccountPasswordPolicyService$Rules` | `EI_EXPOSE_REP2`: `constructor (bannedSubstrings)` | 1 | `request-policy-transport` |
| `dev.keynest.manager.security.PasswordHashingService` | `CT_CONSTRUCTOR_THROW`: `constructor` | 1 | `startup-validation` |
| `dev.keynest.manager.tools.ToolsController` | `CT_CONSTRUCTOR_THROW`: `constructor` | 1 | `startup-validation` |
| `dev.keynest.manager.tools.ToolsController` | `EI_EXPOSE_REP2`: `constructor (demoProperties)` | 1 | `managed-dependency` |
| `dev.keynest.manager.vault.VaultController` | `EI_EXPOSE_REP2`: `constructor (vaultService)` | 1 | `managed-dependency` |
| `dev.keynest.manager.vault.VaultExportSelection` | `EI_EXPOSE_REP`: `ids (ids)` | 1 | `unmodifiable-stream-selection` |
| `dev.keynest.manager.vault.VaultExportSnapshots` | `EI_EXPOSE_REP2`: `constructor (em)` | 1 | `managed-dependency` |
| `dev.keynest.manager.vault.VaultExportSnapshots` | `EI_EXPOSE_REP2`: `constructor (vault)` | 1 | `managed-dependency` |
| `dev.keynest.manager.vault.VaultItem` | `EI_EXPOSE_REP`: `getEnvelopeBytes (envelopeBytes)` | 1 | `managed-ciphertext-entity` |
| `dev.keynest.manager.vault.VaultItem` | `EI_EXPOSE_REP2`: `constructor (envelopeBytes)` | 1 | `managed-ciphertext-entity` |
| `dev.keynest.manager.vault.VaultItem` | `EI_EXPOSE_REP2`: `setEnvelopeBytes (envelopeBytes)` | 1 | `managed-ciphertext-entity` |
| `dev.keynest.manager.vault.VaultMetadataQueries` | `EI_EXPOSE_REP2`: `constructor (em)` | 1 | `managed-dependency` |
| `dev.keynest.manager.vault.VaultQueryService$Overview` | `EI_EXPOSE_REP`: `attention (attention)` | 1 | `owner-metadata-transport` |
| `dev.keynest.manager.vault.VaultQueryService$Overview` | `EI_EXPOSE_REP`: `recent (recent)` | 1 | `owner-metadata-transport` |
| `dev.keynest.manager.vault.VaultQueryService$Overview` | `EI_EXPOSE_REP2`: `constructor (attention)` | 1 | `owner-metadata-transport` |
| `dev.keynest.manager.vault.VaultQueryService$Overview` | `EI_EXPOSE_REP2`: `constructor (recent)` | 1 | `owner-metadata-transport` |
| `dev.keynest.manager.vault.VaultQueryService$SearchPage` | `EI_EXPOSE_REP`: `items (items)` | 1 | `owner-metadata-transport` |
| `dev.keynest.manager.vault.VaultQueryService$SearchPage` | `EI_EXPOSE_REP2`: `constructor (items)` | 1 | `owner-metadata-transport` |
| `dev.keynest.manager.vault.VaultRestorePlans$Ticket` | `EI_EXPOSE_REP`: `entries (entries)` | 1 | `restore-ticket-transport` |
| `dev.keynest.manager.vault.VaultRestorePlans$Ticket` | `EI_EXPOSE_REP2`: `constructor (entries)` | 1 | `restore-ticket-transport` |
| `dev.keynest.manager.vault.VaultRestoreService` | `EI_EXPOSE_REP2`: `constructor (em)` | 1 | `managed-dependency` |
| `dev.keynest.manager.vault.VaultRestoreService` | `EI_EXPOSE_REP2`: `constructor (vault)` | 1 | `managed-dependency` |
| `dev.keynest.manager.vault.VaultService` | `CT_CONSTRUCTOR_THROW`: `constructor` | 1 | `startup-validation` |
| `dev.keynest.manager.vault.export.PortableVaultFormat` | `REC_CATCH_EXCEPTION`: `loadSchema` | 1 | `sanitized-failure-boundary` |
| `dev.keynest.policy.schema.PasswordPolicyDocument` | `EI_EXPOSE_REP`: `appliesTo (appliesTo)` | 1 | `mutable-policy-document` |
| `dev.keynest.policy.schema.PasswordPolicyDocument` | `EI_EXPOSE_REP2`: `setAppliesTo (appliesTo)` | 1 | `mutable-policy-document` |

## Activation foundation source review (#336)

The two existing `AuthService` constructor findings still retain the Spring-managed EntityManager/session repository references; constructor ownership and dependency handling are unchanged. Retiring direct signup and checking account eligibility does not add an exposed mutable value or suppress another finding. Only that reviewed source digest is refreshed after formatting; existing fingerprints, counts, threshold and rationale remain enforced. Real analyzer/CI evidence belongs to the implementation PR.

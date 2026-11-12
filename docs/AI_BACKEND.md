# BYOK policy explanations

The Manager API and web workflow implement [ADR-0010](./ADR-0010-ephemeral-byok-policy-explanation.md). AI explanations are disabled by default. Existing vault, tools and readiness routes do not invoke a provider.

## Contract

`POST /api/v1/ai/policy/explain` requires the existing Manager session bearer in `Authorization` and a per-invocation provider key in `X-Keynest-AI-Key`. Its only JSON fields are:

```json
{"providerId":"openai","policyId":"keynest.default.v1"}
```

Unknown/duplicate fields, trailing JSON, non-text identifiers and bodies over 8192 bytes are rejected. Duplicate key headers and credentials outside 1–4096 printable non-space ASCII characters are rejected without echo. Only the built-in default policy is supported. There is no custom prompt, endpoint, model, user policy, vault lookup or mutation input.

The response contains `providerId`, `policyId` and `explanation`, with `Cache-Control: no-store`. The explanation is untrusted plain text, nonblank and at most 4000 Java UTF-16 characters. Manager Web renders the explanation through text binding and clears the key input on submission. Credentials remain request-scoped with best-effort mutable-buffer clearing; Java/HTTP/runtime String copies cannot be guaranteed erased.

The provider receives only 11 scalar policy facts: min/max length; upper/lower/digit/symbol requirements; minimum categories; ambiguity, whitespace and username-substring flags; and optional max age. Policy IDs, descriptions, denylists/regexes, accounts, metadata, passwords, envelopes and keys are absent from prompt data.

## Configuration and limits

- `KEYNEST_AI_ENABLED` defaults to false.
- `KEYNEST_AI_OPENAI_MODEL` has no default. The operator must review a model supporting Responses structured JSON output with a single assistant message. Blank/invalid model configuration fails the invocation without network access and does not prevent core startup.
- The first adapter uses only `https://api.openai.com/v1/responses`. No endpoint/proxy override is exposed. The package-private test constructor permits only a loopback HTTP mock at the same path.
- Transport uses normal TLS, no redirects, an explicit direct proxy selector, HTTP/1.1, no application retries, a 3-second connection bound and 15-second total future/request bound. Timed-out/interrupted work is cancelled locally; cancellation cannot guarantee upstream billing stops.
- Responses are bounded to 32768 bytes while reading; non-identity content encoding is rejected. The request sets `store:false`, non-streaming output, an empty tools list, 512 output tokens and strict single-field JSON output. Incomplete/refusal/tool/multiple-message/malformed/extra-field responses are rejected. Output parsing and reflection checks are still enforced locally.
- Admission has no queue: two active calls, five admitted requests per authenticated user/minute and twenty per process/minute, using fixed windows. Counters reset each minute and retain at most twenty distinct user IDs per window. Repeated lease close cannot increase concurrency. Replicas multiply these bounds; this is not a distributed limiter or monetary guarantee.

The local Compose file intentionally does not forward AI settings or provider keys. Enabling AI in a deployed container needs explicit operator configuration. No server-wide provider key is configured or read. The key is used only in the selected request's upstream authorization header, never prompt/telemetry/persistence.

## Errors and events

All AI failures return a fixed `error` code with no-store caching. Existing session authentication retains its own 401 contract.

| HTTP | Codes |
| --- | --- |
| 400 | AI_INVALID_REQUEST, AI_PROVIDER_UNSUPPORTED, AI_POLICY_UNSUPPORTED, AI_CREDENTIAL_REQUIRED, AI_CREDENTIAL_INVALID |
| 413 | AI_REQUEST_TOO_LARGE |
| 429 | AI_RATE_LIMITED, AI_CAPACITY_LIMITED |
| 503 | AI_DISABLED |
| 504 | AI_TIMEOUT |
| 502 | AI_PROVIDER_AUTH_FAILED, AI_UNAVAILABLE, AI_INVALID_OUTPUT |

Only fixed `AI_POLICY_EXPLAIN` HTTP outcome events are added under [ADR-0009](./ADR-0009-sanitized-http-security-events.md). No prompt, response text, key, provider request identifier or policy metadata is logged. Upstream exception/cause/body text is not returned. An exact credential echo is rejected in the raw response and decoded explanation; transformed encodings or a malicious provider are outside that narrow reflection check.

## Evidence and remaining gates

The fake-adapter tests exercise minimal projection, redacted/closed credentials, no-call rejection paths, quotas, concurrency, provider failures and output validation. Mock HTTP tests exercise serialization/credential separation, successive credentials, redirect/proxy denial, bounded reads, strict parsing, slow-body timeout, interruption and destination restrictions. Spring tests cover authentication, disabled/core behavior, duplicate/oversized inputs, stable errors and log redaction. No test contacts the real provider.

Manager Web CI uses mocked HTTP to verify the fixed request body and separate credential header, clearing the input, rendering provider output as text, suppressing raw provider errors and cancelling the request on navigation.

### Operator verification

1. With default settings, sign in to Manager and open **Policy explanation**. Submit a dummy key. The page should show a generic failure, the input should be empty, and the API should return `503 AI_DISABLED`. Existing vault and tools routes should remain usable.
2. For live verification, use an isolated deployment and explicitly approve the provider data disclosure and possible charges. Configure `KEYNEST_AI_ENABLED=true` and set `KEYNEST_AI_OPENAI_MODEL` to the reviewed model. Use a dedicated test key entered directly in the page.
3. Submit once and verify a plain-text explanation and an empty key field. Record the environment, model and outcome without storing the key or copying raw request headers.
4. Restore `KEYNEST_AI_ENABLED=false` and verify the disabled behavior again. This rollback requires no schema, key or session migration.

Live-provider verification remains pending.

## Provider references checked for implementation

- [Responses creation API](https://developers.openai.com/api/reference/resources/responses/methods/create)
- [Structured outputs](https://developers.openai.com/api/docs/guides/structured-outputs?api-mode=responses)
- [Provider data controls](https://developers.openai.com/api/docs/guides/your-data)

`store:false` requests no Responses application-state storage; it is not a claim of universal zero retention. Provider abuse-monitoring/retention terms and account controls still apply. No provider account, plan access or model availability is assumed.

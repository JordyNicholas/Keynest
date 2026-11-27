# Security

Start with the [security model and limits](overview.md) for the public trust and recovery overview. The [threat model](threat-model.md) covers assets, implemented controls, and residual risks. Use the [security policy](../../SECURITY.md) to report a suspected vulnerability privately.

- **Authentication and sessions** · [API and handler authentication](controls/api-authentication.md), [sign-in verification](controls/login-verification.md), [sign-in throttling](controls/login-throttling.md), [expired-session cleanup](controls/session-cleanup.md)
- **Browser delivery** · [Production Web CSP](controls/web-csp.md), [browser verification fixtures](controls/web-csp-verification.md), [API headers and HTTPS ingress](../operators/deployment.md#api-response-headers-and-https-ingress)
- **Availability and evidence** · [Admission and saturation](controls/availability.md), [sanitized security events](events.md), [dependency scanning](dependency-scanning.md), [historical security reviews](../archive/README.md#security-reviews)
- **Key custody and recovery** · [Vault-key and item binding decision](../architecture/decisions/ADR-0006-manager-vault-key-and-aad.md), [database recovery](../operators/database-migrations.md), [portable backup format](../api/vault-portability.md)

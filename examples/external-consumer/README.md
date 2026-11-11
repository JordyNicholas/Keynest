# Standalone Keynest library consumer

This Maven project depends only on `keynest-seal`, `keynest-policy` and `keynest-generate`. It has its own POM, no Keynest parent and no root reactor membership. Maven Enforcer rejects direct or transitive Manager/Spring dependencies. No API process, database, cloud service, AI provider or configured key is needed to run the example.

## Build and run

Prerequisites: Java 21+ and Maven 3.9+. From the repository root, install the current development artifacts into your local Maven repository:

```bash
mvn -B -pl libs/keynest-seal,libs/keynest-policy,libs/keynest-generate -am install -DskipTests
mvn -B -f examples/external-consumer/pom.xml verify
```

The first command selects only the libraries and their build parents, not Manager. The second is an independent Maven invocation; it compiles and executes the example during `verify`. Library tests run separately in the repository's Java CI jobs. To rerun the compiled example, use `mvn -f examples/external-consumer/pom.xml exec:java`.

CI builds and runs the consumer on Java 21 and Java 25. This demonstrates local snapshot consumption; it does not claim published artifacts in Maven Central or GitHub Packages. Once release artifacts are published, change `keynest.version` and configure the documented artifact repository instead of the local install step.

## What the executable verifies

It loads the bundled schema-backed default policy, generates a password and validates it with the same policy. It seals/serializes/opens the value, rewraps under a second ephemeral key with unchanged AAD, verifies the value is preserved, and requires wrong-key/wrong-context opens to fail.

Only a fixed PASS line is printed. Keys, generated values, plaintext and envelope bytes are never printed or written to files. Caller-owned key/plaintext buffers are cleared best-effort. The generator returns an immutable Java String, and JVM copies may remain; memory erasure is not guaranteed. See [Seal's buffer-hygiene notes](../../docs/ADR-0003-keynest-seal.md#appendix--sensitive-buffer-hygiene).

The example's AAD is consumer-owned and is not Manager's owner/item binding format. Integrators must define their own canonical versioned context, authorization, key custody, backup/rotation and recovery rules. Both example keys are discarded after the run; this demonstrates the public API, not persistent key management.

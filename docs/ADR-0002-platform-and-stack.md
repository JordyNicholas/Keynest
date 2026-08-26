# ADR 0002 — Platform split and Spring/Angular stack

## Status

Accepted (product grooming)

## Context

Keynest started as a Next.js password-generator prototype. The product intent is broader:

1. Exportable tools for encrypt/decrypt, validate, generate, and rotate.
2. A first-party **Manager** product (vault UX) that uses those tools.
3. Preference for a different stack than the author’s usual frontend-heavy JS path: **Java/Spring Boot** + **Angular**.

## Decision

1. Structure the repo as a **platform** (`keynest-seal`, `keynest-policy`, `keynest-generate`) plus **apps** (`manager-api`, `manager-web`).
2. Implement libraries and Manager API in **Java 21 LTS + Spring Boot 4** (see [ADR-0005](./ADR-0005-java-and-toolchain-policy.md)).
3. Implement Manager UI in **Angular 21 LTS** (see ADR-0005).
4. Treat the existing Next.js app as a disposable prototype; do not extend it.
5. Manager must depend on the public library APIs only (dogfood the tools).

## Consequences

- Greenfield module layout; migration cost from Next.js is accepted.
- JVM-first distribution (Maven). Non-JVM consumers need a future HTTP sidecar or ports.
- Clearer separation of “SDK product” vs “vault product,” matching the business intent.
- Team learns/uses Spring + Angular conventions; docs and CI must cover both JVM and Node (Angular) toolchains.

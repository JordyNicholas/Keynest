# ADR 0005 — Java and toolchain policy

## Status

Accepted

## Context

Keynest ships **embeddable JVM libraries** (`keynest-seal`, `keynest-policy`, `keynest-generate`) plus a **Manager** product (Spring Boot API + Angular UI). We need a stable, documented policy for:

- Which Java versions we compile for and run on
- When to adopt Java 25 LTS
- Spring Boot and Angular support windows

Earlier grooming ([ADR-0002](./ADR-0002-platform-and-stack.md)) chose Java 21 and “current LTS” Angular. Spring Boot 3.4.2 and Angular 19 have since fallen out of vendor support.

## Decision

### Java (compile + runtime policy)

1. **Compile target:** `maven.compiler.release=21` for all modules (libs + Manager API). Published Maven artifacts remain usable on **Java 21+** integrator JVMs.
2. **Runtime (Manager API / Docker / CI primary):** **Java 21 LTS** (Temurin). Revisit **Java 25 LTS** as a deliberate platform bump when embeddable-lib consumers and Spring Boot alignment are ready — not opportunistically.
3. **CI matrix:** Run `mvn test` on **JDK 21 and JDK 25** while keeping `--release 21`, to detect future JDK incompatibilities early without changing bytecode requirements.

### Spring Boot

1. Upgrade Manager API from Spring Boot 3.4.x to **Spring Boot 4.0.x** (supported OSS line on Java 21).
2. Use modular starters (`spring-boot-starter-webmvc`, `spring-boot-starter-webmvc-test`) per Boot 4 migration guide.
3. Do not pin Jackson 2 overrides unless a third-party library requires it; prefer Boot-managed Jackson 3.

### Angular

1. Manager Web targets **Angular 21 LTS** (supported through mid-2027 per [angular.dev](https://angular.dev/reference/releases)).
2. Angular Material (when adopted per [ROADMAP.md](./ROADMAP.md) P2) must match the Angular major version.

### Node

1. **Node 20+** for Manager Web build (unchanged).

## Consequences

- External JVM services can depend on Keynest libs without upgrading to Java 25.
- CI catches JDK 25 runtime issues before we flip the compile target.
- Spring Boot 4 migration cost is paid once (test starter modules, Jackson 3 defaults); avoids running on EOL Boot 3.4.
- Angular 21 upgrade restores security patches for the Manager UI.
- Future “Keynest platform 2.0” may raise `--release` to 25 with semver and README notice — tracked in ROADMAP, not ad hoc.

## References

- [Oracle Java SE support roadmap](https://www.oracle.com/java/technologies/java-se-support-roadmap.html)
- [Spring Boot 4.0 migration guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)
- [Angular version support](https://angular.dev/reference/releases)

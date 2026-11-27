# H7 architecture regression gates

Roadmap M1 H7 / #315. This is the first quality-gate slice, not completion of H7. Spotless, static analysis and measured JaCoCo floors remain separate work. No production package is moved and no runtime/security contract is changed.

## Enforced boundaries

`apps/manager-api/src/test/java/dev/keynest/manager/architecture/ArchitectureRulesTest.java` imports production bytecode from Manager and all three public library dependencies, excluding test output. It runs as an ordinary JUnit test in the existing Java 21/25 `mvn -B test` checks; no extra CI privilege, action or test engine is needed.

| Rule | Reason and scope |
| --- | --- |
| Libraries cannot depend on Manager, Spring, Jakarta persistence or servlet packages | Preserve independent policy/generation/Seal consumption. Jackson/schema validation and native Argon2 remain legitimate library dependencies. The standalone consumer's dependency-level ban remains an independent check. |
| Spring `@Service` types cannot depend on `dev.keynest.manager.api..` | Keep use cases independent of HTTP edge helpers, requests, controllers and route constants. Existing feature controllers in `auth`/`vault` may use `ApiPaths`; this rule targets services rather than banning all feature-to-edge references. |
| Spring `@Service` types cannot depend on `@RestController` types | Preserve controller-to-use-case direction even when the controller lives outside `api`. |

Imports assert concrete anchors from every production module and critical services. Each rule fails on an empty selection. Explicit negative fixtures demonstrate forbidden Manager/Spring library dependencies, a service-to-API reference and a service-to-controller reference outside `api`; an unrelated import demonstrates empty-selection refusal. The invalid fixtures are test-only, outside the application's `dev.keynest.manager` component-scan root, and excluded from production imports. There is no frozen-violation store or ignore list.

These are dependency rules, not proof of thin controllers, complete clean architecture, absence of reflection/network calls, authorization or tenant isolation. Services outside the Spring `@Service` convention and new transports need explicit rule review. Existing transport exceptions in services are not removed by this slice. Export/restore stays in its current package until a separately reviewed boundary/refactoring change is justified.

## Dependency, validation and rollback

ArchUnit core is pinned to `com.tngtech.archunit:archunit:1.5.1` with **test scope only in Manager**. It is not added to library artifacts or API runtime dependencies. The core library works with the existing JUnit assertions without introducing a new engine; see the [official installation guide](https://www.archunit.org/userguide/html/000_Index.html#_other_test_frameworks) and [release notes](https://github.com/TNG/ArchUnit/releases/tag/v1.5.1). Existing dependency inventory includes test scope and must scan the new dependency on the exact candidate. Tool version/API checks are not a substitute for Java 21/25 runtime results.

Run the full repository gates with `mvn -B test` (Docker is required for existing PostgreSQL tests). For a focused iteration: `mvn -B -pl apps/manager-api -am test -Dtest=ArchitectureRulesTest -Dsurefire.failIfNoSpecifiedTests=false`. The latter omits unrelated tests and is not release evidence. Record actual final-SHA seven-check and dependency results on #315/the PR; no result is inferred from these instructions.

Rollback removes the test-only dependency/property, rule/fixture files and this guide. It reopens architecture regression detection gaps without changing runtime bytecode, data, keys, sessions or published library APIs. Further quality gates need their own evidence and review; this slice does not satisfy the M1 exit by itself.

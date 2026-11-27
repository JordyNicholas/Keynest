# H7 Java coverage reporting and floors

Roadmap M1 H7 / #315, focused slice #317. JaCoCo measures executed Java bytecode; it does not prove assertions, security, absence of defects or production performance. Architecture gates landed separately in #316. Formatting and static analysis remain outstanding H7 controls.

## Measured baseline and initial floors

**[verified]** Source `92f1bc4491228d2f63f2eaa985212bcaa6424115` ran the existing tests with JaCoCo on Java 21/25 in [CI 46002555959](https://github.com/JordyNicholas/Keynest/actions/runs/46002555959). Both Java jobs passed, including all 321 Manager tests without failures/errors/skips. These are module-local test measurements, not an aggregate that lets Manager tests inflate library floors. The initial draft reported coverage before floors were selected; the final candidate must run enforcement and all seven checks again.

| Module | Java 21 lines / branches | Java 25 lines / branches | Enforced line / branch floor |
| --- | --- | --- | --- |
| Policy | 308/356 (86.52%) / 107/174 (61.49%) | 308/356 (86.52%) / 107/174 (61.49%) | 85% / 60% |
| Generate | 40/43 (93.02%) / 21/24 (87.50%) | 40/43 (93.02%) / 21/24 (87.50%) | 90% / 85% |
| Seal | 134/154 (87.01%) / 17/34 (50.00%) | 134/154 (87.01%) / 17/34 (50.00%) | 85% / 50% |
| Manager | 2242/2374 (94.44%) / 1155/1488 (77.62%) | 2241/2377 (94.28%) / 1151/1488 (77.35%) | 90% / 75% |

The initial floors round the lower observed percentage across the two JDKs down to a five-percentage-point step. Seal's branch floor equals its measured baseline; its remaining branch gap is visible work, not a security acceptance. JDK/compiler-specific totals differ; do not average the two runs or treat these counts as permanent source constants. Changes to measured code, compiler or JaCoCo version require reviewing the evidence. Raise floors as coverage improves; do not lower them or exclude security/generated code merely to pass a regression.

## Build and enforcement

JaCoCo **0.8.16** is pinned as a Maven build plugin, not a runtime/library dependency. The [official change history](https://www.jacoco.org/jacoco/trunk/doc/changes.html) records its release and support beyond the project's Java 21/25 toolchains. No snapshot is used. The [agent](https://www.jacoco.org/jacoco/trunk/doc/prepare-agent-mojo.html) and [report](https://www.jacoco.org/jacoco/trunk/doc/report-mojo.html) goals run only with the explicit `coverage` profile. Only forked Surefire JVMs receive the agent, using a separate late-evaluated argument property plus existing `argLine` arguments. Do not set `forkCount=0`: uninstrumented test execution cannot supply the required data.

The profile instruments `dev.keynest.*` test-JVM classes and reports each module's production output without report includes/excludes. Report generation is bound after Surefire in `test`, preserving the existing lifecycle endpoint. Normal Maven, consumer, Docker and packaged production JVMs do not activate the profile. No offline class instrumentation, agent listener, class dump, application JVM flag or dependency is added to production.

Run the full coverage gate from the repository root (Docker is needed by existing PostgreSQL tests):

```bash
python3 -m unittest discover -s scripts/tests -p 'test_java_coverage.py'
mvn -B -Pcoverage clean test
python3 scripts/check_java_coverage.py
```

A successful Maven command alone reports coverage; **the following Python command enforces floors**. This deliberate CI gate checks all four reports together and closes JaCoCo's missing-data/skip behavior without adding another Maven plugin. `scripts/java-coverage-floors.json` is the single floor source. It must match every direct reactor module and contain finite, nonzero LINE and BRANCH floors. A new module without floors fails; a reactor topology change needs explicit gate review.

The gate requires nonempty `target/jacoco.exec`, production `.class` output, a bounded nonempty XML report containing classes and exactly one valid nonzero total for each required counter. Integer/Decimal comparison enforces each module/metric separately, including equality at the floor without floating-point rounding. Missing/malformed data, empty counters, invalid/disabled floors and below-floor coverage fail. `clean` prevents prior-run data from making a skipped candidate appear covered. The gate does not cryptographically prove report provenance or parse the binary execution file; CI generates outputs from the checked-out source in the same job. Do not reuse old reports as final-source evidence.

Eight Python tests exercise threshold equality, independent module/metric regressions, missing/empty execution/classes/reports, malformed/oversized XML, duplicate/invalid/zero counters, invalid/nonfinite floors, incomplete module inventory and unsafe paths. Invalid report/configuration content is not echoed to diagnostics; successful output and controlled failures contain source module/metric names and aggregate counts only.

## CI, artifacts and review limits

Java 21/25 retain their existing check names and run the three commands above. Angular, both independent consumers and both Compose/browser checks remain separate required gates. XML metadata alone is uploaded through the already pinned artifact action, with seven-day retention and no new permissions/external reporting service. Failed jobs may retain partial XML for diagnosis; an artifact's presence does not mean the gate passed. HTML reports remain local under each module's `target/site/jacoco/index.html`. Execution data, HTML/source copies, test reports/bodies, environment dumps, database files and JVM diagnostics are not uploaded by this slice.

The dependency workflow still scans runtime/test inventories. Maven build-plugin transitive dependencies are not fully established by that SBOM; the pinned plugin and official release were reviewed separately. No independent supply-chain certification, production workload test, release or publication is claimed. Exact final-source CI/scanner results and reviewed approval belong on #317/the PR. A local run blocked on unavailable cached Maven artifacts is not counted as successful execution.

Rollback reverts the profile/property, coverage-only CI steps, gate/configuration/tests and this guide together, restoring the prior Java test command. It reopens coverage regression detection without runtime/data/key/session migration. Preserve the seven existing checks, dependency scanner, test-only architecture rules and explicit image-publication behavior. This slice does not finish H7 or accept any threat-model residual risk.

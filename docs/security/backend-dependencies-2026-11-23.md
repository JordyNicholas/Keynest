# Backend dependency remediation — November 23, 2026

Follow-up to the first complete source inventory/OSV scan in #256/#257. The published `v0.1.0` remains immutable. This change requires separate reviewed-change approval and a later maintenance release; no publication is implied.

## Evidence and selected versions

Baseline [scan run 45285181016](https://github.com/JordyNicholas/Keynest/actions/runs/45285181016) generated valid inventories, scanned 151 Java and 965 Web coordinates, and returned findings (exit 1). Java matches included Jackson 2 core 2.21.5/databind 2.18.2, Jackson 3 core/databind 3.1.5 and Tomcat embed core 11.0.24. Test/development dependencies were included; inventory matches are not proof of exploitability.

| Component | Baseline | Proposed resolution |
| --- | --- | --- |
| Spring Boot BOM/plugin | 4.0.8 | 4.0.10, same framework line |
| Jackson 2 explicit databind | 2.18.2 | 2.21.8, aligned with Boot's Jackson 2 BOM |
| Jackson 2 core / Jackson 3 core and databind | 2.21.5 / 3.1.5 | Boot 4.0.10 managed 2.21.8 / 3.1.8 |
| Embedded Tomcat core/el/websocket | 11.0.24 | Explicitly aligned at released 11.0.29 |

The explicit Tomcat management is needed because importing Boot's BOM does not make overriding a parent-style property sufficient. Boot 4.0.10 manages 11.0.27, while Apache lists additional released fixes in 11.0.29. Keep the three embedded modules aligned. Remove the override only after the managed version is reviewed and at least as recent.

## Applicability and limits

The Jackson matches include parser/mapper issues; those are runtime packages used by the API/libraries, so update and retain strict input limits plus negative tests. Do not infer that every deserialization advisory is reachable: Keynest does not enable arbitrary polymorphic default typing, and authenticated backup parsing has explicit bounds.

The four baseline Tomcat advisory IDs were GHSA-9xv2-5v5q-p794, GHSA-f525-44xv-f2qj, GHSA-gcx9-497g-6cp6 and GHSA-h3x4-894j-xpx5. Conditions include DIGEST/FORM/container security constraints and HTTP/2/TLS behavior. Keynest uses its own opaque bearer/owner checks and local Compose serves HTTP behind separately configured ingress; the package-level CRITICAL label is not proof those conditions occur in the tested topology. Apache lists further HTTP/2, upgrade, filter and optional-feature fixes after 11.0.27, so use released 11.0.29 rather than stopping at the scanner's minimum fix. The unreleased 11.0.30 TLS-PSK cluster fix is not selected; Keynest does not configure Tomcat clustering/TLS-PSK. Custom operator topologies need their own review.

This is dependency maintenance, not a new crypto/envelope/key/session/database design or independent audit. Jackson 2 moves across minor versions, so compatibility must be demonstrated rather than assumed. Keep golden vectors, native payload/backup fidelity, strict transport parsing, policy behavior, Java 21/25 external consumers, PostgreSQL restore and actual packaged HTTP smokes in the verification set. Re-run the inventory scan on the patched dependency graph; do not mark it clean from a version edit or build alone. Development-tool findings are separate Web scope, not suppressed by this change.

Rollback restores prior dependency management and its known advisory matches. It does not require a database rollback because no schema is changed. Do not mutate or retag the published 0.1.0.

## Primary references

- [Spring Boot 4.0.10 managed coordinates](https://docs.spring.io/spring-boot/4.0/appendix/dependency-versions/coordinates.html)
- [Pinned Boot source](https://github.com/spring-projects/spring-boot/tree/v4.0.10/platform/spring-boot-dependencies)
- [Apache Tomcat 11 security fixes and conditions](https://tomcat.apache.org/security-11.html)
- [Jackson security advisories](https://github.com/FasterXML/jackson-databind/security/advisories)

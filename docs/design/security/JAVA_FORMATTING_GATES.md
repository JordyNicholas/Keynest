# H7 incremental Java formatting

Roadmap M1 H7 / #315, focused slice #319. This gate enforces the existing [two-space coding standard](../../CODING_STANDARDS.md) for newly adopted Java files. It does not claim that untouched historical source, Angular or every repository language has already been formatted.

## Tools and adoption boundary

Spotless Maven **3.12.0** and Eclipse JDT **4.37** are pinned in an explicit, non-inherited root `formatting` profile. The [official Spotless release history](https://github.com/diffplug/spotless/blob/main/plugin-maven/CHANGES.md) records the plugin release; the [Maven guide](https://github.com/diffplug/spotless/tree/main/plugin-maven#eclipse-jdt) documents Eclipse configuration and Git-based incremental enforcement. Eclipse handles this repository's Java 21 source baseline; actual execution on Java 21/25 must be demonstrated by the candidate checks, not inferred from a version setting.

The committed `config/eclipse-java-formatter.xml` sets spaces, two-space indentation and one indentation-level continuation (two spaces), including wrapped parameters/arguments and arrays. The line split is 120. UTF-8, LF, trailing-whitespace removal and final newline are enforced. Comment reflow, import/member rearrangement, cleanup/refactoring steps and formatter-off tags are not enabled. This is source formatting, not static analysis or a security proof. Review future formatting diffs, especially literals/text blocks; do not treat a formatter upgrade as automatically behavior-neutral.

The fixed adoption commit is **`c609e9634b731afc0abc4f1b94a09de8f46a1207`** (approved coverage #318 merge). Spotless checks whole Java files changed/new since that point, not just edited lines. Untouched legacy files are outside adoption until changed; editing one adopts the whole file. The reference stays fixed after merging, so a subsequent main update cannot erase a violation. Moving the baseline, changing profiles/tools or adding exclusions requires separate reviewed evidence. There is no moving `HEAD`/main default or permissive fallback when the reference is unavailable.

Root source patterns cover main and test Java under every `libs/*`, Manager API and the standalone external consumer. The consumer remains standalone: it does not inherit the root POM or gain a formatter/runtime dependency. Generated `target` output and other languages are not Java source targets. New source roots/apps need explicit gate-scope review. Stage new files before local checking so the Git-based adoption boundary can observe them; CI checks the committed candidate. Ignored/untracked or out-of-scope files are not a full-repository cleanliness guarantee.

The small Manager entrypoint is the only initial Java formatting change, with whitespace-only differences. No business code, policy/crypto behavior, API, key/session/schema contract or saved data is rewritten.

## Local commands and checkout requirements

From the repository root, with the supported JDK and Maven:

```bash
python3 scripts/java_formatting.py check
python3 scripts/java_formatting.py apply
python3 scripts/java_formatting.py check
```

The wrapper first parses the configured fixed commit and uses `git cat-file` to require it, then runs `mvn -B -N -Pformatting spotless:check` or `spotless:apply`. This guard does not rely on formatter caches/reference diagnostics. Bare Maven goals still invoke Spotless but are not the complete checked-history gate. `check` is read-only. `apply` changes adopted working-tree Java files; inspect that diff before committing. No hook, automatic commit, member/import rewrite or application refactor is installed. The `-N` root command intentionally formats all configured source patterns without changing the consumer/child build graphs.

The adoption commit and Git metadata must be available. Use a normal full checkout or explicitly fetch the needed history; a shallow checkout missing the reference must fail rather than pass empty. Java CI uses full checkout history with checkout credentials still unpersisted and permissions still read-only. Normal builds, Docker build contexts and published library POM consumers do not activate the profile and do not need this Git history. No formatter dependency or JVM agent is added to application runtime.

## Real rejection and repair probes

`python3 scripts/verify_java_formatting.py` exercises **committed HEAD** in a uniquely allocated, detached temporary worktree. It never applies formatting to the caller's checkout. Commit formatter configuration changes before using this harness; use the commands above for uncommitted working-tree edits.

On each JDK, the harness first checks the actual candidate, then stages payload-free malformed Java probes for a new library main class, Manager test class, both consumer main/test classes and an edited legacy policy enum. Each must fail specifically for formatting, change under `apply`, then pass `check`; new-class probes also check two-space method and parameter-continuation indentation. It temporarily replaces only the private copy's adoption reference with a nonexistent commit, requires rejection by the explicit history guard before Maven and verifies recovery after restoration. Maven source-diff output is captured, not printed/uploaded; only fixed categories and synthetic source-path outcomes are reported. The private index/files and worktree are cleaned up on success/failure, without force-updating any branch or other worktree.

These probes verify gate behavior, not application tests. CI runs them before existing coverage rejection cases and the full Java test suite, then enforces measured coverage floors. Both Java job names remain unchanged; the job has a 15-minute bound and each probe subprocess has a three-minute bound. The wrapper resolves Maven from PATH (including its Windows launcher), bounds Git lookup to 30 seconds and the Maven process to two minutes, leaving time for child exit before private-worktree cleanup. OS-specific execution beyond the actual CI environment is not claimed. Angular, both independent consumers and packaged/hosted Compose/browser checks stay required. No source-bearing formatter artifact is uploaded, and no CI permission, action SHA, external reporting service or opt-in publication behavior is changed.

## Evidence, limits and rollback

Record exact final-source seven-check/scanner results and both-JDK formatter probe outcomes on #319/the PR. Local XML/Python syntax, actionlint, whitespace/token-diff checks do not substitute for executing the formatter. Build-plugin/formatter transitive supply-chain coverage is not established by the existing runtime/test dependency SBOM; pins and upstream documentation were reviewed separately. No independent audit, production performance, deployment or release is claimed. Coverage and architecture are already separate H7 controls; static analysis remains outstanding.

Rollback removes the formatting profile, formatter configuration, wrapper, Java-only CI/history/probe steps and this guide/standards link, optionally reverting the entrypoint whitespace change. Restore the prior Java-job timeout/checkout settings if reverting that slice. Preserve all seven checks, coverage/architecture tests, dependency scanning and explicit publication. This reopens incremental-formatting detection without a runtime/data migration and does not accept any threat-model residual risk.

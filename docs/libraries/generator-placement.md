# Java generator category placement

Roadmap M1 H5 / #311. The Java library previously injected required lower/upper/digit/symbol characters into leading positions. Its final charset reconciliation did not shuffle them. On the inspected main source, all 5,000 synthetic 16-character samples placed those four categories at indices 0/1/2/3. No sample values were emitted. Exact candidate/merge evidence is recorded on the linked issue/PR; this document alone is not runtime proof.

## Change and scope

After category injection and charset reconciliation, permute the complete candidate array with descending Fisher–Yates swaps. For index `i`, draw `j` using the existing injected `SecureRandom.nextInt(i + 1)`, including the current position, and swap the two characters. There is no remainder-based index selection, new RNG or custom cryptographic primitive. The public constructor/default CSPRNG, current ASCII alphabets, category/exclusion rules, minimum output length and bounded retry count remain unchanged. The candidate is validated only after placement; forbidden patterns and every other existing validator check still decide whether it may be returned.

This removes required categories' fixed leading positions. A uniform permutation of a candidate is not a promise of uniform sampling over every policy-valid password. Injection, character choices, repetitions and rejection by constraints shape accepted outputs. Position-sensitive rules intentionally affect accepted positions. This is not an RNG certification, entropy estimate or replacement for choosing an adequate length/policy. The browser generator is separate and is not changed by this Java-library slice.

## Regression evidence design

`PasswordGeneratorPlacementTest` uses a private test-only deterministic source through the existing injection constructor. Production still constructs `SecureRandom`; the seeded `java.util.Random` delegate never enters production sources or default behavior. A fixed seed makes regression results reproducible across the supported JDKs.

For an unconstrained four-character/four-required-category policy, every returned sample contains exactly one of each category. Across 24,000 samples, test all 24 category orders and each category at each position. Marginal expected count is 6,000 with a 5% band (±300); order expected count is 1,000 with a 15% band (±150). These broad bands catch forced-position/obvious permutation regressions without demanding an exact seeded sequence. With independent uniform permutations their widths exceed four marginal standard deviations; the actual fixed-seed tests are repeatable regression checks, not production randomness significance tests. Failures report category/order counts only, never generated password values.

A second fixed-seed distribution case generates 12,000 sixteen-character candidates and compares every position with the observed global category mix using a 10% band. This accounts for different alphabet sizes/injection counts and rejects a permutation incorrectly limited to the four-character prefix.

Additional cases cover a feasible digit-first rule that rejects the original fixed lower-first layout, all sixteen required-category combinations, both ambiguity modes, lengths 8/16/128, denylist/repetition restrictions, a one-character supported policy, and an unsatisfiable policy's configured retry failure. A generous test-only RNG draw guard prevents a retry regression from hanging the negative test. Existing default-policy checks keep the generate→validate invariant and omit password values from assertion messages.

## Compatibility and rollback

No dependency, schema, API, key/envelope, session or stored-password migration. Only newly generated output and RNG draw order change; callers must not depend on exact seeded strings or leading-category positions across versions. Existing Vault values and native backup files remain untouched. Rolling the library/application back restores the earlier behavior for future generation; it does not rewrite already saved values. Validation still fails when the attempt budget cannot satisfy a policy. No new total-runtime or satisfiability guarantee for arbitrary caller-supplied regular expressions is introduced.

Require Java 21/25 library tests, standalone consumers and the existing seven application/dependency gates on the exact candidate before merge. This verification is separate from an independent cryptographic/security audit.

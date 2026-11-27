package dev.keynest.generate;

import dev.keynest.policy.PasswordPolicy;
import dev.keynest.policy.PasswordValidator;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PasswordGeneratorPlacementTest {
  @Test
  void requiredCategoryPositionsAndOrdersAreReproduciblyBalanced() {
    int samples = 24_000;
    var generator = new PasswordGenerator(new SeededRandom(0x4b45594e455354L), 100);
    var policy = policy(4, 15, false);
    int[][] counts = new int[4][4];
    Map<String, Integer> orders = new TreeMap<>();
    for (int sample = 0; sample < samples; sample++) {
      String value = generator.generate(policy);
      assertTrue(PasswordValidator.validate(value, policy).valid(), "Sample satisfies the four-category policy");
      var order = new StringBuilder(4);
      for (int position = 0; position < value.length(); position++) {
        int category = category(value.charAt(position));counts[position][category]++;
        order.append("luds".charAt(category));
      }
      orders.merge(order.toString(), 1, Integer::sum);
    }
    assertEquals(24, orders.size(), "All four-category permutations must be reachable");
    // Fixed seed makes these regression bounds repeatable, not a production RNG certification.
    // Marginals: 6000 +/- 300 (5%); complete orders: 1000 +/- 150 (15%).
    for (int position = 0; position < 4; position++) for (int category = 0; category < 4; category++) {
      assertTrue(Math.abs(counts[position][category] - samples / 4) <= samples / 80,
        "Category position count outside regression bound: position=" + position + ", category=" + category + ", count=" + counts[position][category]);
    }
    for (var order : orders.entrySet()) {
      assertTrue(Math.abs(order.getValue() - samples / 24) <= samples / 160,
        "Category permutation count outside regression bound: order=" + order.getKey() + ", count=" + order.getValue());
    }
  }

  @Test
  void categoryMixIsBalancedAcrossTheWholeLongerCandidate() {
    int length = 16, samples = 12_000;
    var generator = new PasswordGenerator(new SeededRandom(0x504c4143454d454eL), 100);
    var policy = policy(length, 15, false);
    int[][] counts = new int[length][4];
    int[] totals = new int[4];
    for (int sample = 0; sample < samples; sample++) {
      String value = generator.generate(policy);
      for (int position = 0; position < length; position++) {
        int category = category(value.charAt(position));counts[position][category]++;totals[category]++;
      }
    }
    // Empirical category totals account for different alphabet sizes and forced injections.
    // Checking every position also rejects a shuffle limited to the four-character prefix.
    for (int position = 0; position < length; position++) for (int category = 0; category < 4; category++) {
      double expected = (double) totals[category] / length;
      assertTrue(Math.abs(counts[position][category] - expected) <= expected * 0.10,
        "Whole-candidate category count outside regression bound: position=" + position + ", category=" + category + ", count=" + counts[position][category]);
    }
  }

  @Test
  void positionSensitiveRulesAreValidatedAfterPermutation() {
    var policy = PasswordPolicy.builder().minLength(8).maxLength(8).requireLower(true).requireUpper(true)
      .requireDigit(true).requireSymbol(true).minCategories(4).excludeAmbiguous(false)
      .bannedRegexes(List.of("^[^0-9]")).build();
    String value = new PasswordGenerator(new SeededRandom(17), 256).generate(policy);
    assertTrue(Character.isDigit(value.charAt(0)), "Position-sensitive rule must hold in the returned candidate");
    assertTrue(PasswordValidator.validate(value, policy).valid(), "Returned candidate satisfies every rule");
  }

  @Test
  void categoryExclusionAndDenylistRulesSurvivePlacementAcrossPoliciesAndLengths() {
    for (int length : new int[]{8, 16, 128}) for (int mask = 0; mask < 16; mask++) {
      for (boolean ambiguous : new boolean[]{false, true}) {
        var policy = PasswordPolicy.builder().minLength(length).maxLength(length)
          .requireLower((mask & 1) != 0).requireUpper((mask & 2) != 0)
          .requireDigit((mask & 4) != 0).requireSymbol((mask & 8) != 0).minCategories(Integer.bitCount(mask))
          .excludeAmbiguous(ambiguous).bannedSubstrings(List.of("password", "admin"))
          .bannedRegexes(List.of("(.)\\1{3,}")).build();
        var generator = new PasswordGenerator(new SeededRandom(1000L + length * 32L + mask), 1000);
        String allowed = PasswordValidator.charsetFor(policy);
        for (int sample = 0; sample < 10; sample++) {
          String value = generator.generate(policy);
          assertEquals(length, value.length(), "Requested minimum length is retained");
          assertTrue(PasswordValidator.validate(value, policy).valid(), "Required categories and denylist remain enforced");
          assertTrue(value.chars().allMatch(character -> allowed.indexOf(character) >= 0), "Characters remain in the allowed alphabet");
          if (ambiguous) assertTrue(value.chars().noneMatch(character -> PasswordValidator.AMBIGUOUS.indexOf(character) >= 0), "Ambiguous characters remain excluded");
        }
      }
    }
  }

  @Test
  void aSingleCharacterPolicyNeedsNoInvalidShuffleBound() {
    var policy = PasswordPolicy.builder().minLength(1).maxLength(1).requireLower(true).requireUpper(false)
      .requireDigit(false).requireSymbol(false).minCategories(0).excludeAmbiguous(true).build();
    String value = new PasswordGenerator(new SeededRandom(9), 10).generate(policy);
    assertEquals(1, value.length());
    assertTrue(PasswordValidator.validate(value, policy).valid(), "Small supported policy remains valid");
  }

  @Test
  void unsatisfiablePoliciesStillStopAtTheConfiguredAttemptLimit() {
    var policy = PasswordPolicy.builder().minLength(8).maxLength(8).bannedRegexes(List.of(".")).build();
    var generator = new PasswordGenerator(new SeededRandom(21, 1000), 3);
    var failure = assertThrows(IllegalStateException.class, () -> generator.generate(policy));
    assertTrue(failure.getMessage().contains("after 3 attempts"), "Failure reports the configured bounded retry limit");
  }

  private static PasswordPolicy policy(int length, int mask, boolean ambiguous) {
    return PasswordPolicy.builder().minLength(length).maxLength(length)
      .requireLower((mask & 1) != 0).requireUpper((mask & 2) != 0)
      .requireDigit((mask & 4) != 0).requireSymbol((mask & 8) != 0).minCategories(Integer.bitCount(mask))
      .excludeAmbiguous(ambiguous).bannedSubstrings(List.of()).bannedRegexes(List.of()).build();
  }

  private static int category(char value) {
    return Character.isLowerCase(value) ? 0 : Character.isUpperCase(value) ? 1 : Character.isDigit(value) ? 2 : 3;
  }

  /** Reproducible test source only; production retains its unchanged SecureRandom default. */
  private static final class SeededRandom extends SecureRandom {
    private static final long serialVersionUID = 1L;
    private final Random values;
    private int remaining;
    SeededRandom(long seed) { this(seed, Integer.MAX_VALUE); }
    SeededRandom(long seed, int budget) { values = new Random(seed);remaining = budget; }
    @Override public int nextInt(int bound) {
      // A generous guard prevents a retry-limit regression from hanging the negative test.
      if (remaining-- <= 0) throw new AssertionError("Test RNG draw budget exhausted");
      return values.nextInt(bound);
    }
  }
}

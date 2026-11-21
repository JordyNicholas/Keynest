package dev.keynest.manager.vault;

import dev.keynest.manager.api.ApiPaths;
import dev.keynest.manager.auth.AuthSession;
import dev.keynest.manager.auth.AuthSessionRepository;
import dev.keynest.manager.auth.UserAccount;
import dev.keynest.manager.auth.UserAccountRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:vault-query-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "keynest.rate-limit.enabled=false"})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class VaultQueryIntegrationTest {
  static final Instant NOW = Instant.parse("2026-11-21T12:00:00Z");
  @Autowired MockMvc mvc;
  @Autowired UserAccountRepository users;
  @Autowired AuthSessionRepository sessions;
  @Autowired VaultItemRepository items;
  @Autowired dev.keynest.manager.policy.AccountPasswordPolicyService policies;
  @MockitoBean(name = "vaultQueryClock") Clock clock;
  UserAccount owner;
  String authorization;

  @BeforeEach
  void setup() throws Exception {
    when(clock.instant()).thenReturn(NOW);
    owner = user();
    String token = UUID.randomUUID().toString();
    String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    sessions.save(new AuthSession(UUID.randomUUID(), owner.getId(), digest, Instant.now().plusSeconds(3600), Instant.now()));
    authorization = "Bearer " + token;
  }

  @Test
  void classifiesExactBoundariesAndReturnsOnlyBoundedOwnerMetadata() throws Exception {
    entry(owner, "Expired before", NOW.minusSeconds(1));entry(owner, "Expired now", NOW);
    var next = entry(owner, "Next", NOW.plusSeconds(1));
    entry(owner, "Before 14", NOW.plus(14, ChronoUnit.DAYS).minusSeconds(1));
    entry(owner, "Exactly 14", NOW.plus(14, ChronoUnit.DAYS));
    entry(owner, "Before 30", NOW.plus(30, ChronoUnit.DAYS).minusSeconds(1));
    entry(owner, "Exactly 30", NOW.plus(30, ChronoUnit.DAYS));entry(owner, "Unset", null);
    entry(user(), "Other owner's private label", NOW.minusSeconds(2));
    var response = mvc.perform(get(ApiPaths.VAULT_OVERVIEW).header(HttpHeaders.AUTHORIZATION, authorization))
      .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
      .andExpect(jsonPath("$.asOf").value(NOW.toString()))
      .andExpect(jsonPath("$.counts.total").value(8)).andExpect(jsonPath("$.counts.expired").value(2))
      .andExpect(jsonPath("$.counts.soon").value(2)).andExpect(jsonPath("$.counts.upcoming").value(2))
      .andExpect(jsonPath("$.counts.later").value(1)).andExpect(jsonPath("$.counts.unset").value(1))
      .andExpect(jsonPath("$.nextExpiry.id").value(next.getId().toString()))
      .andExpect(jsonPath("$.attention", hasSize(4))).andExpect(jsonPath("$.recent", hasSize(5)))
      .andExpect(jsonPath("$.policy.id").value("keynest.default.v1"))
      .andExpect(jsonPath("$.attention[0].username").doesNotExist()).andReturn().getResponse().getContentAsString();
    assertThat(response).doesNotContain("envelope", "passwordHash", "userId", "Other owner's private label");
    // Fixture ciphertext is intentionally invalid; a reveal/decryption attempt would fail.
    search("{\"expiry\":\"attention\"}").andExpect(jsonPath("$.totalMatches").value(4));
    for (var bucket : java.util.Map.of("expired", 2, "soon", 2, "upcoming", 2, "later", 1, "unset", 1).entrySet()) {
      search("{\"expiry\":\"" + bucket.getKey() + "\"}").andExpect(jsonPath("$.totalMatches").value(bucket.getValue()));
    }
  }

  @Test
  void keepsEmptyAndNoFutureExpiryDistinctFromFailure() throws Exception {
    mvc.perform(get(ApiPaths.VAULT_OVERVIEW).header(HttpHeaders.AUTHORIZATION, authorization))
      .andExpect(status().isOk()).andExpect(jsonPath("$.counts.total").value(0)).andExpect(jsonPath("$.nextExpiry").isEmpty());
    search("{}").andExpect(jsonPath("$.items", hasSize(0))).andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.totalPages").value(0));
    entry(owner, "Past", NOW);entry(owner, "No expiry", null);
    mvc.perform(get(ApiPaths.VAULT_OVERVIEW).header(HttpHeaders.AUTHORIZATION, authorization))
      .andExpect(status().isOk()).andExpect(jsonPath("$.nextExpiry").isEmpty());
  }

  @Test
  void inspectsTheSelectedAccountPolicyWithoutChangingMutationLocking() throws Exception {
    var selected = policies.update(owner, new dev.keynest.manager.policy.AccountPasswordPolicyService.Settings("custom",
      new dev.keynest.manager.policy.AccountPasswordPolicyService.Rules(24, 64, true, true, true, false,
        3, false, true, true, java.util.List.of("fixture"), java.util.List.of(), 90)));
    mvc.perform(get(ApiPaths.VAULT_OVERVIEW).header(HttpHeaders.AUTHORIZATION, authorization))
      .andExpect(status().isOk()).andExpect(jsonPath("$.policy.id").value(selected.id()))
      .andExpect(jsonPath("$.policy.rules.minLength").value(24));
  }

  @Test
  void boundsPagesAndClampsAfterConcurrentOrDeletedLastPage() throws Exception {
    for (int i = 0; i < 51; i++) entry(owner, "Item %02d".formatted(i), NOW.plus(40, ChronoUnit.DAYS));
    for (int size : new int[]{10, 25, 30, 50}) search("{\"size\":" + size + "}")
      .andExpect(jsonPath("$.items", hasSize(size))).andExpect(jsonPath("$.totalMatches").value(51));
    search("{\"page\":999,\"size\":10}").andExpect(jsonPath("$.page").value(5))
      .andExpect(jsonPath("$.items", hasSize(1))).andExpect(jsonPath("$.items[0].label").value("Item 50"));
    search("{\"page\":1,\"size\":10}").andExpect(jsonPath("$.items[0].label").value("Item 10"));
  }

  @Test
  void treatsWildcardsLiterallyAndCombinesFieldsExpiryAndOwnedItemSelection() throws Exception {
    var chosen = entry(owner, "Literal 100%_[needle]!", NOW.plus(14, ChronoUnit.DAYS));
    entry(owner, "Literal 100Xanythingneedle", NOW);
    for (String query : new String[]{"%", "_", "!", "[needle]"}) search("{\"query\":\"" + query + "\",\"field\":\"label\"}")
      .andExpect(jsonPath("$.totalMatches").value(1));
    search("{\"query\":\"NEEDLE\",\"expiry\":\"upcoming\"}").andExpect(jsonPath("$.totalMatches").value(1));
    search("{\"query\":\"example.test\",\"field\":\"url\"}").andExpect(jsonPath("$.totalMatches").value(2));
    search("{\"query\":\"DEMO@\",\"field\":\"username\"}").andExpect(jsonPath("$.totalMatches").value(2));
    search("{\"itemId\":\"" + chosen.getId() + "\"}").andExpect(jsonPath("$.items[0].id").value(chosen.getId().toString()));
    var other = entry(user(), "Other", NOW);
    search("{\"itemId\":\"" + other.getId() + "\"}").andExpect(jsonPath("$.totalMatches").value(0));
  }

  @Test
  void sortsExpiryWithNoExpiryLastAndStableLabelTies() throws Exception {
    entry(owner, "Unset", null);entry(owner, "Zeta", NOW.plusSeconds(1));entry(owner, "Alpha", NOW.plusSeconds(1));
    search("{\"sort\":\"expiry\"}").andExpect(jsonPath("$.items[0].label").value("Alpha"))
      .andExpect(jsonPath("$.items[1].label").value("Zeta")).andExpect(jsonPath("$.items[2].label").value("Unset"));
  }

  @Test
  void deniesAnonymousAccessAndDoesNotEchoOrLogInvalidMetadata(CapturedOutput output) throws Exception {
    mvc.perform(get(ApiPaths.VAULT_OVERVIEW)).andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control", "no-store"));
    mvc.perform(post(ApiPaths.VAULT_SEARCH).contentType(MediaType.APPLICATION_JSON).content("{}"))
      .andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control", "no-store"));
    String marker = "private-query-fixture-".repeat(15);
    for (String body : new String[]{"{\"size\":51}", "{\"page\":-1}", "{\"page\":100001}", "{\"field\":\"invalid\"}",
      "{\"expiry\":\"invalid\"}", "{\"sort\":\"invalid\"}", "{\"query\":\"" + marker + "\"}", "{\"itemId\":\"" + marker + "\"}"}) {
      var response = mvc.perform(post(ApiPaths.VAULT_SEARCH).header(HttpHeaders.AUTHORIZATION, authorization)
          .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest()).andExpect(header().string("Cache-Control", "no-store"))
        .andReturn().getResponse().getContentAsString();
      assertThat(response).doesNotContain(marker);
    }
    assertThat(output.getAll()).doesNotContain(marker);
  }

  private org.springframework.test.web.servlet.ResultActions search(String body) throws Exception {
    return mvc.perform(post(ApiPaths.VAULT_SEARCH).header(HttpHeaders.AUTHORIZATION, authorization)
      .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk())
      .andExpect(header().string("Cache-Control", "no-store"));
  }

  private UserAccount user() {
    return users.save(new UserAccount(UUID.randomUUID(), UUID.randomUUID() + "@example.test", "fixture-hash", Instant.now()));
  }

  private VaultItem entry(UserAccount user, String label, Instant expiresAt) {
    return items.save(new VaultItem(UUID.randomUUID(), user.getId(), label, "https://example.test/login", "demo@example.test",
      new byte[]{1, 2, 3}, "keynest.default.v1", 1, expiresAt, NOW, 14, NOW, NOW));
  }
}

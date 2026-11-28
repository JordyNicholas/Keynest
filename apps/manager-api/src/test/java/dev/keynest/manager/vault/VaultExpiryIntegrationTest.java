package dev.keynest.manager.vault;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.keynest.manager.api.ApiPaths;
import dev.keynest.manager.auth.AuthService;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "keynest.rate-limit.enabled=false")
@AutoConfigureMockMvc
class VaultExpiryIntegrationTest {
  @org.springframework.beans.factory.annotation.Autowired
  dev.keynest.manager.testing.TestAccounts accounts;

  private static final ObjectMapper JSON = new ObjectMapper();
  @Autowired
  MockMvc mvc;
  @Autowired
  AuthService auth;
  @Autowired
  VaultItemRepository items;
  private String bearer;

  @BeforeEach
  void login() {
    bearer = "Bearer "
      + accounts.register("expiry-" + UUID.randomUUID() + "@example.invalid", "Synthetic master passphrase!42").token();
  }

  @Test
  void calendarInstantRetainsOffsetPrecisionAndSurvivesMetadataOnlyEdits() throws Exception {
    var offset = OffsetDateTime
      .ofInstant(Instant.now().plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS), ZoneOffset.ofHours(-3))
      .withHour(23).withMinute(59).withSecond(59).withNano(999000000);
    UUID id = create(body().put("expiresAt", offset.toString()));
    assertThat(items.findById(id).orElseThrow().getExpiresAt()).isEqualTo(offset.toInstant());
    mvc
      .perform(put(ApiPaths.vaultItem(id.toString())).header("Authorization", bearer)
        .contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"Updated metadata\"}"))
      .andExpect(status().isOk());
    assertThat(items.findById(id).orElseThrow().getExpiresAt()).isEqualTo(offset.toInstant());
    Instant replacement = offset.toInstant().plus(5, ChronoUnit.DAYS);
    mvc
      .perform(
        put(ApiPaths.vaultItem(id.toString())).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
          .content(JSON.createObjectNode().put("expiresAt", replacement.toString()).toString()))
      .andExpect(status().isOk());
    assertThat(items.findById(id).orElseThrow().getExpiresAt()).isEqualTo(replacement);
  }

  @Test
  void relativeDaysAndMissingExpiryRetainTheExistingContract() throws Exception {
    Instant before = Instant.now();
    UUID id = create(body().put("expiresInDays", 90));
    assertThat(items.findById(id).orElseThrow().getExpiresAt()).isBetween(before.plus(90, ChronoUnit.DAYS),
      Instant.now().plus(90, ChronoUnit.DAYS));
    UUID noExpiry = create(body());
    assertThat(items.findById(noExpiry).orElseThrow().getExpiresAt()).isNull();
  }

  @Test
  void conflictingPastMalformedAndOutOfRangeDatesFailWithoutChangingData() throws Exception {
    String future = Instant.now().plus(10, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS).toString();
    UUID id = create(body().put("expiresAt", future));
    for (ObjectNode invalid : new ObjectNode[]{JSON.createObjectNode().put("expiresAt", future).put("expiresInDays", 3),
      JSON.createObjectNode().put("expiresAt", "2000-01-01T00:00:00Z"),
      JSON.createObjectNode().put("expiresAt", "31/02/2032"),
      JSON.createObjectNode().put("expiresAt", "+10001-01-01T00:00:00Z")}) {
      mvc
        .perform(
          put(ApiPaths.vaultItem(id.toString())).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
            .content(invalid.deepCopy().put("label", "Must not persist").toString()))
        .andExpect(status().isBadRequest());
      ObjectNode create = body();
      create.setAll(invalid);
      mvc.perform(post(ApiPaths.VAULT_ITEMS).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
        .content(create.toString())).andExpect(status().isBadRequest());
    }
    VaultItem item = items.findById(id).orElseThrow();
    assertThat(item.getLabel()).isEqualTo("Calendar test");
    assertThat(item.getExpiresAt()).isEqualTo(Instant.parse(future));
  }

  private UUID create(ObjectNode body) throws Exception {
    byte[] response = mvc.perform(post(ApiPaths.VAULT_ITEMS).header("Authorization", bearer)
      .contentType(MediaType.APPLICATION_JSON).content(body.toString())).andExpect(status().isOk()).andReturn()
      .getResponse().getContentAsByteArray();
    return UUID.fromString(JSON.readTree(response).path("id").textValue());
  }
  private static ObjectNode body() {
    return JSON.createObjectNode().put("label", "Calendar test").put("generate", true);
  }
}

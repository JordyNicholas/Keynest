package dev.keynest.manager;

import com.jayway.jsonpath.JsonPath;
import dev.keynest.manager.api.ApiPaths;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class VaultRevealSecurityIntegrationTest {

  @Autowired
  MockMvc mockMvc;

  @Test
  void postRevealReturnsSecretWithNoStoreHeaders() throws Exception {
    String token = register("reveal-owner");
    String itemId = createItem(token);

    mockMvc.perform(post(ApiPaths.vaultItemReveal(itemId))
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
      .andExpect(status().isOk())
      .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
      .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"))
      .andExpect(header().string(HttpHeaders.EXPIRES, "0"))
      .andExpect(jsonPath("$.item.id", is(itemId)))
      .andExpect(jsonPath("$.password", notNullValue()));
  }

  @Test
  void getReturnsMetadataEvenWhenLegacyRevealQueryIsSupplied() throws Exception {
    String token = register("metadata-owner");
    String itemId = createItem(token);

    mockMvc.perform(get(ApiPaths.vaultItem(itemId))
        .param("reveal", "true")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id", is(itemId)))
      .andExpect(jsonPath("$.password").doesNotExist())
      .andExpect(jsonPath("$.item").doesNotExist());
  }

  @Test
  void revealRejectsMissingSessionAndOtherOwner() throws Exception {
    String ownerToken = register("isolation-owner");
    String otherToken = register("isolation-other");
    String itemId = createItem(ownerToken);

    mockMvc.perform(post(ApiPaths.vaultItemReveal(itemId)))
      .andExpect(status().isUnauthorized());

    mockMvc.perform(post(ApiPaths.vaultItemReveal(itemId))
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.password").doesNotExist());
  }

  private String register(String prefix) throws Exception {
    MvcResult result = mockMvc.perform(post(ApiPaths.AUTH_REGISTER)
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {"email":"%s-%s@example.com","password":"MasterPassphrase!42"}
          """.formatted(prefix, UUID.randomUUID())))
      .andExpect(status().isOk())
      .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
  }

  private String createItem(String token) throws Exception {
    MvcResult result = mockMvc.perform(post(ApiPaths.VAULT_ITEMS)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {
            "label":"Reveal target",
            "username":"owner@example.com",
            "password":"StrongRevealPassword!42#"
          }
          """))
      .andExpect(status().isOk())
      .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }
}

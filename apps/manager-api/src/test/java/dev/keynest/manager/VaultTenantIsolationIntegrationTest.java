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

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class VaultTenantIsolationIntegrationTest {

  @Autowired
  MockMvc mockMvc;

  @Test
  void isolatesEveryVaultOperationByOwner() throws Exception {
    String firstToken = register("tenant-first");
    String secondToken = register("tenant-second");
    String firstItemId = createGeneratedItem(firstToken, "First owner item");
    String secondItemId = createGeneratedItem(secondToken, "Second owner item");

    assertListContainsOnly(firstToken, firstItemId);
    assertListContainsOnly(secondToken, secondItemId);

    assertNotFoundForAllItemOperations(secondToken, firstItemId);
    assertNotFoundForAllItemOperations(secondToken, UUID.randomUUID().toString());

    mockMvc.perform(get(ApiPaths.vaultItem(firstItemId))
        .header(HttpHeaders.AUTHORIZATION, bearer(firstToken)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id", is(firstItemId)))
      .andExpect(jsonPath("$.label", is("First owner item")));

    mockMvc.perform(post(ApiPaths.vaultItemReveal(firstItemId))
        .header(HttpHeaders.AUTHORIZATION, bearer(firstToken)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.item.id", is(firstItemId)))
      .andExpect(jsonPath("$.password", notNullValue()));

    assertListContainsOnly(firstToken, firstItemId);
    assertListContainsOnly(secondToken, secondItemId);
  }

  private void assertNotFoundForAllItemOperations(String token, String itemId) throws Exception {
    mockMvc.perform(get(ApiPaths.vaultItem(itemId))
        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
      .andExpect(status().isNotFound());

    mockMvc.perform(post(ApiPaths.vaultItemReveal(itemId))
        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.password").doesNotExist());

    mockMvc.perform(put(ApiPaths.vaultItem(itemId))
        .header(HttpHeaders.AUTHORIZATION, bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {"label":"Unauthorized change"}
          """))
      .andExpect(status().isNotFound());

    mockMvc.perform(delete(ApiPaths.vaultItem(itemId))
        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
      .andExpect(status().isNotFound());
  }

  private void assertListContainsOnly(String token, String expectedItemId) throws Exception {
    mockMvc.perform(get(ApiPaths.VAULT_ITEMS)
        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$", hasSize(1)))
      .andExpect(jsonPath("$[0].id", is(expectedItemId)));
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

  private String createGeneratedItem(String token, String label) throws Exception {
    MvcResult result = mockMvc.perform(post(ApiPaths.VAULT_ITEMS)
        .header(HttpHeaders.AUTHORIZATION, bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {"label":"%s","generate":true}
          """.formatted(label)))
      .andExpect(status().isOk())
      .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private static String bearer(String token) {
    return "Bearer " + token;
  }
}

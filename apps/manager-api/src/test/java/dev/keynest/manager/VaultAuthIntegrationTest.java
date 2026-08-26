package dev.keynest.manager;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class VaultAuthIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void registerCreateRevealAndDeleteVaultItem() throws Exception {
        String email = "vault-user@example.com";
        String password = "MasterPassphrase!42";

        MvcResult register = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andReturn();

        String token = JsonPath.read(register.getResponse().getContentAsString(), "$.token");

        MvcResult created = mockMvc.perform(post("/api/v1/vault/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "label":"Example",
                                  "url":"https://example.com",
                                  "username":"demo",
                                  "generate":true,
                                  "expiresInDays":90
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label", is("Example")))
                .andExpect(jsonPath("$.policyId", is("keynest.default.v1")))
                .andReturn();

        String id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/v1/vault/items")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        mockMvc.perform(get("/api/v1/vault/items/" + id)
                        .param("reveal", "true")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item.id", is(id)))
                .andExpect(jsonPath("$.password", notNullValue()));

        mockMvc.perform(delete("/api/v1/vault/items/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/vault/items")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}

package dev.keynest.manager.migration;

import com.jayway.jsonpath.JsonPath;
import dev.keynest.manager.api.ApiPaths;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class PostgresMigrationIntegrationTest {

  @Container
  static final PostgreSQLContainer postgres = new PostgreSQLContainer(
    DockerImageName.parse("postgres:16-alpine")
  )
    .withDatabaseName("keynest_test")
    .withUsername("keynest")
    .withPassword("keynest_test");

  @DynamicPropertySource
  static void postgresProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    registry.add("spring.flyway.baseline-on-migrate", () -> "false");
  }

  @Autowired
  MockMvc mockMvc;

  @Autowired
  Flyway flyway;

  @Autowired
  JdbcTemplate jdbcTemplate;

  @Test
  void freshPostgresSchemaMigratesAndSupportsCoreVaultFlow() throws Exception {
    assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("4");
    assertThat(jdbcTemplate.queryForObject(
      """
        SELECT COUNT(*) FROM flyway_schema_history
        WHERE success = TRUE AND version IS NOT NULL
        """,
      Long.class
    )).isEqualTo(4L);
    assertThat(jdbcTemplate.queryForObject("SELECT current_database()", String.class))
      .isEqualTo("keynest_test");

    MvcResult registration = mockMvc.perform(post(ApiPaths.AUTH_REGISTER)
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {"email":"postgres-%s@example.com","password":"MasterPassphrase!42"}
          """.formatted(UUID.randomUUID())))
      .andExpect(status().isOk())
      .andReturn();
    String token = JsonPath.read(registration.getResponse().getContentAsString(), "$.token");

    MvcResult creation = mockMvc.perform(post(ApiPaths.VAULT_ITEMS)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {"label":"PostgreSQL item","generate":true}
          """))
      .andExpect(status().isOk())
      .andReturn();
    String itemId = JsonPath.read(creation.getResponse().getContentAsString(), "$.id");

    mockMvc.perform(post(ApiPaths.vaultItemReveal(itemId))
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.item.id").value(itemId))
      .andExpect(jsonPath("$.password", notNullValue()));

    assertThat(jdbcTemplate.queryForObject(
      "SELECT COUNT(*) FROM vault_items WHERE id = ?", Long.class, UUID.fromString(itemId)
    )).isEqualTo(1L);
  }
}

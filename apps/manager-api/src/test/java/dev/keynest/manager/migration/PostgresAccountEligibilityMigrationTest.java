package dev.keynest.manager.migration;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class PostgresAccountEligibilityMigrationTest extends AccountEligibilityMigrationContract {
  @Container
  static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
    .withDatabaseName("eligibility_upgrade").withUsername("keynest").withPassword("disposable_test");
  @Override
  String url() {
    return postgres.getJdbcUrl();
  }
  @Override
  String username() {
    return postgres.getUsername();
  }
  @Override
  String password() {
    return postgres.getPassword();
  }
}

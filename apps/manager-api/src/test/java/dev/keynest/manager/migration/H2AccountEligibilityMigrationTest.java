package dev.keynest.manager.migration;

import java.util.UUID;

class H2AccountEligibilityMigrationTest extends AccountEligibilityMigrationContract {
  private final String database = "jdbc:h2:mem:eligibility-" + UUID.randomUUID()
    + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
  @Override
  String url() {
    return database;
  }
  @Override
  String username() {
    return "sa";
  }
  @Override
  String password() {
    return "";
  }
}

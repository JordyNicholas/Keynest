package dev.keynest.manager.auth;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(properties = {
  "spring.datasource.url=jdbc:h2:mem:session-cleanup;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
  "keynest.session-cleanup.batch-size=2", "keynest.session-cleanup.delay-seconds=86400"
})
@Import(ExpiredSessionCleanupContract.TimeConfiguration.class)
class ExpiredSessionCleanupIntegrationTest extends ExpiredSessionCleanupContract {}

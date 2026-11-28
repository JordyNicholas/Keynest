package dev.keynest.manager.auth;

import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
  "spring.datasource.url=jdbc:h2:mem:account-verification;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
  "keynest.password-hashing.max-concurrent=4"})
class H2AccountVerificationTest extends AccountVerificationContract {
}

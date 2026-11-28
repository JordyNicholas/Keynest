package dev.keynest.manager.auth.verification;

import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:verification-state;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
class H2VerificationStoreTest extends VerificationStoreContract {
}

package dev.keynest.manager.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {
    Optional<AuthSession> findByTokenDigest(String tokenDigest);
    void deleteByTokenDigest(String tokenDigest);
}

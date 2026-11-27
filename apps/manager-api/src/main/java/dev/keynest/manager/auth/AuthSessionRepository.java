package dev.keynest.manager.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.UUID;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {
    Optional<AuthSession> findByTokenDigest(String tokenDigest);
    void deleteByTokenDigest(String tokenDigest);

    @Modifying
    @Query("delete from AuthSession s where s.userId = :owner")
    int deleteForOwner(@Param("owner") UUID owner);

    @Query("select s.id from AuthSession s where s.expiresAt <= :cutoff order by s.expiresAt, s.id")
    List<UUID> findExpiredIds(@Param("cutoff") Instant cutoff, Pageable page);

    @Modifying
    @Query("delete from AuthSession s where s.id in :ids and s.expiresAt <= :cutoff")
    int deleteExpiredIds(@Param("ids") List<UUID> ids, @Param("cutoff") Instant cutoff);
}

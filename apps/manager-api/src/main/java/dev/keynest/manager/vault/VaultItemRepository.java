package dev.keynest.manager.vault;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VaultItemRepository extends JpaRepository<VaultItem, UUID> {
    List<VaultItem> findByUserIdOrderByUpdatedAtDesc(UUID userId);

    Optional<VaultItem> findByIdAndUserId(UUID id, UUID userId);
}

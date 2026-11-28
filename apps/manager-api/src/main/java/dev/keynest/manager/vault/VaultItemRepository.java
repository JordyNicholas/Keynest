package dev.keynest.manager.vault;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

public interface VaultItemRepository extends JpaRepository<VaultItem, UUID> {
  List<VaultItem> findByUserIdOrderByUpdatedAtDesc(UUID userId);

  Optional<VaultItem> findByIdAndUserId(UUID id, UUID userId);

  @Query("""
    select item from VaultItem item where item.userId = :userId
    and coalesce(trim(item.username), '') = :username
    and coalesce(trim(item.url), '') = :url
    """)
  Stream<VaultItem> streamLoginCandidates(UUID userId, String username, String url);
}

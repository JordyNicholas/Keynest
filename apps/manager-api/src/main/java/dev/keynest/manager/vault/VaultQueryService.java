package dev.keynest.manager.vault;

import dev.keynest.manager.auth.UserAccount;
import dev.keynest.manager.policy.AccountPasswordPolicyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

@Service
public class VaultQueryService {
  private final VaultMetadataQueries queries;
  private final AccountPasswordPolicyService policies;
  private final Clock clock;

  public VaultQueryService(VaultMetadataQueries queries, AccountPasswordPolicyService policies, Clock clock) {
    this.queries = queries;this.policies = policies;this.clock = clock;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Overview overview(UserAccount owner) {
    Instant asOf = clock.instant();
    var next = queries.summaries(owner.getId(), asOf, "next");
    return new Overview(asOf, queries.counts(owner.getId(), asOf), next.isEmpty() ? null : next.getFirst(),
      queries.summaries(owner.getId(), asOf, "attention"), queries.summaries(owner.getId(), asOf, "recent"), policies.inspect(owner));
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public SearchPage search(UserAccount owner, SearchRequest body) {
    VaultSearch search = VaultSearch.of(body);
    Instant asOf = clock.instant();
    long count = queries.countMatches(owner.getId(), search, asOf);
    int pages = (int) Math.min(Integer.MAX_VALUE, (count + search.size() - 1) / search.size());
    int page = Math.min(search.page(), Math.max(0, pages - 1));
    return new SearchPage(asOf, queries.page(owner.getId(), search, asOf, page), count, page, search.size(), pages,
      queries.counts(owner.getId(), asOf));
  }

  public record Overview(Instant asOf, VaultMetadata.Counts counts, VaultMetadata.Summary nextExpiry,
    List<VaultMetadata.Summary> attention, List<VaultMetadata.Summary> recent, AccountPasswordPolicyService.View policy) {}
  public record SearchPage(Instant asOf, List<VaultMetadata> items, long totalMatches, int page, int size, int totalPages,
    VaultMetadata.Counts counts) {}
  public record SearchRequest(@Min(0) @Max(100000) Integer page, Integer size,
    @Size(max = 200) String query, @Size(max = 16) String field, @Size(max = 16) String expiry,
    @Size(max = 16) String sort, UUID itemId) {}
}

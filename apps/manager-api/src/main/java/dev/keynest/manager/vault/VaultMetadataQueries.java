package dev.keynest.manager.vault;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Bounded read model. No query selects a VaultItem entity or envelope_bytes. */
@Repository
public class VaultMetadataQueries {
  private final EntityManager em;

  public VaultMetadataQueries(EntityManager em) { this.em = em; }

  VaultMetadata.Counts counts(UUID owner, Instant asOf) {
    var cb = em.getCriteriaBuilder();
    var query = cb.createTupleQuery();
    var item = query.from(VaultItem.class);
    query.multiselect(cb.count(item), bucketCount(cb, expiry(cb, item, VaultSearch.Expiry.EXPIRED, asOf)),
      bucketCount(cb, expiry(cb, item, VaultSearch.Expiry.SOON, asOf)),
      bucketCount(cb, expiry(cb, item, VaultSearch.Expiry.UPCOMING, asOf)),
      bucketCount(cb, expiry(cb, item, VaultSearch.Expiry.LATER, asOf)),
      bucketCount(cb, expiry(cb, item, VaultSearch.Expiry.UNSET, asOf)));
    query.where(cb.equal(item.get("userId"), owner));
    Tuple values = em.createQuery(query).getSingleResult();
    return new VaultMetadata.Counts(values.get(0, Long.class), values.get(1, Long.class), values.get(2, Long.class),
      values.get(3, Long.class), values.get(4, Long.class), values.get(5, Long.class));
  }

  long countMatches(UUID owner, VaultSearch search, Instant asOf) {
    var cb = em.getCriteriaBuilder();
    var query = cb.createQuery(Long.class);
    var item = query.from(VaultItem.class);
    query.select(cb.count(item)).where(filters(cb, item, owner, search, asOf));
    return em.createQuery(query).getSingleResult();
  }

  List<VaultMetadata> page(UUID owner, VaultSearch search, Instant asOf, int page) {
    var cb = em.getCriteriaBuilder();
    CriteriaQuery<VaultMetadata> query = cb.createQuery(VaultMetadata.class);
    var item = query.from(VaultItem.class);
    query.select(cb.construct(VaultMetadata.class, item.get("id"), item.get("label"), item.get("url"),
      item.get("username"), item.get("policyId"), item.get("expiresAt"), item.get("lastRotatedAt"),
      item.get("notifyBeforeDays"), item.get("createdAt"), item.get("updatedAt")));
    query.where(filters(cb, item, owner, search, asOf));
    var order = new ArrayList<jakarta.persistence.criteria.Order>();
    if (search.order() == VaultSearch.Order.EXPIRY) {
      order.add(cb.asc(cb.<Integer>selectCase().when(cb.isNull(item.get("expiresAt")), 1).otherwise(0)));
      order.add(cb.asc(item.get("expiresAt")));
    }
    order.add(cb.asc(cb.lower(item.get("label"))));order.add(cb.asc(item.get("id")));
    query.orderBy(order);
    return em.createQuery(query).setFirstResult(page * search.size()).setMaxResults(search.size()).getResultList();
  }

  List<VaultMetadata.Summary> summaries(UUID owner, Instant asOf, String kind) {
    var cb = em.getCriteriaBuilder();
    var query = cb.createQuery(VaultMetadata.Summary.class);
    var item = query.from(VaultItem.class);
    query.select(cb.construct(VaultMetadata.Summary.class, item.get("id"), item.get("label"), item.get("url"),
      item.get("expiresAt"), item.get("createdAt"), item.get("updatedAt")));
    var predicates = new ArrayList<Predicate>();predicates.add(cb.equal(item.get("userId"), owner));
    if ("attention".equals(kind)) predicates.add(expiry(cb, item, VaultSearch.Expiry.ATTENTION, asOf));
    if ("next".equals(kind)) predicates.add(cb.greaterThan(item.get("expiresAt"), asOf));
    query.where(predicates.toArray(Predicate[]::new));
    query.orderBy("recent".equals(kind) ? cb.desc(item.get("updatedAt")) : cb.asc(item.get("expiresAt")), cb.asc(item.get("id")));
    return em.createQuery(query).setMaxResults("next".equals(kind) ? 1 : 5).getResultList();
  }

  private Predicate[] filters(CriteriaBuilder cb, Root<VaultItem> item, UUID owner, VaultSearch search, Instant asOf) {
    var predicates = new ArrayList<Predicate>();
    predicates.add(cb.equal(item.get("userId"), owner));
    predicates.add(expiry(cb, item, search.expiry(), asOf));
    if (search.itemId() != null) predicates.add(cb.equal(item.get("id"), search.itemId()));
    if (!search.query().isEmpty()) {
      String literal = "%" + search.query().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
      List<String> fields = search.field() == VaultSearch.Field.ALL ? List.of("label", "url", "username")
        : List.of(search.field().name().toLowerCase(java.util.Locale.ROOT));
      predicates.add(cb.or(fields.stream().map(field -> cb.like(cb.lower(item.get(field)), literal, '!')).toArray(Predicate[]::new)));
    }
    return predicates.toArray(Predicate[]::new);
  }

  private static jakarta.persistence.criteria.Expression<Long> bucketCount(CriteriaBuilder cb, Predicate predicate) {
    return cb.coalesce(cb.sum(cb.<Long>selectCase().when(predicate, 1L).otherwise(0L)), 0L);
  }

  private static Predicate expiry(CriteriaBuilder cb, Root<VaultItem> item, VaultSearch.Expiry group, Instant asOf) {
    var expires = item.<Instant>get("expiresAt");
    Instant day14 = asOf.plus(14, ChronoUnit.DAYS), day30 = asOf.plus(30, ChronoUnit.DAYS);
    return switch (group) {
      case ALL -> cb.conjunction();
      case ATTENTION -> cb.lessThan(expires, day14);
      case EXPIRED -> cb.lessThanOrEqualTo(expires, asOf);
      case SOON -> cb.and(cb.greaterThan(expires, asOf), cb.lessThan(expires, day14));
      case UPCOMING -> cb.and(cb.greaterThanOrEqualTo(expires, day14), cb.lessThan(expires, day30));
      case LATER -> cb.greaterThanOrEqualTo(expires, day30);
      case UNSET -> cb.isNull(expires);
    };
  }
}

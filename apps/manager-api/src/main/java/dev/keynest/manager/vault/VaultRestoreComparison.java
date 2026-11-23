package dev.keynest.manager.vault;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;
import static dev.keynest.manager.vault.VaultRestoreDecision.Action.*;
import static dev.keynest.manager.vault.VaultRestoreFailure.Code.*;

/** Called under the receiving-account lock and bulk-work lease. No retained secret index. */
@Component
final class VaultRestoreComparison {
  static final int MAX_CANDIDATES = 5000, MAX_MATCHES = 5000, MAX_REVIEW_BYTES = 8 * 1024 * 1024;
  private final EntityManager em;
  private final VaultService vault;
  VaultRestoreComparison(EntityManager em, VaultService vault) { this.em = em;this.vault = vault; }

  State inspect(UUID owner, JsonNode rows) throws Exception {
    List<Source> sources = new ArrayList<>();Map<Location, List<Source>> locations = new HashMap<>();Map<Content, Integer> first = new HashMap<>();long reviewBytes = 0;
    for (JsonNode row : rows) {
      Metadata fields = fields(row);Content content = new Content(fields, row.path("password").textValue());
      Integer repeated = first.putIfAbsent(content, sources.size());var source = new Source(sources.size(), row, fields, repeated);sources.add(source);
      locations.computeIfAbsent(location(fields.label, fields.url, fields.username), ignored -> new ArrayList<>()).add(source);
      reviewBytes += 256L + 6L * (length(fields.label) + length(fields.url) + length(fields.username));
    }
    checkReviewBytes(reviewBytes);
    // Complete website/username pairs identify candidates. Missing identifiers also require a label match.
    var cb = em.getCriteriaBuilder();var query = cb.createQuery(UUID.class);var item = query.from(VaultItem.class);List<Predicate> alternatives = new ArrayList<>();
    for (Location location : locations.keySet()) alternatives.add(cb.and(location.label == null ? cb.conjunction() : cb.equal(item.get("label"), location.label),
      location.url == null ? cb.isNull(item.get("url")) : cb.equal(item.get("url"), location.url),
      location.username == null ? cb.isNull(item.get("username")) : cb.equal(item.get("username"), location.username)));
    query.select(item.get("id")).where(cb.and(cb.equal(item.get("userId"), owner), cb.or(alternatives.toArray(Predicate[]::new)))).orderBy(cb.asc(item.get("id")));
    List<UUID> ids = em.createQuery(query).setMaxResults(MAX_CANDIDATES + 1).getResultList();
    if (ids.size() > MAX_CANDIDATES) throw new VaultRestoreFailure(RESTORE_COMPARISON_LIMIT);
    if (!ids.isEmpty()) {
      var sizes = em.createNativeQuery("select octet_length(envelope_bytes) from vault_items where user_id = :owner and id in (:ids)")
        .setParameter("owner", owner).setParameter("ids", ids).setMaxResults(MAX_CANDIDATES + 1).getResultList();
      if (sizes.size() != ids.size()) throw new VaultRestoreFailure(RESTORE_CHANGED);
      for (Object size : sizes) if (!(size instanceof Number length) || length.longValue() < 32 || length.longValue() > 8192) throw new VaultRestoreFailure(RESTORE_LIMIT_EXCEEDED);
    }
    MessageDigest digest = MessageDigest.getInstance("SHA-256");add(digest, "keynest.restore.receiving-state.v1");add(digest, owner);add(digest, ids.size());int matches = 0;
    for (int offset = 0; offset < ids.size(); offset += 64) {
      List<VaultItem> candidates = em.createQuery("select v from VaultItem v where v.userId = :owner and v.id in :ids order by v.id", VaultItem.class)
        .setParameter("owner", owner).setParameter("ids", ids.subList(offset, Math.min(offset + 64, ids.size()))).getResultList();
      if (candidates.size() != Math.min(64, ids.size() - offset)) throw new VaultRestoreFailure(RESTORE_CHANGED);
      for (VaultItem candidate : candidates) {
        try {
          add(digest, candidate.getId());add(digest, candidate.getLabel());add(digest, candidate.getUrl());add(digest, candidate.getUsername());
          add(digest, candidate.getPolicyId());add(digest, candidate.getExpiresAt());add(digest, candidate.getLastRotatedAt());add(digest, candidate.getNotifyBeforeDays());
          add(digest, candidate.getCreatedAt());add(digest, candidate.getUpdatedAt());add(digest, candidate.getAadVersion());bytes(digest, candidate.getEnvelopeBytes());
          var matching = locations.get(location(candidate.getLabel(), candidate.getUrl(), candidate.getUsername()));
          if (matching != null) {
            matches += matching.size();reviewBytes += matching.size() * (768L + 6L * length(candidate.getLabel()));
            if (matches > MAX_MATCHES) throw new VaultRestoreFailure(RESTORE_COMPARISON_LIMIT);checkReviewBytes(reviewBytes);
            String password = vault.openPassword(candidate);Metadata fields = fields(candidate);
            for (Source source : matching) source.matches.add(new VaultRestoreEntry.Match(candidate.getId(), candidate.getLabel(), candidate.getCreatedAt(), candidate.getUpdatedAt(), differences(source, fields, password)));
          }
        } finally { em.detach(candidate); }
      }
    }
    List<VaultRestoreEntry> entries = new ArrayList<>();List<VaultRestoreDecision> defaults = new ArrayList<>();
    for (Source source : sources) {
      String kind = source.matches.stream().anyMatch(match -> match.changedFields().isEmpty()) ? "identical" : !source.matches.isEmpty() ? "conflict" : source.repeatedOf != null ? "repeated" : "new";
      entries.add(new VaultRestoreEntry(source.index, source.fields.label, source.fields.url, source.fields.username, kind, source.repeatedOf, source.matches));
      defaults.add(new VaultRestoreDecision(source.index, kind.equals("new") ? ADD : IGNORE, null));
    }
    return new State(List.copyOf(entries), List.copyOf(defaults), rows, HexFormat.of().formatHex(digest.digest()));
  }

  private static List<String> differences(Source source, Metadata target, String password) {
    Metadata incoming = source.fields;List<String> fields = new ArrayList<>();
    if (!Objects.equals(incoming.label, target.label)) fields.add("label");
    if (!Objects.equals(incoming.url, target.url)) fields.add("url");
    if (!Objects.equals(incoming.username, target.username)) fields.add("username");
    if (!source.row.path("password").textValue().equals(password)) fields.add("password");
    if (!Objects.equals(incoming.policyId, target.policyId)) fields.add("policyId");
    if (!Objects.equals(incoming.expiresAt, target.expiresAt)) fields.add("expiresAt");
    if (!Objects.equals(incoming.rotatedAt, target.rotatedAt)) fields.add("lastRotatedAt");
    if (!Objects.equals(incoming.notifyBeforeDays, target.notifyBeforeDays)) fields.add("notifyBeforeDays");
    return fields;
  }
  private static Location location(String label, String url, String username) { return new Location(url != null && !url.isBlank() && username != null && !username.isBlank() ? null : label, url, username); }
  private static Metadata fields(JsonNode row) { return new Metadata(text(row, "label"), text(row, "url"), text(row, "username"), text(row, "policyId"), time(text(row, "expiresAt")), time(text(row, "lastRotatedAt")), row.path("notifyBeforeDays").isNull() ? null : row.path("notifyBeforeDays").intValue()); }
  private static Metadata fields(VaultItem row) { return new Metadata(row.getLabel(), row.getUrl(), row.getUsername(), row.getPolicyId(), stored(row.getExpiresAt()), stored(row.getLastRotatedAt()), row.getNotifyBeforeDays()); }
  private static String text(JsonNode row, String name) { return row.path(name).isNull() ? null : row.path(name).textValue(); }
  private static Instant time(String value) { return value == null ? null : stored(Instant.parse(value)); }
  private static Instant stored(Instant value) { return value == null ? null : value.plusNanos(500).truncatedTo(ChronoUnit.MICROS); }
  private static int length(String value) { return value == null ? 0 : value.length(); }
  private static void checkReviewBytes(long bytes) { if (bytes > MAX_REVIEW_BYTES) throw new VaultRestoreFailure(RESTORE_COMPARISON_LIMIT); }
  private static void add(MessageDigest digest, Object value) {
    byte[] data = value == null ? null : value.toString().getBytes(StandardCharsets.UTF_8);
    try { bytes(digest, data); } finally { if (data != null) Arrays.fill(data, (byte) 0); }
  }
  private static void bytes(MessageDigest digest, byte[] value) { digest.update(ByteBuffer.allocate(4).putInt(value == null ? -1 : value.length).array());if (value != null) digest.update(value); }
  private record Location(String label, String url, String username) { @Override public String toString() { return "RestoreLocation[redacted]"; } }
  private record Metadata(String label, String url, String username, String policyId, Instant expiresAt, Instant rotatedAt, Integer notifyBeforeDays) { @Override public String toString() { return "RestoreMetadata[redacted]"; } }
  private record Content(Metadata fields, String password) { @Override public String toString() { return "RestoreContent[redacted]"; } }
  private static final class Source {
    private final int index;private final JsonNode row;private final Metadata fields;private final Integer repeatedOf;private final List<VaultRestoreEntry.Match> matches = new ArrayList<>();
    private Source(int index, JsonNode row, Metadata fields, Integer repeatedOf) { this.index = index;this.row = row;this.fields = fields;this.repeatedOf = repeatedOf; }
  }
  record Operation(JsonNode row, UUID targetId) { @Override public String toString() { return "RestoreOperation[redacted]"; } }
  record Selection(List<Operation> operations, int newItemCount, int updatedItemCount, int skippedCount, List<VaultRestoreDecision> decisions) { @Override public String toString() { return "RestoreSelection[redacted]"; } }
  record State(List<VaultRestoreEntry> entries, List<VaultRestoreDecision> defaults, JsonNode rows, String snapshot) {
    Selection select(List<VaultRestoreDecision> requested) {
      List<VaultRestoreDecision> decisions = requested == null ? defaults : requested;
      if (decisions.size() != entries.size()) throw new VaultRestoreFailure(RESTORE_SELECTION_INVALID);
      var seen = new HashSet<Integer>();var targets = new HashSet<UUID>();var operations = new ArrayList<Operation>();int additions = 0, updates = 0, ignored = 0;
      for (var decision : decisions) {
        if (decision.sourceIndex() < 0 || decision.sourceIndex() >= entries.size() || !seen.add(decision.sourceIndex()) || decision.action() == null) throw new VaultRestoreFailure(RESTORE_SELECTION_INVALID);
        if (decision.action() == IGNORE) { if (decision.targetId() != null) throw new VaultRestoreFailure(RESTORE_SELECTION_INVALID);ignored++; }
        else if (decision.action() == ADD) { if (decision.targetId() != null) throw new VaultRestoreFailure(RESTORE_SELECTION_INVALID);operations.add(new Operation(rows.get(decision.sourceIndex()), null));additions++; }
        else {
          if (decision.targetId() == null || !targets.add(decision.targetId()) || entries.get(decision.sourceIndex()).matches().stream().noneMatch(match -> match.targetId().equals(decision.targetId()) && !match.changedFields().isEmpty())) throw new VaultRestoreFailure(RESTORE_SELECTION_INVALID);
          operations.add(new Operation(rows.get(decision.sourceIndex()), decision.targetId()));updates++;
        }
      }
      return new Selection(List.copyOf(operations), additions, updates, ignored, List.copyOf(decisions));
    }
    @Override public String toString() { return "RestoreComparison[redacted]"; }
  }
}

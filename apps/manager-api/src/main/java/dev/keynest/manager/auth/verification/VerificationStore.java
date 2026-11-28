package dev.keynest.manager.auth.verification;

import dev.keynest.manager.auth.verification.VerificationRequest.OwnerBinding;
import dev.keynest.manager.auth.verification.VerificationRequest.Purpose;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Internal state only. Public input parsing, fresh password proof and SMTP remain separate boundaries. */
@Service
public class VerificationStore {
  private static final String ROWS = """
    SELECT c.*, j.status, j.claim_id, j.claim_until FROM verification_challenges c
    JOIN verification_delivery_jobs j ON j.challenge_id = c.id
    """;
  private final JdbcTemplate jdbc;
  private final TransactionTemplate transactions;
  private final Clock clock;
  private final Limits limits;

  @Autowired
  public VerificationStore(DataSource source, PlatformTransactionManager manager, Clock clock) {
    this(source, manager, clock, Limits.DEFAULT);
  }

  VerificationStore(DataSource source, PlatformTransactionManager manager, Clock clock, Limits limits) {
    jdbc = new JdbcTemplate(source);
    jdbc.setQueryTimeout(5);
    transactions = new TransactionTemplate(manager);
    transactions.setTimeout(10);
    transactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    this.clock = clock;
    this.limits = limits;
  }

  /** Caller supplies a canonical identity from a reviewed parser and trusted ingress client identity. */
  public Admission admit(VerificationRequest request, String trustedClient) {
    Objects.requireNonNull(request, "Verification request required");
    if (trustedClient == null || trustedClient.isBlank() || trustedClient.length() > 128
      || trustedClient.chars().anyMatch(c -> c < 33 || c > 126)) {
      throw new IllegalArgumentException("Invalid verification client identity");
    }
    String client = VerificationDelivery.digestClient(trustedClient);
    return transaction(() -> {
      if (request.owner() != null && !lockOwner(request.owner().ownerId()))
        return Admission.INVALID_BINDING;
      return gated(now -> {
        prune(now);
        // Reserve worst-case room before looking at recipient state, including on resend.
        if (count("SELECT COUNT(*) FROM verification_recipients")
          + count("SELECT COUNT(*) FROM verification_challenges") > limits.capacity() - 2
          || count("SELECT COUNT(*) FROM verification_clients") >= limits.clientKeys())
          return Admission.CAPACITY;
        if (count("SELECT COUNT(*) FROM verification_admissions WHERE accepted_at > ?",
          utc(now.minusSeconds(3600))) >= limits.global())
          return Admission.GLOBAL_LIMIT;
        if (count("SELECT COUNT(*) FROM verification_client_events WHERE client_key = ? AND accepted_at > ?", client,
          utc(now.minusSeconds(60))) >= limits.client())
          return Admission.CLIENT_LIMIT;
        if (count("SELECT COUNT(*) FROM verification_clients WHERE client_key = ?", client) == 0) {
          jdbc.update("INSERT INTO verification_clients (client_key, created_at) VALUES (?, ?)", client, utc(now));
        }
        jdbc.update("INSERT INTO verification_client_events (id, client_key, accepted_at) VALUES (?, ?, ?)",
          UUID.randomUUID(), client, utc(now));
        if (count("SELECT COUNT(*) FROM verification_admissions WHERE recipient_key = ? AND accepted_at > ?",
          request.recipient(), utc(now.minusSeconds(60))) > 0
          || count("SELECT COUNT(*) FROM verification_admissions WHERE recipient_key = ? AND accepted_at > ?",
            request.recipient(), utc(now.minusSeconds(3600))) >= limits.recipient())
          return Admission.RECIPIENT_LIMIT;
        if (!currentBinding(request, now))
          return Admission.INVALID_BINDING;
        if (count("SELECT COUNT(*) FROM verification_recipients WHERE recipient_key = ?", request.recipient()) == 0) {
          jdbc.update("INSERT INTO verification_recipients (recipient_key, created_at) VALUES (?, ?)",
            request.recipient(), utc(now));
        }
        jdbc.update("INSERT INTO verification_admissions (id, recipient_key, accepted_at) VALUES (?, ?, ?)",
          UUID.randomUUID(), request.recipient(), utc(now));
        jdbc.update("DELETE FROM verification_challenges WHERE recipient_key = ? AND purpose = ?", request.recipient(),
          request.purpose().name());
        UUID generation = UUID.randomUUID();
        jdbc.update("""
          INSERT INTO verification_challenges
          (id, recipient_key, delivery_address, purpose, owner_id, session_digest, created_at, intent_expires_at)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?)
          """, generation, request.recipient(), request.deliveryAddress(), request.purpose().name(),
          request.owner() == null ? null : request.owner().ownerId(),
          request.owner() == null ? null : request.owner().sessionDigest(), utc(now), utc(now.plusSeconds(300)));
        jdbc.update("INSERT INTO verification_delivery_jobs (challenge_id, status, created_at) VALUES (?, 'QUEUED', ?)",
          generation, utc(now));
        return Admission.ACCEPTED;
      });
    });
  }

  /** Claims exactly once and commits the digest before any secret is returned to a worker. */
  public Optional<VerificationDelivery> claimNext() {
    requireIndependentTransaction();
    VerificationDelivery[] material = new VerificationDelivery[1];
    try {
      return transaction(() -> gated(now -> {
        prune(now);
        if (count("SELECT COUNT(*) FROM verification_delivery_jobs WHERE status = 'CLAIMED' AND claim_until > ?",
          utc(now)) >= limits.claims())
          return Optional.empty();
        List<Row> next = jdbc.query(
          ROWS + " WHERE j.status = 'QUEUED' AND c.intent_expires_at > ? ORDER BY c.created_at, c.id LIMIT 1",
          VerificationStore::row, utc(now));
        if (next.isEmpty())
          return Optional.empty();
        Row row = next.getFirst();
        if (!currentBinding(row.request(), now)
          || (row.request().purpose() == Purpose.NEW_ACCOUNT && accountExists(row.request().recipient()))) {
          jdbc.update(
            "UPDATE verification_delivery_jobs SET status = 'FINISHED', outcome = 'SUPPRESSED' WHERE challenge_id = ?",
            row.id());
          return Optional.empty();
        }
        UUID claim = UUID.randomUUID();
        material[0] = VerificationDelivery.mint(row.id(), claim, row.request(), now.plusSeconds(600));
        jdbc.update(
          "UPDATE verification_delivery_jobs SET status = 'CLAIMED', claim_id = ?, claim_until = ? WHERE challenge_id = ?",
          claim, utc(now.plusSeconds(30)), row.id());
        jdbc.update("UPDATE verification_challenges SET token_digest = ?, token_expires_at = ? WHERE id = ?",
          material[0].digest(), utc(material[0].expiresAt()), row.id());
        return Optional.of(material[0]);
      }));
    } catch (RuntimeException | Error failure) {
      if (material[0] != null)
        material[0].close();
      throw failure;
    }
  }

  /** Immediately-before-send guard. No transaction/lock is held while the caller performs I/O. */
  public boolean canDeliver(VerificationDelivery delivery) {
    requireIndependentTransaction();
    if (delivery.closed())
      return false;
    return transaction(() -> gated(now -> {
      Optional<Row> found = find(" WHERE c.id = ?", delivery.generation());
      if (found.isEmpty())
        return false;
      Row row = found.get();
      return row.status().equals("CLAIMED") && delivery.claim().equals(row.claim())
        && delivery.request().equals(row.request()) && delivery.digest().equals(row.digest())
        && row.claimUntil().isAfter(now) && row.expiresAt().isAfter(now) && currentBinding(row.request(), now)
        && (row.request().purpose() != Purpose.NEW_ACCOUNT || !accountExists(row.request().recipient()));
    }));
  }

  public boolean finish(VerificationDelivery delivery, DeliveryResult result) {
    requireIndependentTransaction();
    Objects.requireNonNull(result, "Verification result required");
    return transaction(() -> gated(now -> jdbc.update("""
      UPDATE verification_delivery_jobs SET status = 'FINISHED', outcome = ?, claim_id = NULL, claim_until = NULL
      WHERE challenge_id = ? AND status = 'CLAIMED' AND claim_id = ? AND claim_until > ?
      """, result.name(), delivery.generation(), delivery.claim(), utc(now)) == 1));
  }

  /** Non-consuming preflight before password work. Never grants authority; consumption rechecks in the account transaction. */
  public boolean canConsume(char[] token, Purpose expected, OwnerBinding owner) {
    try {
      requireIndependentTransaction();
      if (expected == null || (expected == Purpose.NEW_ACCOUNT) != (owner == null))
        throw new IllegalArgumentException("Invalid verification purpose binding");
      String digest = VerificationDelivery.digestPresented(token);
      if (digest == null)
        return false;
      return transaction(() -> gated(now -> {
        Optional<Row> found = find(" WHERE c.token_digest = ?", digest);
        if (found.isEmpty())
          return false;
        Row row = found.get();
        return row.request().purpose() == expected && Objects.equals(row.request().owner(), owner)
          && row.expiresAt().isAfter(now) && currentBinding(row.request(), now)
          && (expected != Purpose.NEW_ACCOUNT || !accountExists(row.request().recipient()));
      }));
    } finally {
      if (token != null)
        Arrays.fill(token, '\0');
    }
  }

  /** MUST join the account mutation's writable transaction; it never activates/authenticates by itself. */
  public Optional<VerificationRequest> consume(char[] token, Purpose expected, OwnerBinding owner) {
    try {
      if (!TransactionSynchronizationManager.isActualTransactionActive()
        || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
        throw new IllegalStateException("Verification consumption requires an owning writable transaction");
      }
      if (expected == null || (expected == Purpose.NEW_ACCOUNT) != (owner == null))
        throw new IllegalArgumentException("Invalid verification purpose binding");
      String digest = VerificationDelivery.digestPresented(token);
      if (digest == null)
        return Optional.empty();
      return transaction(() -> {
        if (owner != null && !lockOwner(owner.ownerId()))
          return Optional.empty();
        return gated(now -> {
          Optional<Row> found = find(" WHERE c.token_digest = ?", digest);
          if (found.isEmpty())
            return Optional.empty();
          Row row = found.get();
          if (row.request().purpose() != expected || !Objects.equals(row.request().owner(), owner)
            || !row.expiresAt().isAfter(now) || !currentBinding(row.request(), now)
            || (expected == Purpose.NEW_ACCOUNT && accountExists(row.request().recipient())))
            return Optional.empty();
          return jdbc.update("DELETE FROM verification_challenges WHERE id = ?", row.id()) == 1
            ? Optional.of(row.request())
            : Optional.empty();
        });
      });
    } finally {
      if (token != null)
        Arrays.fill(token, '\0');
    }
  }

  public int cleanBatch() {
    return transaction(() -> gated(this::prune));
  }

  private <T> T transaction(Supplier<T> work) {
    try {
      return transactions.execute(status -> {
        try {
          if (TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            throw new Unavailable();
          return work.get();
        } catch (RuntimeException failure) {
          // Sanitize before TransactionTemplate can report a rollback failure with the application cause.
          throw new Unavailable();
        }
      });
    } catch (RuntimeException failure) {
      // Commit/rollback/connection failures must not escape with driver parameters or row metadata.
      throw new Unavailable();
    }
  }

  private <T> T gated(Function<Instant, T> work) {
    Integer isolation = jdbc.execute((ConnectionCallback<Integer>) connection -> connection.getTransactionIsolation());
    if (!Objects.equals(isolation, Connection.TRANSACTION_READ_COMMITTED))
      throw new Unavailable();
    // Touch, rather than only lock, the singleton: stronger caller snapshots must serialize or abort.
    if (jdbc.update("UPDATE verification_gate SET turn = NOT turn WHERE id = 1") != 1)
      throw new Unavailable();
    return work.apply(clock.instant().truncatedTo(ChronoUnit.MICROS));
  }

  private boolean lockOwner(UUID owner) {
    return !jdbc.queryForList("SELECT id FROM users WHERE id = ? FOR UPDATE", UUID.class, owner).isEmpty();
  }

  private boolean currentBinding(VerificationRequest request, Instant now) {
    if (request.purpose() == Purpose.NEW_ACCOUNT)
      return true; // No synchronous public account lookup.
    return jdbc.queryForList("""
      SELECT u.email FROM users u JOIN auth_sessions s ON s.user_id = u.id
      WHERE u.id = ? AND s.token_digest = ? AND s.expires_at > ?
        AND u.email_verified_at IS NULL AND u.legacy_access_granted = TRUE
      """, String.class, request.owner().ownerId(), request.owner().sessionDigest(), utc(now)).stream()
      .anyMatch(email -> email.equals(request.deliveryAddress()));
  }

  private boolean accountExists(String recipient) {
    return count("SELECT COUNT(*) FROM users WHERE LOWER(email) = ?", recipient) != 0;
  }

  private int count(String sql, Object... arguments) {
    Integer value = jdbc.queryForObject(sql, Integer.class, arguments);
    if (value == null)
      throw new Unavailable();
    return value;
  }
  private Optional<Row> find(String where, Object value) {
    return jdbc.query(ROWS + where, VerificationStore::row, value).stream().findFirst();
  }
  private static OffsetDateTime utc(Instant value) {
    return value.atOffset(ZoneOffset.UTC);
  }

  private static void requireIndependentTransaction() {
    if (TransactionSynchronizationManager.isActualTransactionActive())
      throw new IllegalStateException("Verification delivery requires its own committed transaction");
  }

  private int prune(Instant now) {
    int changed = jdbc.update(
      """
        UPDATE verification_delivery_jobs SET status = 'FINISHED', outcome = 'ABANDONED', claim_id = NULL, claim_until = NULL
        WHERE challenge_id IN (SELECT challenge_id FROM verification_delivery_jobs
          WHERE status = 'CLAIMED' AND claim_until <= ? ORDER BY claim_until, challenge_id LIMIT 100)
        """,
      utc(now));
    changed += jdbc.update("""
      DELETE FROM verification_challenges WHERE id IN (
        SELECT c.id FROM verification_challenges c LEFT JOIN verification_delivery_jobs j ON j.challenge_id = c.id
        WHERE j.challenge_id IS NULL OR (j.status = 'QUEUED' AND c.intent_expires_at <= ?)
          OR (j.status = 'FINISHED' AND (c.token_expires_at IS NULL OR c.token_expires_at <= ?))
        ORDER BY c.created_at, c.id LIMIT 100)
      """, utc(now), utc(now));
    changed += jdbc.update(
      "DELETE FROM verification_admissions WHERE id IN (SELECT id FROM verification_admissions WHERE accepted_at <= ? ORDER BY accepted_at, id LIMIT 100)",
      utc(now.minusSeconds(3600)));
    changed += jdbc.update(
      "DELETE FROM verification_client_events WHERE id IN (SELECT id FROM verification_client_events WHERE accepted_at <= ? ORDER BY accepted_at, id LIMIT 100)",
      utc(now.minusSeconds(60)));
    changed += jdbc.update("""
      DELETE FROM verification_clients WHERE client_key IN (
        SELECT c.client_key FROM verification_clients c WHERE NOT EXISTS
          (SELECT 1 FROM verification_client_events e WHERE e.client_key = c.client_key)
        ORDER BY c.created_at, c.client_key LIMIT 100)
      """);
    changed += jdbc.update("""
      DELETE FROM verification_recipients WHERE recipient_key IN (
        SELECT r.recipient_key FROM verification_recipients r WHERE NOT EXISTS
          (SELECT 1 FROM verification_admissions a WHERE a.recipient_key = r.recipient_key)
          AND NOT EXISTS (SELECT 1 FROM verification_challenges c WHERE c.recipient_key = r.recipient_key)
        ORDER BY r.created_at, r.recipient_key LIMIT 100)
      """);
    return changed;
  }

  private static Row row(ResultSet result, int ignored) throws SQLException {
    UUID owner = result.getObject("owner_id", UUID.class);
    return new Row(result.getObject("id", UUID.class),
      new VerificationRequest(Purpose.valueOf(result.getString("purpose")), result.getString("recipient_key"),
        result.getString("delivery_address"),
        owner == null ? null : new OwnerBinding(owner, result.getString("session_digest"))),
      result.getString("status"), result.getObject("claim_id", UUID.class), instant(result, "claim_until"),
      result.getString("token_digest"), instant(result, "token_expires_at"));
  }

  private static Instant instant(ResultSet result, String name) throws SQLException {
    OffsetDateTime value = result.getObject(name, OffsetDateTime.class);
    return value == null ? null : value.toInstant();
  }

  private record Row(UUID id, VerificationRequest request, String status, UUID claim, Instant claimUntil, String digest,
    Instant expiresAt) {
    @Override
    public String toString() {
      return "VerificationState[redacted]";
    }
  }

  public enum Admission {
    ACCEPTED, RECIPIENT_LIMIT, CLIENT_LIMIT, GLOBAL_LIMIT, CAPACITY, INVALID_BINDING
  }
  public enum DeliveryResult {
    SENT, FAILED, AMBIGUOUS
  }

  public static final class Unavailable extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private Unavailable() {
      super("Verification state unavailable");
    }
  }

  record Limits(int capacity, int clientKeys, int recipient, int global, int client, int claims) {
    static final Limits DEFAULT = new Limits(1000, 1000, 3, 100, 10, 2);
    Limits {
      if (capacity < 2 || capacity > 1000 || clientKeys < 1 || clientKeys > 1000 || recipient < 1 || recipient > 3
        || global < 1 || global > 100 || client < 1 || client > 10 || claims < 1 || claims > 2)
        throw new IllegalArgumentException("Invalid verification bounds");
    }
  }
}

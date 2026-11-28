package dev.keynest.manager.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserAccount {

  static final int EMAIL_MAX_LENGTH = 320;

  @Id
  private UUID id;

  @Column(nullable = false, unique = true, length = EMAIL_MAX_LENGTH)
  private String email;

  @Column(name = "password_hash", nullable = false, length = 255)
  private String passwordHash;

  @Column(name = "password_policy", length = 4000)
  private String passwordPolicy;

  public String getPasswordPolicy() {
    return passwordPolicy;
  }

  public void setPasswordPolicy(String passwordPolicy) {
    this.passwordPolicy = passwordPolicy;
  }

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "email_verified_at")
  private Instant emailVerifiedAt;

  @Column(name = "delivery_email", length = EMAIL_MAX_LENGTH)
  private String deliveryEmail;

  @Column(name = "legacy_access_granted", nullable = false)
  private boolean legacyAccessGranted;

  protected UserAccount() {
  }

  public UserAccount(UUID id, String email, String passwordHash, Instant createdAt) {
    this.id = id;
    this.email = email;
    this.passwordHash = passwordHash;
    this.createdAt = createdAt;
  }

  /** Used only after mailbox proof; eligibility is also enforced by the database. */
  public static UserAccount verified(UUID id, String email, String deliveryEmail, String passwordHash, Instant now) {
    UserAccount account = new UserAccount(id, email, passwordHash, now);
    account.emailVerifiedAt = java.util.Objects.requireNonNull(now);
    account.deliveryEmail = java.util.Objects.requireNonNull(deliveryEmail);
    return account;
  }

  public String getDeliveryEmail() {
    return deliveryEmail;
  }

  public boolean isEmailVerified() {
    return emailVerifiedAt != null;
  }

  /** Caller has consumed the exact owner/session/address-bound proof in this account transaction. */
  void confirmEmail(String deliveryEmail, Instant now) {
    if (isEmailVerified() || !email.equals(deliveryEmail))
      throw new IllegalStateException("Verification binding unavailable");
    this.deliveryEmail = java.util.Objects.requireNonNull(deliveryEmail);
    this.emailVerifiedAt = java.util.Objects.requireNonNull(now);
  }

  public boolean isActivationEligible() {
    return isEmailVerified() || legacyAccessGranted;
  }

  public UUID getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  void replacePasswordHash(String passwordHash) {
    this.passwordHash = java.util.Objects.requireNonNull(passwordHash);
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}

package dev.keynest.manager.vault;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vault_items")
public class VaultItem {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 200)
    private String label;

    @Column(length = 2000)
    private String url;

    @Column(length = 320)
    private String username;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "envelope_bytes", nullable = false, length = 8192)
    private byte[] envelopeBytes;

    @Column(name = "policy_id", nullable = false, length = 120)
    private String policyId;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "last_rotated_at")
    private Instant lastRotatedAt;

    @Column(name = "notify_before_days")
    private Integer notifyBeforeDays;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected VaultItem() {
    }

    public VaultItem(
            UUID id,
            UUID userId,
            String label,
            String url,
            String username,
            byte[] envelopeBytes,
            String policyId,
            Instant expiresAt,
            Instant lastRotatedAt,
            Integer notifyBeforeDays,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.userId = userId;
        this.label = label;
        this.url = url;
        this.username = username;
        this.envelopeBytes = envelopeBytes;
        this.policyId = policyId;
        this.expiresAt = expiresAt;
        this.lastRotatedAt = lastRotatedAt;
        this.notifyBeforeDays = notifyBeforeDays;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public byte[] getEnvelopeBytes() {
        return envelopeBytes;
    }

    public void setEnvelopeBytes(byte[] envelopeBytes) {
        this.envelopeBytes = envelopeBytes;
    }

    public String getPolicyId() {
        return policyId;
    }

    public void setPolicyId(String policyId) {
        this.policyId = policyId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getLastRotatedAt() {
        return lastRotatedAt;
    }

    public void setLastRotatedAt(Instant lastRotatedAt) {
        this.lastRotatedAt = lastRotatedAt;
    }

    public Integer getNotifyBeforeDays() {
        return notifyBeforeDays;
    }

    public void setNotifyBeforeDays(Integer notifyBeforeDays) {
        this.notifyBeforeDays = notifyBeforeDays;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

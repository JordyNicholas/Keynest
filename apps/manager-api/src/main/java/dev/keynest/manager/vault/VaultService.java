package dev.keynest.manager.vault;

import dev.keynest.generate.PasswordGenerator;
import dev.keynest.manager.auth.UserAccount;
import dev.keynest.manager.config.VaultKeyProperties;
import dev.keynest.policy.PasswordPolicy;
import dev.keynest.policy.PolicyOperation;
import dev.keynest.policy.ValidationResult;
import dev.keynest.policy.lifecycle.PasswordStandards;
import dev.keynest.seal.KeynestSeal;
import dev.keynest.seal.SealEnvelope;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class VaultService {

    private final VaultItemRepository items;
    private final PasswordStandards passwordStandards;
    private final PasswordGenerator passwordGenerator;
    private final KeynestSeal seal;
    private final byte[] vaultKey;
    private final String vaultKeyId;

    public VaultService(
            VaultItemRepository items,
            PasswordStandards passwordStandards,
            PasswordGenerator passwordGenerator,
            KeynestSeal seal,
            VaultKeyProperties vaultKeyProperties
    ) {
        this.items = items;
        this.passwordStandards = passwordStandards;
        this.passwordGenerator = passwordGenerator;
        this.seal = seal;
        this.vaultKey = HexFormat.of().parseHex(vaultKeyProperties.getKeyHex());
        if (this.vaultKey.length != 32) {
            throw new IllegalStateException("keynest.vault.key-hex must decode to 32 bytes");
        }
        this.vaultKeyId = vaultKeyProperties.getKeyId();
    }

    @Transactional(readOnly = true)
    public List<VaultItemView> list(UserAccount user) {
        return items.findByUserIdOrderByUpdatedAtDesc(user.getId()).stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public VaultItemSecretView get(UserAccount user, UUID id, boolean reveal) {
        VaultItem item = requireOwned(user, id);
        return new VaultItemSecretView(toView(item), reveal ? openPassword(item) : null);
    }

    @Transactional
    public VaultItemView create(UserAccount user, CreateVaultItemRequest request) {
        if (request.label() == null || request.label().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "label required");
        }
        String policyId = blankToDefault(request.policyId(), PasswordPolicy.DEFAULT_POLICY_ID);
        String password = resolvePassword(request, policyId);
        assertValid(policyId, PolicyOperation.CREATE, password, request.username());

        Instant now = Instant.now();
        Instant expiresAt = request.expiresInDays() == null
                ? null
                : now.plus(request.expiresInDays(), ChronoUnit.DAYS);

        VaultItem item = new VaultItem(
                UUID.randomUUID(),
                user.getId(),
                request.label().trim(),
                blankToNull(request.url()),
                blankToNull(request.username()),
                sealPassword(password, policyId),
                policyId,
                expiresAt,
                now,
                request.notifyBeforeDays() == null ? 14 : request.notifyBeforeDays(),
                now,
                now
        );
        items.save(item);
        return toView(item);
    }

    @Transactional
    public VaultItemView update(UserAccount user, UUID id, UpdateVaultItemRequest request) {
        VaultItem item = requireOwned(user, id);
        if (request.label() != null && !request.label().isBlank()) {
            item.setLabel(request.label().trim());
        }
        if (request.url() != null) {
            item.setUrl(blankToNull(request.url()));
        }
        if (request.username() != null) {
            item.setUsername(blankToNull(request.username()));
        }
        if (request.password() != null && !request.password().isBlank()) {
            String policyId = blankToDefault(request.policyId(), item.getPolicyId());
            assertValid(policyId, PolicyOperation.UPDATE, request.password(), item.getUsername());
            item.setEnvelopeBytes(sealPassword(request.password(), policyId));
            item.setPolicyId(policyId);
            item.setLastRotatedAt(Instant.now());
        }
        if (request.expiresInDays() != null) {
            item.setExpiresAt(Instant.now().plus(request.expiresInDays(), ChronoUnit.DAYS));
        }
        if (request.notifyBeforeDays() != null) {
            item.setNotifyBeforeDays(request.notifyBeforeDays());
        }
        item.setUpdatedAt(Instant.now());
        items.save(item);
        return toView(item);
    }

    @Transactional
    public void delete(UserAccount user, UUID id) {
        items.delete(requireOwned(user, id));
    }

    private String resolvePassword(CreateVaultItemRequest request, String policyId) {
        if (Boolean.TRUE.equals(request.generate())) {
            PasswordPolicy policy = passwordStandards.runtimePolicy(policyId, PolicyOperation.GENERATE);
            return passwordGenerator.generate(policy);
        }
        if (request.password() == null || request.password().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "password required unless generate=true");
        }
        return request.password();
    }

    private void assertValid(String policyId, PolicyOperation operation, String password, String username) {
        ValidationResult result = switch (operation) {
            case CREATE -> passwordStandards.validateForCreate(policyId, password, username);
            case UPDATE -> passwordStandards.validateForUpdate(policyId, password, username);
            case ROTATE -> passwordStandards.validateForRotate(policyId, password, username);
            case GENERATE -> passwordStandards.validateForGenerateCheck(policyId, password);
        };
        if (!result.valid()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "password policy violations: " + result.violations());
        }
    }

    private byte[] sealPassword(String password, String policyId) {
        byte[] aad = policyId.getBytes(StandardCharsets.UTF_8);
        return seal.seal(password.getBytes(StandardCharsets.UTF_8), vaultKey, vaultKeyId, aad).toBytes();
    }

    private String openPassword(VaultItem item) {
        SealEnvelope envelope = SealEnvelope.fromBytes(item.getEnvelopeBytes());
        byte[] aad = item.getPolicyId().getBytes(StandardCharsets.UTF_8);
        return new String(seal.open(envelope, vaultKey, aad), StandardCharsets.UTF_8);
    }

    private VaultItem requireOwned(UserAccount user, UUID id) {
        return items.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "vault item not found"));
    }

    private VaultItemView toView(VaultItem item) {
        return new VaultItemView(
                item.getId(),
                item.getLabel(),
                item.getUrl(),
                item.getUsername(),
                item.getPolicyId(),
                item.getExpiresAt(),
                item.getLastRotatedAt(),
                item.getNotifyBeforeDays(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    public record CreateVaultItemRequest(
            String label,
            String url,
            String username,
            String password,
            Boolean generate,
            String policyId,
            Integer expiresInDays,
            Integer notifyBeforeDays
    ) {
    }

    public record UpdateVaultItemRequest(
            String label,
            String url,
            String username,
            String password,
            String policyId,
            Integer expiresInDays,
            Integer notifyBeforeDays
    ) {
    }

    public record VaultItemView(
            UUID id,
            String label,
            String url,
            String username,
            String policyId,
            Instant expiresAt,
            Instant lastRotatedAt,
            Integer notifyBeforeDays,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record VaultItemSecretView(VaultItemView item, String password) {
    }
}

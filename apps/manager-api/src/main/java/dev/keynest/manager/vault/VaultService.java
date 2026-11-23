package dev.keynest.manager.vault;

import dev.keynest.generate.PasswordGenerator;
import dev.keynest.manager.auth.UserAccount;
import dev.keynest.manager.auth.UserAccountRepository;
import dev.keynest.manager.config.VaultKeyProperties;
import dev.keynest.manager.policy.AccountPasswordPolicyService;
import dev.keynest.policy.PasswordValidator;
import dev.keynest.policy.PasswordPolicy;
import dev.keynest.policy.ValidationResult;
import dev.keynest.seal.KeynestSeal;
import dev.keynest.seal.SealEnvelope;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class VaultService {

    private static final Instant MAX_CALENDAR_EXPIRY = Instant.parse("+10000-01-02T00:00:00Z");
    private final VaultItemRepository items;
    private final AccountPasswordPolicyService accountPolicies;
    private final PasswordGenerator passwordGenerator;
    private final KeynestSeal seal;
    private final byte[] vaultKey;
    private final String vaultKeyId;
    private final UserAccountRepository users;

    public VaultService(
            VaultItemRepository items,
            PasswordGenerator passwordGenerator,
            KeynestSeal seal,
            VaultKeyProperties vaultKeyProperties,
            AccountPasswordPolicyService accountPolicies,
            UserAccountRepository users
    ) {
        this.items = items;
        this.users = users;
        this.accountPolicies = accountPolicies;
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
    public VaultItemView get(UserAccount user, UUID id) {
        return toView(requireOwned(user, id));
    }

    @Transactional(readOnly = true)
    public VaultItemSecretView reveal(UserAccount user, UUID id) {
        VaultItem item = requireOwned(user, id);
        return new VaultItemSecretView(toView(item), openPassword(item));
    }

    @Transactional
    public VaultItemView create(UserAccount user, CreateVaultItemRequest request) {
        lockOwner(user);
        if (request.label() == null || request.label().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "label required");
        }
        Instant now = Instant.now();
        Instant expiresAt = resolveExpiry(request.expiresInDays(), request.expiresAt(), now);
        PasswordPolicy policy = currentPolicy(user, request.policyId());
        String policyId = policy.id();
        String password = resolvePassword(request, policy);
        assertValid(policy, password, request.username());

        UUID itemId = UUID.randomUUID();
        VaultItem item = new VaultItem(
                itemId,
                user.getId(),
                request.label().trim(),
                blankToNull(request.url()),
                blankToNull(request.username()),
                sealPassword(password, user.getId(), itemId, policyId),
                policyId,
                VaultItemAad.VERSION_ITEM_V1,
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
        lockOwner(user);
        VaultItem item = requireOwned(user, id);
        Instant expiresAt = resolveExpiry(request.expiresInDays(), request.expiresAt(), Instant.now());
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
            PasswordPolicy policy = currentPolicy(user, request.policyId());
            String policyId = policy.id();
            assertValid(policy, request.password(), item.getUsername());
            item.setEnvelopeBytes(sealPassword(
                    request.password(), item.getUserId(), item.getId(), policyId
            ));
            item.setPolicyId(policyId);
            item.setAadVersion(VaultItemAad.VERSION_ITEM_V1);
            item.setLastRotatedAt(Instant.now());
        }
        if (expiresAt != null) {
            item.setExpiresAt(expiresAt);
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
        lockOwner(user);
        items.delete(requireOwned(user, id));
    }

    private void lockOwner(UserAccount user) {
        users.findForPolicyUpdate(user.getId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "account unavailable"));
    }

    private static Instant resolveExpiry(Integer days, Instant absolute, Instant now) {
        if (days != null && absolute != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose an expiry date or a duration, not both");
        }
        if (absolute != null) {
            if (!absolute.isAfter(now) || absolute.isAfter(MAX_CALENDAR_EXPIRY)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expiry date must be in the future and within the supported calendar");
            }
            return absolute;
        }
        // Preserve the existing relative-day API, including callers importing already-expired items.
        return days == null ? null : now.plus(days, ChronoUnit.DAYS);
    }

    private PasswordPolicy currentPolicy(UserAccount user, String requestedId) {
        PasswordPolicy policy = accountPolicies.effective(user);
        if (requestedId != null && !requestedId.isBlank() && !policy.id().equals(requestedId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Password policy changed. Reload the form.");
        }
        return policy;
    }

    private String resolvePassword(CreateVaultItemRequest request, PasswordPolicy policy) {
        if (Boolean.TRUE.equals(request.generate())) {
            for (int attempt = 0; attempt < 100; attempt++) {
                String candidate = passwordGenerator.generate(policy);
                if (PasswordValidator.validate(candidate, policy, request.username()).valid()) return candidate;
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a different username or policy");
        }
        if (request.password() == null || request.password().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "password required unless generate=true");
        }
        return request.password();
    }

    private void assertValid(PasswordPolicy policy, String password, String username) {
        ValidationResult result = PasswordValidator.validate(password, policy, username);
        if (!result.valid()) {
            throw new PolicyRejected(List.copyOf(result.violations()));
        }
    }

    static final class PolicyRejected extends RuntimeException {
        private final List<String> violations;

        PolicyRejected(List<String> violations) {
            super("Vault password policy rejected the password");
            this.violations = violations;
        }

        List<String> violations() {
            return violations;
        }
    }

    // Package-local restore reuses the existing instance key and receiver/item AAD.
    byte[] sealPassword(
            String password, UUID userId, UUID itemId, String policyId
    ) {
        byte[] aad = VaultItemAad.itemV1(userId, itemId, policyId);
        byte[] plaintext = password.getBytes(StandardCharsets.UTF_8);
        try {
            return seal.seal(plaintext, vaultKey, vaultKeyId, aad).toBytes();
        } finally {
            Arrays.fill(plaintext, (byte) 0);
        }
    }

    // Package-local bulk export also calls this only after authorizing the complete selection.
    String openPassword(VaultItem item) {
        SealEnvelope envelope = SealEnvelope.fromBytes(item.getEnvelopeBytes());
        byte[] aad = switch (item.getAadVersion() == null ? 0 : item.getAadVersion()) {
            case 0 -> item.getPolicyId().getBytes(StandardCharsets.UTF_8);
            case VaultItemAad.VERSION_ITEM_V1 -> VaultItemAad.itemV1(
                    item.getUserId(), item.getId(), item.getPolicyId()
            );
            default -> throw new IllegalStateException(
                    "unsupported vault item AAD version: " + item.getAadVersion()
            );
        };
        byte[] plaintext = seal.open(envelope, vaultKey, aad);
        try {
            return new String(plaintext, StandardCharsets.UTF_8);
        } finally {
            Arrays.fill(plaintext, (byte) 0);
        }
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

    public record CreateVaultItemRequest(
            String label,
            String url,
            String username,
            String password,
            Boolean generate,
            String policyId,
            Integer expiresInDays,
            Integer notifyBeforeDays,
            Instant expiresAt
    ) {
        public CreateVaultItemRequest(String label, String url, String username, String password, Boolean generate,
                String policyId, Integer expiresInDays, Integer notifyBeforeDays) {
            this(label, url, username, password, generate, policyId, expiresInDays, notifyBeforeDays, null);
        }
    }

    public record UpdateVaultItemRequest(
            String label,
            String url,
            String username,
            String password,
            String policyId,
            Integer expiresInDays,
            Integer notifyBeforeDays,
            Instant expiresAt
    ) {
        public UpdateVaultItemRequest(String label, String url, String username, String password,
                String policyId, Integer expiresInDays, Integer notifyBeforeDays) {
            this(label, url, username, password, policyId, expiresInDays, notifyBeforeDays, null);
        }
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

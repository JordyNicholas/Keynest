package dev.keynest.manager.vault;

import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.keynest.manager.auth.AuthService;
import dev.keynest.manager.auth.UserAccount;
import dev.keynest.manager.policy.AccountPasswordPolicyService;
import dev.keynest.manager.vault.export.EncryptedVaultBackup;
import jakarta.persistence.EntityManager;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = "keynest.rate-limit.enabled=false")
class VaultRestoreIntegrationTest {
  private static final String ACCOUNT = "Synthetic master passphrase!42";
  private static final String PHRASE = "Synthetic portable file passphrase";
  private static final String SECRET = "aB2#mangoC3$deF4%";
  @Autowired AuthService auth;
  @Autowired VaultRestoreService restore;
  @Autowired VaultExportSnapshots exports;
  @Autowired VaultItemRepository items;
  @Autowired VaultExportAdmission admission;
  @Autowired AccountPasswordPolicyService policies;
  @Autowired EntityManager em;
  @MockitoSpyBean VaultService vault;
  @MockitoSpyBean EncryptedVaultBackup backups;
  private UserAccount source, receiver;
  private byte[] file;
  private UUID original;

  @BeforeEach void setup() {
    source = owner();receiver = owner();
    original = vault.create(source, new VaultService.CreateVaultItemRequest("Synthetic source", "https://example.invalid", "sample", SECRET, false, null, -3, 14)).id();
    file = backups.encrypt(payload(), PHRASE.toCharArray());clearInvocations(vault, backups);
  }

  @Test void preservesHistoryWithoutOverwritingOrApplyingSourcePolicy() {
    var sourceItem = items.findById(original).orElseThrow();
    var rules = policies.inspect(receiver).rules();
    policies.update(receiver, new AccountPasswordPolicyService.Settings("custom", new AccountPasswordPolicyService.Rules(
      40, 128, rules.requireUpper(), rules.requireLower(), rules.requireDigit(), rules.requireSymbol(), rules.minCategories(),
      rules.excludeAmbiguous(), rules.forbidWhitespace(), rules.forbidUsernameSubstring(), rules.bannedSubstrings(), rules.bannedRegexes(), rules.maxAgeDays())));
    String policy = policies.inspect(receiver).id();
    char[] phrase = PHRASE.toCharArray();var preview = restore.preview(receiver.getId(), file, phrase);assertThat(phrase).containsOnly('\0');
    assertThat(preview.itemCount()).isEqualTo(1);assertThat(items.findByUserIdOrderByUpdatedAtDesc(receiver.getId())).isEmpty();
    assertThat(restore.restore(receiver.getId(), file, PHRASE.toCharArray(), preview.snapshot()).restoredCount()).isEqualTo(1);
    assertThat(policies.inspect(receiver).id()).isEqualTo(policy);
    var restored = items.findByUserIdOrderByUpdatedAtDesc(receiver.getId()).getFirst();
    assertThat(restored.getId()).isNotEqualTo(original);assertThat(restored.getUserId()).isEqualTo(receiver.getId());
    assertThat(restored.getAadVersion()).isEqualTo(VaultItemAad.VERSION_ITEM_V1);
    assertThat(restored.getLabel()).isEqualTo(sourceItem.getLabel());assertThat(restored.getUrl()).isEqualTo(sourceItem.getUrl());
    assertThat(restored.getUsername()).isEqualTo(sourceItem.getUsername());assertThat(restored.getPolicyId()).isEqualTo(sourceItem.getPolicyId());
    assertThat(restored.getExpiresAt()).isEqualTo(sourceItem.getExpiresAt());assertThat(restored.getCreatedAt()).isEqualTo(sourceItem.getCreatedAt());
    assertThat(restored.getUpdatedAt()).isEqualTo(sourceItem.getUpdatedAt());assertThat(restored.getLastRotatedAt()).isEqualTo(sourceItem.getLastRotatedAt());
    assertThat(restored.getNotifyBeforeDays()).isEqualTo(sourceItem.getNotifyBeforeDays());
    assertThat(SECRET.equals(vault.reveal(receiver, restored.getId()).password())).isTrue();
    assertThatThrownBy(() -> vault.get(source, restored.getId())).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    assertThat(items.findById(original).orElseThrow().getUserId()).isEqualTo(source.getId());
  }

  @Test void changedFileWrongPassphraseTamperAndDifferentReceiverCannotUseAReview() {
    var preview = restore.preview(receiver.getId(), file, PHRASE.toCharArray());
    char[] wrong = "Wrong synthetic portable passphrase".toCharArray();
    assertCode(() -> restore.restore(receiver.getId(), file, wrong, preview.snapshot()), VaultRestoreFailure.Code.RESTORE_INVALID_FILE);
    assertThat(wrong).containsOnly('\0');
    byte[] tampered = file.clone();tampered[tampered.length - 1] ^= 1;
    assertCode(() -> restore.preview(receiver.getId(), tampered, PHRASE.toCharArray()), VaultRestoreFailure.Code.RESTORE_INVALID_FILE);
    assertCode(() -> restore.restore(receiver.getId(), tampered, PHRASE.toCharArray(), preview.snapshot()), VaultRestoreFailure.Code.RESTORE_CHANGED);
    assertCode(() -> restore.restore(source.getId(), file, PHRASE.toCharArray(), preview.snapshot()), VaultRestoreFailure.Code.RESTORE_CHANGED);
    assertThat(items.findByUserIdOrderByUpdatedAtDesc(receiver.getId())).isEmpty();
  }

  @Test void failureAfterAFlushedInsertRollsBackAllRowsAndReturnsNoProviderCause() {
    vault.create(source, new VaultService.CreateVaultItemRequest("Second", null, null, SECRET, false, null, null, null));
    file = backups.encrypt(payload(), PHRASE.toCharArray());var preview = restore.preview(receiver.getId(), file, PHRASE.toCharArray());
    AtomicInteger calls = new AtomicInteger();
    doAnswer(call -> {
      if (calls.incrementAndGet() == 2) {
        em.flush();assertThat(items.findByUserIdOrderByUpdatedAtDesc(receiver.getId())).hasSize(1);
        throw new IllegalStateException("synthetic provider detail");
      }
      return call.callRealMethod();
    }).when(vault).sealPassword(anyString(), eq(receiver.getId()), any(), anyString());
    assertThatThrownBy(() -> restore.restore(receiver.getId(), file, PHRASE.toCharArray(), preview.snapshot()))
      .isInstanceOf(VaultRestoreFailure.class).hasMessage("RESTORE_FAILED").hasNoCause();
    assertThat(items.findByUserIdOrderByUpdatedAtDesc(receiver.getId())).isEmpty();
    try (var lease = admission.acquire()) { assertThat(lease).isNotNull(); }
  }

  @Test void clearsOwnedCopiesPayloadAndCredentialsAndRejectsBusyWork() {
    AtomicReference<byte[]> copiedFile = new AtomicReference<>();AtomicReference<ObjectNode> plain = new AtomicReference<>();
    doAnswer(call -> { copiedFile.set(call.getArgument(0));ObjectNode result = (ObjectNode) call.callRealMethod();plain.set(result);return result; })
      .when(backups).decrypt(any(byte[].class), any(char[].class));
    byte[] originalFile = file.clone();restore.preview(receiver.getId(), file, PHRASE.toCharArray());
    assertThat(copiedFile.get()).isNotSameAs(file).containsOnly((byte) 0);assertThat(plain.get().isEmpty()).isTrue();assertThat(Arrays.equals(file, originalFile)).isTrue();
    char[] phrase = PHRASE.toCharArray();
    try (var lease = admission.acquire()) { assertCode(() -> restore.preview(receiver.getId(), file, phrase), VaultRestoreFailure.Code.RESTORE_BUSY); }
    assertThat(phrase).containsOnly('\0');
  }

  @Test void oversizedReceivingEnvelopeRejectsTheEntireRestore() {
    ObjectNode document = payload();((ObjectNode) document.path("items").get(0)).put("password", "x".repeat(8192));
    byte[] large = backups.encrypt(document, PHRASE.toCharArray());
    var preview = restore.preview(receiver.getId(), large, PHRASE.toCharArray());
    assertCode(() -> restore.restore(receiver.getId(), large, PHRASE.toCharArray(), preview.snapshot()), VaultRestoreFailure.Code.RESTORE_LIMIT_EXCEEDED);
    assertThat(items.findByUserIdOrderByUpdatedAtDesc(receiver.getId())).isEmpty();
  }

  @Test void missingOwnerAndEmptyFileCannotWriteAndAlwaysConsumeCredentials() {
    char[] phrase = PHRASE.toCharArray();
    assertCode(() -> restore.preview(UUID.randomUUID(), file, phrase), VaultRestoreFailure.Code.RESTORE_OWNER_UNAVAILABLE);assertThat(phrase).containsOnly('\0');
    ObjectNode document = payload();document.putArray("items");byte[] empty = backups.encrypt(document, PHRASE.toCharArray());
    assertCode(() -> restore.preview(receiver.getId(), empty, PHRASE.toCharArray()), VaultRestoreFailure.Code.RESTORE_EMPTY);
    assertThat(items.findByUserIdOrderByUpdatedAtDesc(receiver.getId())).isEmpty();
  }

  private ObjectNode payload() {
    var selection = new VaultExportSelection(VaultExportSelection.Scope.ALL, java.util.List.of());
    return exports.preparePayloadForVerifiedUser(source, selection, exports.preview(source, selection).snapshot());
  }
  private UserAccount owner() { return auth.requireUser("Bearer " + auth.register("restore-" + UUID.randomUUID() + "@example.invalid", ACCOUNT).token()); }
  private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, VaultRestoreFailure.Code code) {
    assertThatThrownBy(action).isInstanceOfSatisfying(VaultRestoreFailure.class, failure -> assertThat(failure.code()).isEqualTo(code)).hasNoCause();
  }
}

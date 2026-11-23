package dev.keynest.manager.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.keynest.manager.auth.AuthService;
import dev.keynest.manager.auth.UserAccount;
import dev.keynest.manager.security.PasswordHashingService;
import dev.keynest.manager.vault.VaultExportAdmission;
import dev.keynest.manager.vault.VaultExportSnapshots;
import dev.keynest.manager.vault.export.EncryptedVaultBackup;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VaultExportControllerTest {
  @Test void disconnectClearsBuffersAndReleasesBulkCapacity() throws Exception {
    var auth = mock(AuthService.class);var passwords = mock(PasswordHashingService.class);
    var snapshots = mock(VaultExportSnapshots.class);var backups = mock(EncryptedVaultBackup.class);
    var admission = new VaultExportAdmission();
    var owner = new UserAccount(UUID.randomUUID(), "synthetic@example.invalid", "synthetic hash", Instant.now());
    when(auth.requireUser(anyString())).thenReturn(owner);
    AtomicReference<char[]> account = new AtomicReference<>(), phrase = new AtomicReference<>();
    when(passwords.verifyAndClear(any(), anyString())).thenAnswer(call -> { account.set(call.getArgument(0));return true; });
    ObjectNode payload = new ObjectMapper().createObjectNode().put("password", "synthetic transient value");
    when(snapshots.preparePayloadForVerifiedUser(any(), any(), anyString())).thenReturn(payload);
    byte[] encrypted = new byte[128];java.util.Arrays.fill(encrypted, (byte) 42);
    when(backups.encrypt(any(), any())).thenAnswer(call -> { phrase.set(call.getArgument(1));return encrypted; });
    var request = new MockHttpServletRequest("POST", ApiPaths.VAULT_EXPORT_DOWNLOAD);
    request.addHeader("Authorization", "Bearer synthetic-session");request.setContentType("application/json");
    request.setContent(("{\"destination\":\"keynest\",\"scope\":\"all\",\"snapshot\":\"" + "0".repeat(64)
      + "\",\"accountPassword\":\"Synthetic account password\",\"backupPassphrase\":\"Synthetic backup passphrase\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8));
    var response = new MockHttpServletResponse() {
      @Override public ServletOutputStream getOutputStream() {
        return new ServletOutputStream() {
          @Override public boolean isReady() { return true; }
          @Override public void setWriteListener(WriteListener listener) {}
          @Override public void write(int value) throws IOException { throw new IOException("synthetic disconnect"); }
        };
      }
    };
    var controller = new VaultExportController(auth, passwords, snapshots, admission, backups);
    assertThatThrownBy(() -> controller.download(request, response)).isInstanceOf(IOException.class);
    assertThat(encrypted).containsOnly((byte) 0);assertThat(account.get()).containsOnly('\0');assertThat(phrase.get()).containsOnly('\0');
    assertThat(payload.isEmpty()).isTrue();
    try (var lease = admission.acquire()) { lease.close(); }
    try (var lease = admission.acquire()) {
      assertThatThrownBy(admission::acquire).isInstanceOf(dev.keynest.manager.vault.VaultExportFailure.class);
    }
  }
}

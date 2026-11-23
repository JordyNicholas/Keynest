package dev.keynest.manager.api;

import dev.keynest.manager.vault.VaultRestoreFailure;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.*;

class VaultRestoreRequestTest {
  @Test void boundsStreamingDecisionsAndRejectsUnknownNestedFieldsBeforeBuildingUntrustedTrees() {
    String base = "{\"file\":\"AA==\",\"backupPassphrase\":\"Synthetic file passphrase\",\"decisions\":";
    for (String decisions : new String[]{"[" + "{\"sourceIndex\":0,\"action\":\"ignore\"},".repeat(500) + "{\"sourceIndex\":1,\"action\":\"ignore\"}]",
      "[{\"sourceIndex\":0,\"action\":\"ignore\",\"unknown\":[[]]}]", "[{\"sourceIndex\":0,\"action\":\"ignore\",\"action\":\"add\"}]"}) {
      assertThatThrownBy(() -> VaultRestoreRequest.read(request(base + decisions + "}"), false)).isInstanceOf(VaultRestoreFailure.class);
    }
    try (var body = VaultRestoreRequest.read(request(base + "[{\"sourceIndex\":0,\"action\":\"ignore\",\"targetId\":null}]}"), false)) {
      assertThat(body.decisions).hasSize(1);
    }
  }
  @Test void ownsAndClearsFileAndCredentialsAndReleasesTheSingleAdmissionExactlyOnce() {
    var request = request("{\"file\":\"AA==\",\"backupPassphrase\":\"Synthetic file passphrase\",\"accountPassword\":\"Synthetic account password\",\"planId\":\"" + "a".repeat(43) + "\"}");
    var body = VaultRestoreRequest.read(request, true);
    assertThatThrownBy(() -> VaultRestoreRequest.read(request, true)).isInstanceOf(VaultRestoreFailure.class);
    body.close();body.close();assertThat(body.file).containsOnly((byte) 0);assertThat(body.passphrase).containsOnly('\0');assertThat(body.accountPassword).containsOnly('\0');
    assertThat(body.toString()).isEqualTo("VaultRestoreRequest[redacted]");
    try (var next = VaultRestoreRequest.read(request("{\"file\":\"AA==\",\"backupPassphrase\":\"Synthetic file passphrase\"}"), false)) {
      assertThatThrownBy(() -> VaultRestoreRequest.read(request, true)).isInstanceOf(VaultRestoreFailure.class);
    }
  }
  @Test void boundsUnknownLengthInputAndRestoresAdmissionOnRejection() {
    var request = new MockHttpServletRequest() { @Override public long getContentLengthLong() { return -1; } };
    request.setContentType("application/json");request.setContent(new byte[VaultRestoreRequest.MAX_BYTES + 1]);
    assertThatThrownBy(() -> VaultRestoreRequest.read(request, false)).isInstanceOfSatisfying(VaultRestoreFailure.class,
      failure -> assertThat(failure.code()).isEqualTo(VaultRestoreFailure.Code.RESTORE_REQUEST_TOO_LARGE));
    try (var next = VaultRestoreRequest.read(request("{\"file\":\"AA==\",\"backupPassphrase\":\"Synthetic file passphrase\"}"), false)) { assertThat(next.file).hasSize(1); }
  }
  private static MockHttpServletRequest request(String body) { var request = new MockHttpServletRequest();request.setContentType("application/json");request.setContent(body.getBytes(StandardCharsets.UTF_8));return request; }
}

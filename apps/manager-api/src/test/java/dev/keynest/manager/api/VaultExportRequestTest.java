package dev.keynest.manager.api;

import dev.keynest.manager.vault.VaultExportFailure;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.*;

class VaultExportRequestTest {
  @Test void unknownLengthBodiesStillEnforceTheBound() {
    var request = new MockHttpServletRequest() {
      @Override public long getContentLengthLong() { return -1; }
      @Override public int getContentLength() { return -1; }
    };
    request.setContentType("application/json");request.setContent(new byte[VaultExportRequest.MAX_BYTES + 1]);
    assertThatThrownBy(() -> VaultExportRequest.read(request, false)).isInstanceOfSatisfying(VaultExportFailure.class,
      failure -> assertThat(failure.code()).isEqualTo(VaultExportFailure.Code.EXPORT_REQUEST_TOO_LARGE));
  }

  @Test void ownedCredentialsAreRedactedAndClearedOnClose() {
    var request = new MockHttpServletRequest();request.setContentType("application/json");
    request.setContent(("{\"destination\":\"keynest\",\"scope\":\"all\",\"snapshot\":\"" + "0".repeat(64)
      + "\",\"accountPassword\":\"Synthetic account password\",\"backupPassphrase\":\"Synthetic backup passphrase\"}").getBytes(StandardCharsets.UTF_8));
    VaultExportRequest body = VaultExportRequest.read(request, true);
    assertThat(body.toString()).doesNotContain("Synthetic", "password");
    assertThat(body.selection.toString()).doesNotContain("scope", "ids");
    body.close();assertThat(body.accountPassword).containsOnly('\0');assertThat(body.backupPassphrase).containsOnly('\0');
  }
}

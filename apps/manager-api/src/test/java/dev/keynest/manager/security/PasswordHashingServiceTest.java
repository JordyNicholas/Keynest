package dev.keynest.manager.security;

import dev.keynest.seal.PasswordHasher;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasswordHashingServiceTest {

  @Test
  void hashAndVerifyShareCapacity() throws Exception {
    PasswordHasher hasher = mock(PasswordHasher.class);
    PasswordHashingService work = new PasswordHashingService(hasher, 1);
    CountDownLatch started = new CountDownLatch(1);
    CountDownLatch finish = new CountDownLatch(1);
    when(hasher.hashPassword(any(char[].class))).thenAnswer(call -> {
      started.countDown();
      assertThat(finish.await(5, TimeUnit.SECONDS)).isTrue();
      return "synthetic hash";
    });
    when(hasher.verifyPassword(any(char[].class), eq("synthetic hash"))).thenReturn(true);

    try (var worker = Executors.newSingleThreadExecutor()) {
      var inFlight = worker.submit(() -> work.hash("synthetic password"));
      try {
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        assertThatThrownBy(() -> work.verify("synthetic password", "synthetic hash"))
          .isInstanceOfSatisfying(ResponseStatusException.class,
            failure -> assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        char[] rejected = "synthetic password".toCharArray();
        assertThatThrownBy(() -> work.verifyAndClear(rejected, "synthetic hash")).isInstanceOf(ResponseStatusException.class);
        assertThat(rejected).containsOnly('\0');
        char[] newPassword = "synthetic replacement".toCharArray();
        assertThatThrownBy(() -> work.hashAndClear(newPassword)).isInstanceOf(ResponseStatusException.class);
        assertThat(newPassword).containsOnly('\0');
        verify(hasher, never()).verifyPassword(any(char[].class), eq("synthetic hash"));
      } finally {
        finish.countDown();
      }
      assertThat(inFlight.get(5, TimeUnit.SECONDS)).isEqualTo("synthetic hash");
    }

    assertThat(work.verify("synthetic password", "synthetic hash")).isTrue();
  }

  @Test
  void consumesOwnedPasswordBuffersOnSuccessAndProviderFailure() {
    PasswordHasher hasher = mock(PasswordHasher.class);
    PasswordHashingService work = new PasswordHashingService(hasher, 1);
    when(hasher.verifyPassword(any(char[].class), eq("synthetic hash"))).thenReturn(true);
    char[] password = "synthetic credential".toCharArray();
    assertThat(work.verifyAndClear(password, "synthetic hash")).isTrue();
    assertThat(password).containsOnly('\0');
    when(hasher.verifyPassword(any(char[].class), eq("synthetic hash"))).thenThrow(new IllegalStateException());
    char[] failed = "synthetic credential".toCharArray();
    assertThatThrownBy(() -> work.verifyAndClear(failed, "synthetic hash")).isInstanceOf(IllegalStateException.class);
    assertThat(failed).containsOnly('\0');
  }

  @Test
  void newPasswordHashingConsumesOwnedBuffersOnSuccessAndFailure() {
    PasswordHasher hasher = mock(PasswordHasher.class);
    PasswordHashingService work = new PasswordHashingService(hasher, 1);
    when(hasher.hashPassword(any(char[].class))).thenReturn("synthetic hash").thenThrow(new IllegalStateException());
    char[] success = "synthetic replacement".toCharArray();
    assertThat(work.hashAndClear(success)).isEqualTo("synthetic hash");assertThat(success).containsOnly('\0');
    char[] failed = "synthetic replacement".toCharArray();
    assertThatThrownBy(() -> work.hashAndClear(failed)).isInstanceOf(IllegalStateException.class);
    assertThat(failed).containsOnly('\0');
  }

  @Test
  void releasesCapacityAfterFailure() {
    PasswordHasher hasher = mock(PasswordHasher.class);
    PasswordHashingService work = new PasswordHashingService(hasher, 1);
    when(hasher.hashPassword(any(char[].class)))
      .thenThrow(new IllegalStateException("synthetic failure"));
    when(hasher.verifyPassword(any(char[].class), eq("synthetic hash"))).thenReturn(true);

    assertThatThrownBy(() -> work.hash("synthetic password")).isInstanceOf(IllegalStateException.class);
    assertThat(work.verify("synthetic password", "synthetic hash")).isTrue();
  }
}

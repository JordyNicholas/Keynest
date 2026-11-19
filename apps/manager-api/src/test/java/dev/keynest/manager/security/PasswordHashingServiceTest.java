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
        verify(hasher, never()).verifyPassword(any(char[].class), eq("synthetic hash"));
      } finally {
        finish.countDown();
      }
      assertThat(inFlight.get(5, TimeUnit.SECONDS)).isEqualTo("synthetic hash");
    }

    assertThat(work.verify("synthetic password", "synthetic hash")).isTrue();
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

package dev.keynest.manager.auth;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import dev.keynest.manager.security.PasswordHashingService;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class PasswordChangeServiceTest {
  private final AuthService auth = mock(AuthService.class);
  private final PasswordHashingService passwords = mock(PasswordHashingService.class);
  private final PasswordChangeTransactions transactions = mock(PasswordChangeTransactions.class);
  private final PasswordChangeService service = new PasswordChangeService(auth, passwords, transactions);
  private final UserAccount owner = dev.keynest.manager.testing.TestAccounts.account(UUID.randomUUID(),
    "synthetic@example.invalid", "stored-hash", Instant.now());
  private static final String NEXT = "Synthetic new password!42";

  @Test
  void verifiesLegacyCurrentProofBeforeHashingAndConsumesBothBuffers() {
    when(auth.requireUser("Bearer synthetic")).thenReturn(owner);
    when(passwords.verifyAndClear(any(), eq("stored-hash"))).thenReturn(true);
    when(passwords.hashAndClear(any())).thenReturn("new-hash");
    char[] current = "short".toCharArray(), next = NEXT.toCharArray();
    service.change("Bearer synthetic", current, next);
    assertThat(Arrays.equals(current, new char[current.length])).isTrue();
    assertThat(Arrays.equals(next, new char[next.length])).isTrue();
    var order = inOrder(passwords, transactions);
    order.verify(passwords).verifyAndClear(any(), eq("stored-hash"));
    order.verify(passwords).hashAndClear(any());
    order.verify(transactions).replace(owner.getId(), "stored-hash", SessionTokenDigest.digest("synthetic"),
      "new-hash");
  }

  @Test
  void invalidNewPasswordsAndAliasedBuffersCannotPerformPasswordWork() {
    for (String value : new String[]{"", " ".repeat(15), "🔑".repeat(14), "a".repeat(32769)}) {
      char[] current = "legacy".toCharArray(), next = value.toCharArray();
      status(() -> service.change("Bearer synthetic", current, next), HttpStatus.BAD_REQUEST);
      assertThat(Arrays.equals(current, new char[current.length])).isTrue();
      assertThat(Arrays.equals(next, new char[next.length])).isTrue();
    }
    char[] alias = NEXT.toCharArray();
    status(() -> service.change("Bearer synthetic", alias, alias), HttpStatus.BAD_REQUEST);
    verifyNoInteractions(auth, passwords, transactions);
  }

  @Test
  void wrongProofDoesNotHashTheReplacementOrMutate() {
    when(auth.requireUser("Bearer synthetic")).thenReturn(owner);
    char[] current = "wrong".toCharArray(), next = NEXT.toCharArray();
    status(() -> service.change("Bearer synthetic", current, next), HttpStatus.FORBIDDEN);
    assertThat(Arrays.equals(next, new char[next.length])).isTrue();
    verify(passwords, never()).hashAndClear(any());
    verifyNoInteractions(transactions);
  }

  @Test
  void hashingFailureClearsBothBuffersAndReleasesAdmission() {
    when(auth.requireUser("Bearer synthetic")).thenReturn(owner);
    when(passwords.verifyAndClear(any(), anyString())).thenReturn(true);
    when(passwords.hashAndClear(any())).thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE))
      .thenReturn("new-hash");
    char[] current = "legacy".toCharArray(), next = NEXT.toCharArray();
    status(() -> service.change("Bearer synthetic", current, next), HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(Arrays.equals(current, new char[current.length])).isTrue();
    assertThat(Arrays.equals(next, new char[next.length])).isTrue();
    verifyNoInteractions(transactions);
    service.change("Bearer synthetic", "legacy".toCharArray(), NEXT.toCharArray());
    verify(transactions).replace(any(), anyString(), anyString(), eq("new-hash"));
  }

  @Test
  void simultaneousLocalRequestIsRejectedWithoutQueuingAndCleared() throws Exception {
    when(auth.requireUser("Bearer synthetic")).thenReturn(owner);
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    when(passwords.verifyAndClear(any(), anyString())).thenAnswer(call -> {
      entered.countDown();
      assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
      return true;
    });
    when(passwords.hashAndClear(any())).thenReturn("new-hash");
    try (var pool = Executors.newSingleThreadExecutor()) {
      var first = pool.submit(() -> service.change("Bearer synthetic", "legacy".toCharArray(), NEXT.toCharArray()));
      try {
        assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
        char[] current = "legacy".toCharArray(), next = NEXT.toCharArray();
        status(() -> service.change("Bearer synthetic", current, next), HttpStatus.TOO_MANY_REQUESTS);
        assertThat(Arrays.equals(current, new char[current.length])).isTrue();
        assertThat(Arrays.equals(next, new char[next.length])).isTrue();
      } finally {
        release.countDown();
      }
      first.get(10, TimeUnit.SECONDS);
    }
    verify(passwords).verifyAndClear(any(), anyString());
  }

  private static void status(Runnable action, HttpStatus status) {
    assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class,
      failure -> assertThat(failure.getStatusCode()).isEqualTo(status));
  }
}

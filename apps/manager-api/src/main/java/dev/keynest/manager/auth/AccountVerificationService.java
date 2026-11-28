package dev.keynest.manager.auth;

import dev.keynest.manager.auth.verification.VerificationMail;
import dev.keynest.manager.auth.verification.VerificationMailbox;
import dev.keynest.manager.auth.verification.VerificationRequest;
import dev.keynest.manager.auth.verification.VerificationRequest.Purpose;
import dev.keynest.manager.auth.verification.VerificationStore;
import dev.keynest.manager.security.PasswordHashingService;
import java.util.Arrays;
import java.util.concurrent.Semaphore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

/** Internal use cases. Public bounded DTOs, ingress identities and browser enablement remain separate. */
@Service
public class AccountVerificationService {
  private final AuthService auth;
  private final PasswordHashingService passwords;
  private final AccountVerificationTransactions transactions;
  private final VerificationStore store;
  private final VerificationMail mail;
  private final Semaphore passwordOperation = new Semaphore(1);

  public AccountVerificationService(AuthService auth, PasswordHashingService passwords,
    AccountVerificationTransactions transactions, VerificationStore store, VerificationMail mail) {
    this.auth = auth;
    this.passwords = passwords;
    this.transactions = transactions;
    this.store = store;
    this.mail = mail;
  }

  public VerificationStore.Admission requestSignup(String address, String trustedClient) {
    independent();
    try {
      ready();
      VerificationMailbox mailbox;
      try {
        mailbox = VerificationMailbox.parse(address);
      } catch (IllegalArgumentException invalid) {
        throw badRequest();
      }
      return store.admit(new VerificationRequest(Purpose.NEW_ACCOUNT, mailbox.identity(), mailbox.delivery(), null),
        trustedClient);
    } catch (ResponseStatusException fixed) {
      throw fixed;
    } catch (RuntimeException failure) {
      throw AccountVerificationTransactions.unavailable();
    }
  }

  public void activate(char[] token, char[] newPassword) {
    boolean admitted = false;
    try {
      independent();
      if (token == null || token.length != 43)
        throw AccountVerificationTransactions.invalidLink();
      if (token == newPassword || newPassword == null || newPassword.length > 32768
        || Character.codePointCount(newPassword, 0, newPassword.length) < AuthService.MIN_MASTER_PASSWORD_LENGTH
        || blank(newPassword))
        throw badRequest();
      if (!(admitted = passwordOperation.tryAcquire()))
        throw busy();
      if (!store.canConsume(token.clone(), Purpose.NEW_ACCOUNT, null))
        throw AccountVerificationTransactions.invalidLink();
      String hash = passwords.hashAndClear(newPassword);
      transactions.activate(token, hash);
    } catch (ResponseStatusException fixed) {
      throw fixed;
    } catch (RuntimeException failure) {
      throw AccountVerificationTransactions.unavailable();
    } finally {
      clear(token);
      clear(newPassword);
      if (admitted)
        passwordOperation.release();
    }
  }

  public VerificationStore.Admission requestOwner(String authorization, char[] currentPassword, String trustedClient) {
    boolean admitted = false;
    try {
      independent();
      if (currentPassword == null || currentPassword.length == 0 || currentPassword.length > 32768)
        throw badRequest();
      if (!(admitted = passwordOperation.tryAcquire()))
        throw busy();
      UserAccount owner = auth.requireUser(authorization);
      ready();
      String verifiedHash = owner.getPasswordHash();
      if (!passwords.verifyAndClear(currentPassword, verifiedHash))
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "password verification failed");
      return transactions.requestOwner(owner.getId(), verifiedHash,
        SessionTokenDigest.digest(AuthService.extractToken(authorization)), trustedClient);
    } catch (ResponseStatusException fixed) {
      throw fixed;
    } catch (RuntimeException failure) {
      throw AccountVerificationTransactions.unavailable();
    } finally {
      clear(currentPassword);
      if (admitted)
        passwordOperation.release();
    }
  }

  public void confirmOwner(String authorization, char[] token) {
    try {
      independent();
      if (token == null || token.length != 43)
        throw AccountVerificationTransactions.invalidLink();
      UserAccount owner = auth.requireUser(authorization);
      transactions.confirmOwner(owner.getId(), SessionTokenDigest.digest(AuthService.extractToken(authorization)),
        token);
    } catch (ResponseStatusException fixed) {
      throw fixed;
    } catch (RuntimeException failure) {
      throw AccountVerificationTransactions.unavailable();
    } finally {
      clear(token);
    }
  }

  private void ready() {
    if (mail.readiness() != VerificationMail.Readiness.READY)
      throw AccountVerificationTransactions.unavailable();
  }
  private static void independent() {
    if (TransactionSynchronizationManager.isActualTransactionActive())
      throw new IllegalStateException("Verification use cases require no account transaction");
  }
  private static boolean blank(char[] value) {
    for (int i = 0; i < value.length;) {
      int point = Character.codePointAt(value, i);
      if (!Character.isWhitespace(point))
        return false;
      i += Character.charCount(point);
    }
    return true;
  }
  private static void clear(char[] value) {
    if (value != null)
      Arrays.fill(value, '\0');
  }
  private static ResponseStatusException badRequest() {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid verification request");
  }
  private static ResponseStatusException busy() {
    return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "verification operations busy");
  }
}

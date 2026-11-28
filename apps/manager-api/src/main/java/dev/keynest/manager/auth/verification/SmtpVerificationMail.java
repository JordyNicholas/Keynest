package dev.keynest.manager.auth.verification;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.URLName;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.Date;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import org.eclipse.angus.mail.smtp.SMTPTransport;
import org.eclipse.angus.mail.util.WriteTimeoutSocket;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Internal one-attempt adapter. No automatic claims, retries, health probes or public routes. */
public final class SmtpVerificationMail implements VerificationMail, AutoCloseable {
  private final VerificationMailSettings settings;
  private final SSLSocketFactory tls;
  private final int socketMillis;
  private final long attemptMillis;
  private final ThreadPoolExecutor workers;
  private final ScheduledThreadPoolExecutor deadlines;
  private final Set<Attempt> active = ConcurrentHashMap.newKeySet();

  SmtpVerificationMail(VerificationMailSettings settings) {
    this(settings, (SSLSocketFactory) SSLSocketFactory.getDefault(), Duration.ofSeconds(5), Duration.ofSeconds(15));
  }

  /** Tests may only shorten bounds and supply their isolated fixture trust root. */
  SmtpVerificationMail(VerificationMailSettings settings, SSLSocketFactory tls, Duration socketTimeout,
    Duration attemptTimeout) {
    socketMillis = Math.toIntExact(socketTimeout.toMillis());
    attemptMillis = attemptTimeout.toMillis();
    if (socketMillis < 1 || socketMillis > 5000 || attemptMillis < 1 || attemptMillis > 15000)
      throw new IllegalArgumentException("Invalid verification mail bounds");
    this.settings = settings;
    this.tls = tls;
    workers = new ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS, new SynchronousQueue<>(), task -> {
      Thread thread = new Thread(task, "keynest-verification-mail");
      thread.setDaemon(true);
      return thread;
    }, new ThreadPoolExecutor.AbortPolicy());
    deadlines = new ScheduledThreadPoolExecutor(1, task -> {
      Thread thread = new Thread(task, "keynest-verification-mail-deadline");
      thread.setDaemon(true);
      return thread;
    });
    deadlines.setRemoveOnCancelPolicy(true);
  }

  @Override
  public Readiness readiness() {
    return settings.readiness;
  }

  @Override
  public Result send(VerificationDelivery delivery, BooleanSupplier stillEligible) {
    if (TransactionSynchronizationManager.isActualTransactionActive())
      throw new IllegalStateException("Verification mail requires no account transaction");
    if (readiness() != Readiness.READY || workers.isShutdown())
      return Result.UNAVAILABLE;
    Attempt attempt = new Attempt();
    active.add(attempt);
    try {
      var expiry = deadlines.schedule(attempt::close, attemptMillis, TimeUnit.MILLISECONDS);
      try {
        var future = workers.submit(() -> deliver(attempt, delivery, stillEligible));
        try {
          return future.get(attemptMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException failure) {
          attempt.close();
          future.cancel(true);
          Thread.currentThread().interrupt();
          return attempt.result();
        } catch (TimeoutException failure) {
          attempt.close();
          future.cancel(true);
          return attempt.result();
        } catch (ExecutionException failure) {
          return attempt.result();
        }
      } finally {
        expiry.cancel(false);
      }
    } catch (RejectedExecutionException failure) {
      return Result.UNAVAILABLE;
    } finally {
      attempt.close();
      active.remove(attempt);
    }
  }

  private Result deliver(Attempt attempt, VerificationDelivery delivery, BooleanSupplier stillEligible) {
    try {
      if (delivery.closed() || !stillEligible.getAsBoolean() || attempt.closed.get())
        return Result.SUPPRESSED;
      // Re-parse the stored delivery spelling: the state API alone is not a mailbox syntax boundary.
      VerificationMailbox recipient = VerificationMailbox.parse(delivery.request().deliveryAddress());
      if (!recipient.identity().equals(delivery.request().recipient()))
        return Result.SUPPRESSED;
      Socket raw = attempt.socket;
      // Assign the configured name to the resolved address; connect(Socket) must not reverse-resolve a new TLS name.
      InetAddress address = InetAddress.getByName(settings.host);
      raw.connect(new InetSocketAddress(InetAddress.getByAddress(settings.host, address.getAddress()), settings.port),
        socketMillis);
      raw.setSoTimeout(socketMillis);
      Socket timed = new WriteTimeoutSocket(raw, socketMillis, deadlines);
      Socket connected = timed;
      if (settings.implicitTls) {
        SSLSocket secure = (SSLSocket) tls.createSocket(timed, settings.host, settings.port, true);
        secure.setEnabledProtocols(new String[]{"TLSv1.3", "TLSv1.2"});
        var parameters = secure.getSSLParameters();
        parameters.setEndpointIdentificationAlgorithm("HTTPS");
        secure.setSSLParameters(parameters);
        secure.startHandshake();
        connected = secure;
      }
      Session session = Session.getInstance(properties());
      session.setDebug(false);
      SMTPTransport transport = new SMTPTransport(session,
        new URLName("smtp", settings.host, settings.port, null, settings.username, settings.password));
      try {
        transport.connect(connected);
        // Angus permits a connection without AUTH when the server advertises none. Never submit in that case.
        if (!transport.isSSL() || !(transport.supportsExtension("AUTH") || transport.supportsExtension("AUTH=LOGIN")))
          return Result.FAILED;
        if (!stillEligible.getAsBoolean() || attempt.closed.get() || delivery.closed())
          return Result.SUPPRESSED;
        delivery.useToken(token -> {
          try {
            MimeMessage message = message(session, recipient, delivery, token);
            if (attempt.closed.get())
              throw new SendFailure();
            attempt.submitting.set(true);
            transport.sendMessage(message, message.getAllRecipients());
          } catch (MessagingException failure) {
            throw new SendFailure();
          }
        });
        return Result.SENT;
      } finally {
        // Close the raw socket first: TLS/Transport.close can otherwise wait on an unresponsive peer.
        attempt.close();
        try {
          transport.close();
        } catch (MessagingException failure) {
          // Submission result is already known; never retry because of QUIT/close.
        }
      }
    } catch (IOException | MessagingException | RuntimeException failure) {
      return attempt.result();
    } finally {
      attempt.close();
    }
  }

  private Properties properties() {
    Properties values = new Properties();
    values.setProperty("mail.smtp.auth", "true");
    values.setProperty("mail.smtp.auth.mechanisms", "PLAIN LOGIN");
    values.setProperty("mail.smtp.starttls.enable", Boolean.toString(!settings.implicitTls));
    values.setProperty("mail.smtp.starttls.required", Boolean.toString(!settings.implicitTls));
    values.setProperty("mail.smtp.ssl.checkserveridentity", "true");
    values.setProperty("mail.smtp.ssl.protocols", "TLSv1.3 TLSv1.2");
    values.setProperty("mail.smtp.socketFactory.fallback", "false");
    values.put("mail.smtp.ssl.socketFactory", tls);
    values.setProperty("mail.smtp.quitwait", "false");
    values.setProperty("mail.smtp.localhost", "keynest.invalid");
    values.setProperty("mail.from", settings.sender);
    values.setProperty("mail.smtp.from", settings.sender);
    return values;
  }

  private MimeMessage message(Session session, VerificationMailbox recipient, VerificationDelivery delivery,
    char[] token) throws MessagingException {
    String purpose = delivery.request().purpose().name();
    String link = settings.origin + "/verify-email#purpose=" + purpose + "&token=" + new String(token);
    String instructions = delivery.request().purpose() == VerificationRequest.Purpose.NEW_ACCOUNT
      ? "Choose your password on the confirmation page, then sign in normally."
      : "Return to the same Keynest tab and session that requested verification. This link cannot sign you in.";
    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress(settings.sender));
    message.setRecipient(Message.RecipientType.TO, new InternetAddress(recipient.delivery()));
    message.setSubject("Confirm your email for Keynest", "UTF-8");
    message.setSentDate(new Date());
    message.setText("A verification link was requested for Keynest at " + settings.origin + ".\n\n" + link
      + "\n\nThis link expires ten minutes after it was created. " + instructions
      + "\nIf you did not request this message, ignore it.\n", "UTF-8");
    message.saveChanges();
    return message;
  }

  @Override
  public void close() {
    workers.shutdownNow();
    active.forEach(Attempt::close);
    deadlines.shutdown();
  }

  @Override
  public String toString() {
    return "SmtpVerificationMail[redacted]";
  }

  private static final class Attempt implements AutoCloseable {
    final Socket socket = new Socket();
    final AtomicBoolean closed = new AtomicBoolean();
    final AtomicBoolean submitting = new AtomicBoolean();
    Result result() {
      return submitting.get() ? Result.AMBIGUOUS : Result.FAILED;
    }
    @Override
    public void close() {
      if (closed.compareAndSet(false, true)) {
        try {
          socket.close();
        } catch (IOException failure) {
          /* Fixed outcome only. */ }
      }
    }
  }
  private static final class SendFailure extends RuntimeException {
    private static final long serialVersionUID = 1L;
    SendFailure() {
      super("Verification mail submission failed");
    }
  }
}

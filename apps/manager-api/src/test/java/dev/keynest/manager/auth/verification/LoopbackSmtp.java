package dev.keynest.manager.auth.verification;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;

/** Disposable loopback sink. Never prints SMTP, credentials, recipients, messages or links. */
final class LoopbackSmtp implements AutoCloseable {
  enum Mode {
    NORMAL, NO_STARTTLS, REJECT_STARTTLS, NO_AUTH, REJECT_AUTH, LOST_ACK, HANG_GREETING, SLOW_GREETING, HANG_HANDSHAKE, LEGACY_TLS
  }
  final ServerSocket server;
  final Thread thread;
  final CountDownLatch peerClosed = new CountDownLatch(1);
  final CountDownLatch accepted = new CountDownLatch(1);
  final AtomicInteger connections = new AtomicInteger();
  final AtomicInteger authentications = new AtomicInteger();
  final AtomicInteger messages = new AtomicInteger();
  volatile String captured;
  volatile String protocol;
  volatile boolean authenticatedOverTls;
  volatile boolean expectedEnvelope;
  private volatile Socket active;

  LoopbackSmtp(SSLContext tls, boolean implicit, Mode mode) throws Exception {
    server = new ServerSocket(0, 2, InetAddress.getLoopbackAddress());
    thread = new Thread(() -> serve(tls, implicit, mode), "disposable-smtp-fixture");
    thread.setDaemon(true);
    thread.start();
  }

  private void serve(SSLContext tls, boolean implicit, Mode mode) {
    try (Socket raw = server.accept()) {
      active = raw;
      connections.incrementAndGet();
      accepted.countDown();
      raw.setSoTimeout(10000);
      if (mode == Mode.HANG_GREETING || mode == Mode.HANG_HANDSHAKE) {
        while (raw.getInputStream().read() != -1) {
        }
        peerClosed.countDown();
        return;
      }
      Socket socket = implicit ? upgrade(tls, raw, mode) : raw;
      boolean secure = implicit;
      BufferedReader input = reader(socket);
      BufferedWriter output = writer(socket);
      if (mode == Mode.SLOW_GREETING) {
        for (char c : ("220 " + "x".repeat(100) + "\r\n").toCharArray()) {
          output.write(c);
          output.flush();
          Thread.sleep(30);
        }
      } else
        reply(output, "220 disposable.invalid ready");
      for (String line; (line = input.readLine()) != null;) {
        if (line.startsWith("EHLO") || line.startsWith("HELO")) {
          reply(output, "250-disposable.invalid");
          if (!secure && mode != Mode.NO_STARTTLS)
            reply(output, "250-STARTTLS");
          if (mode != Mode.NO_AUTH)
            reply(output, "250-AUTH PLAIN");
          reply(output, "250 SIZE 4096");
        } else if (line.equals("STARTTLS")) {
          if (mode == Mode.REJECT_STARTTLS) {
            reply(output, "454 TLS unavailable");
            continue;
          }
          reply(output, "220 begin TLS");
          socket = upgrade(tls, raw, mode);
          secure = true;
          input = reader(socket);
          output = writer(socket);
        } else if (line.startsWith("AUTH PLAIN")) {
          authentications.incrementAndGet();
          authenticatedOverTls = secure;
          String payload = line.length() > 11 ? line.substring(11) : "";
          if (payload.isEmpty()) {
            reply(output, "334 ");
            payload = input.readLine();
          }
          byte[] decoded = Base64.getDecoder().decode(payload);
          boolean valid;
          try {
            valid = Arrays.equals(decoded,
              "\0fixture-user\0disposable-fixture-password".getBytes(StandardCharsets.UTF_8))
              || Arrays.equals(decoded,
                "fixture-user\0fixture-user\0disposable-fixture-password".getBytes(StandardCharsets.UTF_8));
          } finally {
            Arrays.fill(decoded, (byte) 0);
          }
          reply(output, valid && mode != Mode.REJECT_AUTH ? "235 authenticated" : "535 authentication refused");
        } else if (line.startsWith("MAIL FROM:")) {
          expectedEnvelope = line.equals("MAIL FROM:<Keynest@Example.Invalid>");
          reply(output, "250 sender accepted");
        } else if (line.startsWith("RCPT TO:")) {
          expectedEnvelope &= line.equals("RCPT TO:<Owner+Tag@Example.Invalid>");
          reply(output, "250 recipient accepted");
        } else if (line.equals("DATA")) {
          reply(output, "354 message follows");
          StringBuilder body = new StringBuilder();
          while ((line = input.readLine()) != null && !line.equals(".")) {
            if (body.length() > 16384)
              throw new IllegalStateException("Fixture message bound exceeded");
            body.append(line).append("\r\n");
          }
          captured = body.toString();
          messages.incrementAndGet();
          if (mode != Mode.LOST_ACK)
            reply(output, "250 accepted");
        } else if (line.equals("QUIT")) {
          reply(output, "221 bye");
          break;
        } else if (line.equals("RSET"))
          reply(output, "250 reset");
        else
          reply(output, "500 unsupported");
      }
      peerClosed.countDown();
    } catch (Exception expected) {
      // TLS rejection and peer cancellation are expected fixture paths, never report payloads.
      peerClosed.countDown();
    }
  }

  private SSLSocket upgrade(SSLContext context, Socket raw, Mode mode) throws Exception {
    SSLSocket secure = (SSLSocket) context.getSocketFactory().createSocket(raw, "localhost", raw.getPort(), false);
    secure.setUseClientMode(false);
    if (mode == Mode.LEGACY_TLS)
      secure.setEnabledProtocols(new String[]{"TLSv1.1"});
    secure.startHandshake();
    protocol = secure.getSession().getProtocol();
    return secure;
  }
  private static BufferedReader reader(Socket socket) throws Exception {
    return new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
  }
  private static BufferedWriter writer(Socket socket) throws Exception {
    return new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII));
  }
  private static void reply(BufferedWriter output, String value) throws Exception {
    output.write(value + "\r\n");
    output.flush();
  }
  boolean observedClose() throws InterruptedException {
    return peerClosed.await(3, TimeUnit.SECONDS);
  }
  @Override
  public void close() throws Exception {
    server.close();
    if (active != null)
      active.close();
    thread.join(3000);
    captured = null;
  }
  @Override
  public String toString() {
    return "LoopbackSmtp[redacted]";
  }
}

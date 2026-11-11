package dev.keynest.manager.api;

import dev.keynest.manager.config.RateLimitProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Resolves a limiter identity without trusting caller-controlled forwarding headers by default. */
@Component
public final class ClientIpResolver {

  private static final int MAX_FORWARDED_FOR_LENGTH = 2_048;
  private static final int MAX_FORWARDED_FOR_ENTRIES = 64;

  private final List<IpNetwork> trustedProxies;
  private final int maxForwardedForEntries;

  public ClientIpResolver(RateLimitProperties properties) {
    if (properties.getMaxForwardedForEntries() < 1
      || properties.getMaxForwardedForEntries() > MAX_FORWARDED_FOR_ENTRIES) {
      throw new IllegalStateException(
        "keynest.rate-limit.max-forwarded-for-entries must be between 1 and "
          + MAX_FORWARDED_FOR_ENTRIES
      );
    }
    this.maxForwardedForEntries = properties.getMaxForwardedForEntries();
    this.trustedProxies = properties.getTrustedProxies().stream()
      .filter(value -> value != null && !value.isBlank())
      .map(IpNetwork::parse)
      .toList();
  }

  public String resolve(HttpServletRequest request) {
    String peer = normalizeLiteral(request.getRemoteAddr());
    if (peer == null) {
      return "unknown-peer";
    }
    if (!isTrusted(peer)) {
      return peer;
    }

    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded == null || forwarded.isBlank()) {
      return peer;
    }
    if (forwarded.length() > MAX_FORWARDED_FOR_LENGTH) {
      return peer;
    }

    String[] values = forwarded.split(",", -1);
    if (values.length > maxForwardedForEntries) {
      return peer;
    }

    List<String> hops = new ArrayList<>(values.length);
    for (String value : values) {
      String hop = normalizeLiteral(value.trim());
      if (hop == null) {
        return peer;
      }
      hops.add(hop);
    }

    String current = peer;
    for (int index = hops.size() - 1; index >= 0; index--) {
      if (!isTrusted(current)) {
        return current;
      }
      current = hops.get(index);
    }
    return current;
  }

  private boolean isTrusted(String address) {
    byte[] candidate = parseLiteral(address);
    if (candidate == null) {
      return false;
    }
    return trustedProxies.stream().anyMatch(network -> network.contains(candidate));
  }

  private static String normalizeLiteral(String value) {
    byte[] address = parseLiteral(value);
    if (address == null) {
      return null;
    }
    try {
      return InetAddress.getByAddress(address).getHostAddress();
    } catch (UnknownHostException exception) {
      throw new IllegalStateException("validated IP address became invalid", exception);
    }
  }

  private static byte[] parseLiteral(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String literal = stripBrackets(value.trim());
    if (literal.indexOf(':') < 0) {
      return parseIpv4(literal);
    }
    if (!literal.matches("[0-9a-fA-F:.]+")) {
      return null;
    }
    try {
      byte[] bytes = InetAddress.getByName(literal).getAddress();
      return bytes.length == 16 ? bytes : null;
    } catch (UnknownHostException exception) {
      return null;
    }
  }

  private static byte[] parseIpv4(String literal) {
    String[] octets = literal.split("\\.", -1);
    if (octets.length != 4) {
      return null;
    }
    byte[] address = new byte[4];
    for (int index = 0; index < octets.length; index++) {
      if (!octets[index].matches("[0-9]{1,3}")) {
        return null;
      }
      int value;
      try {
        value = Integer.parseInt(octets[index]);
      } catch (NumberFormatException exception) {
        return null;
      }
      if (value > 255) {
        return null;
      }
      address[index] = (byte) value;
    }
    return address;
  }

  private static String stripBrackets(String value) {
    if (value.startsWith("[") && value.endsWith("]") && value.length() > 2) {
      return value.substring(1, value.length() - 1);
    }
    return value;
  }

  private record IpNetwork(byte[] network, int prefixLength) {

    private static IpNetwork parse(String value) {
      String candidate = value.trim();
      int slash = candidate.indexOf('/');
      String literal = slash < 0 ? candidate : candidate.substring(0, slash);
      byte[] address = parseLiteral(literal);
      if (address == null) {
        throw new IllegalStateException("invalid trusted proxy IP/CIDR: " + value);
      }

      int maxBits = address.length * Byte.SIZE;
      int prefix = maxBits;
      if (slash >= 0) {
        if (candidate.indexOf('/', slash + 1) >= 0) {
          throw new IllegalStateException("invalid trusted proxy IP/CIDR: " + value);
        }
        try {
          prefix = Integer.parseInt(candidate.substring(slash + 1));
        } catch (NumberFormatException exception) {
          throw new IllegalStateException("invalid trusted proxy IP/CIDR: " + value, exception);
        }
      }
      if (prefix < 0 || prefix > maxBits) {
        throw new IllegalStateException("invalid trusted proxy prefix length: " + value);
      }
      if (prefix == 0) {
        throw new IllegalStateException("all-address trusted proxy ranges are forbidden: " + value);
      }

      byte[] network = Arrays.copyOf(address, address.length);
      applyMask(network, prefix);
      return new IpNetwork(network, prefix);
    }

    private boolean contains(byte[] candidate) {
      if (candidate.length != network.length) {
        return false;
      }
      byte[] masked = Arrays.copyOf(candidate, candidate.length);
      applyMask(masked, prefixLength);
      return Arrays.equals(network, masked);
    }

    private static void applyMask(byte[] address, int prefixLength) {
      int wholeBytes = prefixLength / Byte.SIZE;
      int remainingBits = prefixLength % Byte.SIZE;
      if (remainingBits > 0) {
        int mask = 0xFF << (Byte.SIZE - remainingBits);
        address[wholeBytes] = (byte) (address[wholeBytes] & mask);
        wholeBytes++;
      }
      Arrays.fill(address, wholeBytes, address.length, (byte) 0);
    }
  }
}

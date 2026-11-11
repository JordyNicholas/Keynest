package dev.keynest.manager.ai;

import java.util.Arrays;

/** One invocation only. Transport strings/runtime copies cannot be reliably erased. */
public final class ProviderCredential implements AutoCloseable {
  private char[] value;

  public ProviderCredential(String supplied) {
    if (supplied == null || supplied.isEmpty()) throw new AiFailure(AiFailure.Code.AI_CREDENTIAL_REQUIRED);
    if (supplied.length() > 4096 || supplied.chars().anyMatch(c -> c < 33 || c > 126)) {
      throw new AiFailure(AiFailure.Code.AI_CREDENTIAL_INVALID);
    }
    value = supplied.toCharArray();
  }

  String transportValue() {
    if (value.length == 0) throw new AiFailure(AiFailure.Code.AI_CREDENTIAL_INVALID);
    return new String(value);
  }

  public boolean appearsIn(String text) {
    if (text == null || value.length == 0) return false;
    for (int start = 0; start <= text.length() - value.length; start++) {
      int index = 0;
      while (index < value.length && text.charAt(start + index) == value[index]) index++;
      if (index == value.length) return true;
    }
    return false;
  }

  @Override public String toString() { return "ProviderCredential[REDACTED]"; }
  @Override public void close() {
    Arrays.fill(value, '\0');
    value = new char[0];
  }
}

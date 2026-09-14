package dev.keynest.seal;

import java.util.Arrays;

/**
 * Best-effort clearing of sensitive buffers. Does not claim to defeat JVM heap dumps or GC copies.
 */
final class SensitiveBuffers {

    private SensitiveBuffers() {
    }

    static void clear(byte[]... buffers) {
        if (buffers == null) {
            return;
        }
        for (byte[] buffer : buffers) {
            if (buffer != null) {
                Arrays.fill(buffer, (byte) 0);
            }
        }
    }

    static void clear(char[]... buffers) {
        if (buffers == null) {
            return;
        }
        for (char[] buffer : buffers) {
            if (buffer != null) {
                Arrays.fill(buffer, '\0');
            }
        }
    }
}

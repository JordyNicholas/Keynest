package dev.keynest.seal;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;

import java.util.Objects;

/**
 * Argon2id hash/verify for master passwords and similar auth secrets.
 * Never used for recoverable vault secrets — use {@link KeynestSeal#seal} for those.
 */
public final class PasswordHasher {

    private final Argon2 argon2;
    private final int iterations;
    private final int memoryKiB;
    private final int parallelism;

    public PasswordHasher() {
        this(3, 65536, 1);
    }

    public PasswordHasher(int iterations, int memoryKiB, int parallelism) {
        this.argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id);
        this.iterations = iterations;
        this.memoryKiB = memoryKiB;
        this.parallelism = parallelism;
    }

    public String hashPassword(char[] password) {
        Objects.requireNonNull(password, "password");
        try {
            return argon2.hash(iterations, memoryKiB, parallelism, password);
        } finally {
            argon2.wipeArray(password);
        }
    }

    public boolean verifyPassword(char[] password, String hash) {
        Objects.requireNonNull(password, "password");
        Objects.requireNonNull(hash, "hash");
        try {
            return argon2.verify(hash, password);
        } finally {
            argon2.wipeArray(password);
        }
    }
}

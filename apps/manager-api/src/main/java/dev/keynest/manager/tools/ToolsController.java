package dev.keynest.manager.tools;

import dev.keynest.generate.PasswordGenerator;
import dev.keynest.policy.PasswordPolicy;
import dev.keynest.policy.PasswordValidator;
import dev.keynest.policy.ValidationResult;
import dev.keynest.seal.KeynestSeal;
import dev.keynest.seal.PasswordHasher;
import dev.keynest.seal.SealEnvelope;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

/**
 * Thin HTTP façade over Keynest libs — Manager dogfoods the same tools external services will use.
 */
@RestController
@RequestMapping(path = "/api/v1/tools", produces = MediaType.APPLICATION_JSON_VALUE)
public class ToolsController {

    private final PasswordPolicy defaultPolicy;
    private final PasswordGenerator generator;
    private final KeynestSeal seal;
    private final PasswordHasher passwordHasher;

    /** Dev-only demo key so seal/open works without a KMS yet. Replace before any real deploy. */
    private final byte[] demoKey;

    public ToolsController(
            PasswordPolicy defaultPolicy,
            PasswordGenerator generator,
            KeynestSeal seal,
            PasswordHasher passwordHasher
    ) {
        this.defaultPolicy = defaultPolicy;
        this.generator = generator;
        this.seal = seal;
        this.passwordHasher = passwordHasher;
        this.demoKey = Arrays.copyOf(
                HexFormat.of().parseHex("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"),
                32
        );
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "ok",
                "product", "keynest-manager-api",
                "policyId", defaultPolicy.id()
        );
    }

    @PostMapping("/validate")
    public Map<String, Object> validate(@RequestBody ValidateRequest request) {
        ValidationResult result = PasswordValidator.validate(
                request.password(),
                defaultPolicy,
                request.usernameOrEmail()
        );
        return Map.of(
                "valid", result.valid(),
                "violations", result.violations(),
                "policyId", defaultPolicy.id()
        );
    }

    @PostMapping("/generate")
    public Map<String, Object> generate() {
        String password = generator.generate(defaultPolicy);
        ValidationResult result = PasswordValidator.validate(password, defaultPolicy);
        return Map.of(
                "password", password,
                "valid", result.valid(),
                "policyId", defaultPolicy.id()
        );
    }

    @PostMapping("/seal")
    public Map<String, Object> seal(@RequestBody SealRequest request) {
        byte[] aad = defaultPolicy.id().getBytes(StandardCharsets.UTF_8);
        SealEnvelope envelope = seal.seal(
                request.plaintext().getBytes(StandardCharsets.UTF_8),
                demoKey,
                "demo-kid",
                aad
        );
        return Map.of(
                "envelopeBase64", Base64.getUrlEncoder().withoutPadding().encodeToString(envelope.toBytes()),
                "fingerprint", seal.fingerprint(envelope),
                "policyId", defaultPolicy.id()
        );
    }

    @PostMapping("/open")
    public Map<String, Object> open(@RequestBody OpenRequest request) {
        byte[] raw = Base64.getUrlDecoder().decode(request.envelopeBase64());
        SealEnvelope envelope = SealEnvelope.fromBytes(raw);
        byte[] aad = defaultPolicy.id().getBytes(StandardCharsets.UTF_8);
        byte[] plaintext = seal.open(envelope, demoKey, aad);
        return Map.of(
                "plaintext", new String(plaintext, StandardCharsets.UTF_8),
                "fingerprint", seal.fingerprint(envelope)
        );
    }

    @PostMapping("/hash-password")
    public Map<String, Object> hashPassword(@RequestBody HashRequest request) {
        String hash = passwordHasher.hashPassword(request.password().toCharArray());
        return Map.of("hash", hash, "algorithm", "argon2id");
    }

    @PostMapping("/verify-password")
    public Map<String, Object> verifyPassword(@RequestBody VerifyRequest request) {
        boolean ok = passwordHasher.verifyPassword(request.password().toCharArray(), request.hash());
        return Map.of("valid", ok);
    }

    public record ValidateRequest(@NotBlank String password, String usernameOrEmail) {
    }

    public record SealRequest(@NotBlank String plaintext) {
    }

    public record OpenRequest(@NotBlank String envelopeBase64) {
    }

    public record HashRequest(@NotBlank String password) {
    }

    public record VerifyRequest(@NotBlank String password, @NotBlank String hash) {
    }
}

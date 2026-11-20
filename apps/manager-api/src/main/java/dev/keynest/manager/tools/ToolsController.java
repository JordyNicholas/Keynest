package dev.keynest.manager.tools;

import dev.keynest.generate.PasswordGenerator;
import dev.keynest.manager.api.ApiPaths;
import dev.keynest.manager.auth.AuthService;
import dev.keynest.manager.policy.AccountPasswordPolicyService;
import dev.keynest.policy.PasswordValidator;
import dev.keynest.manager.config.ToolsDemoProperties;
import dev.keynest.manager.security.PasswordHashingService;
import dev.keynest.policy.PasswordPolicy;
import dev.keynest.policy.PolicyOperation;
import dev.keynest.policy.ValidationResult;
import dev.keynest.seal.KeynestSeal;
import dev.keynest.seal.SealEnvelope;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.regex.Pattern;

/**
* Thin HTTP façade over Keynest libs — Manager dogfoods the same tools external services will use.
*/
@RestController
@RequestMapping(path = ApiPaths.TOOLS, produces = MediaType.APPLICATION_JSON_VALUE)
public class ToolsController {

  // Keep public verification within the work budget of a Manager-generated hash.
  private static final int MAX_VERIFY_MEMORY_KIB = 64 * 1024;
  private static final int MAX_VERIFY_ITERATIONS = 3;
  private static final int MAX_VERIFY_PARALLELISM = 1;
  private static final Pattern ARGON2ID_HASH = Pattern.compile(
    "\\$argon2id\\$(?:v=(?:16|19)\\$)?m=([1-9][0-9]{0,4}),t=([1-9][0-9]?),p=([1-9][0-9]?)"
      + "\\$[A-Za-z0-9+/]{11,86}\\$[A-Za-z0-9+/]{6,86}");
  
  private final AuthService auth;
  private final AccountPasswordPolicyService accountPolicies;
  private final PasswordPolicy defaultPolicy;
  private final PasswordGenerator generator;
  private final KeynestSeal seal;
  private final PasswordHashingService passwords;
  private final ToolsDemoProperties demoProperties;
  private final byte[] demoKey;
  
  public ToolsController(
    PasswordPolicy defaultPolicy,
    PasswordGenerator generator,
    KeynestSeal seal,
    PasswordHashingService passwords,
    ToolsDemoProperties demoProperties,
    AuthService auth,
    AccountPasswordPolicyService accountPolicies
  ) {
    this.auth = auth;
    this.accountPolicies = accountPolicies;
    this.defaultPolicy = defaultPolicy;
    this.generator = generator;
    this.seal = seal;
    this.passwords = passwords;
    this.demoProperties = demoProperties;
    // Parse demo DEK only when the gate is on — docker/prod leave enabled=false with no key.
    this.demoKey = this.setKey();
  }
  
  @GetMapping("/health")
  public Map<String, Object> health() {
    return Map.of(
      "status", "ok",
      "product", "keynest-manager-api",
      "policyId", defaultPolicy.id()
    );
  }
  
  @GetMapping("/policy")
  public org.springframework.http.ResponseEntity<Map<String, Object>> policy(
    @RequestParam(name = "policyId", required = false) String policyId,
    @RequestHeader(value = "Authorization", required = false) String bearer
  ) {
    PasswordPolicy policy = effectivePolicy(bearer, policyId);
    var view = accountPolicies.view(policy);
    return org.springframework.http.ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(
      Map.of("id", view.id(), "version", 1, "description", "Selected password policy",
      "appliesTo", java.util.List.of("generate", "create", "update", "rotate"), "rules", view.rules()));
  }

  @PostMapping("/validate")
  public Map<String, Object> validate(
    @RequestBody ValidateRequest request,
    @RequestParam(name = "operation", defaultValue = "update") String operation,
    @RequestHeader(value = "Authorization", required = false) String bearer
  ) {
    PolicyOperation op = PolicyOperation.fromWire(operation);
    PasswordPolicy policy = effectivePolicy(bearer, request.policyId());
    ValidationResult result = PasswordValidator.validate(request.password(), policy, request.usernameOrEmail());
    return Map.of("valid", result.valid(), "violations", result.violations(),
      "policyId", policy.id(), "operation", op.wireValue());
  }

  @PostMapping("/generate")
  public Map<String, Object> generate(
    @RequestParam(name = "policyId", required = false) String policyId,
    @RequestHeader(value = "Authorization", required = false) String bearer
  ) {
    PasswordPolicy policy = effectivePolicy(bearer, policyId);
    String password = generator.generate(policy);
    return Map.of("password", password, "valid", PasswordValidator.validate(password, policy).valid(),
      "policyId", policy.id());
  }

  private PasswordPolicy effectivePolicy(String bearer, String requestedId) {
    PasswordPolicy policy = accountPolicies.effective(bearer == null ? null : auth.requireUser(bearer));
    if (requestedId != null && !requestedId.isBlank() && !policy.id().equals(requestedId)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Password policy changed. Reload the form.");
    }
    return policy;
  }

  @PostMapping("/seal")
  public Map<String, Object> seal(@RequestBody SealRequest request) {
    requireDemoEnabled();
    byte[] aad = defaultPolicy.id().getBytes(StandardCharsets.UTF_8);
    SealEnvelope envelope = seal.seal(
      request.plaintext().getBytes(StandardCharsets.UTF_8),
      demoKey,
      demoProperties.getKeyId(),
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
    requireDemoEnabled();
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
    String hash = passwords.hash(request.password());
    return Map.of("hash", hash, "algorithm", "argon2id");
  }
  
  @PostMapping("/verify-password")
  public Map<String, Object> verifyPassword(@RequestBody VerifyRequest request) {
    boolean ok = request.password() != null && isSupportedPasswordHash(request.hash())
      && passwords.verify(request.password(), request.hash());
    return Map.of("valid", ok);
  }

  private static boolean isSupportedPasswordHash(String hash) {
    if (hash == null) {
      return false;
    }
    var costs = ARGON2ID_HASH.matcher(hash);
    return costs.matches()
      && Integer.parseInt(costs.group(1)) <= MAX_VERIFY_MEMORY_KIB
      && Integer.parseInt(costs.group(2)) <= MAX_VERIFY_ITERATIONS
      && Integer.parseInt(costs.group(3)) <= MAX_VERIFY_PARALLELISM;
  }
  
  public record ValidateRequest(@NotBlank String password, String usernameOrEmail, String policyId) {
  }
  
  public record SealRequest(@NotBlank String plaintext) {
  }
  
  public record OpenRequest(@NotBlank String envelopeBase64) {
  }
  
  public record HashRequest(@NotBlank String password) {
  }
  
  public record VerifyRequest(@NotBlank String password, @NotBlank String hash) {
  }
  
  private void requireDemoEnabled() {
    if (!demoProperties.isEnabled()) {
      throw new ResponseStatusException(
        HttpStatus.FORBIDDEN,
        "tools demo seal/open is disabled"
      );
    }
  }
  
  private byte[] setKey() {
    if (demoProperties.isEnabled()) {
      byte[] key = HexFormat.of().parseHex(demoProperties.getKeyHex());
      if (key.length != 32) {
        throw new IllegalStateException("keynest.tools.demo.key-hex must decode to 32 bytes");
      }
      return key;
    } else {
      return new byte[0];
    }
  }
}

package dev.keynest.manager.tools;

import dev.keynest.generate.PasswordGenerator;
import dev.keynest.manager.config.ToolsDemoProperties;
import dev.keynest.policy.PasswordPolicy;
import dev.keynest.policy.PolicyOperation;
import dev.keynest.policy.ValidationResult;
import dev.keynest.policy.lifecycle.PasswordStandards;
import dev.keynest.policy.schema.PasswordPolicyDocument;
import dev.keynest.seal.KeynestSeal;
import dev.keynest.seal.PasswordHasher;
import dev.keynest.seal.SealEnvelope;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
* Thin HTTP façade over Keynest libs — Manager dogfoods the same tools external services will use.
*/
@RestController
@RequestMapping(path = "/api/v1/tools", produces = MediaType.APPLICATION_JSON_VALUE)
public class ToolsController {
  
  private final PasswordPolicy defaultPolicy;
  private final PasswordStandards passwordStandards;
  private final PasswordGenerator generator;
  private final KeynestSeal seal;
  private final PasswordHasher passwordHasher;
  private final ToolsDemoProperties demoProperties;
  private final byte[] demoKey;
  
  public ToolsController(
    PasswordPolicy defaultPolicy,
    PasswordStandards passwordStandards,
    PasswordGenerator generator,
    KeynestSeal seal,
    PasswordHasher passwordHasher,
    ToolsDemoProperties demoProperties
  ) {
    this.defaultPolicy = defaultPolicy;
    this.passwordStandards = passwordStandards;
    this.generator = generator;
    this.seal = seal;
    this.passwordHasher = passwordHasher;
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
  public Map<String, Object> policy(
    @RequestParam(name = "policyId", defaultValue = PasswordPolicy.DEFAULT_POLICY_ID) String policyId
  ) {
    PasswordPolicyDocument document = passwordStandards.registry().require(policyId);
    Map<String, Object> rules = new LinkedHashMap<>();
    PasswordPolicy runtime = document.toRuntimePolicy();
    rules.put("minLength", runtime.minLength());
    rules.put("maxLength", runtime.maxLength());
    rules.put("requireUpper", runtime.requireUpper());
    rules.put("requireLower", runtime.requireLower());
    rules.put("requireDigit", runtime.requireDigit());
    rules.put("requireSymbol", runtime.requireSymbol());
    rules.put("minCategories", runtime.minCategories());
    rules.put("excludeAmbiguous", runtime.excludeAmbiguous());
    rules.put("forbidWhitespace", runtime.forbidWhitespace());
    rules.put("forbidUsernameSubstring", runtime.forbidUsernameSubstring());
    rules.put("maxAgeDays", runtime.maxAgeDays());
    rules.put("bannedSubstrings", runtime.bannedSubstrings());
    
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("id", document.id());
    body.put("version", document.version());
    body.put("description", document.description());
    body.put("appliesTo", document.appliesTo());
    body.put("rules", rules);
    return body;
  }
  
  @PostMapping("/validate")
  public Map<String, Object> validate(
    @RequestBody ValidateRequest request,
    @RequestParam(name = "operation", defaultValue = "update") String operation
  ) {
    PolicyOperation op = PolicyOperation.fromWire(operation);
    String policyId = request.policyId() == null || request.policyId().isBlank()
    ? PasswordPolicy.DEFAULT_POLICY_ID
    : request.policyId();
    ValidationResult result = switch (op) {
      case CREATE -> passwordStandards.validateForCreate(policyId, request.password(), request.usernameOrEmail());
      case UPDATE -> passwordStandards.validateForUpdate(policyId, request.password(), request.usernameOrEmail());
      case ROTATE -> passwordStandards.validateForRotate(policyId, request.password(), request.usernameOrEmail());
      case GENERATE -> passwordStandards.validateForGenerateCheck(policyId, request.password());
    };
    return Map.of(
      "valid", result.valid(),
      "violations", result.violations(),
      "policyId", policyId,
      "operation", op.wireValue()
    );
  }
  
  @PostMapping("/generate")
  public Map<String, Object> generate(
    @RequestParam(name = "policyId", defaultValue = PasswordPolicy.DEFAULT_POLICY_ID) String policyId
  ) {
    PasswordPolicy policy = passwordStandards.runtimePolicy(policyId, PolicyOperation.GENERATE);
    String password = generator.generate(policy);
    ValidationResult result = passwordStandards.validateForGenerateCheck(policyId, password);
    return Map.of(
      "password", password,
      "valid", result.valid(),
      "policyId", policyId
    );
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
    String hash = passwordHasher.hashPassword(request.password().toCharArray());
    return Map.of("hash", hash, "algorithm", "argon2id");
  }
  
  @PostMapping("/verify-password")
  public Map<String, Object> verifyPassword(@RequestBody VerifyRequest request) {
    boolean ok = passwordHasher.verifyPassword(request.password().toCharArray(), request.hash());
    return Map.of("valid", ok);
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

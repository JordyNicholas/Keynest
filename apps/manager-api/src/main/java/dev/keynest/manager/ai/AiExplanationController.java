package dev.keynest.manager.ai;

import dev.keynest.manager.api.ApiPaths;
import dev.keynest.manager.auth.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

@RestController
public class AiExplanationController {
  private final AuthService auth;
  private final AiExplanationService explanations;

  public AiExplanationController(AuthService auth, AiExplanationService explanations) {
    this.auth = auth;
    this.explanations = explanations;
  }

  @PostMapping(path = ApiPaths.AI_EXPLAIN, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<AiExplanationService.Explanation> explain(HttpServletRequest request) {
    var user = auth.requireUser(request.getHeader("Authorization"));
    explanations.requireEnabled();
    try {
      if (request.getContentType() == null || !MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(request.getContentType()))) {
        throw new AiFailure(AiFailure.Code.AI_INVALID_REQUEST);
      }
      byte[] body = request.getInputStream().readNBytes(8193);
      if (body.length > 8192) throw new AiFailure(AiFailure.Code.AI_REQUEST_TOO_LARGE);
      var node = AiJson.read(body, AiFailure.Code.AI_INVALID_REQUEST);
      if (!node.isObject() || node.size() != 2 || !node.path("providerId").isTextual() || !node.path("policyId").isTextual()) {
        throw new AiFailure(AiFailure.Code.AI_INVALID_REQUEST);
      }
      var keys = Collections.list(request.getHeaders("X-Keynest-AI-Key"));
      if (keys.size() > 1) throw new AiFailure(AiFailure.Code.AI_CREDENTIAL_INVALID);
      return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(explanations.explain(
        user.getId(), node.get("providerId").textValue(), node.get("policyId").textValue(), keys.isEmpty() ? null : keys.getFirst()));
    } catch (AiFailure failure) {
      throw failure;
    } catch (IOException | IllegalArgumentException ignored) {
      throw new AiFailure(AiFailure.Code.AI_INVALID_REQUEST);
    }
  }

  @ExceptionHandler(AiFailure.class)
  public ResponseEntity<Map<String, String>> failure(AiFailure failure) {
    return ResponseEntity.status(failure.code().status()).cacheControl(CacheControl.noStore())
      .body(Map.of("error", failure.code().name()));
  }
}

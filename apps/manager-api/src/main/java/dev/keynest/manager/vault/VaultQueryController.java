package dev.keynest.manager.vault;

import dev.keynest.manager.api.ApiPaths;
import dev.keynest.manager.auth.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.Errors;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
public class VaultQueryController {
  private final AuthService auth;
  private final VaultQueryService queries;

  public VaultQueryController(AuthService auth, VaultQueryService queries) { this.auth = auth;this.queries = queries; }

  /** Runs before handler argument binding, including malformed-body/validation failures. */
  @ModelAttribute
  void noStore(HttpServletResponse response) {
    response.setHeader("Cache-Control", "no-store");response.setHeader("Pragma", "no-cache");
  }

  @GetMapping(ApiPaths.VAULT_OVERVIEW)
  public VaultQueryService.Overview overview(@RequestHeader(value = "Authorization", required = false) String authorization) {
    return queries.overview(auth.requireUser(authorization));
  }

  @PostMapping(ApiPaths.VAULT_SEARCH)
  public VaultQueryService.SearchPage search(@RequestHeader(value = "Authorization", required = false) String authorization,
      @Valid @RequestBody VaultQueryService.SearchRequest body, Errors errors) {
    // Handle rejected values locally so framework validation logs cannot include search metadata.
    if (errors.hasErrors()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Vault search options");
    return queries.search(auth.requireUser(authorization), body);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<java.util.Map<String, String>> unreadableBody() {
    return ResponseEntity.badRequest().cacheControl(CacheControl.noStore())
      .body(java.util.Map.of("code", "INVALID_VAULT_QUERY"));
  }

}

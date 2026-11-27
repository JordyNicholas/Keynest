package dev.keynest.manager.api;

import dev.keynest.manager.ai.AiExplanationController;
import dev.keynest.manager.auth.AuthController;
import dev.keynest.manager.policy.AccountPasswordPolicyController;
import dev.keynest.manager.tools.ToolsController;
import dev.keynest.manager.vault.VaultController;
import dev.keynest.manager.vault.VaultQueryController;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPatternParser;

/** Explicit HTTP/handler decisions; a new application mapping has no implicit grant. */
@Component
public class RouteAccessCatalog {
  public enum Access { PUBLIC, OPTIONAL_BEARER, AUTHENTICATED }
  public record Route(String method, String pattern, Class<?> controller, String handler, Access access) {
    public Route {
      // An overloaded handler name must be reviewed explicitly, never collapsed into one grant.
      long matches = java.util.Arrays.stream(controller.getDeclaredMethods()).filter(candidate -> candidate.getName().equals(handler)).count();
      if (matches != 1 || access == null) throw new IllegalStateException("Ambiguous route access binding");
    }
    public boolean matches(String verb, String path, HandlerMethod target) {
      return method.equals(verb) && pattern.equals(path)
        && controller.equals(ClassUtils.getUserClass(target.getBeanType()))
        && handler.equals(target.getMethod().getName());
    }
  }

  private static final List<Route> ROUTES = List.of(
    new Route("POST", ApiPaths.AUTH_REGISTER, AuthController.class, "register", Access.PUBLIC),
    new Route("POST", ApiPaths.AUTH_LOGIN, AuthController.class, "login", Access.PUBLIC),
    // Logout remains idempotent without a usable session; its controller revokes any presented digest.
    new Route("POST", ApiPaths.AUTH_LOGOUT, AuthController.class, "logout", Access.PUBLIC),
    new Route("POST", ApiPaths.AUTH_ME, AuthController.class, "me", Access.AUTHENTICATED),
    new Route("GET", ApiPaths.TOOLS_HEALTH, ToolsController.class, "health", Access.PUBLIC),
    new Route("GET", ApiPaths.TOOLS_POLICY, ToolsController.class, "policy", Access.OPTIONAL_BEARER),
    new Route("POST", ApiPaths.TOOLS_VALIDATE, ToolsController.class, "validate", Access.OPTIONAL_BEARER),
    new Route("POST", ApiPaths.TOOLS_GENERATE, ToolsController.class, "generate", Access.OPTIONAL_BEARER),
    // Public only subject to the existing development-demo gate in ToolsController.
    new Route("POST", ApiPaths.TOOLS_SEAL, ToolsController.class, "seal", Access.PUBLIC),
    new Route("POST", ApiPaths.TOOLS_OPEN, ToolsController.class, "open", Access.PUBLIC),
    new Route("POST", ApiPaths.TOOLS_HASH_PASSWORD, ToolsController.class, "hashPassword", Access.PUBLIC),
    new Route("POST", ApiPaths.TOOLS_VERIFY_PASSWORD, ToolsController.class, "verifyPassword", Access.PUBLIC),
    new Route("GET", ApiPaths.VAULT_ITEMS, VaultController.class, "list", Access.AUTHENTICATED),
    new Route("GET", ApiPaths.VAULT_ITEMS + "/{id}", VaultController.class, "get", Access.AUTHENTICATED),
    new Route("POST", ApiPaths.VAULT_ITEMS, VaultController.class, "create", Access.AUTHENTICATED),
    new Route("PUT", ApiPaths.VAULT_ITEMS + "/{id}", VaultController.class, "update", Access.AUTHENTICATED),
    new Route("DELETE", ApiPaths.VAULT_ITEMS + "/{id}", VaultController.class, "delete", Access.AUTHENTICATED),
    new Route("POST", ApiPaths.VAULT_ITEMS + "/{id}/reveal", VaultController.class, "reveal", Access.AUTHENTICATED),
    new Route("GET", ApiPaths.VAULT_OVERVIEW, VaultQueryController.class, "overview", Access.AUTHENTICATED),
    new Route("POST", ApiPaths.VAULT_SEARCH, VaultQueryController.class, "search", Access.AUTHENTICATED),
    new Route("GET", "/api/v1/account/password-policy", AccountPasswordPolicyController.class, "read", Access.AUTHENTICATED),
    new Route("PUT", "/api/v1/account/password-policy", AccountPasswordPolicyController.class, "update", Access.AUTHENTICATED),
    new Route("POST", ApiPaths.VAULT_EXPORT_PREVIEW, VaultExportController.class, "preview", Access.AUTHENTICATED),
    new Route("POST", ApiPaths.VAULT_EXPORT_DOWNLOAD, VaultExportController.class, "download", Access.AUTHENTICATED),
    new Route("POST", ApiPaths.VAULT_RESTORE_PREVIEW, VaultRestoreController.class, "preview", Access.AUTHENTICATED),
    new Route("POST", ApiPaths.VAULT_RESTORE_APPLY, VaultRestoreController.class, "apply", Access.AUTHENTICATED),
    new Route("POST", ApiPaths.AI_EXPLAIN, AiExplanationController.class, "explain", Access.AUTHENTICATED)
  );
  private static final Map<Route, org.springframework.web.util.pattern.PathPattern> PATTERNS = ROUTES.stream()
    .collect(Collectors.toUnmodifiableMap(Function.identity(), route -> PathPatternParser.defaultInstance.parse(route.pattern())));

  public List<Route> routes() { return ROUTES; }

  public Access pathAccess(String method, PathContainer path) {
    String verb = "HEAD".equals(method) ? "GET" : method;
    return ROUTES.stream().filter(route -> route.method().equals(verb)
      && PATTERNS.get(route).matches(path))
      .map(Route::access).findFirst().orElse(Access.AUTHENTICATED);
  }

  public Route resolve(String method, String pattern, HandlerMethod handler) {
    // Spring supports HEAD via the matching GET handler; no new content permission is granted.
    String verb = "HEAD".equals(method) ? "GET" : method;
    return ROUTES.stream().filter(route -> route.matches(verb, pattern, handler)).findFirst().orElse(null);
  }
}

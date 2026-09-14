package dev.keynest.manager.api;

import java.util.Set;

/**
 * Shared HTTP path catalog for Manager API edge code (controllers, filters, tests).
 * Domain services stay path-agnostic — do not import this from use cases or libs.
 */
public final class ApiPaths {

  public static final String API_V1 = "/api/v1";
  public static final String API_PATTERN = "/api/**";

  public static final String TOOLS = API_V1 + "/tools";
  public static final String TOOLS_HEALTH = TOOLS + "/health";
  public static final String TOOLS_POLICY = TOOLS + "/policy";
  public static final String TOOLS_VALIDATE = TOOLS + "/validate";
  public static final String TOOLS_GENERATE = TOOLS + "/generate";
  public static final String TOOLS_SEAL = TOOLS + "/seal";
  public static final String TOOLS_OPEN = TOOLS + "/open";
  public static final String TOOLS_HASH_PASSWORD = TOOLS + "/hash-password";
  public static final String TOOLS_VERIFY_PASSWORD = TOOLS + "/verify-password";

  public static final String AUTH = API_V1 + "/auth";
  public static final String AUTH_REGISTER = AUTH + "/register";
  public static final String AUTH_LOGIN = AUTH + "/login";
  public static final String AUTH_LOGOUT = AUTH + "/logout";
  public static final String AUTH_ME = AUTH + "/me";

  public static final String VAULT_ITEMS = API_V1 + "/vault/items";

  /**
   * POST surfaces throttled for brute-force / oracle abuse (#17).
   * Keep in sync with controller mappings that use these constants.
   */
  public static final Set<String> RATE_LIMITED_POSTS = Set.of(
    TOOLS_VALIDATE,
    AUTH_REGISTER,
    AUTH_LOGIN
  );

  private ApiPaths() {
  }

  public static String vaultItem(String id) {
    return VAULT_ITEMS + "/" + id;
  }
}

package dev.keynest.manager.api;

import dev.keynest.manager.security.SecurityEventLogger;
import dev.keynest.manager.security.SecurityEventLogger.Action;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

/** Wrap the limiter so rejected requests also produce a payload-free outcome. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class SecurityEventFilter extends OncePerRequestFilter {

  private static final String UUID_PATH = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
  private static final Pattern ITEM = Pattern.compile(ApiPaths.VAULT_ITEMS + "/" + UUID_PATH);
  private static final Pattern REVEAL = Pattern.compile(ApiPaths.VAULT_ITEMS + "/" + UUID_PATH + "/reveal");
  private final SecurityEventLogger events;

  public SecurityEventFilter(SecurityEventLogger events) {
    this.events = events;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
    throws ServletException, IOException {
    Action action = action(request.getMethod(), request.getRequestURI());
    if (action == null) {
      chain.doFilter(request, response);
      return;
    }
    boolean completed = false;
    try {
      chain.doFilter(request, response);
      completed = true;
    } finally {
      // Current controllers are synchronous. Future async routes need completion-aware handling.
      if (!request.isAsyncStarted()) {
        try {
          events.record(action, completed ? response.getStatus() : 500);
        } catch (RuntimeException ignored) {
          // Observability failure must not change authorization or mask the application result.
        }
      }
    }
  }

  private static Action action(String method, String path) {
    if ("POST".equals(method)) {
      if (ApiPaths.AI_EXPLAIN.equals(path)) return Action.AI_POLICY_EXPLAIN;
      if (ApiPaths.AUTH_REGISTER.equals(path)) return Action.AUTH_REGISTER;
      if (ApiPaths.AUTH_LOGIN.equals(path)) return Action.AUTH_LOGIN;
      if (ApiPaths.AUTH_LOGOUT.equals(path)) return Action.AUTH_LOGOUT;
      if (ApiPaths.AUTH_ME.equals(path)) return Action.AUTH_ME;
      if (ApiPaths.VAULT_ITEMS.equals(path)) return Action.VAULT_CREATE;
      if (REVEAL.matcher(path).matches()) return Action.VAULT_REVEAL;
    }
    if (ApiPaths.VAULT_ITEMS.equals(path) && "GET".equals(method)) return Action.VAULT_LIST;
    if (ITEM.matcher(path).matches()) {
      return switch (method) {
        case "GET" -> Action.VAULT_READ;
        case "PUT" -> Action.VAULT_UPDATE;
        case "DELETE" -> Action.VAULT_DELETE;
        default -> null;
      };
    }
    return null;
  }
}

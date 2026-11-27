package dev.keynest.manager.api;

import dev.keynest.manager.auth.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Collections;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.ServletRequestPathUtils;

/** Central check before argument/body binding, retaining handler/domain checks. */
@Component
public class CentralAuthenticationInterceptor implements HandlerInterceptor {
  private final AuthService auth;
  private final RouteAccessCatalog catalog;

  public CentralAuthenticationInterceptor(AuthService auth, RouteAccessCatalog catalog) {
    this.auth = auth;this.catalog = catalog;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    // Spring's CORS processor validates origin/method/header grants before this interceptor.
    if (CorsUtils.isPreFlightRequest(request)) return true;
    String path = ServletRequestPathUtils.parseAndCache(request).pathWithinApplication().value();
    boolean api = path.equals("/api") || path.startsWith("/api/");
    if (!(handler instanceof HandlerMethod target)) {
      if (api) throw deny(response);
      return true;
    }
    boolean application = target.getBeanType().getPackageName().startsWith("dev.keynest.manager.");
    // Actuator/error/static-framework handling retains its independent configured controls.
    if (!application && !api) return true;
    Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    RouteAccessCatalog.Route route = pattern == null ? null : catalog.resolve(request.getMethod(), pattern.toString(), target);
    if (route == null) throw deny(response);
    if (route.access() == RouteAccessCatalog.Access.PUBLIC) return true;
    var headers = Collections.list(request.getHeaders(HttpHeaders.AUTHORIZATION));
    if (route.access() == RouteAccessCatalog.Access.OPTIONAL_BEARER && headers.isEmpty()) return true;
    noStore(response);
    if (headers.size() != 1) throw deny(response);
    if (Boolean.TRUE.equals(request.getAttribute(CentralAuthenticationFilter.CHECKED))) return true;
    // Never cache this identity in AuthService: export/restore deliberately recheck revocation.
    auth.requireUser(headers.getFirst());
    return true;
  }

  private static void noStore(HttpServletResponse response) {
    response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    response.setHeader(HttpHeaders.PRAGMA, "no-cache");
  }

  private static ResponseStatusException deny(HttpServletResponse response) {
    noStore(response);
    return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "authentication required");
  }
}

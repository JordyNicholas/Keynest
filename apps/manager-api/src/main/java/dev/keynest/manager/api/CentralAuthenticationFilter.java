package dev.keynest.manager.api;

import dev.keynest.manager.auth.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.ServletRequestPathUtils;

/** Primary API gate, after existing bounds/rate admission and before MVC binding. */
public class CentralAuthenticationFilter extends OncePerRequestFilter {
  static final String CHECKED = CentralAuthenticationFilter.class.getName() + ".checked";
  private final AuthService auth;
  private final RouteAccessCatalog catalog;

  public CentralAuthenticationFilter(AuthService auth, RouteAccessCatalog catalog) { this.auth = auth;this.catalog = catalog; }
  @Override protected boolean shouldNotFilterAsyncDispatch() { return false; }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
    throws ServletException, IOException {
    request.removeAttribute(CHECKED);
    var path = ServletRequestPathUtils.parseAndCache(request).pathWithinApplication();
    if (!(path.value().equals("/api") || path.value().startsWith("/api/")) || CorsUtils.isPreFlightRequest(request)) {
      chain.doFilter(request, response);return;
    }
    var access = catalog.pathAccess(request.getMethod(), path);
    if (access == RouteAccessCatalog.Access.PUBLIC) { chain.doFilter(request, response);return; }
    var headers = Collections.list(request.getHeaders(HttpHeaders.AUTHORIZATION));
    if (access == RouteAccessCatalog.Access.OPTIONAL_BEARER && headers.isEmpty()) { chain.doFilter(request, response);return; }
    response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");response.setHeader(HttpHeaders.PRAGMA, "no-cache");
    if (headers.size() != 1) { refuse(response, path.value());return; }
    try { auth.requireUser(headers.getFirst()); }
    catch (ResponseStatusException failure) {
      if (failure.getStatusCode().value() != 401) throw failure;
      refuse(response, path.value());return;
    }
    request.setAttribute(CHECKED, Boolean.TRUE);
    try { chain.doFilter(request, response); }
    finally { request.removeAttribute(CHECKED); }
  }

  private static void refuse(HttpServletResponse response, String path) throws IOException {
    response.setStatus(401);response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    if (path.startsWith(ApiPaths.VAULT_EXPORTS + "/")) response.getWriter().write("{\"code\":\"EXPORT_SESSION_REQUIRED\"}");
    else if (path.startsWith(ApiPaths.VAULT_RESTORES + "/")) response.getWriter().write("{\"code\":\"RESTORE_SESSION_REQUIRED\"}");
    else response.getWriter().write("{\"error\":\"authentication_required\"}");
  }
}

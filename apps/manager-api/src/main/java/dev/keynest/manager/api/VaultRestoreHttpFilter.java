package dev.keynest.manager.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/** Wrap rate/handler errors too; fixed-action events wrap this filter's own size rejection. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
public class VaultRestoreHttpFilter extends OncePerRequestFilter {
  private static final PathPattern RESTORE = PathPatternParser.defaultInstance.parse(ApiPaths.VAULT_RESTORES + "/**");
  @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
    throws IOException, ServletException {
    PathContainer path = ServletRequestPathUtils.parseAndCache(request).pathWithinApplication();
    if (!RESTORE.matches(path)) { chain.doFilter(request, response);return; }
    response.setHeader("Cache-Control", "no-store");response.setHeader("Pragma", "no-cache");response.setHeader("Expires", "0");
    response.setHeader("X-Content-Type-Options", "nosniff");
    if (request.getContentLengthLong() > VaultRestoreRequest.MAX_BYTES) {
      response.setStatus(413);response.setContentType("application/json");
      response.getWriter().write("{\"code\":\"RESTORE_REQUEST_TOO_LARGE\"}");return;
    }
    chain.doFilter(request, response);
  }
}

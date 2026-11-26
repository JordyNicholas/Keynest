package dev.keynest.manager.api;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;

/** Document restrictions for the API server, not the separately hosted Angular application. */
public final class ApiResponseHeadersFilter implements Filter {

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
    throws IOException, ServletException {
    HttpServletResponse http = (HttpServletResponse) response;
    applyHeaders(http);
    chain.doFilter(request, new HttpServletResponseWrapper(http) {
      @Override
      public void reset() {
        super.reset();
        applyHeaders(this);
      }
    });
  }

  private static void applyHeaders(HttpServletResponse response) {
    response.setHeader("X-Content-Type-Options", "nosniff");
    response.setHeader("Referrer-Policy", "no-referrer");
    response.setHeader("X-Frame-Options", "DENY");
    response.setHeader("Content-Security-Policy",
      "default-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'");
  }
}

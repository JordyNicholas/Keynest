package dev.keynest.manager.api;

import dev.keynest.manager.config.RateLimitProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory fixed-window limiter for {@link ApiPaths#RATE_LIMITED_POSTS}.
 * Per-instance only — not shared across replicas (document for self-host).
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

  private final RateLimitProperties properties;
  private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

  public RateLimitFilter(RateLimitProperties properties) {
    this.properties = properties;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    if (!properties.isEnabled()) {
      return true;
    }
    if (!HttpMethod.POST.matches(request.getMethod())) {
      return true;
    }
    return !ApiPaths.RATE_LIMITED_POSTS.contains(request.getRequestURI());
  }

  @Override
  protected void doFilterInternal(
    HttpServletRequest request,
    HttpServletResponse response,
    FilterChain filterChain
  ) throws ServletException, IOException {
    String key = clientIp(request) + "|" + request.getRequestURI();
    long nowMs = System.currentTimeMillis();
    long windowMs = properties.getWindowSeconds() * 1000L;

    pruneExpired(nowMs, windowMs);

    Window window = windows.compute(key, (k, existing) -> {
      if (existing == null || nowMs - existing.startedAtMs >= windowMs) {
        return new Window(nowMs);
      }
      return existing;
    });

    if (window.count.incrementAndGet() > properties.getLimit()) {
      response.setStatus(429);
      response.setHeader("Retry-After", String.valueOf(properties.getWindowSeconds()));
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.getWriter().write("{\"error\":\"rate_limit_exceeded\"}");
      return;
    }

    filterChain.doFilter(request, response);
  }

  private static String clientIp(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      int comma = forwarded.indexOf(',');
      return (comma < 0 ? forwarded : forwarded.substring(0, comma)).trim();
    }
    return request.getRemoteAddr();
  }

  private void pruneExpired(long nowMs, long windowMs) {
    if (windows.size() < 1_000) {
      return;
    }
    Iterator<Map.Entry<String, Window>> it = windows.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<String, Window> entry = it.next();
      if (nowMs - entry.getValue().startedAtMs >= windowMs) {
        it.remove();
      }
    }
  }

  private static final class Window {
    private final long startedAtMs;
    private final AtomicInteger count = new AtomicInteger(0);

    private Window(long startedAtMs) {
      this.startedAtMs = startedAtMs;
    }
  }
}

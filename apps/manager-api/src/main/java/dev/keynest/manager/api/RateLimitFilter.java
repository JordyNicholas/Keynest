package dev.keynest.manager.api;

import dev.keynest.manager.config.RateLimitProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory fixed-window limiter for {@link ApiPaths#RATE_LIMITED_POSTS}.
 * Per-instance only — not shared across replicas (document for self-host).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 40)
public class RateLimitFilter extends OncePerRequestFilter {

  private static final List<PathPattern> RATE_LIMITED_PATHS = ApiPaths.RATE_LIMITED_POSTS.stream()
    .map(PathPatternParser.defaultInstance::parse)
    .toList();

  private final RateLimitProperties properties;
  private final ClientIpResolver clientIpResolver;
  private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
  private final Object windowLock = new Object();

  public RateLimitFilter(RateLimitProperties properties, ClientIpResolver clientIpResolver) {
    if (properties.getLimit() < 1 || properties.getWindowSeconds() < 1 || properties.getMaxEntries() < 1) {
      throw new IllegalStateException("rate-limit limit, window-seconds and max-entries must be positive");
    }
    this.properties = properties;
    this.clientIpResolver = clientIpResolver;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !properties.isEnabled() || !HttpMethod.POST.matches(request.getMethod());
  }

  @Override
  protected void doFilterInternal(
    HttpServletRequest request,
    HttpServletResponse response,
    FilterChain filterChain
  ) throws ServletException, IOException {
    PathContainer path = ServletRequestPathUtils.parseAndCache(request).pathWithinApplication();
    PathPattern route = RATE_LIMITED_PATHS.stream()
      .filter(pattern -> pattern.matches(path))
      .findFirst()
      .orElse(null);
    if (route == null) {
      filterChain.doFilter(request, response);
      return;
    }

    String key = clientIpResolver.resolve(request) + "|" + route.getPatternString();
    long nowMs = System.currentTimeMillis();
    long windowMs = properties.getWindowSeconds() * 1000L;

    Window window = windowFor(key, nowMs, windowMs);

    if (window == null || window.count.incrementAndGet() > properties.getLimit()) {
      reject(response);
      return;
    }

    filterChain.doFilter(request, response);
  }

  private Window windowFor(String key, long nowMs, long windowMs) {
    Window existing = windows.get(key);
    if (existing != null && nowMs - existing.startedAtMs < windowMs) {
      return existing;
    }

    synchronized (windowLock) {
      existing = windows.get(key);
      if (existing != null && nowMs - existing.startedAtMs < windowMs) {
        return existing;
      }
      if (windows.size() >= properties.getMaxEntries()) {
        pruneExpired(nowMs, windowMs);
      }
      if (existing == null && windows.size() >= properties.getMaxEntries()) {
        return null;
      }
      Window replacement = new Window(nowMs);
      windows.put(key, replacement);
      return replacement;
    }
  }

  private void reject(HttpServletResponse response) throws IOException {
    response.setStatus(429);
    response.setHeader("Retry-After", String.valueOf(properties.getWindowSeconds()));
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.getWriter().write("{\"error\":\"rate_limit_exceeded\"}");
  }

  private void pruneExpired(long nowMs, long windowMs) {
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

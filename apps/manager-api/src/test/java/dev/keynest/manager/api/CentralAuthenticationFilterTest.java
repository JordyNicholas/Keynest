package dev.keynest.manager.api;

import dev.keynest.manager.auth.AuthService;
import jakarta.servlet.DispatcherType;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CentralAuthenticationFilterTest {
  private final AuthService auth = mock(AuthService.class);
  private final CentralAuthenticationFilter filter = new CentralAuthenticationFilter(auth, new RouteAccessCatalog());

  @Test
  void missingAndDuplicateCredentialsCannotReachUnknownOrProtectedRoutes() throws Exception {
    for (String path : new String[]{ApiPaths.VAULT_ITEMS, "/api/v1/unclassified"}) {
      var request = new MockHttpServletRequest("GET", path);var response = new MockHttpServletResponse();
      filter.doFilter(request, response, (req, res) -> { throw new AssertionError("Protected chain reached"); });
      assertThat(response.getStatus()).isEqualTo(401);
      assertThat(response.getContentAsString()).isEqualTo("{\"error\":\"authentication_required\"}");
      assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
    }
    var duplicate = new MockHttpServletRequest("GET", ApiPaths.VAULT_ITEMS);
    duplicate.addHeader("Authorization", "Bearer first");duplicate.addHeader("Authorization", "Bearer second");
    var response = new MockHttpServletResponse();
    filter.doFilter(duplicate, response, (req, res) -> { throw new AssertionError("Duplicate credential chain reached"); });
    assertThat(response.getStatus()).isEqualTo(401);verifyNoInteractions(auth);
  }

  @Test
  void privateDispatchMarkerIsClearedAndAsyncDispatchRechecksRevocation() throws Exception {
    var request = new MockHttpServletRequest("GET", ApiPaths.VAULT_ITEMS);request.addHeader("Authorization", "Bearer synthetic");
    filter.doFilter(request, new MockHttpServletResponse(), (req, res) ->
      assertThat(req.getAttribute(CentralAuthenticationFilter.CHECKED)).isEqualTo(Boolean.TRUE));
    assertThat(request.getAttribute(CentralAuthenticationFilter.CHECKED)).isNull();
    when(auth.requireUser("Bearer synthetic")).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    request.setDispatcherType(DispatcherType.ASYNC);var response = new MockHttpServletResponse();
    filter.doFilter(request, response, (req, res) -> { throw new AssertionError("Revoked async chain reached"); });
    assertThat(response.getStatus()).isEqualTo(401);verify(auth, times(2)).requireUser("Bearer synthetic");
  }

  @Test
  void optionalCredentialsAreVerifiedWhileIrrelevantPublicCredentialsStayIrrelevant() throws Exception {
    when(auth.requireUser(anyString())).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    var invalid = new MockHttpServletRequest("GET", ApiPaths.TOOLS_POLICY);invalid.addHeader("Authorization", "Bearer invalid");
    var response = new MockHttpServletResponse();
    filter.doFilter(invalid, response, (req, res) -> { throw new AssertionError("Invalid optional credential chain reached"); });
    assertThat(response.getStatus()).isEqualTo(401);
    var calls = new AtomicInteger();
    var publicRequest = new MockHttpServletRequest("GET", ApiPaths.TOOLS_HEALTH);publicRequest.addHeader("Authorization", "Bearer invalid");
    filter.doFilter(publicRequest, new MockHttpServletResponse(), (req, res) -> calls.incrementAndGet());
    filter.doFilter(new MockHttpServletRequest("GET", ApiPaths.TOOLS_POLICY), new MockHttpServletResponse(), (req, res) -> calls.incrementAndGet());
    assertThat(calls.get()).isEqualTo(2);verify(auth).requireUser("Bearer invalid");
  }

  @Test
  void bulkAuthenticationRefusalsRetainTheirFixedTransportCodes() throws Exception {
    for (String[] target : new String[][]{{ApiPaths.VAULT_EXPORT_PREVIEW, "EXPORT_SESSION_REQUIRED"}, {ApiPaths.VAULT_RESTORE_APPLY, "RESTORE_SESSION_REQUIRED"}}) {
      var response = new MockHttpServletResponse();
      filter.doFilter(new MockHttpServletRequest("POST", target[0]), response, (req, res) -> { throw new AssertionError("Bulk chain reached"); });
      assertThat(response.getStatus()).isEqualTo(401);
      assertThat(response.getContentAsString()).isEqualTo("{\"code\":\"" + target[1] + "\"}");
    }
  }

  @Test
  void preflightDelegatesToSpringWithoutGrantingCorsItself() throws Exception {
    var request = new MockHttpServletRequest("OPTIONS", ApiPaths.VAULT_ITEMS);
    request.addHeader("Origin", "https://unlisted.example.invalid");request.addHeader("Access-Control-Request-Method", "POST");
    var response = new MockHttpServletResponse();var calls = new AtomicInteger();
    filter.doFilter(request, response, (req, res) -> calls.incrementAndGet());
    assertThat(calls.get()).isEqualTo(1);assertThat(response.getHeader("Access-Control-Allow-Origin")).isNull();
    verifyNoInteractions(auth);
  }

  @Test
  void downstreamFailureCannotRetainTheAuthenticationMarker() {
    var request = new MockHttpServletRequest("GET", ApiPaths.VAULT_ITEMS);request.addHeader("Authorization", "Bearer synthetic");
    assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> { throw new IllegalStateException("synthetic failure"); }))
      .isInstanceOf(IllegalStateException.class);
    assertThat(request.getAttribute(CentralAuthenticationFilter.CHECKED)).isNull();
  }
}

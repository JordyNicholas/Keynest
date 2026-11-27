package dev.keynest.manager.api;

import dev.keynest.manager.auth.AuthService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
  "server.servlet.context-path=/keynest", "keynest.rate-limit.enabled=false",
  "keynest.tools.demo.enabled=false", "keynest.ai.enabled=false"
})
class CentralAuthenticationHttpIntegrationTest {
  @LocalServerPort int port;
  @Autowired AuthService auth;
  @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings;
  @Autowired jakarta.servlet.ServletContext servlets;
  @MockitoSpyBean RouteAccessCatalog catalog;
  private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  @Test
  void everyApplicationMappingHasExactlyOneExplicitMethodPathAndHandlerDecision() {
    var actual = new ArrayList<String>();
    mappings.getHandlerMethods().forEach((mapping, handler) -> {
      if (!handler.getBeanType().getPackageName().startsWith("dev.keynest.manager.")) return;
      assertThat(mapping.getMethodsCondition().getMethods()).isNotEmpty();
      assertThat(mapping.getParamsCondition().isEmpty()).isTrue();
      assertThat(mapping.getHeadersCondition().isEmpty()).isTrue();
      assertThat(mapping.getCustomCondition()).isNull();
      for (var verb : mapping.getMethodsCondition().getMethods()) for (var path : mapping.getPatternValues()) {
        assertThat(path).startsWith("/api/");
        actual.add(key(verb.name(), path, ClassUtils.getUserClass(handler.getBeanType()), handler.getMethod().getName()));
        assertThat(catalog.resolve(verb.name(), path, handler)).isNotNull();
      }
    });
    var declared = catalog.routes().stream().map(route -> key(route.method(), route.pattern(), route.controller(), route.handler())).toList();
    assertThat(actual).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(declared);
    // A new servlet can bypass MVC's handler check and therefore requires explicit review.
    assertThat(servlets.getServletRegistrations().values()).isNotEmpty().allSatisfy(registration ->
      assertThat(registration.getClassName()).isEqualTo("org.springframework.web.servlet.DispatcherServlet"));
  }

  @Test
  void anonymousProtectedRequestsAreRefusedBeforeHeaderPathAndJsonBinding() throws Exception {
    for (String[] target : new String[][] {
      {"GET", ApiPaths.VAULT_ITEMS}, {"GET", ApiPaths.vaultItem("not-a-uuid")},
      {"POST", ApiPaths.VAULT_ITEMS}, {"PUT", ApiPaths.vaultItem("not-a-uuid")},
      {"DELETE", ApiPaths.vaultItem("not-a-uuid")}, {"POST", ApiPaths.vaultItemReveal("not-a-uuid")},
      {"POST", ApiPaths.VAULT_SEARCH}, {"GET", ApiPaths.VAULT_OVERVIEW},
      {"GET", "/api/v1/account/password-policy"}, {"PUT", "/api/v1/account/password-policy"},
      {"POST", ApiPaths.AUTH_LOGOUT_ALL}, {"POST", ApiPaths.AUTH_ME}, {"POST", ApiPaths.AI_EXPLAIN},
      {"POST", ApiPaths.VAULT_EXPORT_PREVIEW}, {"POST", ApiPaths.VAULT_EXPORT_DOWNLOAD},
      {"POST", ApiPaths.VAULT_RESTORE_PREVIEW}, {"POST", ApiPaths.VAULT_RESTORE_APPLY}
    }) {
      var denied = request(target[0], target[1], "{not valid JSON");
      assertThat(denied.statusCode()).isEqualTo(401);
      assertThat(denied.headers().firstValue("Cache-Control")).contains("no-store");
    }
  }

  @Test
  void publicOptionalDemoHeadAndCorsContractsRemainExplicit() throws Exception {
    assertThat(request("GET", ApiPaths.TOOLS_HEALTH, null, "Authorization", "Bearer invalid").statusCode()).isEqualTo(200);
    assertThat(request("HEAD", ApiPaths.TOOLS_HEALTH, null).statusCode()).isEqualTo(200);
    assertThat(request("GET", ApiPaths.TOOLS_POLICY, null).statusCode()).isEqualTo(200);
    assertThat(request("POST", ApiPaths.TOOLS_GENERATE, "{}").statusCode()).isEqualTo(200);
    assertThat(request("GET", ApiPaths.TOOLS_POLICY, null, "Authorization", "Bearer invalid").statusCode()).isEqualTo(401);
    assertThat(request("POST", ApiPaths.AUTH_LOGOUT, null).statusCode()).isEqualTo(200);
    assertThat(request("POST", ApiPaths.TOOLS_SEAL, "{}").statusCode()).isEqualTo(403);
    assertThat(request("HEAD", ApiPaths.VAULT_ITEMS, null).statusCode()).isEqualTo(401);
    var allowed = request("OPTIONS", ApiPaths.VAULT_ITEMS, null, "Origin", "http://localhost:4200",
      "Access-Control-Request-Method", "POST", "Access-Control-Request-Headers", "authorization,content-type");
    assertThat(allowed.statusCode()).isEqualTo(200);
    assertThat(allowed.headers().firstValue("Access-Control-Allow-Origin")).contains("http://localhost:4200");
    var denied = request("OPTIONS", ApiPaths.VAULT_ITEMS, null, "Origin", "https://unlisted.example.invalid",
      "Access-Control-Request-Method", "POST");
    assertThat(denied.statusCode()).isEqualTo(403);
    assertThat(denied.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
    var expired = request("GET", ApiPaths.VAULT_ITEMS, null, "Origin", "http://localhost:4200", "Authorization", "Bearer invalid");
    assertThat(expired.statusCode()).isEqualTo(401);
    assertThat(expired.headers().firstValue("Access-Control-Allow-Origin")).contains("http://localhost:4200");
    assertThat(expired.headers().firstValue("Access-Control-Allow-Credentials")).isEmpty();
  }

  @Test
  void invalidDuplicateAndRevokedCredentialsCannotReachProtectedHandlers() throws Exception {
    String bearer = bearer();
    assertThat(request("GET", ApiPaths.VAULT_ITEMS, null, "Authorization", "Bearer invalid").statusCode()).isEqualTo(401);
    assertThat(request("GET", ApiPaths.VAULT_ITEMS, null, "Authorization", bearer, "Authorization", "Bearer invalid").statusCode()).isEqualTo(401);
    assertThat(request("HEAD", ApiPaths.VAULT_ITEMS, null, "Authorization", bearer).statusCode()).isEqualTo(200);
    auth.logout(bearer);
    assertThat(request("GET", ApiPaths.VAULT_ITEMS, null, "Authorization", bearer).statusCode()).isEqualTo(401);
  }

  @Test
  void unclassifiedMappingsCannotRunEvenWithAValidSessionInsideOrOutsideApi() throws Exception {
    var handler = new PlainHandler();String bearer = bearer();
    for (String path : new String[]{"/api/v1/forgotten-auth", "/forgotten-auth"}) {
      var info = mapping(path);mappings.registerMapping(info, handler, PlainHandler.class.getMethod("content"));
      try {
        assertThat(request("GET", path, null).statusCode()).isEqualTo(401);
        assertThat(request("GET", path, null, "Authorization", bearer).statusCode()).isEqualTo(401);
        assertThat(handler.calls.get()).isZero();
      } finally { mappings.unregisterMapping(info); }
    }
  }

  @Test
  void aClassifiedHandlerWithoutALocalAuthenticationCallIsProtectedCentrally() throws Exception {
    var handler = new PlainHandler();String path = "/api/v1/classified-test", bearer = bearer();
    var info = mapping(path);var route = new RouteAccessCatalog.Route("GET", path, PlainHandler.class, "content", RouteAccessCatalog.Access.AUTHENTICATED);
    doReturn(route).when(catalog).resolve(eq("GET"), eq(path), any(HandlerMethod.class));
    mappings.registerMapping(info, handler, PlainHandler.class.getMethod("content"));
    try {
      assertThat(request("GET", path, null).statusCode()).isEqualTo(401);
      assertThat(handler.calls.get()).isZero();
      assertThat(request("GET", path, null, "Authorization", bearer).statusCode()).isEqualTo(200);
      assertThat(handler.calls.get()).isEqualTo(1);
    } finally { mappings.unregisterMapping(info); }
  }

  @Test
  void anUnexpectedHandlerCannotInheritAnExistingPublicPathGrant() throws Exception {
    var original = mappings.getHandlerMethods().entrySet().stream().filter(entry -> entry.getKey().getPatternValues().contains(ApiPaths.TOOLS_HEALTH)).findFirst().orElseThrow();
    var info = original.getKey();var target = original.getValue();var replacement = new PlainHandler();
    mappings.unregisterMapping(info);
    try {
      mappings.registerMapping(info, replacement, PlainHandler.class.getMethod("content"));
      assertThat(request("GET", ApiPaths.TOOLS_HEALTH, null).statusCode()).isEqualTo(401);
      assertThat(request("GET", ApiPaths.TOOLS_HEALTH, null, "Authorization", bearer()).statusCode()).isEqualTo(401);
      assertThat(replacement.calls.get()).isZero();
    } finally {
      mappings.unregisterMapping(info);mappings.registerMapping(info, target.getBean(), target.getMethod());
    }
  }

  private String bearer() { return "Bearer " + auth.register("central-" + UUID.randomUUID() + "@example.invalid", "Synthetic account passphrase!42").token(); }
  private RequestMappingInfo mapping(String path) { return RequestMappingInfo.paths(path).methods(RequestMethod.GET).options(mappings.getBuilderConfiguration()).build(); }
  private static String key(String method, String path, Class<?> controller, String handler) { return method + " " + path + " " + controller.getName() + "#" + handler; }
  private HttpResponse<String> request(String method, String path, String body, String... headers) throws Exception {
    var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/keynest" + path)).timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json");
    if (headers.length > 0) request.headers(headers);
    return client.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
  }

  static class PlainHandler {
    final AtomicInteger calls = new AtomicInteger();
    @ResponseBody public java.util.Map<String, String> content() { calls.incrementAndGet();return java.util.Map.of("status", "synthetic"); }
  }
}

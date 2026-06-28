package org.triplea.services.auth;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

/// Validates the double-submit CSRF token on every *unsafe* request to a [CsrfProtected]
/// resource: the `_csrf` form field must be present and equal (constant-time)
/// to the `csrf_token` cookie. Mismatch or absence aborts with **403**.
///
/// Safe methods (GET/HEAD/OPTIONS) are not checked — they don't mutate, and the GET render is
/// what issues the cookie + field in the first place.
///
/// Runs *after* [MapAdminAuthFilter] (higher priority value) so an anonymous caller to
/// a MapAdmin-gated form gets the authorization 401 rather than a 403 — a non-MapAdmin has no
/// business reaching the CSRF check.
@Provider
@CsrfProtected
@Priority(Priorities.AUTHORIZATION + 100)
public class CsrfRequestFilter implements ContainerRequestFilter {

  private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

  @Inject CsrfTokenProvider csrfTokenProvider;

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    if (SAFE_METHODS.contains(requestContext.getMethod())) {
      return;
    }

    String cookieToken = cookieToken(requestContext);
    String formToken = readFormToken(requestContext);

    if (cookieToken == null
        || cookieToken.isBlank()
        || !constantTimeEquals(cookieToken, formToken)) {
      requestContext.abortWith(
          Response.status(Response.Status.FORBIDDEN)
              .entity("Invalid or missing CSRF token")
              .build());
    }
  }

  private static String cookieToken(ContainerRequestContext requestContext) {
    Cookie cookie = requestContext.getCookies().get(CsrfTokenProvider.COOKIE_NAME);
    return cookie == null ? null : cookie.getValue();
  }

  /// Reads the `_csrf` field from a form-urlencoded body, then restores the entity stream so
  /// the resource method's `@FormParam`s still bind. Non-form bodies have no token.
  private static String readFormToken(ContainerRequestContext requestContext) throws IOException {
    MediaType mediaType = requestContext.getMediaType();
    if (mediaType == null || !mediaType.isCompatible(MediaType.APPLICATION_FORM_URLENCODED_TYPE)) {
      return null;
    }
    byte[] body = requestContext.getEntityStream().readAllBytes();
    requestContext.setEntityStream(new ByteArrayInputStream(body));
    return parseFormField(new String(body, StandardCharsets.UTF_8), CsrfTokenProvider.FORM_FIELD);
  }

  private static String parseFormField(String body, String field) {
    for (String pair : body.split("&")) {
      int eq = pair.indexOf('=');
      String name = eq < 0 ? pair : pair.substring(0, eq);
      if (field.equals(URLDecoder.decode(name, StandardCharsets.UTF_8))) {
        return eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
      }
    }
    return null;
  }

  private static boolean constantTimeEquals(String a, String b) {
    if (a == null || b == null) {
      return false;
    }
    return MessageDigest.isEqual(
        a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
  }
}

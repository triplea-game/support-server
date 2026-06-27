package org.triplea.services.auth;

import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/// Issues the double-submit CSRF cookie. When [CsrfTokenProvider] had to mint a fresh token
/// for this request (no usable cookie arrived) — typically the GET that renders a form — this
/// filter emits the matching `Set-Cookie` so the browser will echo it on the subsequent POST.
///
/// The cookie is `HttpOnly` (no JS needs it — the field is rendered server-side) and
/// `SameSite=Strict` (it is only ever sent to our own same-site form posts; unlike the oauth2-proxy
/// *session* cookie, it is not involved in any cross-site OAuth redirect, so Strict is safe).
/// `Secure` is config-driven: false for local http, true in prod behind TLS.
@Provider
@CsrfProtected
public class CsrfCookieResponseFilter implements ContainerResponseFilter {

  @Inject CsrfTokenProvider csrfTokenProvider;

  @ConfigProperty(name = "app.auth.csrf-cookie-secure", defaultValue = "false")
  boolean cookieSecure;

  @Override
  public void filter(
      ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
    if (!csrfTokenProvider.isGenerated()) {
      return;
    }
    NewCookie cookie =
        new NewCookie.Builder(CsrfTokenProvider.COOKIE_NAME)
            .value(csrfTokenProvider.token())
            .path("/")
            .httpOnly(true)
            .secure(cookieSecure)
            .sameSite(NewCookie.SameSite.STRICT)
            .build();
    responseContext.getHeaders().add("Set-Cookie", cookie);
  }
}

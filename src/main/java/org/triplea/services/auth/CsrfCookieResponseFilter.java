package org.triplea.services.auth;

import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/// Issues a CSRF cookie. When [CsrfTokenProvider] mints a fresh token for this request and
/// no usable cookie arrived — typically the GET that renders a form — this
/// filter emits the matching `Set-Cookie` so the browser will echo it on the subsequent POST.
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

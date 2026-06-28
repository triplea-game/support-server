package org.triplea.services.auth;

import jakarta.ws.rs.NameBinding;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Marks a JAX-RS resource class or method whose mutating (unsafe) requests must carry a valid CSRF
/// token. Binds [CsrfRequestFilter] (validates the token on POST/PUT/PATCH/DELETE) and
/// [CsrfCookieResponseFilter] (issues the double-submit cookie on safe responses).
///
/// Orthogonal to [RequiresMapAdmin]: MapAdmin status is authorization, CSRF is request-forgery
/// protection. The browser forms behind the cookie session (`MapAttributesController`) need
/// both; a future public form would need only CSRF.
@NameBinding
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface CsrfProtected {}

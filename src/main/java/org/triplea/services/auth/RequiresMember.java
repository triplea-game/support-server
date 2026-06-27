package org.triplea.services.auth;

import jakarta.ws.rs.NameBinding;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Marks a JAX-RS resource class or method as requiring membership of the authorizing team. Binds
/// [MemberAuthFilter], which rejects non-members. Apply to a class to gate every endpoint it
/// declares, or to a single method.
@NameBinding
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresMember {}

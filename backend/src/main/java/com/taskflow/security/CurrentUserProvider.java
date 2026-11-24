package com.taskflow.security;

import com.taskflow.service.UserAccountService;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * The single trusted source of "who is calling". Controllers and AI tools ask this component for the user id;
 * it is never taken from a request body, a path variable or an argument the LLM generated.
 * <p>
 * It reads the {@link SecurityContextHolder}, which Spring Security fills from the validated access token and
 * which is bound to the request thread. The Phase 2 supervisor runs its sub-agents and their tools synchronously
 * on that same thread (the {@code AgentRunRecorder} relies on the same fact), so tools see the caller's identity.
 * If code ever runs on another thread without propagating the context, this method throws: it fails closed and
 * never falls back to a default user.
 */
@Component
public class CurrentUserProvider {

    private final UserAccountService userAccounts;

    public CurrentUserProvider(UserAccountService userAccounts) {
        this.userAccounts = userAccounts;
    }

    public AuthenticatedUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token) || !token.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("No authenticated user in the current execution context");
        }
        Jwt jwt = token.getToken();
        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new BadCredentialsException("The access token has no subject");
        }
        return userAccounts.resolve(subject, jwt.getClaimAsString("preferred_username"));
    }

    public UUID userId() {
        return currentUser().id();
    }
}

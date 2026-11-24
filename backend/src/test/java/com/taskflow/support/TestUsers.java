package com.taskflow.support;

import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * The two demo users of the Keycloak realm, as MockMvc request identities. {@code jwt()} puts an already
 * validated token into the security context, so tests exercise the application's authorization without a
 * running Keycloak. Alice's subject matches the owner migration V3 assigned to pre-Phase-3 data.
 */
public final class TestUsers {

    public static final String ALICE_SUBJECT = "5f2b8c1e-3d4a-4e6f-8a9b-1c2d3e4f5a61";
    public static final String BOB_SUBJECT = "9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c62";

    private TestUsers() {
    }

    public static RequestPostProcessor alice() {
        return as(ALICE_SUBJECT, "alice");
    }

    public static RequestPostProcessor bob() {
        return as(BOB_SUBJECT, "bob");
    }

    public static RequestPostProcessor as(String subject, String username) {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(jwt -> jwt.subject(subject).claim("preferred_username", username));
    }
}

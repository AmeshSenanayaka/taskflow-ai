package com.taskflow.security;

import java.util.UUID;

/**
 * The caller of the current request, as the application knows it.
 *
 * @param id       TaskFlow's own user id: the owner of tasks, preferences and AI conversations
 * @param subject  the identity provider's stable subject ({@code sub} claim of the access token)
 * @param username a display name ({@code preferred_username}); informational only, never used for authorization
 */
public record AuthenticatedUser(UUID id, String subject, String username) {
}

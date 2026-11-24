package com.taskflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * A TaskFlow user, linked to the identity provider by its stable {@code subject}. Rows are created on a user's
 * first authenticated request ({@code UserAccountService}); passwords and profiles stay in Keycloak.
 */
@Entity
@Table(name = "app_users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAccount {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "subject", nullable = false, updatable = false, length = 255)
    private String subject;

    @Column(name = "username", length = 255)
    private String username;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;
}

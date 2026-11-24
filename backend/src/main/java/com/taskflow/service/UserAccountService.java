package com.taskflow.service;

import com.taskflow.entity.UserAccount;
import com.taskflow.repository.PreferencesRepository;
import com.taskflow.repository.UserAccountRepository;
import com.taskflow.security.AuthenticatedUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps an identity-provider subject to a TaskFlow user, creating the user (and default preferences) on the
 * first authenticated request: "just-in-time provisioning". Keycloak owns credentials and profiles; TaskFlow
 * only needs a stable id to own data.
 * <p>
 * The subject-to-user mapping never changes once created, so it is cached in memory to avoid a database
 * round trip on every request and every tool call.
 */
@Service
public class UserAccountService {

    private final UserAccountRepository userAccountRepository;
    private final PreferencesRepository preferencesRepository;
    private final TransactionTemplate transactionTemplate;
    private final Map<String, AuthenticatedUser> bySubject = new ConcurrentHashMap<>();

    public UserAccountService(UserAccountRepository userAccountRepository,
                              PreferencesRepository preferencesRepository,
                              PlatformTransactionManager transactionManager) {
        this.userAccountRepository = userAccountRepository;
        this.preferencesRepository = preferencesRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public AuthenticatedUser resolve(String subject, String username) {
        AuthenticatedUser cached = bySubject.get(subject);
        if (cached != null) {
            return cached;
        }
        AuthenticatedUser user = transactionTemplate.execute(status -> provision(subject, username));
        bySubject.put(subject, user);
        return user;
    }

    private AuthenticatedUser provision(String subject, String username) {
        userAccountRepository.insertIfAbsent(UUID.randomUUID(), subject, username);
        UserAccount account = userAccountRepository.findBySubject(subject)
                .orElseThrow(() -> new IllegalStateException("User row for subject was not created"));
        preferencesRepository.insertDefaultsIfAbsent(account.getId());
        return new AuthenticatedUser(account.getId(), subject, account.getUsername());
    }
}

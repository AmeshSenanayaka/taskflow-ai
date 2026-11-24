package com.taskflow.repository;

import com.taskflow.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findBySubject(String subject);

    /**
     * Creates the user unless one with this subject already exists. {@code ON CONFLICT DO NOTHING} makes two
     * concurrent first requests of the same user safe: exactly one row wins, the other insert is a no-op.
     */
    @Modifying
    @Query(value = """
            INSERT INTO app_users (id, subject, username)
            VALUES (:id, :subject, :username)
            ON CONFLICT (subject) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("subject") String subject, @Param("username") String username);
}

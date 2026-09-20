package com.azneotech.userauthservice.repos;

import com.azneotech.userauthservice.models.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface SessionRepo extends JpaRepository<UserSession, Long> {

    Optional<UserSession> findByTokenHash(String tokenHash);

    // Bulk JPQL updates bypass the auditing listener, so lastModifiedDate is set explicitly.
    // Callers must run inside a transaction.

    /** Revokes every live session of a user (used on password reset). Returns the number of rows touched. */
    @Modifying
    @Query("""
            update UserSession s
               set s.status = com.azneotech.userauthservice.models.Status.INACTIVE,
                   s.lastModifiedDate = CURRENT_TIMESTAMP
             where s.user.id = :userId
               and s.status = com.azneotech.userauthservice.models.Status.ACTIVE
            """)
    int deactivateAllActiveForUser(@Param("userId") Long userId);

    /** Marks sessions whose expiry has passed as INACTIVE. Returns the number of rows touched. */
    @Modifying
    @Query("""
            update UserSession s
               set s.status = com.azneotech.userauthservice.models.Status.INACTIVE,
                   s.lastModifiedDate = CURRENT_TIMESTAMP
             where s.status = com.azneotech.userauthservice.models.Status.ACTIVE
               and s.expiresAt < :now
            """)
    int deactivateExpired(@Param("now") LocalDateTime now);

}

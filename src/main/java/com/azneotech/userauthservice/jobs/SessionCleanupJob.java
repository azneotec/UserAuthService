package com.azneotech.userauthservice.jobs;

import com.azneotech.userauthservice.repos.SessionRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Sessions are normally marked INACTIVE the first time an expired token is presented; this sweep
 * catches the ones that were simply never used again.
 */
@Slf4j
@Component
public class SessionCleanupJob {

    private final SessionRepo sessionRepo;
    private final Clock clock;

    public SessionCleanupJob(SessionRepo sessionRepo, Clock clock) {
        this.sessionRepo = sessionRepo;
        this.clock = clock;
    }

    @Transactional
    @Scheduled(fixedDelayString = "${session.cleanup-interval:PT1H}")
    public void deactivateExpiredSessions() {
        int count = sessionRepo.deactivateExpired(LocalDateTime.now(clock));
        if (count > 0) {
            log.info("Deactivated {} expired session(s)", count);
        }
    }

}

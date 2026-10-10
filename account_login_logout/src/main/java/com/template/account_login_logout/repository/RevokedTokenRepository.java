package com.template.account_login_logout.repository;

import com.template.account_login_logout.model.RevokedToken;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RevokedTokenRepository extends JpaRepository<RevokedToken, Long> {
    Optional<RevokedToken> findByTokenHash(String tokenHash);
    boolean existsByTokenHash(String tokenHash);
    boolean existsByTokenHashAndExpiresAtAfter(String tokenHash, Instant now);
    void deleteByExpiresAtBefore(Instant now);
}

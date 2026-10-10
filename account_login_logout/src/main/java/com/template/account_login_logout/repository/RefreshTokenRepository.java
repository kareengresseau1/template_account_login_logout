package com.template.account_login_logout.repository;

import com.template.account_login_logout.model.RefreshToken;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);

    List<RefreshToken> findByUser_UsernameOrderByCreatedAtDesc(String username);

    boolean existsBySessionKeyAndUser_Username(String sessionKey, String username);

    void deleteBySessionKeyIsNull();

    void deleteByIdAndUser_Username(Long id, String username);

    void deleteBySessionKeyAndUser_Username(String sessionKey, String username);

    void deleteByUser_Username(String username);
}

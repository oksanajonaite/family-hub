package com.familyhub.repository;

import com.familyhub.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByToken(String token);
    void deleteByUserId(Long userId);                           // removes old tokens before creating a new one
    void deleteByExpiresAtBefore(LocalDateTime dateTime);       // cleans up expired tokens (intended for a future scheduler)
}

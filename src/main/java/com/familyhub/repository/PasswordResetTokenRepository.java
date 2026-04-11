package com.familyhub.repository;

import com.familyhub.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByToken(String token);     // token'o paieška pagal reikšmę
    void deleteByUserId(Long userId);                           // ištrina visus vartotojo token'us (prieš kuriant naują)
    void deleteByExpiresAtBefore(LocalDateTime dateTime);       // valo pasibaigusius token'us (Scheduler naudos ateityje)
}

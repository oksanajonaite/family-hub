package com.familyhub.repository;

import com.familyhub.entity.FamilyInvite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface FamilyInviteRepository extends JpaRepository<FamilyInvite, Long> {

    Optional<FamilyInvite> findByCodeAndUsedFalse(String code);
    void deleteAllByExpiresAtBefore(LocalDateTime now);
    Optional<FamilyInvite> findTopByFamilyIdAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(Long familyId, LocalDateTime now);
}

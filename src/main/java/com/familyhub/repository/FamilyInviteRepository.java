package com.familyhub.repository;

import com.familyhub.entity.FamilyInvite;
import com.familyhub.entity.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface FamilyInviteRepository extends JpaRepository<FamilyInvite, Long> {

    Optional<FamilyInvite> findByCode(String code);
    void deleteAllByExpiresAtBefore(LocalDateTime now);

    // Finds the most recent active code for the given family and role — used to display PARENT/KID codes separately
    Optional<FamilyInvite> findTopByFamilyIdAndRoleAndExpiresAtAfterOrderByCreatedAtDesc(
            Long familyId, Role role, LocalDateTime now
    );
}

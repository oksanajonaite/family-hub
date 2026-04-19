package com.familyhub.repository;

import com.familyhub.entity.User;
import com.familyhub.entity.enums.Role;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    List<User> findAllByFamilyId(Long familyId);

    @EntityGraph(attributePaths = "family")
    List<User> findAllByOrderByCreatedAtDesc();

    long countByFamilyIsNull();

    long countByFamilyIsNullAndRoleNot(Role role);

    // Used by the birthday reminder scheduler — finds users whose birthday falls on the given month/day.
    // Only users who belong to a family are included — no family means nobody to notify.
    @Query("SELECT u FROM User u WHERE u.family IS NOT NULL AND u.dateOfBirth IS NOT NULL " +
           "AND MONTH(u.dateOfBirth) = :month AND DAY(u.dateOfBirth) = :day")
    List<User> findUsersWithBirthdayOn(@Param("month") int month, @Param("day") int day);
}

package com.familyhub.repository;

import com.familyhub.entity.User;
import com.familyhub.entity.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    List<User> findAllByFamilyId(Long familyId);
    List<User> findAllByOrderByCreatedAtDesc();             // newest first — used in admin panel
    long countByFamilyIsNull();
    long countByFamilyIsNullAndRoleNot(Role role);          // excludes ADMIN users who never have a family
}

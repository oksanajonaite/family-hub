package com.familyhub.repository;

import com.familyhub.entity.User;
import com.familyhub.entity.enums.Role;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
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
}

package com.familyhub.repository;

import com.familyhub.entity.User;
import com.familyhub.entity.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);               // įrašas gali egzistuoti, gali ne
    boolean existsByEmail(String email);                    // ar emailas užimtas
    List<User> findAllByFamilyId(Long familyId);            // visi šeimos nariai
    List<User> findAllByOrderByCreatedAtDesc();             // visi vartotojai, naujausias pirmas (admin panel)
    long countByFamilyIsNull();                             // vartotojai be šeimos (statistikai)
    long countByFamilyIsNullAndRoleNot(Role role);          // vartotojai be šeimos, išskyrus nurodytą rolę
}

package com.familyhub.repository;

import com.familyhub.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email); //irasas gali egzistuoti gali ne
    boolean existsByEmail(String email); //ar emailas uzimtas
    List<User> findAllByFamilyId(Long familyId); //visi seimos nariai
}

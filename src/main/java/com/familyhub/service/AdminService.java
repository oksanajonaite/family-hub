package com.familyhub.service;

import com.familyhub.entity.Family;
import com.familyhub.entity.User;
import com.familyhub.entity.enums.Role;
import com.familyhub.repository.FamilyRepository;
import com.familyhub.repository.NotificationRepository;
import com.familyhub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final FamilyRepository familyRepository;
    private final NotificationRepository notificationRepository;

    // --- Bendras vartotojų skaičius ---
    // count() — JpaRepository metodas, generuoja: SELECT COUNT(*) FROM users
    @Transactional(readOnly = true)
    public long getTotalUsers() {
        return userRepository.count();
    }

    // --- Bendras šeimų skaičius ---
    @Transactional(readOnly = true)
    public long getTotalFamilies() {
        return familyRepository.count();
    }

    // --- Vartotojai be šeimos ---
    // Naujai užsiregistravę, bet dar neprisijungę prie šeimos.
    // ADMIN vartotojai neįtraukiami — jie neturi šeimos pagal dizainą.
    // countByFamilyIsNullAndRoleNot — Spring Data JPA generuoja:
    // SELECT COUNT(*) FROM users WHERE family_id IS NULL AND role != 'ADMIN'
    @Transactional(readOnly = true)
    public long getUsersWithoutFamily() {
        return userRepository.countByFamilyIsNullAndRoleNot(Role.ADMIN);
    }

    // --- Bendras neperskaitytų pranešimų skaičius sistemoje ---
    // Naudinga stebėti ar pranešimų sistema veikia normaliai
    @Transactional(readOnly = true)
    public long getTotalUnreadNotifications() {
        return notificationRepository.count();
    }

    // --- Visi vartotojai, naujausias pirmas ---
    // findAllByOrderByCreatedAtDesc — Spring Data JPA generuoja:
    // SELECT * FROM users ORDER BY created_at DESC
    @Transactional(readOnly = true)
    public List<User> getAllUsers() {
        return userRepository.findAllByOrderByCreatedAtDesc();
    }

    // --- Visos šeimos ---
    // findAll() — JpaRepository metodas, grąžina visus įrašus
    @Transactional(readOnly = true)
    public List<Family> getAllFamilies() {
        return familyRepository.findAll();
    }

    // --- Šeimos narių skaičius ---
    // Pagalbinis metodas template'ui — grąžina narių skaičių konkrečiai šeimai
    @Transactional(readOnly = true)
    public long getMemberCount(Long familyId) {
        return userRepository.findAllByFamilyId(familyId).size();
    }
}

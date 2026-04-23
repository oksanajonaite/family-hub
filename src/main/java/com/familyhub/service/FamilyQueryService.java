package com.familyhub.service;

import com.familyhub.dto.response.family.FamilyPageData;
import com.familyhub.entity.Family;
import com.familyhub.entity.User;
import com.familyhub.entity.enums.Role;
import com.familyhub.exception.FamilyNotFoundException;
import com.familyhub.repository.FamilyMemberRepository;
import com.familyhub.repository.FamilyRepository;
import com.familyhub.repository.PetRepository;
import com.familyhub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FamilyQueryService {

    private final FamilyRepository familyRepository;
    private final UserRepository userRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final PetRepository petRepository;
    private final FamilyInviteService familyInviteService;

    @Transactional(readOnly = true)
    public Family getFamily(Long familyId) {
        return familyRepository.findById(familyId)
                .orElseThrow(() -> new FamilyNotFoundException(familyId));
    }

    @Transactional(readOnly = true)
    public List<User> getFamilyUsers(Long familyId) {
        return userRepository.findAllByFamilyId(familyId);
    }

    @Transactional(readOnly = true)
    public FamilyPageData buildFamilyPageData(Long familyId, Long currentUserId) {
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new com.familyhub.exception.UserNotFoundException(currentUserId));

        return new FamilyPageData(
                getFamily(familyId),
                userRepository.findAllByFamilyId(familyId),
                familyMemberRepository.findAllByFamilyId(familyId),
                petRepository.findAllByFamilyId(familyId),
                familyInviteService.getActiveInviteCode(familyId, Role.PARENT),
                familyInviteService.getActiveInviteCode(familyId, Role.KID),
                currentUserId,
                currentUser.getDateOfBirth(),
                currentUser.isEmailNotificationsEnabled()
        );
    }
}

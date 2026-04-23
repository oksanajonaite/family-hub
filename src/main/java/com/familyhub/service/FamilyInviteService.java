package com.familyhub.service;

import com.familyhub.dto.request.family.CreateFamilyRequest;
import com.familyhub.entity.Family;
import com.familyhub.entity.FamilyInvite;
import com.familyhub.entity.User;
import com.familyhub.entity.enums.Role;
import com.familyhub.exception.FamilyNotFoundException;
import com.familyhub.exception.InvalidInviteCodeException;
import com.familyhub.exception.UserAlreadyInFamilyException;
import com.familyhub.exception.UserNotFoundException;
import com.familyhub.repository.FamilyInviteRepository;
import com.familyhub.repository.FamilyRepository;
import com.familyhub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FamilyInviteService {

    private final FamilyRepository familyRepository;
    private final FamilyInviteRepository familyInviteRepository;
    private final UserRepository userRepository;

    @Transactional
    public Family createFamily(CreateFamilyRequest request, Long creatorId) {
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new UserNotFoundException(creatorId));

        if (creator.getFamily() != null) {
            throw new UserAlreadyInFamilyException();
        }

        Family family = Family.builder()
                .name(request.name())
                .createdBy(creator)
                .build();
        family = familyRepository.save(family);

        creator.setFamily(family);
        userRepository.save(creator);

        createInviteCode(family, creator, Role.PARENT);
        createInviteCode(family, creator, Role.KID);

        return family;
    }

    @Transactional
    public Family joinByInviteCode(String code, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (user.getFamily() != null) {
            throw new UserAlreadyInFamilyException();
        }

        FamilyInvite invite = familyInviteRepository.findByCode(code)
                .filter(i -> i.getExpiresAt().isAfter(LocalDateTime.now()))
                .orElseThrow(InvalidInviteCodeException::new);

        user.setFamily(invite.getFamily());
        user.setRole(invite.getRole());
        userRepository.save(user);

        return invite.getFamily();
    }

    @Transactional
    public void generateInviteCode(Long familyId, Long requestingUserId, Role role) {
        Family family = familyRepository.findById(familyId)
                .orElseThrow(() -> new FamilyNotFoundException(familyId));
        User user = userRepository.findById(requestingUserId)
                .orElseThrow(() -> new UserNotFoundException(requestingUserId));
        createInviteCode(family, user, role);
    }

    @Transactional(readOnly = true)
    public String getActiveInviteCode(Long familyId, Role role) {
        return familyInviteRepository
                .findTopByFamilyIdAndRoleAndExpiresAtAfterOrderByCreatedAtDesc(
                        familyId, role, LocalDateTime.now()
                )
                .map(FamilyInvite::getCode)
                .orElse(null);
    }

    private void createInviteCode(Family family, User requestingUser, Role role) {
        String code = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();

        FamilyInvite invite = FamilyInvite.builder()
                .family(family)
                .code(code)
                .role(role)
                .createdBy(requestingUser)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        familyInviteRepository.save(invite);
    }
}

package com.familyhub.service;

import com.familyhub.dto.request.family.CreateFamilyRequest;
import com.familyhub.dto.response.FamilyPageData;
import com.familyhub.entity.Family;
import com.familyhub.entity.FamilyInvite;
import com.familyhub.entity.User;
import com.familyhub.entity.enums.Role;
import com.familyhub.exception.*;
import com.familyhub.repository.FamilyInviteRepository;
import com.familyhub.repository.FamilyMemberRepository;
import com.familyhub.repository.FamilyRepository;
import com.familyhub.repository.PetRepository;
import com.familyhub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FamilyService {

    private final FamilyRepository familyRepository;
    private final FamilyInviteRepository familyInviteRepository;
    private final UserRepository userRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final PetRepository petRepository;

    // @Transactional ensures the entire method runs in a single DB transaction.
    // If anything throws an exception, ALL changes are rolled back.
    // Example: if familyRepository.save() succeeds but userRepository.save() fails,
    // the family record is also reverted. Data integrity is guaranteed.
    @Transactional
    public Family createFamily(CreateFamilyRequest request, Long creatorId) {
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new UserNotFoundException(creatorId));

        // Business rule: one user belongs to exactly one family
        if (creator.getFamily() != null) {
            throw new UserAlreadyInFamilyException();
        }

        Family family = Family.builder()
                .name(request.name())
                .createdBy(creator)
                .build();
        // First save() gets the DB-generated id, required before linking to the user
        family = familyRepository.save(family);

        // Link the creator to the family and persist
        creator.setFamily(family);
        userRepository.save(creator);

        // Generate two invite codes upfront — one for PARENT role, one for KID role
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

        // findByCodeAndUsedFalse finds only an unused code.
        // .filter() adds an extra check: the code must not have expired (expiresAt > now).
        FamilyInvite invite = familyInviteRepository
                .findByCodeAndUsedFalse(code)
                .filter(i -> i.getExpiresAt().isAfter(LocalDateTime.now()))
                .orElseThrow(InvalidInviteCodeException::new);

        // Assign both family AND role from the invite — PARENT code grants PARENT, KID code grants KID
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

    @Transactional
    public void removeMember(Long memberId, Long requestingParentId, Long familyId) {
        User memberToRemove = userRepository.findById(memberId)
                .orElseThrow(() -> new UserNotFoundException(memberId));
        if (memberToRemove.getId().equals(requestingParentId)) {
            throw new CannotRemoveMemberException("You cannot remove yourself from the family");
        }
        if (!memberToRemove.getFamily().getId().equals(familyId)) {
            throw new CannotRemoveMemberException("This member does not belong to your family");
        }
        memberToRemove.setFamily(null);
        userRepository.save(memberToRemove);
    }

    // readOnly = true — Hibernate skips dirty checking, improving read performance
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
        return new FamilyPageData(
                familyRepository.findById(familyId).orElseThrow(() -> new FamilyNotFoundException(familyId)),
                userRepository.findAllByFamilyId(familyId),
                familyMemberRepository.findAllByFamilyId(familyId),
                petRepository.findAllByFamilyId(familyId),
                getActiveInviteCode(familyId, Role.PARENT),
                getActiveInviteCode(familyId, Role.KID),
                currentUserId
        );
    }

    // Returns the active invite code for the given role — PARENT and KID codes are stored separately
    @Transactional(readOnly = true)
    public String getActiveInviteCode(Long familyId, Role role) {
        return familyInviteRepository
                .findTopByFamilyIdAndRoleAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
                        familyId, role, LocalDateTime.now()
                )
                .map(FamilyInvite::getCode)
                .orElse(null);
    }

    // Creates and persists an invite code — used internally by createFamily() and generateInviteCode().
    // UUID.randomUUID() generates a random universally unique identifier.
    // Strip dashes and take the first 12 characters as a short, uppercase invite code.
    private void createInviteCode(Family family, User requestingUser, Role role) {
        String code = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();

        FamilyInvite invite = FamilyInvite.builder()
                .family(family)
                .code(code)
                .role(role)
                .createdBy(requestingUser)
                // Invite codes are valid for 7 days from creation
                .expiresAt(LocalDateTime.now().plusDays(7))
                .used(false)
                .build();

        familyInviteRepository.save(invite);
    }
}

package com.familyhub.service;

import com.familyhub.dto.request.family.CreateFamilyRequest;
import com.familyhub.entity.Family;
import com.familyhub.entity.FamilyInvite;
import com.familyhub.entity.User;
import com.familyhub.entity.enums.Role;
import com.familyhub.exception.*;
import com.familyhub.repository.FamilyInviteRepository;
import com.familyhub.repository.FamilyRepository;
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

    // @Transactional ensures the entire method runs in a single DB transaction.
    // If anything throws an exception, ALL changes are rolled back.
    // Example: if familyRepository.save() succeeds but userRepository.save() fails,
    // the family record is also reverted. Data integrity is guaranteed.
    @Transactional
    public Family createFamily(CreateFamilyRequest request, User creator) {
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
        generateInviteCode(family, creator, Role.PARENT);
        generateInviteCode(family, creator, Role.KID);

        return family;
    }

    @Transactional
    public Family joinByInviteCode(String code, User user) {
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

    // Controllers should not access UserRepository directly — all user lookups go through this service
    @Transactional(readOnly = true)
    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    @Transactional
    public void generateInviteCode(Family family, User requestingUser, Role role) {
        // UUID.randomUUID() generates a random universally unique identifier.
        // Strip dashes and take the first 12 characters as a short, uppercase invite code.
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

    @Transactional
    public void removeMember(Long memberId, User requestingParent) {
        User memberToRemove = userRepository.findById(memberId)
                .orElseThrow(() -> new UserNotFoundException(memberId));
        if (memberToRemove.getId().equals(requestingParent.getId())) {
            throw new CannotRemoveMemberException("You cannot remove yourself from the family");
        }
        if (!memberToRemove.getFamily().getId().equals(requestingParent.getFamily().getId())) {
            throw new CannotRemoveMemberException("This member does not belong to your family");
        }
        memberToRemove.setFamily(null);
        userRepository.save(memberToRemove);
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
}

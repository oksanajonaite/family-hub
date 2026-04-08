package com.familyhub.service;

import com.familyhub.dto.request.family.CreateFamilyRequest;
import com.familyhub.entity.Family;
import com.familyhub.entity.FamilyInvite;
import com.familyhub.entity.User;
import com.familyhub.exception.FamilyNotFoundException;
import com.familyhub.exception.InvalidInviteCodeException;
import com.familyhub.exception.UserAlreadyInFamilyException;
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

    @Transactional
    public Family createFamily(CreateFamilyRequest request, User creator) {
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

        generateInviteCode(family, creator);

        return family;
    }

    @Transactional
    public Family joinByInviteCode(String code, User user) {
        if (user.getFamily() != null) {
            throw new UserAlreadyInFamilyException();
        }

        FamilyInvite invite = familyInviteRepository
                .findByCodeAndUsedFalse(code)
                .filter(i -> i.getExpiresAt().isAfter(LocalDateTime.now()))
                .orElseThrow(InvalidInviteCodeException::new);

        invite.setUsed(true);
        familyInviteRepository.save(invite);

        user.setFamily(invite.getFamily());
        userRepository.save(user);

        return invite.getFamily();
    }

    @Transactional
    public FamilyInvite generateInviteCode(Family family, User requestingUser) {
        String code = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();

        FamilyInvite invite = FamilyInvite.builder()
                .family(family)
                .code(code)
                .createdBy(requestingUser)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .used(false)
                .build();

        return familyInviteRepository.save(invite);
    }

    @Transactional(readOnly = true)
    public Family getFamily(Long familyId) {
        return familyRepository.findById(familyId)
                .orElseThrow(() -> new FamilyNotFoundException(familyId));
    }

    @Transactional(readOnly = true)
    public List<User> getFamilyMembers(Long familyId) {
        return userRepository.findAllByFamilyId(familyId);
    }

    @Transactional(readOnly = true)
    public String getActiveInviteCode(Long familyId) {
        return familyInviteRepository
                .findTopByFamilyIdAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(familyId, LocalDateTime.now())
                .map(FamilyInvite::getCode)
                .orElse(null);
    }
}

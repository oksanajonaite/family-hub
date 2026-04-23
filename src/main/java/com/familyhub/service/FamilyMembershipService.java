package com.familyhub.service;

import com.familyhub.entity.User;
import com.familyhub.exception.CannotRemoveMemberException;
import com.familyhub.exception.UserNotFoundException;
import com.familyhub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FamilyMembershipService {

    private final UserRepository userRepository;

    @Transactional
    public void removeMember(Long memberId, Long requestingParentId, Long familyId) {
        User memberToRemove = userRepository.findById(memberId)
                .orElseThrow(() -> new UserNotFoundException(memberId));

        if (memberToRemove.getId().equals(requestingParentId)) {
            throw new CannotRemoveMemberException("You cannot remove yourself from the family");
        }
        if (memberToRemove.getFamily() == null || !memberToRemove.getFamily().getId().equals(familyId)) {
            throw new CannotRemoveMemberException("This member does not belong to your family");
        }

        memberToRemove.setFamily(null);
        userRepository.save(memberToRemove);
    }
}

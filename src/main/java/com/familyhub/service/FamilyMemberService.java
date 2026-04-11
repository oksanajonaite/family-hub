package com.familyhub.service;

import com.familyhub.dto.request.member.CreateFamilyMemberRequest;
import com.familyhub.dto.request.member.UpdateFamilyMemberRequest;
import com.familyhub.entity.Family;
import com.familyhub.entity.FamilyMember;
import com.familyhub.exception.AccessDeniedException;
import com.familyhub.repository.FamilyMemberRepository;
import com.familyhub.repository.FamilyRepository;
import com.familyhub.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FamilyMemberService {

    private final FamilyMemberRepository familyMemberRepository;
    private final FamilyRepository familyRepository;

    @Transactional(readOnly = true)
    public List<FamilyMember> getFamilyMembers(Long familyId) {
        return familyMemberRepository.findAllByFamilyId(familyId);
    }

    @Transactional(readOnly = true)
    public FamilyMember getMemberById(Long memberId, Long familyId) {
        FamilyMember member = familyMemberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("Family member not found: " + memberId));

        // Apsauga nuo URL manipuliavimo
        if (!member.getFamily().getId().equals(familyId)) {
            throw new AccessDeniedException();
        }
        return member;
    }

    // Tik PARENT gali pridėti narį be paskyros — tikrinama controller'yje
    @Transactional
    public FamilyMember createMember(CreateFamilyMemberRequest request, CustomUserDetails currentUser) {
        Family family = familyRepository.findById(currentUser.getFamilyId())
                .orElseThrow(() -> new IllegalStateException("Family not found"));

        FamilyMember member = FamilyMember.builder()
                .family(family)
                .name(request.name())
                .dateOfBirth(request.dateOfBirth())
                .build();

        return familyMemberRepository.save(member);
    }

    // Tik PARENT gali redaguoti — tikrinama controller'yje
    @Transactional
    public FamilyMember updateMember(Long memberId, UpdateFamilyMemberRequest request, Long familyId) {
        FamilyMember member = getMemberById(memberId, familyId);

        member.setName(request.name());
        member.setDateOfBirth(request.dateOfBirth());

        return familyMemberRepository.save(member);
    }

    // Tik PARENT gali trinti — tikrinama controller'yje
    @Transactional
    public void deleteMember(Long memberId, Long familyId) {
        FamilyMember member = getMemberById(memberId, familyId);
        familyMemberRepository.delete(member);
    }
}

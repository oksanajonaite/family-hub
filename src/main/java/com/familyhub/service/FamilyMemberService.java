package com.familyhub.service;

import com.familyhub.dto.request.member.CreateFamilyMemberRequest;
import com.familyhub.dto.request.member.UpdateFamilyMemberRequest;
import com.familyhub.entity.Family;
import com.familyhub.entity.FamilyMember;
import com.familyhub.exception.ForbiddenException;
import com.familyhub.exception.FamilyMemberNotFoundException;
import com.familyhub.exception.FamilyNotFoundException;
import com.familyhub.repository.FamilyMemberRepository;
import com.familyhub.repository.FamilyRepository;
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
                .orElseThrow(() -> new FamilyMemberNotFoundException(memberId));

        // Guard against URL manipulation — the member must belong to this family
        if (!member.getFamily().getId().equals(familyId)) {
            throw new ForbiddenException();
        }
        return member;
    }

    @Transactional(readOnly = true)
    public UpdateFamilyMemberRequest toEditRequest(Long memberId, Long familyId) {
        FamilyMember member = getMemberById(memberId, familyId);
        return new UpdateFamilyMemberRequest(member.getName(), member.getDateOfBirth());
    }

    @Transactional
    public FamilyMember createMember(CreateFamilyMemberRequest request, Long familyId) {
        Family family = familyRepository.findById(familyId)
                .orElseThrow(() -> new FamilyNotFoundException(familyId));

        FamilyMember member = FamilyMember.builder()
                .family(family)
                .name(request.name())
                .dateOfBirth(request.dateOfBirth())
                .build();

        return familyMemberRepository.save(member);
    }

    @Transactional
    public FamilyMember updateMember(Long memberId, UpdateFamilyMemberRequest request, Long familyId) {
        FamilyMember member = getMemberById(memberId, familyId);

        member.setName(request.name());
        member.setDateOfBirth(request.dateOfBirth());

        return familyMemberRepository.save(member);
    }

    @Transactional
    public void deleteMember(Long memberId, Long familyId) {
        FamilyMember member = getMemberById(memberId, familyId);
        familyMemberRepository.delete(member);
    }
}

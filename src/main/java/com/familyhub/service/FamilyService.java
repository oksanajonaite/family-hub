package com.familyhub.service;

import com.familyhub.dto.request.family.CreateFamilyRequest;
import com.familyhub.dto.response.family.FamilyPageData;
import com.familyhub.entity.Family;
import com.familyhub.entity.User;
import com.familyhub.entity.enums.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FamilyService {

    private final FamilyInviteService familyInviteService;
    private final FamilyMembershipService familyMembershipService;
    private final FamilyQueryService familyQueryService;
    private final FamilyDeletionService familyDeletionService;

    @Transactional
    public Family createFamily(CreateFamilyRequest request, Long creatorId) {
        return familyInviteService.createFamily(request, creatorId);
    }

    @Transactional
    public Family joinByInviteCode(String code, Long userId) {
        return familyInviteService.joinByInviteCode(code, userId);
    }

    @Transactional
    public void generateInviteCode(Long familyId, Long requestingUserId, Role role) {
        familyInviteService.generateInviteCode(familyId, requestingUserId, role);
    }

    @Transactional
    public void removeMember(Long memberId, Long requestingParentId, Long familyId) {
        familyMembershipService.removeMember(memberId, requestingParentId, familyId);
    }

    @Transactional(readOnly = true)
    public Family getFamily(Long familyId) {
        return familyQueryService.getFamily(familyId);
    }

    @Transactional(readOnly = true)
    public List<User> getFamilyUsers(Long familyId) {
        return familyQueryService.getFamilyUsers(familyId);
    }

    @Transactional(readOnly = true)
    public FamilyPageData buildFamilyPageData(Long familyId, Long currentUserId) {
        return familyQueryService.buildFamilyPageData(familyId, currentUserId);
    }

    @Transactional(readOnly = true)
    public String getActiveInviteCode(Long familyId, Role role) {
        return familyInviteService.getActiveInviteCode(familyId, role);
    }

    @Transactional
    public void deleteFamily(Long familyId, Long requestingUserId, String confirmedName) {
        familyDeletionService.deleteFamily(familyId, requestingUserId, confirmedName);
    }

    @Transactional
    public void deleteFamilyByAdmin(Long familyId) {
        familyDeletionService.deleteFamilyByAdmin(familyId);
    }
}

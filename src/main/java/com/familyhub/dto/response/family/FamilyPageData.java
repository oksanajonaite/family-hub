package com.familyhub.dto.response.family;

import com.familyhub.entity.Family;
import com.familyhub.entity.FamilyMember;
import com.familyhub.entity.Pet;
import com.familyhub.entity.User;

import java.time.LocalDate;
import java.util.List;

// View model for the family page — bundles all data needed by family/index.html.
// Avoids 7 separate model.addAttribute() calls in FamilyController.
public record FamilyPageData(
        Family family,
        List<User> members,
        List<FamilyMember> familyMembers,
        List<Pet> pets,
        String parentInviteCode,
        String kidInviteCode,
        Long currentUserId,
        LocalDate currentUserDateOfBirth
) {}

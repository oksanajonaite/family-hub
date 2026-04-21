package com.familyhub.controller;

import com.familyhub.security.CustomUserDetails;
import com.familyhub.service.FamilyMemberService;
import com.familyhub.service.PetService;
import com.familyhub.service.ProfileService;
import com.familyhub.service.S3Service;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.view.RedirectView;

import java.io.IOException;

// Serves private S3 photos for all entity types (user, pet, family member)
// as temporary redirects to pre-signed URLs (valid 1 hour).
//
// All three endpoints share the same logic via the private servePhoto() helper — DRY.
// Each endpoint fetches the S3 key from the appropriate service — SRP:
//   each service is responsible for its own entity's data access and ownership check.
//
// Why redirect instead of streaming:
//   - No bandwidth overhead — browser loads image directly from S3 after the redirect
//   - No memory pressure — file bytes don't pass through the JVM
//
// Security: anyRequest().authenticated() in SecurityConfig covers all /api/** endpoints.
// Additionally, pet and member endpoints verify family ownership via their respective services.
@Controller
@RequiredArgsConstructor
public class PhotoController {

    private final ProfileService profileService;
    private final PetService petService;
    private final FamilyMemberService familyMemberService;
    private final S3Service s3Service;

    @GetMapping("/api/photo/user/{userId}")
    public RedirectView getUserPhoto(@PathVariable Long userId,
                                     HttpServletResponse response) throws IOException {
        return servePhoto(profileService.getAvatarKey(userId), response);
    }

    @GetMapping("/api/photo/pet/{petId}")
    public RedirectView getPetPhoto(@PathVariable Long petId,
                                    @AuthenticationPrincipal CustomUserDetails currentUser,
                                    HttpServletResponse response) throws IOException {
        return servePhoto(petService.getPhotoKey(petId, currentUser.getFamilyId()), response);
    }

    @GetMapping("/api/photo/member/{memberId}")
    public RedirectView getMemberPhoto(@PathVariable Long memberId,
                                       @AuthenticationPrincipal CustomUserDetails currentUser,
                                       HttpServletResponse response) throws IOException {
        return servePhoto(familyMemberService.getPhotoKey(memberId, currentUser.getFamilyId()), response);
    }

    // Shared logic for all three photo endpoints.
    // Generates a fresh pre-signed URL and returns a redirect response.
    // Returns 404 if no photo is uploaded — in practice not reached because
    // templates only call these endpoints when photoUrl/avatarKey != null.
    private RedirectView servePhoto(String key, HttpServletResponse response) throws IOException {
        if (key == null || key.isBlank()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }
        return new RedirectView(s3Service.generatePresignedUrl(key));
    }
}

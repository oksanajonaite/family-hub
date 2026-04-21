package com.familyhub.util;

import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

// Validates uploaded photo files before passing them to S3Service.
// Shared across ProfileController, PetController and FamilyMemberController
// to avoid repeating the same validation logic in every upload endpoint.
//
// Usage:
//   PhotoUploadValidator.validate(file).ifPresent(error -> {
//       redirectAttributes.addFlashAttribute("errorMessage", error);
//       // return redirect;
//   });
//
// Returns Optional<String> — present with error message if invalid, empty if valid.
// Follows the utility class pattern (like NavigationUtils): stateless, no Spring injection needed.
public class PhotoUploadValidator {

    private PhotoUploadValidator() {} // utility class — prevent instantiation

    public static Optional<String> validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Optional.of("Please select a photo to upload.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return Optional.of("Only image files are allowed (JPEG, PNG, WebP).");
        }
        return Optional.empty();
    }
}

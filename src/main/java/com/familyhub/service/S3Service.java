package com.familyhub.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;
import java.util.UUID;

// Handles all AWS S3 file operations: upload, pre-signed URL generation, and deletion.
// Called by ProfileService (user avatars) and PetService (pet photos).
//
// Key design decision — files are PRIVATE in S3:
//   uploadFile()          → returns S3 key (e.g. "avatars/uuid.jpg"), NOT a public URL
//   generatePresignedUrl() → creates a temporary URL valid for 1 hour for authenticated access
//   deleteFile()          → accepts the S3 key directly
//
// This means the bucket requires NO public access settings.
// Avatars and pet photos are only accessible to authenticated users via AvatarController.
@Service
@RequiredArgsConstructor
@Slf4j
public class S3Service {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${aws.s3.bucket}")
    private String bucket;

    // Pre-signed URLs are valid for 1 hour.
    // Short enough to limit exposure if a URL is leaked;
    // long enough for a browser session without re-fetching.
    private static final Duration PRESIGNED_URL_DURATION = Duration.ofHours(1);

    // Uploads a file to S3 under the given folder (e.g. "avatars", "pets").
    // Key format: folder/uuid.extension — UUID prevents filename collisions.
    // Returns the S3 key — NOT a public URL. Store this key in the DB.
    // Use generatePresignedUrl(key) when you need to display the file.
    public String uploadFile(MultipartFile file, String folder) {
        String extension = getExtension(file.getOriginalFilename());
        String key = folder + "/" + UUID.randomUUID() + extension;

        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(request, RequestBody.fromBytes(file.getBytes()));

            log.info("[S3] Uploaded: {}", key);
            return key;

        } catch (Exception e) {
            log.error("[S3] Upload failed for key {}: {}", key, e.getMessage());
            throw new RuntimeException("File upload failed. Please try again.", e);
        }
    }

    // Generates a temporary pre-signed URL for a private S3 file.
    // Valid for 1 hour — after that the URL stops working even if someone copied it.
    // The browser loads the image directly from S3 (no bandwidth through this server).
    public String generatePresignedUrl(String key) {
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(PRESIGNED_URL_DURATION)
                .getObjectRequest(GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .build())
                .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    // Deletes a file from S3 by its key (e.g. "avatars/uuid.jpg").
    // Safe to call with null or blank — returns immediately without error.
    // Failure is logged as warn — a missing file should never block a user action.
    public void deleteFile(String key) {
        if (key == null || key.isBlank()) return;

        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
            log.info("[S3] Deleted: {}", key);
        } catch (Exception e) {
            log.warn("[S3] Delete failed for key {}: {}", key, e.getMessage());
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf(".")).toLowerCase();
    }
}

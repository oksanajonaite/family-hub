package com.familyhub.service;

import com.familyhub.dto.request.member.CreateFamilyMemberRequest;
import com.familyhub.dto.request.member.UpdateFamilyMemberRequest;
import com.familyhub.entity.Family;
import com.familyhub.entity.FamilyMember;
import com.familyhub.exception.ForbiddenException;
import com.familyhub.exception.FamilyMemberNotFoundException;
import com.familyhub.exception.FamilyNotFoundException;
import com.familyhub.mapper.FamilyMemberMapper;
import com.familyhub.repository.EventParticipantRepository;
import com.familyhub.repository.FamilyMemberRepository;
import com.familyhub.repository.FamilyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;


@Service
@RequiredArgsConstructor
public class FamilyMemberService {

    private final FamilyMemberRepository familyMemberRepository;
    private final FamilyRepository familyRepository;
    private final EventParticipantRepository eventParticipantRepository;
    private final S3Service s3Service;
    private final FamilyMemberMapper familyMemberMapper;

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

        FamilyMember member = familyMemberMapper.toEntity(request);
        member.setFamily(family);

        return familyMemberRepository.save(member);
    }

    @Transactional
    public FamilyMember updateMember(Long memberId, UpdateFamilyMemberRequest request, Long familyId) {
        FamilyMember member = getMemberById(memberId, familyId);

        familyMemberMapper.updateEntity(request, member);

        return familyMemberRepository.save(member);
    }

    @Transactional
    public void deleteMember(Long memberId, Long familyId) {
        FamilyMember member = getMemberById(memberId, familyId);
        // Delete photo from S3 before removing the entity —
        // once the DB row is gone the key is lost and the file would become an orphan in S3
        s3Service.deleteFile(member.getPhotoUrl());
        // Remove all event participations first — otherwise the FK constraint on event_participants.family_member_id
        // would block the delete if this member is referenced as an event participant
        eventParticipantRepository.deleteAllByFamilyMemberId(memberId);
        familyMemberRepository.delete(member);
    }

    // Returns the S3 key for the member's photo, or null if no photo uploaded.
    // Used by PhotoController to generate a pre-signed URL for the response.
    // Family ownership is verified by getMemberById — prevents accessing other families' photos.
    @Transactional(readOnly = true)
    public String getPhotoKey(Long memberId, Long familyId) {
        return getMemberById(memberId, familyId).getPhotoUrl();
    }

    // Uploads a member photo to S3, replaces the old one if it exists, persists the S3 key.
    // Folder "members/" keeps member photos separate from other entity photos in the bucket.
    @Transactional
    public void uploadPhoto(Long memberId, Long familyId, MultipartFile file) {
        FamilyMember member = getMemberById(memberId, familyId);
        s3Service.deleteFile(member.getPhotoUrl());
        String key = s3Service.uploadFile(file, "members");
        member.setPhotoUrl(key);
        familyMemberRepository.save(member);
    }
}

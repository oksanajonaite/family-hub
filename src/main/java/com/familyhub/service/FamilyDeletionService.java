package com.familyhub.service;

import com.familyhub.entity.Family;
import com.familyhub.entity.TaskItem;
import com.familyhub.entity.User;
import com.familyhub.exception.FamilyNotFoundException;
import com.familyhub.exception.ForbiddenException;
import com.familyhub.exception.UserNotFoundException;
import com.familyhub.repository.EventParticipantRepository;
import com.familyhub.repository.EventRepository;
import com.familyhub.repository.FamilyInviteRepository;
import com.familyhub.repository.FamilyMemberRepository;
import com.familyhub.repository.FamilyRepository;
import com.familyhub.repository.NotificationRepository;
import com.familyhub.repository.PetRepository;
import com.familyhub.repository.TaskRepository;
import com.familyhub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FamilyDeletionService {

    private final FamilyRepository familyRepository;
    private final UserRepository userRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final PetRepository petRepository;
    private final EventRepository eventRepository;
    private final EventParticipantRepository eventParticipantRepository;
    private final TaskRepository taskRepository;
    private final NotificationRepository notificationRepository;
    private final FamilyInviteRepository familyInviteRepository;

    @Transactional
    public void deleteFamily(Long familyId, Long requestingUserId, String confirmedName) {
        Family family = familyRepository.findById(familyId)
                .orElseThrow(() -> new FamilyNotFoundException(familyId));

        User requester = userRepository.findById(requestingUserId)
                .orElseThrow(() -> new UserNotFoundException(requestingUserId));

        if (requester.getFamily() == null || !family.getId().equals(requester.getFamily().getId())) {
            throw new ForbiddenException();
        }

        if (!family.getName().equals(confirmedName)) {
            throw new IllegalArgumentException("Family name does not match. Please type the exact name.");
        }

        performFamilyDeletion(family);
    }

    @Transactional
    public void deleteFamilyByAdmin(Long familyId) {
        Family family = familyRepository.findById(familyId)
                .orElseThrow(() -> new FamilyNotFoundException(familyId));
        performFamilyDeletion(family);
    }

    private void performFamilyDeletion(Family family) {
        Long familyId = family.getId();

        List<Long> eventIds = eventRepository.findAllByFamilyIdOrderByStartsAtAsc(familyId)
                .stream()
                .map(e -> e.getId())
                .toList();
        if (!eventIds.isEmpty()) {
            eventParticipantRepository.deleteAllByEventIdIn(eventIds);
        }

        eventRepository.deleteAllByFamilyId(familyId);

        List<Long> userIds = userRepository.findAllByFamilyId(familyId)
                .stream()
                .map(User::getId)
                .toList();
        if (!userIds.isEmpty()) {
            notificationRepository.deleteAllByRecipientIdIn(userIds);
        }

        List<TaskItem> tasks = taskRepository.findAllByFamilyIdOrderByCreatedAtDesc(familyId);
        taskRepository.deleteAll(tasks);

        petRepository.deleteAllByFamilyId(familyId);
        familyMemberRepository.deleteAllByFamilyId(familyId);
        familyInviteRepository.deleteAllByFamilyId(familyId);

        List<User> members = userRepository.findAllByFamilyId(familyId);
        members.forEach(user -> user.setFamily(null));
        userRepository.saveAll(members);

        familyRepository.delete(family);
    }
}

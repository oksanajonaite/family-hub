package com.familyhub.service;

import com.familyhub.entity.Event;
import com.familyhub.entity.FamilyMember;
import com.familyhub.entity.User;
import com.familyhub.entity.enums.NotificationType;
import com.familyhub.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static java.time.LocalDateTime.now;

// Runs background jobs automatically on a timer — no user action needed.
// @Scheduled requires @EnableScheduling on FamilyHubApplication.
// All jobs are @Transactional so DB writes are atomic and rolled back on failure.
@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduledJobService {

    private final UserRepository userRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final EventRepository eventRepository;
    private final FamilyInviteRepository familyInviteRepository;
    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailService emailService;

    // ─── Job 1: Birthday reminders ────────────────────────────────────────────
    // Runs every morning at 08:00.
    // Finds users and account-less family members whose birthday is today,
    // then notifies every other member of their family.
    //
    // Dedup guard: checks if a BIRTHDAY_REMINDER already exists for today
    // (created after midnight) so the job can safely re-run without duplicates.
    @Scheduled(cron = "0 0 8 * * *")
    @Transactional
    public void sendBirthdayReminders() {
        int month = LocalDate.now().getMonthValue();
        int day   = LocalDate.now().getDayOfMonth();
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();

        log.info("[Scheduler] Running birthday reminders for {}-{}", month, day);

        // 1a. Users with a birthday today — notify their family members
        List<User> usersWithBirthday = userRepository.findUsersWithBirthdayOn(month, day);
        for (User birthdayUser : usersWithBirthday) {
            if (birthdayUser.getFamily() == null) continue;

            List<User> familyUsers = userRepository.findAllByFamilyId(birthdayUser.getFamily().getId());
            for (User recipient : familyUsers) {
                // Don't notify the birthday person about their own birthday
                if (recipient.getId().equals(birthdayUser.getId())) continue;
                sendBirthdayNotificationIfNeeded(
                        recipient, "USER", birthdayUser.getId(),
                        birthdayUser.getDisplayName(), todayStart);
            }
        }

        // 1b. Account-less family members with a birthday today — notify all family users
        List<FamilyMember> membersWithBirthday = familyMemberRepository.findMembersWithBirthdayOn(month, day);
        for (FamilyMember member : membersWithBirthday) {
            List<User> familyUsers = userRepository.findAllByFamilyId(member.getFamily().getId());
            for (User recipient : familyUsers) {
                sendBirthdayNotificationIfNeeded(
                        recipient, "FAMILY_MEMBER", member.getId(),
                        member.getName(), todayStart);
            }
        }

        log.info("[Scheduler] Birthday reminders done. Users: {}, members: {}",
                usersWithBirthday.size(), membersWithBirthday.size());
    }

    // ─── Job 2: Event reminders ────────────────────────────────────────────────
    // Runs every 15 minutes.
    // Finds events starting in the next 50–65 minute window and notifies
    // all family members with accounts.
    //
    // Why 50–65 min?  A 15-minute cron creates non-overlapping 15-min slices.
    // Any event falls into exactly one slice → no duplicate sends.
    // The existsByRecipientId... guard is a second safety net.
    @Scheduled(cron = "0 0/15 * * * *")
    @Transactional
    public void sendEventReminders() {
        LocalDateTime from = LocalDateTime.now().plusMinutes(50);
        LocalDateTime to   = LocalDateTime.now().plusMinutes(65);

        List<Event> upcoming = eventRepository.findAllByStartsAtBetween(from, to);
        if (upcoming.isEmpty()) return;

        log.info("[Scheduler] Sending event reminders for {} event(s).", upcoming.size());

        for (Event event : upcoming) {
            List<User> familyUsers = userRepository.findAllByFamilyId(event.getFamily().getId());
            for (User recipient : familyUsers) {
                // Skip if reminder was already sent for this event (guard against re-runs)
                if (notificationRepository
                        .existsByRecipientIdAndTypeAndRelatedEntityTypeAndRelatedEntityId(
                                recipient.getId(), NotificationType.EVENT_REMINDER,
                                "EVENT", event.getId())) {
                    continue;
                }

                notificationService.createNotification(
                        recipient,
                        NotificationType.EVENT_REMINDER,
                        "📅 Reminder: \"" + event.getTitle() + "\" starts in about 1 hour.",
                        "EVENT",
                        event.getId()
                );
            }
        }
    }

    // ─── Job 4: Delete old notifications ─────────────────────────────────────
    // Runs every night at 01:00.
    // Notifications older than 7 days are no longer relevant and clutter the inbox.
    // Deleting them keeps the notifications table small and queries fast.
    @Scheduled(cron = "0 0 1 * * *")
    @Transactional
    public void deleteOldNotifications() {
        LocalDateTime cutoff = now().minusDays(7);
        notificationRepository.deleteAllByCreatedAtBefore(cutoff);
        log.info("[Scheduler] Deleted notifications older than 7 days (before {}).", cutoff.toLocalDate());
    }

    // ─── Job 5: Delete expired password reset tokens ──────────────────────────
    // Runs every night at 02:00.
    // Password reset tokens expire after 1 hour (set in the auth service).
    // Expired tokens are useless — they cannot be redeemed — but accumulate in the DB over time.
    // PasswordResetTokenRepository.deleteByExpiresAtBefore() was prepared exactly for this job.
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void deleteExpiredPasswordResetTokens() {
        passwordResetTokenRepository.deleteByExpiresAtBefore(now());
        log.info("[Scheduler] Deleted expired password reset tokens.");
    }

    // ─── Job 6: Event reminder emails ─────────────────────────────────────────
    // Runs every morning at 07:00 — one hour before birthday reminders (08:00).
    // Finds events starting tomorrow and emails every family member who has opted in.
    //
    // Dedup: the window is exactly "tomorrow 00:00 → 23:59" and the job runs once a day,
    // so each event is naturally processed once — no extra guard needed.
    // Emails are sent on best-effort: a mail failure is logged but does not stop the loop.
    @Scheduled(cron = "0 0 7 * * *")
    @Transactional
    public void sendEventReminderEmails() {
        LocalDateTime tomorrowStart = LocalDate.now().plusDays(1).atStartOfDay();
        LocalDateTime tomorrowEnd   = LocalDate.now().plusDays(1).atTime(23, 59, 59);

        List<Event> tomorrowEvents = eventRepository.findAllByStartsAtBetween(tomorrowStart, tomorrowEnd);
        if (tomorrowEvents.isEmpty()) return;

        log.info("[Scheduler] Sending event reminder emails for {} event(s) starting tomorrow.", tomorrowEvents.size());

        for (Event event : tomorrowEvents) {
            List<User> familyUsers = userRepository.findAllByFamilyId(event.getFamily().getId());
            String startsAt = event.getStartsAt().toLocalTime().toString();

            for (User recipient : familyUsers) {
                // Respect the user's email notification preference
                if (!recipient.isEmailNotificationsEnabled()) continue;

                try {
                    emailService.sendEventReminder(
                            recipient.getEmail(),
                            recipient.getDisplayName(),
                            event.getTitle(),
                            startsAt
                    );
                } catch (Exception e) {
                    log.warn("[Scheduler] Failed to send event reminder email to {}: {}",
                            recipient.getEmail(), e.getMessage());
                }
            }
        }
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    // Sends a BIRTHDAY_REMINDER notification to the recipient only if one has not
    // already been sent today (checked via createdAtAfter todayStart).
    // Used for both User birthdays (entityType = "USER") and account-less
    // FamilyMember birthdays (entityType = "FAMILY_MEMBER") to avoid duplicating
    // the dedup check + createNotification call in both loops.
    private void sendBirthdayNotificationIfNeeded(
            User recipient, String entityType, Long entityId,
            String birthdayPersonName, LocalDateTime todayStart
    ) {
        boolean alreadySent = notificationRepository
                .existsByRecipientIdAndTypeAndRelatedEntityTypeAndRelatedEntityIdAndCreatedAtAfter(
                        recipient.getId(), NotificationType.BIRTHDAY_REMINDER,
                        entityType, entityId, todayStart);
        if (alreadySent) return;

        notificationService.createNotification(
                recipient,
                NotificationType.BIRTHDAY_REMINDER,
                "🎂 Today is " + birthdayPersonName + "'s birthday!",
                entityType,
                entityId
        );
    }

    // ─── Job 3: Expired invite code cleanup ────────────────────────────────────
    // Runs every night at midnight.
    // Deletes invite codes whose expiresAt is in the past — keeps the DB tidy.
    // Invite codes expire after 7 days (set in FamilyService.createInviteCode).
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void cleanUpExpiredInviteCodes() {
        familyInviteRepository.deleteAllByExpiresAtBefore(LocalDateTime.now());
        log.info("[Scheduler] Expired invite codes cleaned up.");
    }
}

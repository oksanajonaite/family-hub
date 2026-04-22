package com.familyhub.service;

import com.familyhub.entity.Event;
import com.familyhub.entity.Family;
import com.familyhub.entity.FamilyMember;
import com.familyhub.entity.TaskItem;
import com.familyhub.entity.User;
import com.familyhub.entity.enums.NotificationType;
import com.familyhub.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Testuojame ScheduledJobService verslo logiką — ne patį @Scheduled laikrodį.
// @Scheduled yra Spring infrastruktūra, JUnit jos nepaleido.
// Kviečiame metodus tiesiogiai kaip paprastus Java metodus ir tikriname:
//   - ar dedup guard'as tikrai blokuoja pakartotinus pranešimus
//   - ar žinutės tekstas atitinka verslo taisyklę (singular/plural)
//   - ar savo gimtadienio vartotojas neinformuojamas apie save
//
// Laiko priklausomybė (LocalDate.now(), LocalDateTime.now()):
//   Servisas kviečia šiuos metodus viduje — negalime jų mock'inti tiesiogiai.
//   Vietoje to naudojame anyInt() ir any() Mockito matcherius:
//   mock'as grąžins duomenis nepriklausomai nuo to, kokia data yra šiandien.
@ExtendWith(MockitoExtension.class)
class ScheduledJobServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private FamilyMemberRepository familyMemberRepository;
    @Mock private EventRepository eventRepository;
    @Mock private FamilyInviteRepository familyInviteRepository;
    @Mock private TaskRepository taskRepository;
    @Mock private NotificationService notificationService;
    @Mock private NotificationRepository notificationRepository;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private EmailService emailService;

    @InjectMocks
    private ScheduledJobService scheduledJobService;

    // ── Test 1 ────────────────────────────────────────────────────────────────
    // Dedup guard: jei BIRTHDAY_REMINDER pranešimas jau išsiųstas šiandien —
    // naujas neturėtų būti kuriamas.
    // Apsaugo nuo dublikatų jei job'as paleidžiamas kelis kartus (pvz. perkrovus serverį).
    @Test
    void sendBirthdayReminders_whenAlreadySentToday_doesNotSendAgain() {
        // Arrange
        Family family = Family.builder().id(10L).build();
        User birthdayUser = User.builder().id(1L).displayName("Alice").family(family).build();
        User recipient    = User.builder().id(2L).family(family).build();

        when(userRepository.findUsersWithBirthdayOn(anyInt(), anyInt())).thenReturn(List.of(birthdayUser));
        when(familyMemberRepository.findMembersWithBirthdayOn(anyInt(), anyInt())).thenReturn(List.of());
        when(userRepository.findAllByFamilyId(10L)).thenReturn(List.of(birthdayUser, recipient));

        // Dedup: pranešimas recipient'ui jau buvo išsiųstas šiandien
        when(notificationRepository
                .existsByRecipientIdAndTypeAndRelatedEntityTypeAndRelatedEntityIdAndCreatedAtAfter(
                        eq(2L), eq(NotificationType.BIRTHDAY_REMINDER), eq("USER"), eq(1L), any()))
                .thenReturn(true);

        // Act
        scheduledJobService.sendBirthdayReminders();

        // Assert — dedup suveikė, pranešimas nekurtas
        verify(notificationService, never()).createNotification(any(), any(), any(), any(), any());
    }

    // ── Test 2 ────────────────────────────────────────────────────────────────
    // Verslo taisyklė: gimtadienio žmogus NEGAUNA pranešimo apie savo paties gimtadienį.
    // Kai šeimoje yra 2 nariai — pranešimas siunčiamas tik kitam, ne sau.
    @Test
    void sendBirthdayReminders_doesNotNotifyBirthdayPersonAboutOwnBirthday() {
        // Arrange
        Family family     = Family.builder().id(10L).build();
        User birthdayUser = User.builder().id(1L).displayName("Alice").family(family).build();
        User otherMember  = User.builder().id(2L).family(family).build();

        when(userRepository.findUsersWithBirthdayOn(anyInt(), anyInt())).thenReturn(List.of(birthdayUser));
        when(familyMemberRepository.findMembersWithBirthdayOn(anyInt(), anyInt())).thenReturn(List.of());
        when(userRepository.findAllByFamilyId(10L)).thenReturn(List.of(birthdayUser, otherMember));
        // Pranešimas dar neišsiųstas šiandien
        when(notificationRepository
                .existsByRecipientIdAndTypeAndRelatedEntityTypeAndRelatedEntityIdAndCreatedAtAfter(
                        any(), any(), any(), any(), any()))
                .thenReturn(false);

        // Act
        scheduledJobService.sendBirthdayReminders();

        // Assert — tik otherMember gauna pranešimą, birthdayUser — NE
        verify(notificationService, times(1)).createNotification(
                eq(otherMember), eq(NotificationType.BIRTHDAY_REMINDER), any(), any(), any());
        verify(notificationService, never()).createNotification(
                eq(birthdayUser), any(), any(), any(), any());
    }

    // ── Test 3 ────────────────────────────────────────────────────────────────
    // FamilyMember (be paskyros) gimtadienis: visi šeimos nariai informuojami.
    // Skiriasi nuo User gimtadienio — nėra "savęs praleidimo" logikos.
    @Test
    void sendBirthdayReminders_familyMemberBirthday_notifiesAllFamilyUsers() {
        // Arrange
        Family family = Family.builder().id(10L).build();
        FamilyMember member = FamilyMember.builder().id(5L).name("Grandma").family(family).build();
        User user1 = User.builder().id(1L).family(family).build();
        User user2 = User.builder().id(2L).family(family).build();

        when(userRepository.findUsersWithBirthdayOn(anyInt(), anyInt())).thenReturn(List.of());
        when(familyMemberRepository.findMembersWithBirthdayOn(anyInt(), anyInt())).thenReturn(List.of(member));
        when(userRepository.findAllByFamilyId(10L)).thenReturn(List.of(user1, user2));
        when(notificationRepository
                .existsByRecipientIdAndTypeAndRelatedEntityTypeAndRelatedEntityIdAndCreatedAtAfter(
                        any(), any(), any(), any(), any()))
                .thenReturn(false);

        // Act
        scheduledJobService.sendBirthdayReminders();

        // Assert — abu šeimos nariai gauna pranešimą
        verify(notificationService, times(2)).createNotification(
                any(), eq(NotificationType.BIRTHDAY_REMINDER), any(), eq("FAMILY_MEMBER"), eq(5L));
    }

    // ── Test 4 ────────────────────────────────────────────────────────────────
    // Dedup guard: EVENT_REMINDER jau išsiųstas šiam renginiui → nebekuriamas.
    // Svarbu nes job'as veikia kas 15 min — be guard'o tas pats renginys gautų ~4 pranešimus per valandą.
    @Test
    void sendEventReminders_whenAlreadySent_doesNotSendAgain() {
        // Arrange
        Family family = Family.builder().id(10L).build();
        Event event   = Event.builder().id(1L).title("Team meeting").family(family).build();
        User recipient = User.builder().id(1L).build();

        when(eventRepository.findAllByStartsAtBetween(any(), any())).thenReturn(List.of(event));
        when(userRepository.findAllByFamilyId(10L)).thenReturn(List.of(recipient));
        // Dedup: pranešimas jau išsiųstas šiam renginiui
        when(notificationRepository.existsByRecipientIdAndTypeAndRelatedEntityTypeAndRelatedEntityId(
                eq(1L), eq(NotificationType.EVENT_REMINDER), eq("EVENT"), eq(1L)))
                .thenReturn(true);

        // Act
        scheduledJobService.sendEventReminders();

        // Assert — dedup suveikė
        verify(notificationService, never()).createNotification(any(), any(), any(), any(), any());
    }

    // ── Test 5 ────────────────────────────────────────────────────────────────
    // Teigiamas testas: EVENT_REMINDER siunčiamas kai dar nebuvo išsiųstas.
    // Pranešimo žinutėje turi būti renginio pavadinimas.
    @Test
    void sendEventReminders_whenNotSentYet_sendsNotificationWithEventTitle() {
        // Arrange
        Family family = Family.builder().id(10L).build();
        Event event   = Event.builder().id(1L).title("Family dinner").family(family).build();
        User recipient = User.builder().id(1L).build();

        when(eventRepository.findAllByStartsAtBetween(any(), any())).thenReturn(List.of(event));
        when(userRepository.findAllByFamilyId(10L)).thenReturn(List.of(recipient));
        when(notificationRepository.existsByRecipientIdAndTypeAndRelatedEntityTypeAndRelatedEntityId(
                any(), any(), any(), any())).thenReturn(false);

        // Act
        scheduledJobService.sendEventReminders();

        // Assert — pranešimas išsiųstas, žinutėje yra renginio pavadinimas
        verify(notificationService).createNotification(
                eq(recipient),
                eq(NotificationType.EVENT_REMINDER),
                contains("Family dinner"),
                eq("EVENT"),
                eq(1L));
    }

    // ── Test 6 ────────────────────────────────────────────────────────────────
    // Dedup guard: OVERDUE_TASK_REMINDER jau išsiųstas šiandien → nebekuriamas.
    // Vartotojas neturėtų gauti kelių "turite pavėluotų užduočių" pranešimų per dieną.
    @Test
    void sendOverdueTaskReminders_whenAlreadySentToday_doesNotSendAgain() {
        // Arrange
        Family family = Family.builder().id(10L).build();
        TaskItem task = TaskItem.builder().id(1L).family(family).build();
        User recipient = User.builder().id(1L).build();

        when(taskRepository.findAllByDueDateBeforeAndStatusNot(any(), any())).thenReturn(List.of(task));
        when(userRepository.findAllByFamilyId(10L)).thenReturn(List.of(recipient));
        // Dedup: pranešimas jau išsiųstas šiandien
        when(notificationRepository.existsByRecipientIdAndTypeAndCreatedAtAfter(
                eq(1L), eq(NotificationType.OVERDUE_TASK_REMINDER), any()))
                .thenReturn(true);

        // Act
        scheduledJobService.sendOverdueTaskReminders();

        // Assert
        verify(notificationService, never()).createNotification(any(), any(), any(), any(), any());
    }

    // ── Test 7 ────────────────────────────────────────────────────────────────
    // Žinutės formatas: 1 užduotis → "task" (vienaskaita).
    // Gramatiškai teisingas pranešimas — svarbus UX detalė.
    @Test
    void sendOverdueTaskReminders_withOneOverdueTask_usesSingularWord() {
        // Arrange
        Family family = Family.builder().id(10L).build();
        TaskItem task = TaskItem.builder().id(1L).family(family).build();
        User recipient = User.builder().id(1L).build();

        when(taskRepository.findAllByDueDateBeforeAndStatusNot(any(), any())).thenReturn(List.of(task));
        when(userRepository.findAllByFamilyId(10L)).thenReturn(List.of(recipient));
        when(notificationRepository.existsByRecipientIdAndTypeAndCreatedAtAfter(any(), any(), any()))
                .thenReturn(false);

        // Act
        scheduledJobService.sendOverdueTaskReminders();

        // Assert — "task" ne "tasks"
        verify(notificationService).createNotification(
                eq(recipient),
                eq(NotificationType.OVERDUE_TASK_REMINDER),
                eq("Your family has 1 overdue task that need attention."),
                eq("FAMILY"),
                eq(10L));
    }

    // ── Test 8 ────────────────────────────────────────────────────────────────
    // Žinutės formatas: 2+ užduotys → "tasks" (daugiskaita).
    @Test
    void sendOverdueTaskReminders_withMultipleOverdueTasks_usesPluralWord() {
        // Arrange
        Family family = Family.builder().id(10L).build();
        TaskItem task1 = TaskItem.builder().id(1L).family(family).build();
        TaskItem task2 = TaskItem.builder().id(2L).family(family).build();
        User recipient = User.builder().id(1L).build();

        when(taskRepository.findAllByDueDateBeforeAndStatusNot(any(), any()))
                .thenReturn(List.of(task1, task2));
        when(userRepository.findAllByFamilyId(10L)).thenReturn(List.of(recipient));
        when(notificationRepository.existsByRecipientIdAndTypeAndCreatedAtAfter(any(), any(), any()))
                .thenReturn(false);

        // Act
        scheduledJobService.sendOverdueTaskReminders();

        // Assert — "tasks" ne "task"
        verify(notificationService).createNotification(
                eq(recipient),
                eq(NotificationType.OVERDUE_TASK_REMINDER),
                eq("Your family has 2 overdue tasks that need attention."),
                eq("FAMILY"),
                eq(10L));
    }
}

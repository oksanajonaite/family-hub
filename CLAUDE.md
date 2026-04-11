# Family Hub — Claude Context Document

This file exists so Claude can quickly understand the project without needing prior conversation history.

---

## What is this project?

A family management web application built as a student portfolio project (~8 months Java experience). The goal is a working MVP that demonstrates Spring Boot skills: auth, roles, CRUD, relationships, security, notifications.

**Student level** — explain things clearly, use comments in Lithuanian, apply DRY/SOLID but avoid enterprise over-engineering.

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Spring Boot 3, Spring MVC |
| Security | Spring Security — session-based + remember-me (NO JWT) |
| Persistence | Spring Data JPA, Hibernate, PostgreSQL |
| Frontend | Thymeleaf + Bootstrap 5 (NO React/Angular) |
| Build | Maven |
| Java | 21 |
| DB name | `familyapp_db` |

---

## Key Business Rules

- 1 User belongs to exactly 1 Family
- Roles: `PARENT` (family admin), `KID` (limited), `ADMIN` (platform-level, assigned via DB only)
- `ADMIN` is NOT a family member — no family, no tasks, no events. Only sees admin panel.
- `FamilyMember` = person without an account (e.g. toddler). PARENT manages on their behalf.
- `Pet` = animal in the family. No account. Can be event participant.
- Both `FamilyMember` and `Pet` can be attached to Events as participants.
- `FamilyMember` can be assigned tasks (PARENT manages for them).
- Private events visible only to creator.
- Invite codes: 12 chars, valid 7 days, reusable (never marked as used).

---

## What's Done (v1 — Complete)

### Auth
- Register (email, displayName, password) → always PARENT role
- Login with remember-me (30 days)
- Password reset via console token (no email yet — planned v2)
- Files: `AuthController`, `AuthService`, `PasswordResetService`, `PasswordResetToken`

### Family
- Create family, join via invite code
- Generate new invite codes
- Files: `FamilyController`, `FamilyService`, `Family`, `FamilyInvite`

### Tasks
- CRUD with priority (LOW/MEDIUM/HIGH), status (TODO/IN_PROGRESS/DONE)
- Assign to User (with account) OR FamilyMember (without account)
- KID can only change status of their own assigned tasks
- Notification sent when task assigned to another User
- Files: `TaskController`, `TaskService`, `TaskItem`, `TaskMapper`

### Events / Calendar
- CRUD with recurrence (NONE/DAILY/WEEKLY)
- Participants: Users + Pets + FamilyMembers
- Private events (PARENT only)
- Files: `EventController`, `EventService`, `Event`, `EventParticipant`, `EventMapper`

### Pets
- CRUD: name, type (DOG/CAT/RABBIT/BIRD/FISH/OTHER), dateOfBirth
- Files: `PetController`, `PetService`, `Pet`, `PetType`

### Family Members (without account)
- CRUD: name, dateOfBirth
- Used as task assignee or event participant
- Files: `FamilyMemberController`, `FamilyMemberService`, `FamilyMember`

### Notifications
- In-app notification list at `/notifications`
- Badge on dashboard showing unread count
- Triggered automatically when task is assigned to another User
- `createNotification()` method ready for other triggers (scheduler, etc.)
- Files: `NotificationController`, `NotificationService`, `Notification`

### Admin Panel
- Accessible only with `ADMIN` role (set via DB: `UPDATE users SET role = 'ADMIN' WHERE email = '...'`)
- Shows: total users, total families, users without family, total notifications
- Tables: all users (with role/family), all families
- Files: `AdminController`, `AdminService`

### Password Reset
- User enters email → token logged to IntelliJ console
- Token URL: `http://localhost:8080/reset-password?token=...`
- Token valid 1 hour, single-use
- Files: `PasswordResetService`, `PasswordResetToken`, `PasswordResetTokenRepository`
- **Future**: replace `log.info()` in `PasswordResetService.createResetToken()` with `JavaMailSender`

---

## Package Structure

```
com.familyhub/
├── config/           SecurityConfig, RememberMeConfig
├── controller/       Auth, Dashboard, Family, Task, Event, Pet, FamilyMember,
│                     Notification, Admin
├── service/          Auth, Family, Task, Event, Pet, FamilyMember,
│                     Notification, Admin, PasswordReset
├── repository/       User, Family, FamilyInvite, Task, Event, EventParticipant,
│                     Pet, FamilyMember, Notification, PasswordResetToken
├── entity/           User, Family, FamilyInvite, TaskItem, Event, EventParticipant,
│                     Pet, FamilyMember, Notification, PasswordResetToken
│   └── enums/        Role, TaskStatus, TaskPriority, PetType, ParticipantType,
│                     RecurrenceType, NotificationType
├── dto/
│   ├── request/      auth/, family/, task/, event/, pet/, member/, notification/
│   └── response/     auth/, family/, task/, event/, notification/
├── mapper/           Auth, Family, Task, Event, Notification, FamilyInvite
├── security/         CustomUserDetails, CustomUserDetailsService
└── exception/        AccessDeniedException, UserAlreadyExistsException,
                      UserAlreadyInFamilyException, FamilyNotFoundException,
                      TaskNotFoundException, EventNotFoundException,
                      InvalidInviteCodeException, InvalidTokenException,
                      GlobalExceptionHandler
```

---

## Security Rules (SecurityConfig)

| Route | Access |
|-------|--------|
| `/login`, `/register`, `/forgot-password`, `/reset-password` | Public |
| `/admin/**` | ADMIN only |
| `/family/create`, `/family/invite/**`, `/tasks/assign/**` | PARENT only |
| Everything else | Any authenticated user |

---

## DB Setup

```sql
-- Create DB
CREATE DATABASE familyapp_db;

-- Assign ADMIN role (no UI registration for ADMIN)
UPDATE users SET role = 'ADMIN' WHERE email = 'admin@example.com';

-- Fix role check constraint if needed
ALTER TABLE users DROP CONSTRAINT users_role_check;
ALTER TABLE users ADD CONSTRAINT users_role_check
    CHECK (role IN ('PARENT', 'KID', 'ADMIN'));
```

---

## Roadmap Status

### v1 ✅ Complete
Auth, Family, Tasks, Events, Pets, FamilyMembers, Notifications, Admin panel, Password reset

### v2 ⬜ Planned
WebSockets, email notifications (JavaMailSender), pet health tracking, human health reminders,
calendar enhancements (weather, holidays, birthday events), user profiles (avatar, dark mode)

### v3 ⬜ Planned
Receipt scanning (Google Vision API), smart shopping list, budget management

### v4 ⬜ Planned
Audit log, user blocking, full admin governance

---

## Working Style Notes

- Write comments in Lithuanian explaining *why*, not just *what*
- Apply DRY/SOLID but keep it simple — student project, not enterprise
- Label suggestions: P0 (must), P1 (if time), P2 (post-course)
- Short, practical answers — no excessive theory
- When asked for "plan" → weekly plan with hours
- Always check family isolation in services (filter by familyId)

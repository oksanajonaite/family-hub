# Family Hub — Claude Context Document

This file exists so Claude can quickly understand the project without needing prior conversation history.

---

## What is this project?

A family management web application built as a student portfolio project (~8 months Java experience). The goal is a working MVP that demonstrates Spring Boot skills: auth, roles, CRUD, relationships, security, notifications.

**Student level** — explain things clearly, write comments in English, apply DRY/SOLID but avoid enterprise over-engineering.

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Spring Boot 3, Spring MVC |
| Security | Spring Security — session-based + remember-me (NO JWT) |
| Persistence | Spring Data JPA, Hibernate, PostgreSQL |
| Frontend | Thymeleaf + Bootstrap 5 (NO React/Angular) |
| Build | Maven |
| Java | 17 |
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
- Register (email, displayName, password, dateOfBirth optional) → always PARENT role
- Login with remember-me (30 days)
- Password reset via console token (no email yet — planned v2)
- Files: `AuthController`, `AuthService`, `PasswordResetService`, `PasswordResetToken`

### Family
- Create family, join via invite code
- Two separate invite codes: one for PARENT role, one for KID role — joining auto-assigns correct role
- Family page shows: registered members + FamilyMembers (no account) + Pets
- PARENT can remove registered member from family (cannot remove self)
- Files: `FamilyController`, `FamilyService`, `Family`, `FamilyInvite`

### Tasks
- CRUD with priority (LOW/MEDIUM/HIGH), status (TODO/IN_PROGRESS/DONE)
- **Multi-assign**: multiple Users and/or FamilyMembers via checkboxes (`@ManyToMany`, join tables `task_assigned_users`, `task_assigned_members`)
- KID can only change status of tasks where they are in `assignedUsers`
- Notification sent to each assigned User (except self)
- Files: `TaskController`, `TaskService`, `TaskItem`, `TaskMapper`

### Events / Calendar
- CRUD with recurrence (NONE/DAILY/WEEKLY)
- Participants: Users + Pets + FamilyMembers via unified checkbox list (prefixed strings: `USER_42`, `PET_7`, `MEMBER_15`)
- Private events (PARENT only)
- Participant names shown in event list (`EventResponse.participantNames`)
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
├── config/           SecurityConfig
├── controller/       Auth, Dashboard, Family, Task, Event, Pet, FamilyMember,
│                     Notification, Admin, GlobalModelAdvice
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
│   └── response/     auth/, family/, task/, event/, notification/, CalendarDay
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

### v1.1 ✅ Complete (UI improvements)
- Shared navbar via Thymeleaf fragment (`templates/fragments/navbar.html`) — replaces copy-paste navbars in all templates
- `GlobalModelAdvice` — auto-injects `unreadCount` and `today` for all authenticated controllers (uses `assignableTypes`, NOT applied to AuthController)
- `User.dateOfBirth` — optional field added to entity, RegisterRequest, AuthService, register.html
- Date inputs fixed: `min="1926-01-01"` and dynamic `max` (today) in pets/form.html, members/form.html, register.html

### v1.2 ✅ Complete (UX & role improvements)
- **Role-based invite codes**: two separate codes per family — one for PARENT, one for KID. `FamilyInvite` now has `role` field. Joining via code auto-assigns the correct role. DB: `ALTER TABLE family_invites ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'PARENT'`
- **Family page** now shows: registered members (with remove button for PARENT), FamilyMembers (without account), Pets — all in separate cards
- **PARENT can remove family member** from family (`/family/members/{id}/remove`): sets `user.family = null`. Cannot remove self.
- **Event participants** — replaced two separate multi-selects with a single unified checkbox list. Prefixed string pattern: `USER_42`, `PET_7`, `MEMBER_15`. Service parses prefix to determine entity type.
- **Task multi-assign** — replaced single assignee with `@ManyToMany` to both `User` and `FamilyMember`. Same prefixed string pattern (`USER_42`, `MEMBER_15`). Join tables: `task_assigned_users`, `task_assigned_members`.
- **Events index** — participant names now shown in event list (`EventResponse.participantNames`)
- **Edit forms** — checkboxes correctly pre-checked on edit: `participantIds` and `assigneeIds` added as separate model attributes

### v1.3 ✅ Complete (Calendar Dashboard)
- **`DashboardController`** completely rewritten — now injects `EventService` + `TaskService`, builds monthly calendar grid, supports `?year=X&month=Y` navigation params
- **`CalendarDay`** record — new DTO in `dto/response/`: holds `date`, `events`, `tasks`, `currentMonth`, `today` flags
- **`EventService.getVisibleFamilyEventsBetween()`** — new method filtering events by date range for calendar
- **`TaskService.getFamilyTasksBetween()`** — new method; calls `.size()` on each lazy collection inside `@Transactional` to avoid `LazyInitializationException` after transaction closes. `@EntityGraph` NOT used — causes `MultipleBagFetchException` when two `List` collections fetched simultaneously
- **`TaskRepository`** — new method `findAllByFamilyIdAndDueDateBetween()`
- **`dashboard.html`** — monthly calendar grid: events (blue pills), tasks (yellow pills), participants shown under each pill, today cell highlighted with green border
- **Navbar** — added Calendar link after Family
- **CSS note**: `border-collapse: separate` required for `border-radius` on `<td>` — Bootstrap default `collapse` prevents it
- **Month name** formatted with `Locale.ENGLISH` in controller, not in Thymeleaf — avoids JVM locale (Lithuanian) issue

### v1.4 ✅ Complete (UI polish, UX fixes, code cleanup)

#### Admin panel restyled
- `admin/index.html` — replaced Bootswatch with app CSS stack (`variables.css`, `components.css`). Uses `widget-card`, `widget-header deep-clay`, `text-clay`, role badges with rgba colors matching PARENT/KID styles. Added `xmlns:sec`.
- `DashboardController` — ADMIN branch now returns `redirect:/admin` instead of rendering `dashboard.html`. No intermediate dashboard page.
- `dashboard.html` — removed the `sec:authorize="hasRole('ADMIN')"` block (no longer needed).

#### Auth pages restyled
- `auth/login.html`, `register.html`, `forgot-password.html`, `reset-password.html`, `reset-password-error.html` — all replaced Bootswatch with app CSS. Use `widget-card` + `widget-header deep-clay`, `btn-terracotta`, earth-tone palette.

#### PARENT/KID role badges colored (family page)
- `family/index.html` — PARENT badge: olive green (`rgba(107,124,74,0.15)`, `var(--color-olive)`). KID badge: subtle brown (`rgba(90,62,46,0.10)`, `var(--text-muted)`).

#### Event form: optional time + recurrence fix
- `CreateEventRequest` / `UpdateEventRequest` — split `LocalDateTime startsAt` → `LocalDate startDate` (@NotNull) + `LocalTime startTime` (optional). Same for `endsAt` → `endDate` + `endTime`. Both use `@DateTimeFormat`.
- `Event` entity — `ends_at` column made nullable (`@Column(nullable = false)` removed). DB: `ALTER TABLE events ALTER COLUMN ends_at DROP NOT NULL`
- `EventMapper` — added `@Mapping(target = "startsAt/endsAt", ignore = true)`, source fields listed in `ignoreUnmappedSourceProperties`
- `EventService` — added `combineDateTime()` and `buildEndsAt()` helpers. Fixed `getVisibleFamilyEventsBetween()`: fetches non-recurring events in range + virtually expands all recurring events via `expandRecurring()`. Recurring events are NOT duplicated in DB — expansion is in-memory only.
- `EventRepository` — added `findAllByFamilyIdAndRecurrenceTypeNot()` for fetching all recurring events regardless of start date.
- `EventController` — `createForm` uses 10-arg constructor with nulls; `editForm` splits `LocalDateTime` back to `LocalDate`/`LocalTime` for form pre-fill.

#### Flatpickr custom date/time picker
- `static/css/flatpickr-earth.css` — **new file**. Custom Flatpickr theme matching app earth tones: header deep-clay (`#5A3E2E`), selected day deep-clay, today terracotta (`#B8693A`), hover terracotta light. Calendar border-radius 10px.
- Applied to: `events/form.html`, `tasks/form.html`, `members/form.html`, `pets/form.html`, `auth/register.html`.
- All date inputs changed from `type="date"` to `type="text"`, Flatpickr initialized with `dateFormat: "Y-m-d"`. Time inputs: `enableTime: true, noCalendar: true, time_24hr: true, dateFormat: "H:i"`.
- Date of birth pickers: `minDate: "1926-01-01"`, `maxDate: "today"`.

#### Select arrow and priority/recurrence colors
- `static/css/components.css` — added custom clay-brown SVG chevron for `.form-select` (overrides Bootstrap's default blue arrow).
- `events/form.html` — recurrence select colored via JS: NONE→brown, DAILY→olive, WEEKLY→terracotta.
- `tasks/form.html` — priority select colored via JS: HIGH→terracotta, MEDIUM→olive, LOW→subtle brown.

#### Custom confirm modal (replaces browser confirm())
- `templates/fragments/confirm-modal.html` — **new file**. Bootstrap modal with `fa-triangle-exclamation` terracotta icon. JS intercepts `form[data-confirm]` submit, shows modal, submits on confirm. Delete button terracotta, Cancel btn-outline-earth.
- All delete/remove forms in `tasks/`, `events/`, `members/`, `pets/`, `family/` index templates: replaced `onclick="return confirm(...)"` with `data-confirm="..."` attribute on the `<form>`. Added `<div th:replace="~{fragments/confirm-modal :: confirm-modal}"></div>` before `</body>`.

#### Code comments translated to English
- All Lithuanian comments in Java files replaced with English. Files updated: `RegisterRequest`, `ResetPasswordRequest`, `CalendarDay`, `ParticipantType`, `CreateEventRequest`, `UpdateEventRequest`, `CreateTaskRequest`, `UpdateTaskRequest`, `EventResponse`, `EventParticipant`, `FamilyMember`, `PasswordResetToken`, `User`.

### v2 ⬜ Planned
WebSockets, email notifications (JavaMailSender), pet health tracking, human health reminders,
calendar enhancements (weather, holidays, birthday events), user profiles (avatar, dark mode)

### v3 ⬜ Planned
Receipt scanning (Google Vision API), smart shopping list, budget management

### v4 ⬜ Planned
Audit log, user blocking, full admin governance

---

## GlobalModelAdvice — important notes

- Located in `controller/GlobalModelAdvice.java`
- Uses `@ControllerAdvice(assignableTypes = {...})` — explicitly lists which controllers it applies to
- `AuthController` is NOT in the list — login/register pages do not run this advice
- When adding a new authenticated controller, add it to `assignableTypes`
- Injects: `unreadCount` (for PARENT/KID only), `today` (LocalDate as String for all)

## Thymeleaf Navbar Fragment

- Located in `templates/fragments/navbar.html`
- Used in all authenticated templates: `<nav th:replace="~{fragments/navbar :: navbar}"></nav>`
- PARENT/KID: blue (`bg-primary`), links: Family → **Calendar** → Tasks → Events → Pets → Members → Notifications+badge
- ADMIN: dark (`bg-dark`), links: Admin Panel only
- Both: username + Logout on the right
- Templates that use `sec:authorize` outside the navbar still need `xmlns:sec` in `<html>`

## Confirm Modal Fragment

- Located in `templates/fragments/confirm-modal.html`
- Include in every template that has delete/remove forms: `<div th:replace="~{fragments/confirm-modal :: confirm-modal}"></div>` (place before `</body>`, after Bootstrap JS)
- Mark the `<form>` (not the button) with `data-confirm="Your message here"` — JS intercepts submit and shows modal
- Do NOT use `onclick="return confirm(...)"` — browser native dialogs are unstyled

## Flatpickr Date/Time Picker

- CDN: `https://cdn.jsdelivr.net/npm/flatpickr` (CSS + JS)
- Custom theme: `static/css/flatpickr-earth.css` (loaded after Flatpickr CDN CSS)
- Date inputs: `type="text"`, `dateFormat: "Y-m-d"` → Spring MVC binds via `@DateTimeFormat(iso = ISO.DATE)`
- Time inputs: `type="text"`, `enableTime: true, noCalendar: true, time_24hr: true, dateFormat: "H:i"` → Spring MVC binds via `@DateTimeFormat(pattern = "HH:mm")`
- Date of birth fields: add `minDate: "1926-01-01"`, `maxDate: "today"`

## Calendar Dashboard — important notes

- `DashboardController` builds `List<List<CalendarDay>>` weeks passed to Thymeleaf
- Calendar range: `previousOrSame(MONDAY)` of first day → `nextOrSame(SUNDAY)` of last day
- Month name: formatted with `Locale.ENGLISH` in controller → passed as `monthLabel` String attribute
- `MultipleBagFetchException` trap: never use `@EntityGraph` with two `List` (`Bag`) collections simultaneously — use `.size()` inside `@Transactional` to force-initialize lazy collections instead
- CSS: `border-collapse: separate` on `<table>` is required for `border-radius` to work on `<td>` cells

---

## Working Style Notes

- Write all comments in English explaining *why*, not just *what*
- Apply DRY/SOLID but keep it simple — student project, not enterprise
- Label suggestions: P0 (must), P1 (if time), P2 (post-course)
- Short, practical answers — no excessive theory
- When asked for "plan" → weekly plan with hours
- Always check family isolation in services (filter by familyId)

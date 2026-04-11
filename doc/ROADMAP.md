# Family Hub — Roadmap

This document outlines the full planned scope of Family Hub, from the initial MVP to long-term smart features.

Status legend: ✅ Done · 🔄 In progress · ⬜ Planned

---

## v1 — MVP (Core Foundation)

> Goal: A working family management app with auth, family, tasks, and calendar.

### Authentication
- ✅ Register with email, display name, password (BCrypt)
- ✅ Login with Remember Me (30 days)
- ✅ Session-based security (Spring Security)
- ⬜ Password reset via email (expiring token)

### Family Management
- ✅ Create a family
- ✅ Join via 12-character invite code (valid 7 days)
- ✅ View family members
- ⬜ PARENT can remove family members

### Tasks
- ✅ Create / edit / delete tasks
- ✅ Priorities: `LOW` `MEDIUM` `HIGH`
- ✅ Statuses: `TODO` → `IN_PROGRESS` → `DONE`
- ✅ Assign task to one family member
- ✅ `completedAt` timestamp on DONE
- ✅ Role-based access (PARENT manages, KID updates own status)

### Calendar / Events
- ✅ Create / edit / delete events
- ✅ Start & end time, recurrence (none / daily / weekly)
- ✅ Private events (visible only to creator)
- ✅ Participants: family members and/or pets
- ✅ Role-based access (creator or PARENT can edit/delete)

### Pets
- ✅ Pet entity linked to family
- ✅ Pet as event participant

### Notifications
- ✅ Notification entity and repository (infrastructure ready)
- ⬜ In-app notification display (UI)

### Admin
- ⬜ ADMIN role panel — view all users, families, basic statistics
- ⬜ ADMIN assigned manually via database

---

## v2 — Extended Features

> Goal: Richer experience — health tracking, real-time updates, email alerts.

### Real-Time Synchronization
- ⬜ WebSockets + STOMP integration
- ⬜ Calendar changes broadcast instantly to all family members
- ⬜ Task status updates reflected in real time

### Email Notifications
- ⬜ JavaMailSender integration
- ⬜ Email on task assignment
- ⬜ Email on upcoming event reminder
- ⬜ Configurable per user (opt-in/opt-out)

### Enhanced Notifications
- ⬜ Notification types: `BIRTHDAY` `HEALTH_REMINDER` `PET_HEALTH` `EVENT_REMINDER` `SYSTEM`
- ⬜ 7-day notification history
- ⬜ Mark as read / mark all as read

### Pet Health Tracking
- ⬜ Pet types: `DOG` `CAT` `RABBIT` `BIRD` `FISH` `OTHER`
- ⬜ Custom photo or icon (Cloudinary)
- ⬜ Health records: vaccinations, flea/tick tablets, dental cleaning, bathing
- ⬜ Configurable health cycles (e.g. every 3 months)
- ⬜ Automatic reminders when procedure is due
- ⬜ Automatic birthday reminders

### Human Health Reminders
- ⬜ Reminder types: `DOCTOR` `DENTIST` `VISION` `VACCINE` `BLOOD_TEST` `OTHER`
- ⬜ Configurable recurrence cycles
- ⬜ PARENT sees own + children's reminders
- ⬜ KID sees only own reminders

### Calendar Enhancements
- ⬜ Event types with icons: `DOCTOR` `DENTIST` `SCHOOL` `BIRTHDAY` `HIKE` `TRIP` `PARTY` `SHOPPING` `SPORT` `OTHER`
- ⬜ Weather forecast per day (OpenWeatherMap API, cached hourly)
- ⬜ Public holidays integration (Nager.Date API)
- ⬜ Automatic birthday events generated from user date of birth
- ⬜ Soft delete — events restorable within 7 days

### Task Enhancements
- ⬜ Assign task to multiple family members
- ⬜ Drag & drop task onto calendar date (SortableJS)
- ⬜ Status: `TODO` → `SCHEDULED` → `DONE` (SCHEDULED set when dragged to calendar)
- ⬜ Private tasks (PARENT only)
- ⬜ Soft delete with restore

### User Profile
- ⬜ Custom avatar photo or pre-made icons (Cloudinary)
- ⬜ Date of birth field (used for birthday reminders)
- ⬜ Dark / light mode toggle

### Automated Processes (Spring Scheduler)
- ⬜ Birthday checks — daily at 08:00 (3-day advance reminder)
- ⬜ Health reminders — daily at 08:00 (7-day advance reminder)
- ⬜ Pet health reminders — daily at 08:00
- ⬜ Delete old notifications (7d+) — daily at midnight
- ⬜ Delete expired password reset tokens — daily at midnight

---

## v3 — Smart Features

> Goal: AI-assisted spending tracking, shopping intelligence, budget insights.

### Receipt Scanning
- ⬜ User photographs a receipt
- ⬜ Google Vision API extracts shop name, date, products, quantities, prices
- ⬜ Keyword-based categorization engine (JSON dictionary)
- ⬜ Photo deleted immediately after processing
- ⬜ Rate limiting via Bucket4j (protect against excessive API calls)
- ⬜ Spring Events chain: `receipt scanned → categorization → statistics update → cache invalidation → WebSocket broadcast`

**Spending categories:**
- Food: `FOOD_HEALTHY` `FOOD_SWEETS` `FOOD_FASTFOOD` `FOOD_ALCOHOL` `FOOD_DRINKS`
- Other: `MEDICINE` `HYGIENE` `PETS` `ENTERTAINMENT` `CLOTHING` `HOUSEHOLD`

### Smart Shopping List
- ⬜ Manual product entry
- ⬜ System learns from receipt history (avg purchase interval per product)
- ⬜ Suggestions when restock time approaches: *"You buy milk every 7 days — today is day 6"*
- ⬜ One-tap to add suggestion to shopping list
- ⬜ System learns from dismissed suggestions (reduces frequency)

### Budget Management
- ⬜ Monthly spending limits per category
- ⬜ Alerts at 80% and 100% of limit
- ⬜ Budget statistics cached with Caffeine (6-hour TTL)

### Budget Insights (nightly generation)
- ⬜ *"Sweets spending is 40% higher than usual this month"*
- ⬜ *"Every Friday you buy pizza ingredients — today is Thursday!"*
- ⬜ *"Food costs down 15% this month — great job!"*

### Caffeine Cache Strategy
- ⬜ Weather forecast — 1 hour TTL
- ⬜ Public holidays — 24 hours TTL
- ⬜ Budget statistics — 6 hours TTL
- ⬜ Family events — evicted on create/update

---

## v4 — Admin & Governance

> Goal: Platform-level visibility and control.

### Admin Panel (ADMIN role)
- ⬜ View all families and users
- ⬜ Basic platform statistics (user count, family count, active sessions)
- ⬜ Block / unblock users
- ⬜ Delete family (upon PARENT request only)
- ⬜ Audit log — 7-day history of key actions
- ⬜ Delete old audit logs (7d+) — daily at midnight

---

## Out of Scope (Not Planned)

- Mobile native app (iOS / Android)
- Redis (Caffeine sufficient for v1-v3)
- JWT authentication (session-based is sufficient)
- React / Angular frontend
- Multi-language support (Lithuanian i18n — possible post-course)
- Timezone support
- Docker / cloud deployment

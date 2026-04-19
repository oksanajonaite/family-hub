# Family Hub — Roadmap

Status legend: ✅ Done · 🔄 In progress · ⬜ Planned

---

## v1 — MVP (Complete)

> Core foundation: auth, family, tasks, calendar, pets, notifications, admin.

### Authentication
- ✅ Register with email, display name, password (BCrypt)
- ✅ Login with Remember Me (30 days)
- ✅ Session-based security (Spring Security)
- ✅ Optional date of birth field on registration
- ✅ Password reset via expiring token (email via JavaMailSender + Mailpit locally)

### Family Management
- ✅ Create a family
- ✅ Join via 12-character invite code (valid 7 days, reusable)
- ✅ View family members (users with accounts)
- ✅ Two invite codes per family — PARENT code + KID code

### Family Members (without account)
- ✅ Add / edit / delete family members (toddlers, elderly)
- ✅ Assign tasks and events to family members
- ✅ PARENT manages on their behalf

### Tasks
- ✅ CRUD with priorities: LOW / MEDIUM / HIGH
- ✅ Statuses: TODO → IN_PROGRESS → DONE
- ✅ Assign to User or FamilyMember
- ✅ Role-based access (PARENT manages, KID updates own status)
- ✅ Notification sent on task assignment

### Calendar / Events
- ✅ CRUD with start/end time, recurrence (none / daily / weekly)
- ✅ Private events (creator only)
- ✅ Participants: Users + Pets + FamilyMembers
- ✅ Role-based access (creator or PARENT can edit/delete)

### Pets
- ✅ CRUD: name, type (DOG CAT RABBIT BIRD FISH OTHER), date of birth
- ✅ Pet as event participant

### Notifications
- ✅ In-app notifications at /notifications
- ✅ Unread badge on navbar
- ✅ Triggered on task assignment

### Admin Panel
- ✅ Platform stats: users, families, notifications
- ✅ Tables: all users + families

---

## v2 — Quality & Core Extensions

> Goal: Close v1 gaps, add email alerts, background jobs, user profile editing.

### Close v1 Gaps
- ✅ PARENT can remove a registered family member from the family

### Email Notifications (extend existing EmailService)
- ✅ Password reset link via email
- ✅ Email on task assignment (when assigned to another user)
- ✅ Email on upcoming event reminder (day before)
- ✅ Configurable per user — opt-in / opt-out in profile settings

### Enhanced Notifications
- ✅ Notification types: TASK_ASSIGNED · EVENT_REMINDER · BIRTHDAY_REMINDER · SYSTEM
- ✅ Mark all as read button
- ✅ Auto-delete notifications older than 7 days (via scheduler — daily at 01:00)

### Background Jobs (Spring @Scheduled)
- ✅ Birthday reminder — daily at 08:00
- ✅ Event reminder — every 15 min, 50–65 min window
- ✅ Delete old notifications (7d+) — daily at 01:00
- ✅ Delete expired password reset tokens — daily at 02:00
- ✅ Delete expired invite codes — daily at midnight

### User Profile
- ✅ Edit display name and date of birth
- ✅ Change password (requires current password confirmation)

### Calendar Enhancements
- ✅ Event categories with icons (12 types: BIRTHDAY · PARTY · MEDICAL · SCHOOL · SPORT · TRAVEL · FAMILY · WORK · HOLIDAY · SHOPPING · PET · OTHER)
- ✅ Birthday display in calendar and Today/Tomorrow widget

### Task Enhancements
- ✅ Private tasks (visible to PARENT and creator only)

---

## v3 — AWS S3, Media & UX Polish

> Goal: Cloud file storage, avatars, pet profiles, onboarding, weather, holidays.

### AWS S3 Integration
- ⬜ Spring Boot + AWS SDK S3 setup
- ⬜ File upload service — handles upload, URL generation, deletion
- ⬜ Environment-based config (bucket name, region via env vars)

### User Avatars
- ⬜ Upload profile photo (stored in S3)
- ⬜ Avatar shown in navbar, family member list, task assignee, event participants
- ⬜ Fallback to initials avatar when no photo uploaded
- ⬜ Delete old photo from S3 when replaced

### Pet Profiles
- ⬜ Upload pet photo (stored in S3)
- ⬜ Pet photo shown on family page and event participants
- ⬜ Fallback to pet type icon

### Onboarding
- ✅ Welcome card for users without a family (dashboard, auto-hides when family is set)
- ⬜ Empty state illustrations when lists are empty

### Calendar Extras
- ⬜ Weather forecast per day (OpenWeatherMap API, cached 1h with Caffeine)
- ⬜ Public holidays (Nager.Date API, cached 24h)

### Dark Mode
- ⬜ Dark / light toggle saved to user profile
- ⬜ CSS custom properties for theme switching

---

## v4 — Receipt Scanning, Shopping & Budget (AI-assisted)

> Goal: Smart spending tracker powered by Google Vision API and pattern recognition.

### Receipt Scanning
- ⬜ User photographs a receipt (uploaded to S3, deleted after processing)
- ⬜ Google Vision API extracts shop name, date, products, quantities, prices
- ⬜ Rate limiting via Bucket4j (protect against excessive API calls)
- ⬜ Keyword-based categorization engine (JSON dictionary)

**Spending categories:**
- Food: FOOD_HEALTHY · FOOD_SWEETS · FOOD_FASTFOOD · FOOD_ALCOHOL · FOOD_DRINKS
- Other: MEDICINE · HYGIENE · PETS · ENTERTAINMENT · CLOTHING · HOUSEHOLD

### Smart Shopping List
- ⬜ Manual product entry
- ⬜ System learns average purchase interval per product from receipt history
- ⬜ Suggestions when restock time approaches: *"You buy milk every 7 days — today is day 6"*
- ⬜ One-tap to add suggestion to shopping list

### Budget Management
- ⬜ Monthly spending limits per category
- ⬜ Alerts at 80% and 100% of limit
- ⬜ Budget statistics cached with Caffeine (6h TTL)

### Budget Insights (nightly generation)
- ⬜ *"Sweets spending is 40% higher than usual this month"*
- ⬜ *"Every Friday you buy pizza — today is Thursday!"*
- ⬜ *"Food costs down 15% this month — great job!"*

---

## Out of Scope

- WebSockets / real-time sync (page navigation is sufficient for this app type)
- Mobile native app (iOS / Android)
- Redis (Caffeine sufficient)
- JWT (session-based is correct here)
- React / Angular frontend
- Multi-language / i18n
- Timezone support
- Docker / cloud deployment

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
- ✅ Spring Boot + AWS SDK S3 setup
- ✅ File upload service — handles upload, pre-signed URL generation, deletion
- ✅ Environment-based config (bucket name, region via env vars)
- ✅ Private bucket — all files served via pre-signed URLs (1h validity, no public access)
- ✅ IAM user with least-privilege S3-only permissions (`familyhub-backend`)
- ✅ S3 key stored in DB (not URL) — URL generated on demand per request

### User Avatars
- ✅ Upload profile photo (stored in S3 under `avatars/`)
- ✅ Avatar shown in navbar and family page
- ✅ Fallback to initials avatar (navbar) / icon (member list) when no photo uploaded
- ✅ Delete old photo from S3 when replaced

### Pet Profiles
- ✅ Upload pet photo (stored in S3 under `pets/`)
- ✅ Pet photo shown on family page and edit form
- ✅ Fallback to paw icon when no photo uploaded
- ✅ Delete pet photo from S3 on pet deletion (no orphan files)

### Family Member Photos
- ✅ Upload family member photo (stored in S3 under `members/`)
- ✅ Member photo shown on family page and edit form
- ✅ Fallback to user icon when no photo uploaded
- ✅ Delete member photo from S3 on member deletion (no orphan files)

### Unified Photo Architecture
- ✅ `PhotoController` — single controller serving all 3 entity photo types via `/api/photo/{type}/{id}`
- ✅ `PhotoUploadValidator` — shared upload validation utility (DRY across all 3 upload endpoints)
- ✅ 5 MB file size limit with `GlobalExceptionHandler` catching `MaxUploadSizeExceededException`

### Onboarding
- ✅ Welcome card for users without a family (dashboard, auto-hides when family is set)
- ✅ Empty state messages when lists are empty (basic text implementation — sufficient)

### Overdue Tasks
- ✅ Visual "Overdue" badge on task cards and calendar pills when due date has passed and task is not done
- ✅ Daily scheduler job (08:30) — sends one summarised in-app notification per user if family has overdue tasks
- ✅ Dedup guard — one notification per user per day regardless of how many tasks are overdue
- ✅ Manual admin trigger — `POST /admin/jobs/overdue-task-reminders` for demo and testing

### Calendar Extras
- ✅ Public holidays (Nager.Date API, cached 24h with Caffeine)
- 💤 Weather forecast per day — not planned (user can use native weather app; adds API complexity without clear value)

### Dark Mode
- 💤 Dark / light toggle — not planned (warm earth-tone palette would need full redesign; maintenance cost outweighs benefit)

---

## v4 — Receipt Scanning, Shopping & Budget (AI-assisted)

> Goal: Smart spending tracker powered by Gemini Flash Vision and pattern recognition.
> Split into two phases: A (scanning + stats) → B (patterns + smart reminders).

---

### Phase A — Receipt Scanning & Statistics

#### Architecture
- Monolith — same Spring Boot app, new packages: `receipt/`, `spending/`
- Controller → Service (Facade) → ReceiptParsingService / GeminiClient / ReceiptRateLimiterService → Repository
- SRP: each service has one responsibility; DRY: FamilyRequiredInterceptor guards /receipts/** and /spending/**

#### Receipt Scanning
- ✅ User photographs a receipt → processed in-memory (NOT stored in S3 — better for privacy, no temp storage needed)
- 💤 S3 receipt image storage — not needed; image is read → sent to Gemini → discarded in one synchronous step
- 💤 S3 Lifecycle policy — not needed (no S3 receipt storage)
- ✅ **Gemini 2.5 Flash Preview** extracts structured data AND categorizes in one API call (gemini-1.5-flash deprecated)
- ✅ Extracted: shop name, purchase date, line items (name, quantity, unit price), total
- ✅ On processing failure: mark as FAILED, surface error to user (can re-upload)
- ✅ Retry once on FAILED — upload a better photo; retryCount column prevents >1 retry per receipt
- ✅ Rate limiting via Bucket4j — max 5 receipts/hour per user (configurable via application.yaml)
- ✅ Multi-page receipt support — up to 5 photos, results merged (vendorName: first non-null, total: last non-null, items: union)

#### Spending Categories (AI-assigned by Gemini) — 21 categories
- Food: `FOOD_PRODUCE` · `FOOD_DAIRY` · `FOOD_PROTEIN` · `FOOD_BAKERY` · `FOOD_STAPLES` · `FOOD_SNACKS` · `FOOD_DRINKS` · `FOOD_ALCOHOL` · `FOOD_OTHER`
- Health: `MEDICINE` · `SUPPLEMENTS`
- Home: `HYGIENE` · `HOUSEHOLD` · `CLEANING`
- Lifestyle: `CLOTHING` · `ENTERTAINMENT` · `ELECTRONICS`
- Family: `PETS` · `CHILDREN`
- Other: `TRANSPORT` · `OTHER`

#### Data Model
- ✅ `Receipt` — id, familyId, uploadedBy, vendorName, purchaseDate, totalAmount, status (PROCESSING / DONE / FAILED)
- ✅ `ReceiptItem` — id, receiptId, productName, quantity, unitPrice, category
- ✅ `BudgetLimit` — id, familyId, category, monthlyLimit (schema ready, UI pending)

#### Spending Statistics
- ✅ Spending breakdown by category (current month) — colored badges, mini progress bars, share %
- ✅ Monthly total bar chart (last 6 months, leading zero months trimmed)
- ✅ Donut chart by category with legend
- ✅ Receipt history list (date, vendor, total, status filter pills)
- ✅ Receipt detail page (all items, categories, prices)
- ✅ Spending statistics cached with Caffeine (6h TTL) — `spendingByCategory` + `spendingMonthlyTotals`; evicted on receipt upload
- ✅ Category descriptions — Bootstrap tooltip on each category badge explaining which products belong there (spending page legend + table)
- ✅ `purchaseDate` fallback — when Gemini cannot read the date, upload date (`LocalDate.now()`) is used so no receipt is lost from statistics

#### UI & Navigation
- ✅ Spending page — month navigation (← April 2026 →), summary bar, charts, category breakdown
- ✅ Receipt inbox — drag & drop multi-photo upload, processing overlay, catalog grid
- ✅ Navbar — Receipts removed from topbar; Spending link covers both /spending and /receipts active state
- ✅ Mobile bottom nav — 3 balanced links (Home · Family · Spending); Quick Add moved to topbar
- ✅ Feature-switcher removed from both receipts and spending pages (redundant with hero buttons)
- ✅ FamilyRequiredInterceptor — redirects family-less users instead of null checks in every controller

---

### Phase B — Smart Reminders & AI Insights

> Requires sufficient receipt history (Phase A data). Added after Phase A is stable.

#### Pattern Tracking
- ⬜ Track average purchase interval per product/category from receipt history
- ⬜ Statistical reminders: *"You buy dog food every ~28 days — last purchase was 25 days ago"*
- ⬜ Day-of-week patterns: *"Every Friday you buy pizza ingredients — today is Thursday!"*
- ⬜ Gemini analyses full purchase history → returns pattern summary and suggestions
- ⬜ Scheduled job (daily) generates reminders based on detected patterns

#### Smart Shopping List
- ⬜ Manual product entry
- ⬜ Auto-suggestions from pattern tracking when restock time approaches
- ⬜ One-tap to add suggestion to shopping list

#### Budget Management
- ⬜ Monthly spending limits per category (set by PARENT)
- ⬜ Alert at 80% of limit → in-app notification
- ⬜ Alert at 100% of limit → in-app + email notification

#### Budget Insights (Gemini-generated)
- ✅ **Dashboard Spending Insight widget** — Gemini generates a 1–2 sentence personalised insight per family based on category breakdown + weekly spending pattern; cached 24h (`spendingInsight` Caffeine cache), evicted on receipt upload; falls back up to 2 months if current month has no data; skips current month if < 7 days old
- ⬜ Nightly scheduled job generating family-level insights (e.g. *"Sweets spending 40% higher than last month"*)
- ⬜ *"Every Friday you buy pizza — today is Thursday!"* — day-of-week pattern (requires pattern tracking data)

---

## Out of Scope

- WebSockets / real-time sync (page navigation is sufficient for this app type)
- Mobile native app (iOS / Android)
- Redis (Caffeine sufficient)
- JWT (session-based is correct here)
- React / Angular frontend
- Multi-language / i18n
- Timezone support
- Docker / cloud deployment (deployed to Hostinger VPS — sufficient for v1 scope)

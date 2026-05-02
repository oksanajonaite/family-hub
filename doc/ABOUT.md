# Family Hub

> A full-featured family planning web application that brings together calendars, tasks, health tracking, budget management, and smart shopping — all in one place.

---

## About the Project

Modern families juggle dozens of separate apps — one for calendars, another for shopping, a third for budgeting. **Family Hub** solves this by unifying everything into a single shared space. The application lets families plan their lives together, track tasks and events, scan receipts to monitor spending, and get AI-generated insights about their habits. The system doesn't just store data — it works actively, reminding what's coming up and surfacing patterns from everyday purchases. Different family members have different access levels — parents manage, children participate based on their permissions.

---

## User Story

A family member logs in and interacts with the application based on their role. PARENT creates a family, invites members via invite code, views the shared calendar, manages tasks and events, scans receipts to automatically categorize spending, and reads AI-generated spending insights on the dashboard. KID logs in, views the family calendar and participates in tasks based on permissions assigned by the parent. ADMIN monitors all families via the admin panel and handles platform-level requests.

---

## Table of Contents

- [Overview](#overview)
- [Tech Stack](#tech-stack)
- [Architecture](#architecture)
- [Database Schema](#database-schema)
- [Project Structure](#project-structure)

---

## Overview

Family Hub is a household management platform where family members can:

- Share a **calendar** with events, recurrence, categories, and public holidays
- Manage a **task list** with priorities, due dates, and private tasks
- Track **pets** with photos and event participation
- Manage **family members** (toddlers, elderly) without accounts — PARENT acts on their behalf
- **Scan receipts** with Gemini 2.5 Flash Preview and automatically categorize spending into 21 categories
- Get **AI-generated spending insights** on the dashboard — weekly pattern + category breakdown powered by Gemini
- Monitor family **budget limits** per category *(schema ready, UI coming in Phase B)*
- Manage a **smart shopping list** that learns from purchase history *(Phase B — planned)*
- Invite other members via a **shared invite code** (PARENT / KID codes)
- Control what each family member can see and do based on their **role**

One user belongs to exactly one family. All data is fully isolated per family (multi-tenant architecture).

---

## Tech Stack

| Layer              | Technology                                      |
| ------------------ | ----------------------------------------------- |
| Backend            | Spring Boot 3, Spring MVC                       |
| Security           | Spring Security (session-based + Remember Me)   |
| Persistence        | Spring Data JPA, Hibernate                      |
| Database           | PostgreSQL + Flyway migrations                  |
| Scheduling         | Spring `@Scheduled`                             |
| Cache              | Caffeine (in-memory)                            |
| Rate Limiting      | Bucket4j (receipt upload — 5/hour per user)     |
| AI / OCR           | Gemini 2.5 Flash Preview (receipt parsing + spending insights) |
| Media Storage      | AWS S3 (avatars, pet photos, member photos)     |
| Email              | Brevo SMTP via JavaMailSender                   |
| Public Holidays    | Nager.Date API (cached per calendar year, ~400d) |
| Frontend           | Thymeleaf + Bootstrap 5                         |
| Object Mapping     | MapStruct                                       |
| Build              | Maven                                           |

---

## Architecture

### System Overview

```
┌─────────────────────────────────────────────────────────┐
│              Thymeleaf + Bootstrap Frontend              │
│              Server-side rendering · Bootstrap 5         │
└──────────────────────┬──────────────────────────────────┘
                       │ HTTP
┌──────────────────────▼──────────────────────────────────┐
│                   Spring Boot Backend                    │
│                                                          │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌───────────┐  │
│  │Controller│ │ Service  │ │Repository│ │  Security │  │
│  └──────────┘ └──────────┘ └──────────┘ └───────────┘  │
│                                                          │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌───────────┐  │
│  │Scheduler │ │MapStruct │ │ Bucket4j │ │ Caffeine  │  │
│  └──────────┘ └──────────┘ └──────────┘ └───────────┘  │
└────┬──────────────┬──────────────┬──────────────┬───────┘
     │              │              │              │
┌────▼───┐    ┌─────▼────┐  ┌─────▼──────┐  ┌───▼──────────────────────────┐
│  PgSQL │    │ Caffeine │  │   AWS S3   │  │ Gemini 2.5 Flash Preview     │
│Flyway  │    │          │  │  (avatars  │  │ · receipt parsing (vision)   │
└────────┘    └──────────┘  │  & photos) │  │ · spending insights (text)   │
                             └────────────┘  └──────────────────────────────┘
```

### v4 Receipt Scanning Architecture

```
User uploads photo(s) — up to 5 images per receipt
       │
       ▼
ReceiptController
  └─ rate check (Bucket4j — 5/hour per user)
       │
       ▼
ReceiptService  ◄── Facade: orchestrates the pipeline
  ├─ GeminiClient           → base64 image + prompt → JSON (OCR + categorization in one call)
  ├─ ReceiptParsingService  → merge multi-page results → Receipt + ReceiptItems
  │     · vendorName: first non-null across pages
  │     · totalAmount: last non-null (printed on final page)
  │     · items: union from all pages
  │     · purchaseDate: first non-null, fallback to LocalDate.now()
  └─ ReceiptRepository      → save Receipt (status: DONE or FAILED)
       │ image bytes are never stored — processed in-memory and discarded
       ▼
  ReceiptParsedEvent fired → Caffeine cache evicted (spendingByCategory,
                              spendingMonthlyTotals, spendingInsight)
       │
       ▼
Statistics page  ← ReceiptItemRepository.sumByCategory(familyId, from, to)
Dashboard        ← SpendingInsightService.getInsight(familyId) via Gemini text call
```

### GeminiClient internals

```
GeminiClient.parseReceipt(imageBytes, mimeType)
  │
  ├─ Base64.encode(imageBytes)
  │
  ├─ buildRequest()  →  ObjectNode (Gemini generateContent body)
  │     ├─ contents[0].parts[0]  →  inlineData { mimeType, base64 }
  │     ├─ contents[0].parts[1]  →  text { RECEIPT_PROMPT }
  │     └─ generationConfig      →  { responseMimeType: "application/json", temperature: 0.1 }
  │
  ├─ RestClient.POST /models/gemini-2.5-flash-preview-05-20:generateContent?key=***
  │
  └─ extractResult(rawResponse)
        ├─ candidates[0].content.parts[0].text  →  JSON string from Gemini
        └─ ObjectMapper.readValue → GeminiReceiptResult

GeminiReceiptResult
  ├─ vendorName    String        (null if unreadable)
  ├─ purchaseDate  String        "YYYY-MM-DD" (null if unreadable)
  ├─ totalAmount   BigDecimal    (null if unreadable)
  └─ items[]
       ├─ productName   String
       ├─ quantity      BigDecimal   (defaults to 1 via safeQuantity())
       ├─ unitPrice     BigDecimal
       └─ category      String  →  spendingCategory() converts to SpendingCategory enum
                                   falls back to OTHER on unknown values
```

**Key design decisions:**
- `ObjectNode` for the request (not a Java record) — avoids `"inlineData": null` being sent for text parts and vice versa
- `category` kept as `String` in the result — if Gemini returns an unrecognised value, `spendingCategory()` returns `OTHER` instead of throwing `InvalidFormatException`
- `temperature: 0.1` — low randomness forces deterministic structured output for receipt parsing
- `responseMimeType: "application/json"` — Gemini won't wrap JSON in markdown code fences
- `thinkingBudget: 0` on text calls (`generateText`) — Gemini 2.5 Flash thinking mode is enabled by default and would consume the entire `maxOutputTokens` budget, leaving only a few tokens for the actual response; disabled for spending insights

**`GeminiClient.generateText(String prompt)` — text-only call (spending insights):**
- No image, single text part in `contents`
- `temperature: 0.7`, `maxOutputTokens: 200`, `thinkingConfig.thinkingBudget: 0`
- Returns plain text or `null` on failure (service degrades gracefully)

---

## Database Schema

**15 tables across 8 domains:**

| Domain       | Tables                                                                          |
| ------------ | ------------------------------------------------------------------------------- |
| Users & Auth | `users` `password_reset_tokens`                                                 |
| Family       | `families` `family_invites` `family_members`                                    |
| Calendar     | `events` `event_participants`                                                   |
| Tasks        | `tasks` `task_assigned_users` `task_assigned_members`                           |
| Pets         | `pets`                                                                          |
| Receipts     | `receipts` `receipt_items`                                                      |
| Budget       | `budget_limits`                                                                 |
| System       | `notifications`                                                                 |

---

## Project Structure

```
src/main/java/com/familyhub/
├── config/           # SecurityConfig, CacheConfig, WebMvcConfig
├── controller/       # AuthController, FamilyController, FamilyMemberController,
│                     # TaskController, EventController, PetController,
│                     # ProfileController, PhotoController,
│                     # ReceiptController, SpendingController,
│                     # NotificationController, AdminController,
│                     # DashboardController, GlobalModelAdvice, NavigationUtils
├── service/          # Business logic per feature
│                     # GeminiClient — receipt parsing (vision) + spending insights (text)
│                     # ReceiptParsingService, ReceiptRateLimiterService
│                     # SpendingInsightService — Gemini text call, cached 24h per family
├── repository/       # Spring Data JPA repositories
├── entity/           # JPA entities: User, Family, FamilyMember, Event,
│                     # TaskItem, Pet, FamilyInvite, Notification,
│                     # PasswordResetToken, EventParticipant,
│                     # Receipt, ReceiptItem, BudgetLimit
│   └── enums/        # Role, TaskStatus, TaskPriority, RecurrenceType,
│                     # PetType, NotificationType, ParticipantType,
│                     # ReceiptStatus, SpendingCategory (21 categories)
├── dto/
│   ├── gemini/       # GeminiReceiptResult (receipt parsing DTO)
│   ├── request/      # auth/, event/, family/, member/, pet/, task/
│   └── response/     # auth/, event/, family/, notification/, receipt/,
│                     # admin/, CalendarDay, CalendarViewModel
├── mapper/           # MapStruct mappers: Auth, Event, Family, FamilyInvite,
│                     # Notification, Task, Receipt
├── security/         # CustomUserDetails, CustomUserDetailsService,
│                     # SecurityContextHelper
├── scheduler/        # ScheduledJobService (birthday, event reminder,
│                     # overdue tasks, cleanup jobs)
└── exception/        # Custom exceptions + GlobalExceptionHandler

src/main/resources/
├── db/migration/     # V1–V6 Flyway SQL migrations
├── templates/
│   ├── fragments/    # navbar.html, cards.html, confirm-modal.html, head-meta.html
│   ├── auth/         # login.html, register.html, forgot-password.html,
│   │                 # reset-password.html, reset-password-error.html
│   ├── family/       # setup.html, index.html
│   ├── members/      # index.html, form.html
│   ├── events/       # index.html, form.html, detail.html
│   ├── tasks/        # index.html, form.html, detail.html
│   ├── pets/         # index.html, form.html
│   ├── receipts/     # index.html, upload.html, detail.html, retry.html
│   ├── spending/     # index.html
│   ├── notifications/# index.html
│   ├── admin/        # index.html
│   ├── error/        # generic.html
│   └── dashboard.html
└── static/
    ├── css/          # variables.css, components.css
    └── img/          # logo.svg, inline scripts in templates
```

---

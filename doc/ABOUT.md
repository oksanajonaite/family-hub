# Family Hub

> A full-featured family planning web application that brings together calendars, tasks, health tracking, budget management, and smart shopping — all in one place.

---

## About the Project

Modern families juggle dozens of separate apps — one for calendars, another for shopping, a third for budgeting. **Family Hub** solves this by unifying everything into a single shared space. The application lets families plan their lives together, track health reminders for both people and pets, manage budgets, and learn from everyday shopping habits. The system doesn't just store data — it works actively, reminding what's coming up, suggesting what to buy, and alerting when budgets are exceeded. Different family members have different access levels — parents manage, children participate based on their age.

---

## User Story

A family member logs in and interacts with the application based on their role. PARENT creates a family, invites members via invite code, views the shared calendar, manages tasks and events, tracks health reminders for people and pets, scans receipts to monitor the family budget, and receives automatic reminders and shopping suggestions based on purchase history. KID logs in, views the family calendar and their own health reminders, and participates in tasks based on permissions assigned by the parent. ADMIN monitors all families, views audit logs, and handles administrative requests.

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
- **Scan receipts** with Gemini 1.5 Flash Vision and automatically categorize spending
- Monitor a **family budget** with monthly limits per category
- Manage a **smart shopping list** that learns from purchase history (Phase B)
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
| AI / OCR           | Gemini 1.5 Flash Vision (receipt parsing)       |
| Media Storage      | AWS S3 (avatars, pet photos, member photos)     |
| Email              | Brevo SMTP via JavaMailSender                   |
| Public Holidays    | Nager.Date API (cached 24h)                     |
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
┌────▼───┐    ┌─────▼────┐  ┌─────▼──────┐  ┌───▼──────────────────┐
│  PgSQL │    │ Caffeine │  │   AWS S3   │  │ Gemini 1.5 Flash     │
│Flyway  │    │          │  │  (photos + │  │ Vision (receipt OCR) │
└────────┘    └──────────┘  │  receipts) │  └──────────────────────┘
                             └────────────┘
```

### v4 Receipt Scanning Architecture

```
User uploads photo
       │
       ▼
ReceiptController
  └─ rate check (Bucket4j — 5/hour per user)
       │
       ▼
ReceiptService  ◄── Facade: orchestrates the pipeline
  ├─ S3FileService          → upload image to S3 (receipts/ prefix, safety net)
  ├─ GeminiClient           → send image + prompt → get JSON back
  │     └─ one API call: OCR + categorization combined
  ├─ ReceiptParsingService  → parse GeminiReceiptResult → Receipt + ReceiptItems
  ├─ S3FileService          → delete image from S3 immediately after parsing
  └─ ReceiptRepository      → save Receipt (status: DONE or FAILED)
       │
       ▼
Statistics page  ← ReceiptItemRepository.sumByCategory(familyId, from, to)
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
  ├─ RestClient.POST /models/gemini-1.5-flash:generateContent?key=***
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
- `temperature: 0.1` — low randomness forces deterministic structured output
- `responseMimeType: "application/json"` — Gemini won't wrap JSON in markdown code fences

---

## Database Schema

**15 tables across 7 domains:**

| Domain         | Tables                                                                   |
| -------------- | ------------------------------------------------------------------------ |
| Users & Family | `users` `families` `family_members` `password_reset_tokens`              |
| Invites        | `family_invites`                                                         |
| Calendar       | `events` `event_participants`                                            |
| Tasks          | `tasks` `task_assigned_users` `task_assigned_members`                    |
| Pets           | `pets`                                                                   |
| Receipts       | `receipts` `receipt_items`                                               |
| Budget         | `budget_limits`                                                          |
| System         | `notifications`                                                          |

---

## Project Structure

```
src/main/java/com/familyhub/
├── config/           # SecurityConfig, CaffeineConfig, WebMvcConfig
├── controller/       # AuthController, FamilyController, TaskController,
│                     # EventController, PetController, PhotoController,
│                     # ReceiptController, BudgetController,
│                     # NotificationController, AdminController,
│                     # DashboardController, GlobalModelAdvice
├── service/          # Business logic per feature
│                     # GeminiClient — RestClient wrapper for Gemini API
│                     # ReceiptParsingService, ReceiptRateLimiterService
├── repository/       # Spring Data JPA repositories
├── entity/           # JPA entities: User, Family, FamilyMember, Event,
│                     # TaskItem, Pet, FamilyInvite, Notification,
│                     # PasswordResetToken, EventParticipant,
│                     # Receipt, ReceiptItem, BudgetLimit
│   └── enums/        # Role, TaskStatus, TaskPriority, RecurrenceType,
│                     # PetType, NotificationType, ParticipantType,
│                     # ReceiptStatus, SpendingCategory
├── dto/
│   ├── request/      # auth/, event/, family/, member/, pet/, task/
│   └── response/     # auth/, event/, family/, notification/, receipt/,
│                     # admin/, CalendarDay, CalendarViewModel
├── mapper/           # MapStruct mappers: Auth, Event, Family, FamilyInvite,
│                     # Notification, Task, Receipt
├── security/         # CustomUserDetails, CustomUserDetailsService
├── scheduler/        # ScheduledJobService (birthday, event reminder,
│                     # overdue tasks, cleanup jobs)
└── exception/        # Custom exceptions + GlobalExceptionHandler

src/main/resources/
├── db/migration/     # V1–V4 Flyway SQL migrations
├── templates/
│   ├── auth/         # login.html, register.html, forgot-password.html,
│   │                 # reset-password.html
│   ├── family/       # setup.html, index.html
│   ├── calendar/     # index.html, form.html
│   ├── tasks/        # index.html, form.html
│   ├── pets/         # index.html, form.html
│   ├── receipts/     # index.html, upload.html  (v4)
│   ├── budget/       # index.html               (v4)
│   ├── notifications/# index.html
│   ├── profile/      # index.html
│   ├── admin/        # index.html
│   └── dashboard.html
└── static/
    ├── css/          # components.css, ...
    └── js/           # inline scripts in templates
```

---

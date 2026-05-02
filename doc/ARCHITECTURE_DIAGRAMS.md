# Family Hub — Architecture Diagrams

---

## Table of Contents

- [System Overview](#system-overview)
- [Backend Architecture](#backend-architecture)
- [Multi-Tenant Security](#multi-tenant-security)
- [Cache Invalidation Flow](#cache-invalidation-flow)
- [Notification Chain](#notification-chain)
- [Receipt Scanning Flow](#receipt-scanning-flow)
- [Spending Insight Flow](#spending-insight-flow)

---

## System Overview

```
┌─────────────────────────────────────────────────────────┐
│              Thymeleaf + Bootstrap Frontend              │
│              Server-side rendering · Bootstrap 5         │
└──────────────────────┬──────────────────────────────────┘
                       │ HTTP
┌──────────────────────▼──────────────────────────────────┐
│                   Spring Boot Backend                    │
│                                                          │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐                 │
│  │Controller│ │ Service  │ │Repository│                 │
│  └──────────┘ └──────────┘ └──────────┘                 │
│                                                          │
│  Cross-cutting concerns:                                 │
│  · Spring Security (session + remember me)               │
│  · Spring Scheduler                                      │
│  · Spring Events (ReceiptParsedEvent → cache eviction)   │
│  · Caffeine cache                                        │
│  · Bucket4j rate limiting                                │
└────┬──────────────┬──────────────┬──────────────┬───────┘
     │              │              │              │
┌────▼───────┐ ┌────▼───────┐ ┌───▼────────┐ ┌───▼──────────────────────┐
│ PostgreSQL │ │  AWS S3    │ │  Caffeine  │ │   External APIs          │
│ 15 tables  │ │ Avatars &  │ │ In-memory  │ │ · Gemini 2.5 Flash       │
│ multi-     │ │ photos     │ │ cache      │ │   (vision + text)        │
│ tenant     │ └────────────┘ └────────────┘ │ · Nager.Date API         │
└────────────┘                               │ · Brevo SMTP (email)     │
                                             └──────────────────────────┘
```

---

## Backend Architecture

```mermaid
graph TD
    CL([HTTP Client])

    subgraph BE["Spring Boot Backend"]
        CT[Controller]
        SV[Service]
        RP[Repository]

        subgraph CC["Cross-cutting concerns"]
            SEC[Spring Security\nsession + remember me]
            SCH[Spring Scheduler]
            EVT[Spring Events\nReceiptParsedEvent]
            CAC[Caffeine Cache]
            RL[Bucket4j\nrate limiting]
        end
    end

    DB[(PostgreSQL)]
    S3[(AWS S3\navatars & photos)]
    EXT[External APIs\nGemini · Nager.Date · Brevo]

    CL -->|HTTP request| CT
    CT --> SV
    SV --> RP
    RP -->|JPA / Hibernate| DB
    SV -->|store media| S3
    SV -->|REST calls| EXT
    SCH -->|triggers| EVT
    EVT -->|evicts cache| CAC
    SEC -->|guards| CT
    CAC -->|caches| SV
    RL -->|limits| CT
```

---

## Multi-Tenant Security

```mermaid
flowchart TD
    A[HTTP Request] --> B[Session Filter\nSpring Security]
    B --> C{Session valid?}
    C -- No --> D[Redirect to login]
    C -- Yes --> E[Extract family_id]
    E --> F[All DB queries\nfiltered by family_id]
    F --> G{Resource belongs\nto family?}
    G -- No --> H[403 Forbidden]
    G -- Yes --> I[Check Role\nSUPER_ADMIN / PARENT / KID]
    I --> J{Permission granted?}
    J -- No --> K[403 Forbidden]
    J -- Yes --> L[Return data]
```

---

## Cache Invalidation Flow

```mermaid
flowchart LR
    A[User uploads\nreceipt] --> B[ReceiptService]
    B --> C[Save to PostgreSQL\nstatus: DONE]
    B --> D[ReceiptParsedEvent fired]
    D --> E[SpendingService\n@EventListener]
    E --> F[Evict:\nspendingByCategory\nspendingMonthlyTotals\nspendingInsight]
    F --> G[Next page load\nfetches fresh data]
```

---

## Notification Chain

```mermaid
flowchart TD
    A[Spring Scheduler] --> B[Birthday reminder\n08:00 daily]
    A --> C[Event reminder\nevery 15 min]
    A --> D[Overdue task reminder\n08:30 daily]
    A --> E[Cleanup jobs\nmidnight / 01:00 / 02:00]
    B --> F{Already notified\ntoday?}
    C --> F
    D --> F
    F -- No --> G[Create Notification in DB]
    F -- Yes --> H[Skip dedup guard]
    G --> I[In-app badge + /notifications]
    G --> J{emailNotifications\nEnabled?}
    J -- Yes --> K[Email via JavaMailSender\nBrevo SMTP]
    J -- No --> L[Skip]
```

---

## Receipt Scanning Flow

```mermaid
flowchart TD
    A[User uploads photos\nup to 5 per receipt] --> B[ReceiptController]
    B --> C{Rate limit check\nBucket4j — 5/hour}
    C -- Exceeded --> D[Redirect with error]
    C -- OK --> E[ReceiptService facade]
    E --> F[GeminiClient\nbase64 image + prompt]
    F --> G{Gemini 2.5 Flash\nOCR + categorization}
    G -- Failed --> H[Mark receipt as FAILED\nretryCount=0 allows one retry]
    G -- Success --> I[ReceiptParsingService\nmerge multi-page results]
    I --> J[Save Receipt + ReceiptItems\nstatus: DONE]
    J --> K[ReceiptParsedEvent fired]
    K --> L[Evict Caffeine caches\nspendingByCategory\nspendingMonthlyTotals\nspendingInsight]
```

---

## Spending Insight Flow

```mermaid
flowchart TD
    A[Dashboard load] --> B[SpendingInsightService\ngetInsight familyId]
    B --> C{Caffeine cache\nhit?}
    C -- Hit --> D[Return cached text\n24h TTL]
    C -- Miss --> E[Try current month]
    E --> F{Less than 7 days\ninto month?}
    F -- Yes --> G[Skip — too early]
    G --> H[Try previous month]
    F -- No --> I[sumByCategory + sumByDate\nfrom ReceiptItemRepository]
    H --> I
    I --> J{Data found?}
    J -- No --> K[Try month before\nup to 2 months back]
    K --> L{Still nothing?}
    L -- Yes --> M[Return null\nwidget hidden on mobile]
    J -- Yes --> N[Build prompt:\ncategory breakdown\n+ weekly pattern]
    N --> O[GeminiClient.generateText\nthinkingBudget=0]
    O --> P[Cache result 24h]
    P --> D
```

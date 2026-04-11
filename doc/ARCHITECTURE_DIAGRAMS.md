# Family Hub — Architecture Diagrams

---

## Table of Contents

- [System Overview](#system-overview)
- [Backend Architecture](#backend-architecture)
- [Multi-Tenant Security](#multi-tenant-security)
- [Real-Time Synchronization](#real-time-synchronization)
- [Notification Chain](#notification-chain)
- [Receipt Scanning Flow](#receipt-scanning-flow)
- [Shopping Learning Algorithm](#shopping-learning-algorithm)

---

## System Overview

```
┌─────────────────────────────────────────────────────────┐
│              Thymeleaf + Bootstrap Frontend              │
│         Server-side rendering · SortableJS · Bootstrap  │
└──────────────────────┬──────────────────────────────────┘
                       │ HTTP + WebSockets
┌──────────────────────▼──────────────────────────────────┐
│                   Spring Boot Backend                    │
│                                                          │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐                 │
│  │Controller│ │ Service  │ │Repository│                 │
│  └──────────┘ └──────────┘ └──────────┘                 │
│                                                          │
│  Cross-cutting concerns:                                 │
│  · Spring Security (session + remember me)               │
│  · WebSockets + STOMP                                    │
│  · Spring Scheduler                                      │
│  · Spring Events                                         │
│  · Caffeine cache                                        │
│  · Bucket4j rate limiting                                │
└────┬──────────────┬──────────────┬──────────────┬───────┘
     │              │              │              │
┌────▼───────┐ ┌────▼───────┐ ┌───▼────────┐ ┌───▼──────────────────┐
│ PostgreSQL │ │ Cloudinary │ │  Caffeine  │ │   External APIs      │
│ 22 tables  │ │ Avatars &  │ │ In-memory  │ │ · Google Vision API  │
│ multi-     │ │ icons      │ │ cache      │ │ · OpenWeatherMap      │
│ tenant     │ └────────────┘ └────────────┘ │ · Nager.Date API     │
└────────────┘                               │ · SMTP (email)       │
                                             └──────────────────────┘
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
            WST[WebSockets + STOMP]
            SCH[Spring Scheduler]
            EVT[Spring Events]
            CAC[Caffeine Cache]
            RL[Bucket4j\nrate limiting]
        end
    end

    DB[(PostgreSQL)]
    CDN[(Cloudinary)]
    EXT[External APIs]

    CL -->|HTTP request| CT
    CT --> SV
    SV --> RP
    RP -->|JPA / Hibernate| DB
    SV -->|store media| CDN
    SV -->|REST calls| EXT
    WST -->|push events| CL
    SCH -->|triggers| EVT
    EVT -->|notifies| SV
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

## Real-Time Synchronization

```mermaid
flowchart LR
    A[Family Member A\nadds event] --> B[Spring Boot Backend]
    B --> C[Save to PostgreSQL]
    B --> D[Broadcast to\n/topic/family-id]
    D --> E[Family Member B\nsees update instantly]
    D --> F[Family Member C\nsees update instantly]
```

---

## Notification Chain

```mermaid
flowchart TD
    A[Spring Scheduler\n8:00 daily] --> B{Check upcoming}
    B --> C[Birthdays in 3 days]
    B --> D[Health checks in 7 days]
    B --> E[Pet procedures due]
    C --> F[Create Notification in DB]
    D --> F
    E --> F
    F --> G[Spring Event fired]
    G --> H[In-app Bootstrap alert]
    G --> I[Email via JavaMailSender]
```

---

## Receipt Scanning Flow

```mermaid
flowchart TD
    A[User photographs receipt] --> B[Upload to backend]
    B --> C{Rate limit check\nBucket4j}
    C -- Exceeded --> D[429 Too Many Requests]
    C -- OK --> E[Send to Google Vision API]
    E --> F{OCR processing}
    F -- Failed --> G[Mark as FAILED\nDelete photo]
    F -- Success --> H[Extract products text]
    H --> I[Keyword categorization\nJSON dictionary]
    I --> J[Save to DB]
    J --> K[Delete photo automatically]
    K --> L[Spring Event fired]
    L --> M[Update budget statistics]
    L --> N[Update purchase history]
    L --> O[Invalidate Caffeine cache]
    L --> P[WebSocket broadcast to family]
```

---

## Shopping Learning Algorithm

```mermaid
flowchart TD
    A[Receipt scanned] --> B[Save to purchase_history]
    B --> C[Calculate avg_interval_days]
    C --> D{Days since last purchase\n≥ avg_interval - 1?}
    D -- No --> E[No suggestion yet]
    D -- Yes --> F[Generate suggestion]
    F --> G[Show above shopping list]
    G --> H{User action}
    H -- Accept --> I[Add to shopping list]
    H -- Dismiss --> J[Reduce suggestion frequency]
```

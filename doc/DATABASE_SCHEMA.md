# Family Hub — Database Schema (ERD)

> For the interactive ERD diagram with zoom and pan, open `doc/family_hub_erd.html` in your browser.

---

## Entity Relationship Diagram

```mermaid
erDiagram
  families ||--o{ users : "turi"
  families ||--o{ family_invites : "turi"
  families ||--o{ family_members : "turi"
  families ||--o{ events : "turi"
  families ||--o{ tasks : "turi"
  families ||--o{ pets : "turi"
  families ||--o{ receipts : "turi"
  families ||--o{ budget_limits : "turi"
  users ||--o{ notifications : "gauna"
  users ||--o{ password_reset_tokens : "turi"
  users ||--o{ event_participants : "dalyvauja"
  users ||--o{ receipts : "nuskaito"
  events ||--o{ event_participants : "turi"
  tasks }o--o{ users : "task_assigned_users"
  tasks }o--o{ family_members : "task_assigned_members"
  pets ||--o{ event_participants : "dalyvauja"
  family_members ||--o{ event_participants : "dalyvauja"
  receipts ||--o{ receipt_items : "turi"

  families {
    bigint id PK
    varchar name
    bigint created_by_user_id FK
    timestamp created_at
  }
  users {
    bigint id PK
    varchar email
    varchar password_hash
    varchar display_name
    varchar role
    bigint family_id FK
    boolean enabled
    date date_of_birth
    boolean email_notifications_enabled
    varchar avatar_url
    timestamp created_at
  }
  family_invites {
    bigint id PK
    bigint family_id FK
    varchar code
    varchar role
    timestamp expires_at
    bigint created_by_user_id FK
    timestamp created_at
  }
  family_members {
    bigint id PK
    bigint family_id FK
    varchar name
    date date_of_birth
    varchar photo_url
    timestamp created_at
  }
  password_reset_tokens {
    bigint id PK
    bigint user_id FK
    varchar token
    timestamp expires_at
    timestamp created_at
  }
  events {
    bigint id PK
    bigint family_id FK
    varchar title
    text description
    timestamp starts_at
    timestamp ends_at
    boolean private_event
    varchar type
    varchar recurrence_type
    date recurrence_until
    bigint created_by_user_id FK
    timestamp created_at
  }
  event_participants {
    bigint id PK
    bigint event_id FK
    varchar participant_type
    bigint user_id FK
    bigint pet_id FK
    bigint family_member_id FK
  }
  tasks {
    bigint id PK
    bigint family_id FK
    varchar title
    text description
    varchar status
    varchar priority
    date due_date
    boolean private_task
    bigint created_by_user_id FK
    timestamp created_at
    timestamp completed_at
  }
  task_assigned_users {
    bigint task_id FK
    bigint user_id FK
  }
  task_assigned_members {
    bigint task_id FK
    bigint family_member_id FK
  }
  pets {
    bigint id PK
    bigint family_id FK
    varchar name
    varchar type
    date date_of_birth
    varchar photo_url
    timestamp created_at
  }
  receipts {
    bigint id PK
    bigint family_id FK
    bigint uploaded_by FK
    varchar vendor_name
    date purchase_date
    numeric total_amount
    varchar status
    int retry_count
    timestamp created_at
  }
  receipt_items {
    bigint id PK
    bigint receipt_id FK
    varchar product_name
    numeric quantity
    numeric unit_price
    varchar category
  }
  budget_limits {
    bigint id PK
    bigint family_id FK
    varchar category
    numeric monthly_limit
  }
  notifications {
    bigint id PK
    bigint recipient_user_id FK
    varchar type
    varchar message
    boolean is_read
    varchar related_entity_type
    bigint related_entity_id
    timestamp created_at
  }
```

---

## Table Descriptions

```
families              — Family profile; created_by_user_id identifies the founding PARENT
users                 — All registered users; role = PARENT | KID | ADMIN
family_invites        — Invite codes (12 chars, 7-day expiry, reusable); role = PARENT | KID
family_members        — People without accounts (toddlers, elderly); managed by PARENT
password_reset_tokens — Single-use expiring tokens for the password reset flow
events                — Family calendar events with optional recurrence and privacy flag
event_participants    — Polymorphic join: user_id, pet_id, or family_member_id (one set per row)
tasks                 — Family task list; private_task hides from KID role
task_assigned_users   — Many-to-many: tasks ↔ registered users
task_assigned_members — Many-to-many: tasks ↔ family members (no account)
pets                  — Family pets; can participate in events
receipts              — Scanned receipts; status = PROCESSING | DONE | FAILED; retry_count ≤ 1
receipt_items         — Line items extracted by Gemini; category = SpendingCategory enum (21 values)
budget_limits         — Monthly spending cap per category per family; UNIQUE(family_id, category)
notifications         — In-app alerts; related_entity_type/id links to the source record
```

---

## Flyway Migration History

| Version | Description                                      |
| ------- | ------------------------------------------------ |
| V1      | Initial schema — core tables                     |
| V2      | Align with JPA model — adds columns, family_members, task assignment tables, password_reset_tokens |
| V3      | Drop unused `family_invites.used` column         |
| V4      | Receipt scanning — `receipts`, `receipt_items`, `budget_limits` |
| V5      | Migrate old spending category values to new enum |
| V6      | Add `retry_count` column to `receipts`           |

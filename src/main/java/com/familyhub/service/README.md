# Service layer guide

Use this folder as the business-logic map for Family Hub.

Services should own:
- business rules
- entity loading
- view-facing DTO/data assembly where needed
- permission-aware filtering
- transaction boundaries where appropriate

Services should not own:
- HTTP routing
- redirect decisions
- template-specific navigation rules

## Current service responsibilities

### `AuthService`
- Registration and auth-related account setup.
- Must preserve `displayName` handling because UI is designed around names, not emails.

### `EventService`
- Event CRUD and event visibility logic.
- Must continue providing enough readable data for:
  - calendar rendering
  - compact event cards
  - participant names
  - recurrence visibility

### `TaskService`
- Task CRUD, workflow state changes and task visibility logic.
- Must continue providing enough readable data for:
  - dashboard due-soon cards
  - task index cards
  - assignee names
  - task detail page

### `FamilyService`
- Family-level data assembly and invite/access support.
- Must keep Family page data complete and easy for controllers to consume.

### `FamilyMemberService`
- CRUD for members without account.
- Should stay focused on household-member logic, not registered-user account logic.

### `PetService`
- CRUD for pets and pet-related display data.

### `NotificationService`
- Notification retrieval, unread count logic and read-state updates.
- Topbar bell depends on this layer staying stable.

### `PasswordResetService`
- Password reset token and reset flow logic.

### `AdminService`
- Admin-only review and management business rules.

## Service contracts to preserve

### Readable UI data
- If a page currently shows names, dates, recurrence or assignee information without extra controller work, services should keep returning that readable data after refactors.

### Loading strategy
- Be careful with lazy-loaded relationships used by Thymeleaf templates.
- Refactors that remove eager loading or DTO enrichment can silently break views later.

### Display name contract
- UI should keep working with `displayName` as the main human-readable identity.
- Do not shift normal UI back to raw email-based identity.

### Notification contract
- Unread notification count must remain cheap and reliable enough for shell rendering.

## Refactor rules

- Prefer extracting reusable query / assembly logic into focused private methods or helper services instead of growing one large service.
- Keep service APIs explicit about what the UI needs.
- If a method currently serves a page, document or preserve that page contract when changing it.
- Avoid returning partially loaded entities to templates when a DTO/view model would be safer.

## Safe refactor checklist

Before merging service refactors, verify:
- dashboard still has all calendar and side-card data
- event list still shows recurrence/participants correctly
- task list still shows due dates and assignees correctly
- family page still gets members, pets and invite codes
- unread notifications still appear in the shell
- UI does not fall back from names to emails

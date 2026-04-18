# Controller layer guide

Use this folder as the HTTP and view-routing map for Family Hub.

Controllers should stay thin:
- accept requests
- validate and bind form input
- call services
- prepare view models / model attributes
- choose redirect targets and back-navigation context

Controllers should not own business rules that belong in services.

## Current controller responsibilities

### `AuthController`
- Login, register and password reset flows.
- Should keep auth page routing simple and stable.

### `DashboardController`
- Main dashboard page.
- Must preserve:
  - calendar data
  - compact right-side cards
  - summary line data
  - dashboard context links

### `EventController`
- Events list and event create/edit flow.
- Must preserve compact event form model shape and back-navigation context.

### `TaskController`
- Tasks list, task create/edit flow and task detail flow.
- Must preserve compact task form model shape and contextual back behavior.

### `FamilyController`
- Family home base page and family setup / access level flows.
- Must keep the Family page supplied with:
  - registered members
  - members without account
  - pets
  - invite codes

### `FamilyMemberController`
- CRUD for members without account.
- Redirects should return to Family after successful actions unless there is a very clear reason not to.

### `PetController`
- CRUD for pets.
- Redirects should return to Family after successful actions unless there is a very clear reason not to.

### `NotificationController`
- Notification inbox page.
- Must remain reachable from the topbar bell.

### `AdminController`
- Admin-only pages and moderation/review flows.

### `GlobalModelAdvice`
- Shared model attributes used by multiple views.
- Important place for shell-level data such as current URI or unread counts.

## Controller contracts to preserve

### Navigation contracts
- Dashboard-origin pages may depend on `from=dashboard` or equivalent back context.
- Do not remove contextual back behavior during refactors.

### View-model contracts
- If a template expects resolved names, counts or lists, controllers must still provide them after service or DTO refactors.
- Thymeleaf screens are sensitive to missing attributes; silent removals often become runtime template errors.

### Shell contracts
- Authenticated pages depend on shell data from shared controller/model advice wiring.
- Topbar bell, active sidebar state and display name should not break during unrelated route refactors.

## Refactor rules

- Prefer moving business logic out of controllers and into services.
- Keep redirect decisions explicit and readable.
- When changing model attribute names, update the paired Thymeleaf template in the same change.
- When splitting a controller, keep the page ownership obvious.

## Safe refactor checklist

Before merging controller refactors, verify:
- dashboard still renders
- family page still renders
- event and task forms still render
- notifications still open from bell
- back links still return to the correct place
- success/error flash messages still appear where expected

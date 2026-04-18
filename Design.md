# Family Hub Design Guardrails

This file documents the current Family Hub UI/UX direction and the frontend-behavior contracts that should be preserved during backend, controller, service, and refactoring work.

The goal is simple:
- keep the product visually calm
- keep the product easy to scan
- keep the current page hierarchy and interaction logic intact
- avoid accidental regressions where backend changes break the UI structure we intentionally refined

Supporting file maps:
- [src/main/resources/templates/README.md](C:\Users\Lenovo\Project hub\family-hub\src\main\resources\templates\README.md)
- [src/main/resources/static/css/README.md](C:\Users\Lenovo\Project hub\family-hub\src\main\resources\static\css\README.md)

## Core Design Direction

Family Hub should feel:
- warm
- light
- calm
- family-oriented
- product-like, not admin-like

It should not feel:
- heavy
- overly technical
- crowded with analytics
- full of duplicate actions
- dark or visually noisy

## Brand / Visual Rules

### Overall palette

Use:
- light warm neutral base
- clay / terracotta as accent
- olive for active / supportive states
- white / off-white cards

Avoid:
- strong black text where clay-brown is already used
- heavy dark brown blocks as large base surfaces
- large high-contrast fills unless they are true CTAs

### Logo / sidebar brand

Current decision:
- sidebar logo remains unrotated
- logo block stays visible and should preserve brand feel
- do not aggressively shrink the logo area just to fill sidebar emptiness

### Typography

Use the existing soft clay-brown text tone instead of pure black for:
- page titles
- section titles
- important labels

Secondary text should remain softer and lighter.

## Global UX Principles

### Navigation

Keep sidebar simple.

Current sidebar should include only:
- Dashboard
- Family
- Admin Panel for admins only

Notifications:
- must NOT appear in sidebar
- are accessed through the topbar bell icon

Topbar order should remain:
- Welcome
- Notifications bell
- Add button
- user menu

Search:
- removed from topbar because it was not truly functional
- do not reintroduce fake search

### Back navigation

Deep pages should preserve contextual back behavior:
- if opened from dashboard, go back to dashboard
- otherwise go back to the natural list/context page

Do not remove this behavior during controller refactors.

### Quick actions

Primary create entry point is the topbar `Add` menu.

Current rule:
- Quick Add in topbar = main create entry point
- calendar = viewing / opening existing items
- right-side dashboard cards = scan and navigate, not create

Do not reintroduce duplicate create buttons in multiple dashboard areas.

## Dashboard Rules

### Layout

Dashboard structure:
- main calendar center
- compact right-side panels
- topbar greeting
- summary line above calendar

The dashboard should remain calm and open.

### Summary line

Current pattern:
- left: `Today: ...`
- right: selected date
- event/task legend below date area

Do not reintroduce large pills for summary status.

### Right panel

Current design direction:
- `Today + Tomorrow` card is compact
- `Due Soon` card is compact
- these are scan cards, not detailed modules

Event card rules:
- compact
- no icon clutter inside mini event tiles
- show only immediate horizon

Task card rules:
- 2 per row in `Due Soon`
- compact
- no unnecessary assignee line in dashboard quick card

### Sidebar / dashboard balance

Sidebar is intentionally calm.
Do not add random filler content just to make it look less empty.

## Family Page Rules

This page was intentionally simplified.

### Main content that must remain

Primary sections:
- Registered members
- Members without account
- Pets

Secondary utility section:
- Family access

### Family access

Current logic:
- appears below the main three sections
- accessed as utility, not as a primary page hero element
- contains:
  - Parent invite
  - Kid invite

### Removed by design

Do not reintroduce without a strong reason:
- large stats cards
- management note blocks
- oversized utility panels

### Family page composition

Current desired structure:
- page hero
- 3 primary sections in parallel on desktop
- Family access below

Section title pattern:
- section headings are on page background
- section content is inside lighter cards below

This is important and should not be reverted casually.

## Events Page Rules

### Events index (`Family schedule`)

Current design direction:
- remove heavy large stat cards
- use a lightweight inline summary row instead
- use compact event cards in a grid
- avoid very long full-width horizontal list rows

This page should feel like a calm schedule browser, not a report page.

### Event form

Current desired structure:
- one main card
- no right-side helper panel
- compact and non-threatening

Current field layout:
- title
- compact description
- row 1: start date / end date / start time
- row 2: recurrence / repeat until / end time
- participants
- private checkbox
- save actions

Field label rules:
- `optional` removed from labels like end date / repeat until / time
- optionality lives in placeholder text instead

Textarea rule:
- description starts at compact height
- can still grow if needed

Do not reintroduce big multi-section editor cards or side guidance blocks.

## Tasks Page Rules

### Tasks index (`Family workflow`)

Current design direction:
- remove large stats cards
- use lightweight inline summary
- use compact task cards in grid form
- avoid long full-width rows unless there is a very good reason

### Task form

Current desired structure:
- one main card
- no right-side helper panel
- compact

Keep:
- title
- compact description
- priority + due date row
- assign to section
- save / cancel actions

Remove / avoid:
- helper tips side panel
- duplicate or unclear actions

`Save and keep editing` was intentionally removed because it was unclear and noisy.

## Notifications Rules

Notifications should behave like:
- inbox
- lightweight activity center

Notifications should NOT behave like:
- major navigation module
- analytics dashboard

Current design direction:
- no sidebar entry
- access only through bell icon
- no large stat cards
- compact notification items

## Forms / Interaction Rules

### Create / Edit forms should feel:
- compact
- calm
- readable
- low-pressure

Avoid:
- too many sections
- large explanatory sidebars
- duplicated helper blocks

### Undo interactions

Dashboard task complete / undo behavior should remain:
- completing task hides it
- undo is available
- undo UI must match product style, not default dark toast style

Do not replace with raw browser alerts or mismatched toasts.

## Backend / Controller Contracts To Preserve

When refactoring controllers/services, do not accidentally break these UI assumptions:

### Display name

UI uses `displayName`, not email, for:
- topbar user
- greetings
- avatar initials

Do not fall back to email in normal UI if display name exists.

### Dashboard links

Dashboard links can carry `from=dashboard` context.
This is used so back navigation returns correctly.

Do not remove this context behavior unless replaced with an equivalent solution.

### Notifications

Topbar bell depends on unread notification count.
Do not remove or rename the data contract without updating the fragment.

### Family page data

Family page expects:
- registered members
- members without account
- pets
- parent invite code
- kid invite code

Refactors should preserve these view-model data pieces.

### Event / task list pages

Index pages expect enough resolved data for scanability:
- participant names for events
- assigned users / members for tasks
- readable status / recurrence / due dates

If lazy-loading or DTO changes hide these, the UI will silently degrade.

## What Should Not Be Reintroduced Accidentally

Avoid bringing back:
- fake search bar
- duplicate create buttons across dashboard
- sidebar notifications item
- large stat cards on notifications / tasks / events
- heavy helper side panels on forms
- giant utility cards with little content
- black high-contrast text in places already normalized to clay-brown
- overly bold or visually heavy dashboard summary pills

## Refactor Checklist

Before merging backend/controller refactors, verify:
- Dashboard still loads with the same 3-zone layout
- Sidebar still contains only intended modules
- Notifications page is still reachable through bell
- Family page still uses the simplified structure
- Event form is still one compact card
- Task form is still one compact card
- Events index is still compact and not stat-heavy
- Tasks index is still compact and not stat-heavy
- Back links still return to the correct context
- Display name still shows instead of email

## If Future Changes Are Needed

When making new UI changes, prefer:
- removing noise instead of adding new blocks
- reducing visual weight instead of adding more decoration
- keeping the product calm and scannable

If unsure between:
- "more information"
- or "less but clearer"

choose less but clearer.

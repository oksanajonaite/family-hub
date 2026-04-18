# UI Contracts

Short technical reference for safe backend/controller/service refactoring.

Use this file as a fast pre-refactor checklist.

For broader design intent, see [Design.md](C:\Users\Lenovo\Project hub\family-hub\Design.md).
For file ownership maps, see:
- [src/main/resources/templates/README.md](C:\Users\Lenovo\Project hub\family-hub\src\main\resources\templates\README.md)
- [src/main/resources/static/css/README.md](C:\Users\Lenovo\Project hub\family-hub\src\main\resources\static\css\README.md)

## Global Contracts

### User identity

UI should use `displayName`, not email, for:
- topbar username
- welcome text
- avatar initial

### Back navigation

Deep pages may rely on dashboard-origin context.
Preserve contextual back behavior when routes are opened from dashboard.

### Notifications access

Notifications:
- remain reachable through topbar bell
- are not shown as a sidebar module

### Search

Fake topbar search was removed.
Do not reintroduce non-functional search UI.

## Navbar Contracts

Sidebar should remain minimal:
- Dashboard
- Family
- Admin Panel for admins only

Topbar order:
- Welcome
- Bell
- Add
- user menu

Sidebar bottom:
- intentionally empty
- no duplicated user identity block
- no collapse label/button block

## Dashboard Contracts

Dashboard must preserve:
- main calendar center
- compact right panel
- summary row above calendar

Summary row structure:
- left: `Today: ...`
- right: selected date
- legend below date

Right panel behavior:
- `Today + Tomorrow` is compact
- `Due Soon` is compact
- these are scan/navigation blocks, not create blocks

Create actions:
- primary create entry point is topbar `Add`
- no duplicate create CTAs should reappear around the dashboard

Task complete flow:
- complete
- hide
- undo available

## Family Contracts

Family page primary sections:
- Registered members
- Members without account
- Pets

Utility section:
- Family access

Current visual structure:
- section headers on page background
- content inside cards below headers

Do not reintroduce:
- large stats
- management notes
- oversized utility blocks

Family access must preserve:
- Parent invite
- Kid invite

Expected data:
- page.members
- page.familyMembers
- page.pets
- page.parentInviteCode
- page.kidInviteCode

## Events Contracts

### Events index

Must stay:
- lightweight
- compact
- scan-friendly

Current rules:
- no large stat cards
- inline summary row only
- event cards in compact grid, not long full-width rows

Expected event card data:
- title
- description when present
- startsAt
- endsAt when present
- recurrenceType when present
- recurrenceUntil when present
- participantNames when present
- privateEvent

### Event form

Must stay:
- one main card
- no side helper panel
- compact

Current layout:
- title
- compact description
- row 1: start date / end date / start time
- row 2: recurrence / repeat until / end time
- participants
- private checkbox

Field label contract:
- optionality is expressed in placeholder, not label, for end date / repeat until / time

## Tasks Contracts

### Tasks index

Must stay:
- lightweight
- compact
- scan-friendly

Current rules:
- no large stat cards
- inline summary row only
- task cards in compact grid, not long full-width rows

Expected task card data:
- title
- description when present
- priority
- status
- dueDate when present
- completedAt when present
- assignedUsers / assignedMembers when present

### Task form

Must stay:
- one main card
- no side helper panel
- compact

Current layout:
- title
- compact description
- priority + due date row
- assign to
- save / cancel actions

Removed on purpose:
- `Save and keep editing`

## Notifications Contracts

Notifications page should behave like inbox, not dashboard.

Current rules:
- no sidebar item
- no large stat cards
- compact notification cards
- bell remains the main access point

Expected notification view data:
- unreadCount
- notifications list
- each notification supports:
  - id
  - message
  - type
  - createdAt
  - read

## Refactor Danger Zones

Before merging refactors, verify:
- topbar still shows displayName
- bell still links to notifications
- dashboard summary row still renders correctly
- family page still renders all four expected areas
- event/task forms still use one-card compact layout
- tasks/events index pages still use compact cards
- no removed helper/stat/sidebar modules reappear

## Safe Refactor Rule

If a backend refactor changes:
- model names
- DTO field names
- view model shape
- notification count wiring
- participant/assignee eager loading

then verify the related Thymeleaf templates immediately before considering the refactor complete.

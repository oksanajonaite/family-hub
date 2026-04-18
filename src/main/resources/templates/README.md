# Templates folder guide

Use this folder as the server-rendered UI map for Family Hub.

## Top-level file

### `dashboard.html`
- Main family dashboard.
- Owns the calendar, today summary, upcoming events and due soon tasks.

## Subfolders

### `auth/`
- Login, register and password reset screens.
- Visual style should stay minimal and brand-first.

### `events/`
- `index.html`: family schedule view.
- `form.html`: create and edit event form.

### `tasks/`
- `index.html`: family workflow view.
- `form.html`: create and edit task form.
- `detail.html`: single task detail screen.

### `family/`
- `index.html`: family management home base.
- `setup.html`: first-time family setup flow.

### `members/`
- Member-without-account CRUD screens.

### `pets/`
- Pet CRUD screens.

### `notifications/`
- Notification inbox page reached from the topbar bell.
- This is not a primary sidebar module.

### `admin/`
- Admin-only dashboard and review views.

### `fragments/`
- Reusable shared layout pieces.
- `navbar.html`: authenticated app shell, sidebar and topbar.
- `confirm-modal.html`: shared confirmation modal.

### `error/`
- Generic fallback error templates.

## Editing rules

- Keep page-specific structure in templates and reusable visual logic in `static/css/components.css`.
- When a template owns a major product area, document its role with a short English comment only if the structure is not obvious.
- Do not duplicate navigation patterns in multiple templates when `fragments/navbar.html` should own them.
- If a template stops using a CSS class, clean the class from `components.css` when safe.

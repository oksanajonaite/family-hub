# CSS folder guide

Use this folder as the styling map for Family Hub.

## File responsibilities

### `variables.css`
- Global design tokens.
- Brand colors, text colors, shadows, radii and layout shell sizes live here.
- Change this file first when the whole app needs a tone or spacing update.

### `components.css`
- Main application UI system.
- Owns:
  - app shell (`sidebar`, `topbar`, `content`)
  - dashboard widgets and calendar
  - shared cards, buttons, chips and forms
  - page layouts for family, tasks, events, notifications and admin
  - auth page shared pieces
- If a style is reused across multiple templates, it should usually live here.

### `flatpickr-earth.css`
- Theme overrides for the Flatpickr date and time picker only.
- Keeps the popup aligned with the Family Hub palette.
- Do not move normal form styles here.

## Editing rules

- Prefer changing tokens in `variables.css` before hard-coding a new value in `components.css`.
- Keep comments in English and focused on responsibility, not line-by-line narration.
- Remove dead classes when a template no longer uses them.
- Reuse existing shared classes before creating a page-specific one.

## Quick UI ownership map

- Dashboard shell and calendar: `components.css`
- Family page panels and access cards: `components.css`
- Event and task form layouts: `components.css` + `flatpickr-earth.css`
- Auth branding and cards: `components.css`

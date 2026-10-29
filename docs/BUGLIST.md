# BugList

Updated: 29 October 2026. This is the permanent tracker for the three changes supplied in the chat. Each item is marked Done only after implementation and its relevant checks pass; operator review is recorded separately.

| ID | Requested change | Status | Verification | Operator review |
|---|---|---|---|---|
| BUG-001 | Keep user creation, access changes and password/recovery management in one place; remove the duplicate Users menu. | Done | Real Electron checks: creation, reset/new-password login, activation, roles, administrator recovery code and billing permissions. | Pending |
| BUG-002 | Organize related screens under parent menus and submenus. | Done | Real Electron checks: Sales, Catalog and stock, Insights, Administration; keyboard collapse/expand and short-window Sign out. | Pending |
| BUG-003 | Display the useful error message without Electron/IPC or Error prefixes. | Done | Four formatter tests plus actual mobile/access, local money and login errors displayed without wrappers in Electron. | Pending |

Local frontend typecheck/build and the isolated navigation regression passed. The tests use a temporary database and no customer messages are sent. `npm.cmd run test:ui-errors` and `npm.cmd run test:navigation-desktop` repeat these checks; the latter is included in `test:desktop` and Windows CI. CI/merge evidence is recorded on the corresponding GitHub pull request. Done means implemented and locally verified; Pending review means the operator has not yet signed off.

The navigation structure is:

- Sales → New bill, Invoice history, Customers.
- Catalog and stock → Products, Categories, Inventory, Excel import (administrator), Barcode labels.
- Insights (administrator) → Dashboard, Reports.
- Administration (administrator) → Business controls, Billing settings.
- Business controls → Returns, Expenses, Backup and restore, User access. User creation, access changes, billing-password reset and administrator recovery are all in User access.

Examples: [consolidated user controls](screenshots/navigation-user-access.png), [clean mobile validation message](screenshots/navigation-friendly-error.png).

## Review steps

1. As administrator, open Administration → Business controls → User access. Create a billing user, reset its password, manage access and generate an administrator recovery code. There should be no separate Users entry. Store recovery codes securely.
2. Expand/collapse the sidebar parent menus with mouse and keyboard. Select child screens, check the current-screen highlight and confirm Sign out stays reachable on a short window. Billing users must not see administrator-only groups or actions.
3. Trigger an invalid customer mobile submission and another validation error. Confirm that the message contains the actionable text without `Error invoking remote method`, `catalog:request` or a leading `Error:`. Also check receipt export/import errors when applicable.

The restored Windows Share helper and the unresolved intermittent WhatsApp `0.text/plain` issue are outside these three changes. See [receipt sharing rollback](RECEIPT_SHARING_ROLLBACK.md).

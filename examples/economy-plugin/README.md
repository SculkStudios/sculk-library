# Economy Plugin Recipe

Compile-checked example for a small coin economy built with Sculk Studio.

## Shows

- Typed config with validation.
- `sculk-data` repository plus cache.
- Async repository access with sync replies.
- `/coins`, `/coins pay`, admin mutations, top balances, and reload.
- Shutdown flush for known accounts.

## Commands

| Command | Permission | Purpose |
|---|---|---|
| `/coins` | `economy.coins` | View your balance. |
| `/coins <player>` | `economy.coins` | View another online player's balance. |
| `/coins pay <player> <amount>` | `economy.coins.pay` | Pay another player. |
| `/coins give <player> <amount>` | `economy.admin` | Add coins. |
| `/coins take <player> <amount>` | `economy.admin` | Remove coins. |
| `/coins set <player> <amount>` | `economy.admin` | Set balance. |
| `/coins top` | `economy.coins` | Show top balances. |
| `/coins reload` | `economy.reload` | Reload config. |

# Kits Plugin Recipe

Compile-checked example for config-backed kits with persistent cooldowns and item descriptors.

## Shows

- Kit config with `ItemDescriptor`.
- Persistent cooldown records with `sculk-data`.
- Kit list and preview GUIs.
- Claim, admin give, and reload commands.
- Permission checks and give/drop inventory behavior.

## Commands

| Command | Permission | Purpose |
|---|---|---|
| `/kit` | `kits.use` | Open the kit list. |
| `/kit <name>` | `kits.use.<kit>` | Claim a kit. |
| `/kit preview <name>` | `kits.use` | Preview kit contents. |
| `/kit give <player> <name>` | `kits.admin` | Give a kit without cooldown. |
| `/kit reload` | `kits.reload` | Reload kit config. |

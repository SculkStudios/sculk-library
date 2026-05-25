# Crate System Recipe

Compile-checked example for config-driven crates using item descriptors, PDC keys, GUI previews, weighted rewards, and effects.

## Shows

- Crate and reward config.
- Key items marked with persistent data.
- Preview GUI.
- Weighted reward selection.
- Give/drop reward delivery.
- Reload command and startup validation.

## Commands

| Command | Permission | Purpose |
|---|---|---|
| `/crate preview <crate>` | none | Open reward preview. |
| `/crate open <crate>` | none | Consume a key and roll a reward. |
| `/crate key give <player> <crate> <amount>` | `crate.admin` | Give crate keys. |
| `/crate reload` | `crate.admin` | Reload crate config. |

# ADR-0027: Permissions as descriptors over each platform's native system

**Status**: Accepted · **Date**: 2026-10-12 · **Supersedes**: —

## Context

Plugins check permissions as strings scattered through their code, declare them a second time in
`plugin.yml`, and cannot say "only in the nether" without hand-written checks. Bukkit resolves
defaults (`TRUE`, `FALSE`, `OP`, `NOT_OP`) and children natively once a `Permission` is registered
through `PluginManager.addPermission`, and LuckPerms reads that registry, so registering is the
compatible path. Paper's `removePermission` removes the node from the registry but leaves it in the
default sets it hands every permissible (read from `PaperPermissionManager` in Paper 26.3).
Velocity has no registry of defaults at all: a `PermissionProvider` answers a `Tristate`, players
default to `UNDEFINED` and the console to `TRUE` (`docs/research/d-velocity.md` §6). Clients cache
the command tree; a requirement whose result changes needs `Player.updateCommands()` (ADR-0022),
which touches the player and so must run on its owner (`THREADING.md`). A LuckPerms adapter with
contexts and offline loads belongs in `sculk-luckperms` (`LICENSING.md`), not here.

## Decision

`sculk-permissions` is neutral and depends only on `sculk-core`; the platform-api dependency
`MODULES.md` listed is unnecessary, since registration is a service each runtime exposes.

- A `Permission` descriptor holds a validated lower-case node (an optional final `.*`), a
  description, a `PermissionDefault` (`EVERYONE`, `OPERATORS`, `NON_OPERATORS`, `NOBODY`) and
  children with values. Plugins declare them once as properties of a `PermissionGroup` object,
  which prefixes the nodes and lists them for registration.
- `PermissionCheck` is sealed: a descriptor, a raw node, or a `ContextualPermission` built with
  `permission.where(key, values)`. Context conditions are declarative key/value sets (`world`,
  `gamemode`, `server`), not lambdas, so a platform knows which changes make a cached result stale
  and a future LuckPerms adapter can map them onto its contexts.
- `PermissionSubject` is the neutral subject; `CommandSource` extends it. A descriptor check uses
  the subject's own value where the provider sets one, otherwise the descriptor's default, even when
  it is not registered.
- `SculkPermissions.register` returns a handle owned by the plugin scope. Paper registers Bukkit
  permissions and, on removal, sets the default to `FALSE` before removing, which clears the
  stale default sets and recalculates holders. Online players' trees are resent on their entity
  context after registration changes, never while the runtime is stopping. `follow(check)`
  resends a player's tree a tick after a world or game-mode change for the keys a compiled command
  requirement reads; `PaperCommands` calls it per requirement.
- Velocity keeps the descriptors per runtime in an immutable `PermissionResolver` that applies
  Bukkit's rules where the provider leaves a node undefined. The console is the only operator.
  Velocity re-evaluates `requires` whenever the backend sends a command tree, so it needs no resend.
- `CommandBuilder.permission(check)` sits beside `permission = "node"`, which still declares a raw
  node checked exactly as before.

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| Predicate lambdas for context | Opaque: no platform could tell when to resend trees, and no adapter could translate them. |
| Sculk's own resolution on Paper | Bypasses LuckPerms, `plugin.yml` and other plugins' registrations. |
| A `sculk-permissions-paper` module | The backend is small and every Paper runtime needs it; events set the precedent of living in `sculk-paper`. |
| One proxy-wide registry on Velocity | Plugins load in separate class loaders; a static registry would differ per copy of Sculk. |

## Consequences

Descriptors declare permissions once and in code; `plugin.yml` entries are optional. Registering a
node that `plugin.yml` or another plugin already registered throws, and the whole call is undone.
Registration on Paper must run on the enabling thread or the global region. Velocity registrations
apply to this plugin's checks only, and `OPERATORS` holds for no player there. Context keys other
than world and game mode never change on Paper; Velocity's `server` key is read when checked.
Revisit if Bukkit gains a context API or Velocity a default registry.

# API Policy

## Stability markers

Every public type and member carries exactly one marker.

| Marker | Promise | Allowed changes within a major version |
| --- | --- | --- |
| `@SculkStable` | Source and binary compatible across all 6.x releases. | Additive only: new members with defaults, new types, new overloads that do not change resolution. |
| `@SculkExperimental` | May change in any minor release. Consumers opt in with `@OptIn`. | Anything, recorded in the changelog. |
| `@SculkInternal` | Not API. Excluded from the `.api` dump and from documentation. | Anything, silently. |

An unmarked public symbol fails the build.

## Additional metadata

Where a symbol depends on the platform, it also declares that dependency so the documentation
and the compatibility report can be generated rather than written.

| Annotation | Meaning |
| --- | --- |
| `@SinceMinecraft("1.21.4")` | Native support begins at this version. Older versions follow the capability's documented fallback. |
| `@Platforms(PAPER, FOLIA, CANVAS)` | Where the symbol is available. Absence means every platform. |
| `@Requires(ExecutionKind.ENTITY)` | The ownership a caller must hold. Absence means any thread. |

These are processed by the docs manifest generator and checked by the development thread
checker. They are informational for the compiler.

## Binary compatibility

`.api` dumps are committed for every published module. CI fails when the public surface changes
without a matching dump change. Reviewers treat an API diff with the same seriousness as an
implementation diff: a new line in a dump is a promise.

## Versioning

Semantic versioning on the artifact set as a whole. All `sculk-*` artifacts in one release share
one version and are published together; the BOM and version catalog pin that set.

| Change | Version bump |
| --- | --- |
| Removing or changing a `@SculkStable` symbol | Major |
| Changing a `@SculkExperimental` symbol | Minor |
| Adding stable API, raising a minimum platform version within a supported tier | Minor |
| Bug fix with no public surface change | Patch |

## Deprecation

A stable API is never deleted in the release that replaces it.

1. The replacement ships, marked stable.
2. The old symbol gains `@Deprecated(message, ReplaceWith(...), level = WARNING)` naming the
   exact migration. The changelog carries a before/after example.
3. It stays for at least two minor releases.
4. The level rises to `ERROR` for at least one further minor release.
5. It is removed in the next major release.

Experimental APIs may skip steps 3 and 4 but never step 2.

## Platform and Java

Published artifacts target JVM 21 bytecode. Raising that is a major change. Raising the minimum
supported Minecraft version within the 1.21 to 26.x range is a minor change announced one minor
release in advance. Dropping a platform is a major change.

## Internals tier

`sculk-internals-*` modules are `@SculkExperimental` in their entirety. No `net.minecraft` type
appears in any signature outside them. They may break on a Minecraft update without a major
version bump, and each one exists only with an ADR and a benchmark that justify it.

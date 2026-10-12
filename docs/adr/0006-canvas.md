# ADR-0006: Canvas as a first-class platform

**Status**: Accepted · **Date**: 2026-10-12 · **Supersedes**: —

## Context

Canvas is a Folia fork that upstreams from Paper directly and ships ahead of Folia: Canvas 26.3 is
stable (builds 976–978, 2026-10-11) while Folia has no 26.3 build and its 26.2 builds are beta.
Beyond Folia's model it exposes regionized data with merge and split callbacks, the regionizer with
locked and lock-free lookups, bounding-box and velocity ownership checks, seven async lifecycle
events, async world unload with typed results, restored scoreboards with defined write rules, and a
region tick manager. Its project is active but small (four developers), its documentation lags its
builds, and its API licence is not separately stated (`docs/research/c-folia-canvas.md` §3).

## Decision

Ship `sculk-canvas` as a capability provider layered on `sculk-paper`, detected by brand
`canvasmc:canvas` with a class-presence fallback. Expose regionized data through a typed façade
whose merge and split callbacks are non-suspending lambdas run inside a guard; expose the extra
ownership checks, async lifecycle events, async world unload and tick manager as optional
capabilities. Never require Canvas APIs from modules that also run on Paper. Never depend on which
Canvas scheduler algorithm the operator chose. Compile against `canvas-api` as `compileOnly` from
`maven.canvasmc.io` and never bundle it. Test on the current stable Canvas build in the pull-request
matrix and the newest build nightly.

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| Treat Canvas as Folia | Works for the shared model but discards the features that make Canvas worth targeting. |
| Require Horizon or another mixin launcher | Not required by Canvas; increases operator risk for no framework need. |
| Canvas-only features in core modules | Forces a platform-specific dependency on every Paper user. |

## Consequences

Plugins using Canvas-only APIs declare `canvas-supported`; everything else declares
`folia-supported`. The Canvas provider is the first place a small upstream team can break Sculk, so
its capabilities stay optional and its CI lane fails visibly without blocking releases when Canvas
itself is broken. Revisit if Canvas activity drops or its API licence turns out to forbid
compile-time use.

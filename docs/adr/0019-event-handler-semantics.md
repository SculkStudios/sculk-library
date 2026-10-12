# ADR-0019: Event handler semantics

**Status**: Accepted · **Date**: 2026-10-12 · **Supersedes**: —

## Context

A cancellable Paper event needs its decision before the handler returns. Frameworks that let every
handler `suspend` (MCCoroutine) start the coroutine undispatched, so cancellation works only until
the first suspension point and silently stops working after it; the rule lives in an FAQ, not in the
type. Velocity's model is different: resulted events can be held open through `EventTask` and
continuations, and the proxy waits. Canvas adds async lifecycle events whose pre-events must not
mutate entity state. Events fire in parallel across regions on Folia and Canvas
(`docs/research/e1-commands-coroutines-compat.md` §B.2, `docs/research/d-velocity.md` §3,
`docs/research/c-folia-canvas.md` §1.4).

## Decision

Three handler kinds, distinguished by the API rather than by documentation:

| Kind | API | Contract |
| --- | --- | --- |
| Immediate | `events.listen<E> { }` | Non-suspending lambda; runs on the thread firing the event; may cancel or mutate. |
| Observer | `events.observe<E> { }` | Suspending; launched in the plugin scope after the event completes, starting in the event's ownership context; receives an immutable snapshot where the event is mutable; cannot cancel. |
| Awaitable | `events.await<E> { }` | Suspending and able to set the result, offered only where the platform genuinely waits: Velocity resulted events and Sculk's own events. Each await has a timeout. |

Registration goes through the public `PluginManager.registerEvent` overload; nothing reflects into
the plugin manager. Every handler returns a handle owned by a `ResourceScope`. Exceptions are
reported with the event type, the handler's declaration site and the plugin, and never stop other
handlers. Sculk-defined events may be dispatched consecutively to suspending handlers in priority
order. Canvas pre-events are immediate handlers that the development checker forbids from mutating
the entity.

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| Suspend handlers for every event | Cancellation that works until an invisible line in the code. |
| Two-phase `decide { } then { }` builder | Expressible as `listen` plus `observe`; one more concept for no gain. |

## Consequences

A developer cannot write a suspending handler that appears to cancel an event. The optional
`@Listen` annotation maps to `listen`; a `suspend` function annotated `@Listen` on a cancellable
event is a compile error from the KSP processor. Listener state must be thread-safe on Folia and
Canvas, which the docs state on every event page.

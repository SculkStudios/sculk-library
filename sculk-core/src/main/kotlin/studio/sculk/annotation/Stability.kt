package studio.sculk.annotation

/** Source and binary compatible for every 6.x release. Changes are additive only. */
@SculkStable
@MustBeDocumented
@Retention(AnnotationRetention.BINARY)
public annotation class SculkStable

/** May change in any minor release. Callers opt in so the dependency is visible at the call site. */
@SculkStable
@MustBeDocumented
@RequiresOptIn(message = "Experimental Sculk API: it may change in any minor release.", level = RequiresOptIn.Level.WARNING)
@Retention(AnnotationRetention.BINARY)
public annotation class SculkExperimental

/** Not API. Public only so Sculk modules can share it; excluded from ABI dumps and documentation. */
@SculkStable
@MustBeDocumented
@RequiresOptIn(message = "Sculk internal API: it is not supported and may change at any time.", level = RequiresOptIn.Level.ERROR)
@Retention(AnnotationRetention.BINARY)
public annotation class SculkInternal

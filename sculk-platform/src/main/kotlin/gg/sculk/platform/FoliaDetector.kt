package gg.sculk.platform

/**
 * Detects whether the server runtime is Folia (or a Folia fork such as Canvas).
 *
 * Uses class-presence detection — the standard pattern used by cross-platform libraries.
 * Result is cached at class-load time; no per-call overhead.
 */
internal object FoliaDetector {
    val isFolia: Boolean =
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
}

package gg.sculk.data.driver

import gg.sculk.core.annotation.SculkStable

/** SQL dialect used for upsert and other dialect-specific queries. */
@SculkStable
public enum class SqlDialect {
    SQLITE,
    MYSQL,
}

package gg.sculk.data.orm

import gg.sculk.core.annotation.SculkInternal
import java.sql.ResultSet
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible

/** Describes a single column in a mapped entity table. */
@SculkInternal
public data class ColumnMapping(
    val paramName: String,
    val columnName: String,
    val sqlType: String,
    val isPrimaryKey: Boolean,
    val kotlinType: KClass<*>,
)

/** Full ORM mapping for an entity class. Computed once and cached. */
@SculkInternal
public data class OrmMapping<T : Any>(
    val tableName: String,
    val columns: List<ColumnMapping>,
    val primaryKey: ColumnMapping,
    val klass: KClass<T>,
) {
    /** SQL to create the table if it does not exist. */
    public val createTableSql: String by lazy {
        val cols =
            columns.joinToString(", ") { col ->
                val pk = if (col.isPrimaryKey) " PRIMARY KEY" else ""
                "${col.columnName} ${col.sqlType}$pk"
            }
        "CREATE TABLE IF NOT EXISTS $tableName ($cols)"
    }
}

/** Caches [OrmMapping] instances — reflection is only done once per class. */
@SculkInternal
public object OrmMapper {
    private val cache = ConcurrentHashMap<KClass<*>, OrmMapping<*>>()

    /** Returns the [OrmMapping] for [klass], computing it on first access. */
    @Suppress("UNCHECKED_CAST")
    public fun <T : Any> mappingFor(klass: KClass<T>): OrmMapping<T> = cache.getOrPut(klass) { compute(klass) } as OrmMapping<T>

    private fun <T : Any> compute(klass: KClass<T>): OrmMapping<T> {
        val tableAnnotation = klass.findAnnotation<Table>()
        val tableName =
            tableAnnotation?.name?.takeIf { it.isNotEmpty() }
                ?: camelToSnake(klass.simpleName ?: error("Anonymous classes cannot be mapped"))

        val constructor =
            requireNotNull(klass.primaryConstructor) {
                "Entity class ${klass.simpleName} must have a primary constructor."
            }

        val columns =
            constructor.parameters.map { param ->
                val colAnnotation = param.findAnnotation<Column>()
                val columnName = colAnnotation?.name?.takeIf { it.isNotEmpty() } ?: camelToSnake(param.name!!)
                val isPrimaryKey = param.findAnnotation<PrimaryKey>() != null
                val kotlinType = (param.type.classifier as? KClass<*>) ?: error("Unsupported type for ${param.name}")
                ColumnMapping(
                    paramName = param.name!!,
                    columnName = columnName,
                    sqlType = sqlTypeFor(kotlinType),
                    isPrimaryKey = isPrimaryKey,
                    kotlinType = kotlinType,
                )
            }

        val primaryKey =
            columns.firstOrNull { it.isPrimaryKey }
                ?: error("Entity ${klass.simpleName} has no @PrimaryKey field.")

        return OrmMapping(tableName, columns, primaryKey, klass)
    }

    /** Maps a [ResultSet] row to an instance of [T]. */
    public fun <T : Any> fromResultSet(
        rs: ResultSet,
        mapping: OrmMapping<T>,
    ): T {
        val constructor = requireNotNull(mapping.klass.primaryConstructor)
        constructor.isAccessible = true
        val args = mutableMapOf<KParameter, Any?>()
        for (param in constructor.parameters) {
            val col = mapping.columns.first { it.paramName == param.name }
            args[param] = coerceFromSql(rs.getObject(col.columnName), col.kotlinType)
        }
        return constructor.callBy(args)
    }

    /**
     * Returns all column values from [entity] in the same order as [mapping.columns].
     * Used to bind PreparedStatement parameters.
     */
    public fun <T : Any> valuesOf(
        entity: T,
        mapping: OrmMapping<T>,
    ): List<Any?> {
        val klass = entity::class
        return mapping.columns.map { col ->
            val member = klass.members.firstOrNull { it.name == col.paramName }
            member?.isAccessible = true
            coerceToSql(member?.call(entity))
        }
    }

    /** Returns the primary key value for [entity]. */
    public fun <T : Any> primaryKeyOf(
        entity: T,
        mapping: OrmMapping<T>,
    ): Any? {
        val klass = entity::class
        val member = klass.members.firstOrNull { it.name == mapping.primaryKey.paramName }
        return coerceToSql(member?.call(entity))
    }

    // ---------------------------------------------------------------------------
    // Type helpers
    // ---------------------------------------------------------------------------

    private fun sqlTypeFor(type: KClass<*>): String =
        when (type) {
            Int::class, Long::class, Boolean::class -> "INTEGER"
            Double::class, Float::class -> "REAL"
            UUID::class -> "VARCHAR(36)"
            else -> "TEXT"
        }

    private fun coerceFromSql(
        value: Any?,
        type: KClass<*>,
    ): Any? =
        when (type) {
            UUID::class -> value?.let { UUID.fromString(it.toString()) }
            Int::class -> (value as? Number)?.toInt()
            Long::class -> (value as? Number)?.toLong()
            Double::class -> (value as? Number)?.toDouble()
            Float::class -> (value as? Number)?.toFloat()
            Boolean::class ->
                when (value) {
                    is Boolean -> value
                    is Number -> value.toInt() != 0
                    else -> null
                }
            String::class -> value?.toString()
            else -> value
        }

    /** Converts a Kotlin value to its SQL-compatible representation. */
    public fun coerceToSqlPublic(value: Any?): Any? = coerceToSql(value)

    private fun coerceToSql(value: Any?): Any? =
        when (value) {
            is UUID -> value.toString()
            is Boolean -> if (value) 1 else 0
            else -> value
        }

    private fun camelToSnake(name: String): String = name.replace(Regex("([A-Z])")) { "_${it.value.lowercase()}" }.trimStart('_')
}

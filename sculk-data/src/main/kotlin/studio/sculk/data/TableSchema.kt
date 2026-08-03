package studio.sculk.data

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import studio.sculk.annotation.SculkStable

/** The SQL shape of a column, resolved from its serial descriptor rather than from reflection. */
@SculkStable
public enum class SqlType {
    TEXT,
    INTEGER,
    BIGINT,
    DOUBLE,
    BOOLEAN,
    JSON,
}

/** One mapped column. */
@SculkStable
public data class ColumnSchema(
    public val name: String,
    public val type: SqlType,
    public val nullable: Boolean,
    public val primaryKey: Boolean,
    public val indexed: Boolean,
    /** The element index in the serial descriptor, so the codec can line rows up without a name lookup. */
    public val elementIndex: Int,
    /**
     * The Kotlin property name, which is what the query DSL sees.
     *
     * Equal to [name] unless `@Column` renamed the column. The query DSL builds conditions from
     * `KProperty1.name`, so without this a renamed column produced SQL naming a column that does
     * not exist.
     */
    public val propertyName: String = name,
)

/**
 * An entity's table, derived from its compiler-generated descriptor.
 *
 * No reflection: the previous mapper resolved a `KClass`, walked its members and called
 * `primaryConstructor.callBy` for every row, then did a member scan *per column, per row*. All of
 * that is available statically here.
 */
@SculkStable
@OptIn(ExperimentalSerializationApi::class)
public class TableSchema private constructor(public val table: String, public val columns: List<ColumnSchema>) {
    public val key: ColumnSchema = columns.first { it.primaryKey }

    public val columnNames: List<String> = columns.map { it.name }

    public operator fun get(name: String): ColumnSchema? = columns.firstOrNull { it.name == name }

    /**
     * The column a query DSL term refers to.
     *
     * The DSL names Kotlin properties; the table names columns. Those differ whenever `@Column`
     * renamed one, so every query has to come through here rather than assuming they match.
     *
     * Falls back to a column-name match so `topBy`, which takes a column name, keeps working.
     */
    public fun forProperty(propertyName: String): ColumnSchema? = columns.firstOrNull { it.propertyName == propertyName }
        ?: columns.firstOrNull { it.name == propertyName }

    public companion object {
        @SculkStable
        public fun of(descriptor: SerialDescriptor): TableSchema {
            val table = descriptor.annotations.filterIsInstance<Table>().firstOrNull()?.name
                ?: error("${descriptor.serialName} is missing @Table.")

            val columns = (0 until descriptor.elementsCount).map { index ->
                val annotations = descriptor.getElementAnnotations(index)
                val element = descriptor.getElementDescriptor(index)
                val propertyName = descriptor.getElementName(index)
                ColumnSchema(
                    name = annotations.filterIsInstance<Column>().firstOrNull()?.name ?: propertyName,
                    type = sqlTypeOf(element, annotations),
                    nullable = element.isNullable,
                    primaryKey = annotations.any { it is Id },
                    indexed = annotations.any { it is Index },
                    elementIndex = index,
                    propertyName = propertyName,
                )
            }

            val keys = columns.count { it.primaryKey }
            require(keys == 1) {
                "${descriptor.serialName} must have exactly one @Id, found $keys. " +
                    "Without one, saving cannot tell an update from an insert."
            }
            return TableSchema(table, columns)
        }

        private fun sqlTypeOf(element: SerialDescriptor, annotations: List<Annotation>): SqlType {
            if (annotations.any { it is Json }) return SqlType.JSON
            return when (element.kind) {
                PrimitiveKind.BOOLEAN -> SqlType.BOOLEAN

                PrimitiveKind.BYTE, PrimitiveKind.SHORT, PrimitiveKind.INT -> SqlType.INTEGER

                PrimitiveKind.LONG -> SqlType.BIGINT

                PrimitiveKind.FLOAT, PrimitiveKind.DOUBLE -> SqlType.DOUBLE

                PrimitiveKind.CHAR, PrimitiveKind.STRING -> SqlType.TEXT

                // Enums are stored by name, never by ordinal: reordering the constants would
                // silently reinterpret every row already written.
                SerialKind.ENUM -> SqlType.TEXT

                StructureKind.LIST, StructureKind.MAP, StructureKind.CLASS -> SqlType.JSON

                else -> SqlType.TEXT
            }
        }
    }
}

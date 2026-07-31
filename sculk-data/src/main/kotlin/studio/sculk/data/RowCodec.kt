package studio.sculk.data

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

private val json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}

/**
 * Turns an entity into a column map and back, driven by its serializer.
 *
 * Replaces a reflective mapper that resolved a member by name and called it once per column per
 * row — so reading a hundred rows of a ten-column entity did a thousand member-list scans. The
 * serializer already knows the shape at compile time.
 */
@OptIn(ExperimentalSerializationApi::class)
internal object RowCodec {
    fun <T> encode(serializer: SerializationStrategy<T>, value: T, schema: TableSchema): Map<String, Any?> {
        val encoder = RowEncoder(schema)
        serializer.serialize(encoder, value)
        return encoder.values
    }

    fun <T> decode(deserializer: DeserializationStrategy<T>, row: Map<String, Any?>, schema: TableSchema): T =
        deserializer.deserialize(RowDecoder(schema, row))
}

@OptIn(ExperimentalSerializationApi::class)
private class RowEncoder(private val schema: TableSchema) : AbstractEncoder() {
    val values: MutableMap<String, Any?> = LinkedHashMap()
    private var current = 0

    override val serializersModule: SerializersModule = EmptySerializersModule()

    private fun column(index: Int) = schema.columns.first { it.elementIndex == index }.name

    override fun encodeElement(descriptor: SerialDescriptor, index: Int): Boolean {
        current = index
        return true
    }

    override fun encodeValue(value: Any) {
        values[column(current)] = value
    }

    override fun encodeNull() {
        values[column(current)] = null
    }

    override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) {
        values[column(current)] = enumDescriptor.getElementName(index)
    }

    override fun <T> encodeSerializableElement(descriptor: SerialDescriptor, index: Int, serializer: SerializationStrategy<T>, value: T) {
        current = index
        if (isStructured(serializer.descriptor)) {
            values[column(index)] = json.encodeToString(serializer, value)
        } else {
            serializer.serialize(this, value)
        }
    }

    override fun <T : Any> encodeNullableSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        serializer: SerializationStrategy<T>,
        value: T?,
    ) {
        current = index
        if (value == null) {
            values[column(index)] = null
        } else {
            encodeSerializableElement(descriptor, index, serializer, value)
        }
    }

    private fun isStructured(descriptor: SerialDescriptor): Boolean =
        descriptor.kind is StructureKind && descriptor.kind != StructureKind.OBJECT
}

@OptIn(ExperimentalSerializationApi::class)
private class RowDecoder(private val schema: TableSchema, private val row: Map<String, Any?>) : AbstractDecoder() {
    private var position = 0
    private var current = 0

    override val serializersModule: SerializersModule = EmptySerializersModule()

    private fun value(): Any? = row[schema.columns.first { it.elementIndex == current }.name]

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        while (position < descriptor.elementsCount) {
            val index = position++
            val column = schema.columns.firstOrNull { it.elementIndex == index } ?: continue
            // A column the database does not have yet, or holds NULL for, is skipped when the
            // property has a default. That is what lets a row written before a migration still
            // read: the new field takes its Kotlin default instead of failing the whole load.
            if (row[column.name] == null && descriptor.isElementOptional(index)) continue
            current = index
            return index
        }
        return CompositeDecoder.DECODE_DONE
    }

    override fun decodeNotNullMark(): Boolean = value() != null

    override fun decodeNull(): Nothing? = null

    // JDBC drivers are free to widen: SQLite hands back a Long for a column declared INTEGER, and
    // returns 0/1 for a boolean. Coercing here keeps that out of every entity.
    override fun decodeBoolean(): Boolean = when (val raw = value()) {
        is Boolean -> raw
        is Number -> raw.toInt() != 0
        else -> raw.toString().toBooleanStrict()
    }

    override fun decodeByte(): Byte = (value() as Number).toByte()

    override fun decodeShort(): Short = (value() as Number).toShort()

    override fun decodeInt(): Int = (value() as Number).toInt()

    override fun decodeLong(): Long = (value() as Number).toLong()

    override fun decodeFloat(): Float = (value() as Number).toFloat()

    override fun decodeDouble(): Double = (value() as Number).toDouble()

    override fun decodeChar(): Char = value().toString().first()

    override fun decodeString(): String = value().toString()

    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int {
        val name = value().toString()
        val index = enumDescriptor.getElementIndex(name)
        return if (index == CompositeDecoder.UNKNOWN_NAME) {
            error("'$name' is not a value of ${enumDescriptor.serialName}; the constant may have been renamed.")
        } else {
            index
        }
    }

    override fun <T> decodeSerializableValue(deserializer: DeserializationStrategy<T>): T =
        if (deserializer.descriptor.kind is StructureKind && deserializer.descriptor.kind != StructureKind.OBJECT) {
            json.decodeFromString(deserializer, value()?.toString() ?: "null")
        } else {
            deserializer.deserialize(this)
        }
}

/** The schema for [serializer]'s entity. */
internal fun <T> schemaOf(serializer: KSerializer<T>): TableSchema = TableSchema.of(serializer.descriptor)

/** True when [descriptor] is an enum, for callers that need to know before decoding. */
@OptIn(ExperimentalSerializationApi::class)
internal fun SerialDescriptor.isEnum(): Boolean = kind == SerialKind.ENUM

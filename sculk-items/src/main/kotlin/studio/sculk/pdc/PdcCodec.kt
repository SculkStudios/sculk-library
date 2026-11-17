package studio.sculk.pdc

import net.kyori.adventure.key.Key
import org.bukkit.persistence.PersistentDataAdapterContext
import org.bukkit.persistence.PersistentDataType
import studio.sculk.annotation.SculkStable
import java.nio.ByteBuffer
import java.util.UUID

/**
 * How a value of type [T] is stored in a persistent data container. Built-in codecs cover Paper's
 * primitive types plus UUIDs, keys, enums and lists; [map] derives a codec from an existing one.
 *
 * Stored data is input: a value written by an older plugin version, another plugin or a hand-edited
 * world can have any shape, so reading never throws for bad data and reports it instead.
 */
@SculkStable
public class PdcCodec<T : Any> internal constructor(internal val type: PersistentDataType<*, T>, public val description: String) {
    /** A list of values stored with this codec. */
    public fun list(): PdcCodec<List<T>> = PdcCodec(PersistentDataType.LIST.listTypeFrom(type), "a list of $description")

    /**
     * Stores [R] as [T]. [decode] may throw for a stored value that is not a valid [R], such as an
     * unknown enum name; the read reports it as invalid data.
     */
    public fun <R : Any> map(type: Class<R>, description: String, encode: (R) -> T, decode: (T) -> R): PdcCodec<R> =
        PdcCodec(Mapped(this.type, type, encode, decode), description)

    override fun toString(): String = "PdcCodec($description)"

    @SculkStable
    public companion object {
        public val STRING: PdcCodec<String> = PdcCodec(PersistentDataType.STRING, "text")
        public val BOOLEAN: PdcCodec<Boolean> = PdcCodec(PersistentDataType.BOOLEAN, "true or false")
        public val BYTE: PdcCodec<Byte> = PdcCodec(PersistentDataType.BYTE, "a byte")
        public val SHORT: PdcCodec<Short> = PdcCodec(PersistentDataType.SHORT, "a short whole number")
        public val INT: PdcCodec<Int> = PdcCodec(PersistentDataType.INTEGER, "a whole number")
        public val LONG: PdcCodec<Long> = PdcCodec(PersistentDataType.LONG, "a long whole number")
        public val FLOAT: PdcCodec<Float> = PdcCodec(PersistentDataType.FLOAT, "a decimal number")
        public val DOUBLE: PdcCodec<Double> = PdcCodec(PersistentDataType.DOUBLE, "a decimal number")
        public val BYTES: PdcCodec<ByteArray> = PdcCodec(PersistentDataType.BYTE_ARRAY, "bytes")

        /** A UUID as 16 bytes, the most compact form and the one vanilla uses for UUID arrays' content. */
        public val UUID: PdcCodec<UUID> = BYTES.map(java.util.UUID::class.java, "a UUID", ::uuidBytes, ::bytesUuid)

        /** A namespaced key as `namespace:value` text. */
        public val KEY: PdcCodec<Key> = STRING.map(Key::class.java, "a namespaced key", Key::asString) { Key.key(it) }

        /** An enum constant by name, so reordering constants never changes what stored data means. */
        public fun <E : Enum<E>> enum(type: Class<E>): PdcCodec<E> = STRING.map(
            type,
            "one of ${type.enumConstants.joinToString {
                it.name
            }}",
            { it.name },
        ) { name -> java.lang.Enum.valueOf(type, name) }

        /** [enum] for a reified type. */
        public inline fun <reified E : Enum<E>> enum(): PdcCodec<E> = enum(E::class.java)

        private fun uuidBytes(uuid: UUID): ByteArray =
            ByteBuffer.allocate(16).putLong(uuid.mostSignificantBits).putLong(uuid.leastSignificantBits).array()

        private fun bytesUuid(bytes: ByteArray): UUID {
            require(bytes.size == 16) { "a UUID is 16 bytes, found ${bytes.size}" }
            val buffer = ByteBuffer.wrap(bytes)
            return UUID(buffer.long, buffer.long)
        }
    }

    private class Mapped<P : Any, T : Any, R : Any>(
        private val base: PersistentDataType<P, T>,
        private val complex: Class<R>,
        private val encode: (R) -> T,
        private val decode: (T) -> R,
    ) : PersistentDataType<P, R> {
        override fun getPrimitiveType(): Class<P> = base.primitiveType

        override fun getComplexType(): Class<R> = complex

        override fun toPrimitive(complex: R, context: PersistentDataAdapterContext): P = base.toPrimitive(encode(complex), context)

        override fun fromPrimitive(primitive: P, context: PersistentDataAdapterContext): R = decode(base.fromPrimitive(primitive, context))
    }
}

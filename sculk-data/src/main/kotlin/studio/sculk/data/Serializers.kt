package studio.sculk.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import studio.sculk.annotation.SculkStable
import java.time.Instant
import java.util.UUID

/**
 * Stores a UUID as text rather than as bytes.
 *
 * Sixteen bytes would be smaller. Text is readable in whatever client someone opens during an
 * incident, which is worth more than the bytes on a table keyed by player.
 */
@SculkStable
public object UuidSerializer : KSerializer<UUID> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("java.util.UUID", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: UUID): Unit = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): UUID = UUID.fromString(decoder.decodeString())
}

/** Stores an Instant as epoch millis, which every engine can sort and compare without a timezone. */
@SculkStable
public object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("java.time.Instant", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Instant): Unit = encoder.encodeLong(value.toEpochMilli())

    override fun deserialize(decoder: Decoder): Instant = Instant.ofEpochMilli(decoder.decodeLong())
}

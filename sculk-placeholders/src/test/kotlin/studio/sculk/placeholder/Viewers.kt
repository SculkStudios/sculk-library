package studio.sculk.placeholder

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.identity.Identity
import net.kyori.adventure.pointer.Pointers
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import java.util.UUID

/** An audience with an `Identity.UUID` pointer, as a player is on Paper and Velocity. */
class Viewer(val name: String, val uniqueId: UUID = UUID.nameUUIDFromBytes(name.toByteArray())) : Audience {
    private val pointers = Pointers.builder().withStatic(Identity.UUID, uniqueId).build()

    override fun pointers(): Pointers = pointers

    override fun toString(): String = name
}

/** An audience with no identity, as the console is. */
class Console : Audience

fun Component.plain(): String = PlainTextComponentSerializer.plainText().serialize(this)

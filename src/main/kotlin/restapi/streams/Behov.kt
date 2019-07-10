package restapi.streams

import de.huxhorn.sulky.ulid.ULID
import no.nav.dagpenger.events.Packet
import java.time.LocalDate

data class Behov(
    val behovId: String = ulidGenerator.nextULID(),
    val aktørId: String,
        // TODO: Change to LocalDate
    val beregningsDato: LocalDate
) {
    companion object Mapper {
        private val ulidGenerator = ULID()

        fun toJson(behov: Behov): String = toJson(behov)

        fun fromJson(behov: Behov): String = fromJson(behov)

        fun toPacket(behov: Behov): Packet = Packet("{}").apply {
            this.putValue(PacketKeys.BEHOV_ID, behov.behovId)
            this.putValue(PacketKeys.AKTØR_ID, behov.aktørId)
            this.putValue(PacketKeys.BEREGNINGS_DATO, behov.beregningsDato)
        }
    }

    fun toPacket(): Packet = Mapper.toPacket(this)
}
package com.music36.app.lecture.alac

import android.os.Handler
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.CryptoConfig
import androidx.media3.decoder.DecoderException
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.decoder.SimpleDecoder
import androidx.media3.decoder.SimpleDecoderOutputBuffer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DecoderAudioRenderer
import java.nio.ByteOrder

/** Échec du décodeur ALAC embarqué. */
class ExceptionAlac(message: String, cause: Throwable? = null) : DecoderException(message, cause)

/**
 * Le décodeur ALAC embarqué, branché dans ExoPlayer.
 *
 * Il s'intercale comme n'importe quel décodeur logiciel : ExoPlayer lui confie les trames que
 * l'extracteur MP4 a découpées, il rend du PCM 16 bits, et la suite de la chaîne (volume,
 * égaliseur, sortie flottante, DAC USB) ne voit aucune différence.
 */
@UnstableApi
class DecodeurAlacExo(
    tampons: Int,
    tailleInitiale: Int,
    val config: ConfigAlac
) : SimpleDecoder<DecoderInputBuffer, SimpleDecoderOutputBuffer, ExceptionAlac>(
    Array(tampons) { DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT) },
    Array(tampons) { SimpleDecoderOutputBuffer { it.release() } }
) {
    private val moteur = DecodeurAlac(config)
    private val echantillons = ShortArray(config.frameLength * maxOf(config.canaux, 1))
    private val trame = ByteArray(config.maxFrameBytes.coerceAtLeast(tailleInitiale))

    init {
        setInitialInputBufferSize(tailleInitiale)
    }

    override fun getName(): String = "36music.alac"

    override fun createInputBuffer(): DecoderInputBuffer =
        DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT)

    override fun createOutputBuffer(): SimpleDecoderOutputBuffer =
        SimpleDecoderOutputBuffer { releaseOutputBuffer(it) }

    override fun createUnexpectedDecodeException(erreur: Throwable): ExceptionAlac =
        ExceptionAlac("Décodage ALAC interrompu", erreur)

    override fun decode(
        entree: DecoderInputBuffer,
        sortie: SimpleDecoderOutputBuffer,
        remiseAZero: Boolean
    ): ExceptionAlac? {
        if (remiseAZero) {
            // Rien à réinitialiser : chaque trame ALAC porte ses propres coefficients, aucune
            // information ne traverse les trames. C'est ce qui rend la recherche instantanée.
        }
        return try {
            val donnees = entree.data ?: return null
            val taille = donnees.limit()
            if (taille > trame.size) {
                return ExceptionAlac("Trame ALAC de $taille octets, au-delà du maximum annoncé")
            }
            donnees.position(0)
            donnees.get(trame, 0, taille)

            val lus = moteur.decoder(trame, taille, echantillons)
            val octets = lus * config.canaux * 2
            val cible = sortie.init(entree.timeUs, octets)
            cible.order(ByteOrder.nativeOrder())
            cible.asShortBuffer().put(echantillons, 0, lus * config.canaux)
            cible.position(0)
            cible.limit(octets)
            null
        } catch (erreur: DecodeurAlac.ErreurAlac) {
            ExceptionAlac(erreur.message ?: "trame ALAC illisible", erreur)
        } catch (erreur: RuntimeException) {
            ExceptionAlac("Trame ALAC illisible", erreur)
        }
    }
}

/**
 * Le moteur de rendu audio qui prend en charge `audio/alac`.
 *
 * ⚠️ Il est déclaré **avant** le moteur MediaCodec dans la fabrique : quand le téléphone possède
 * un décodeur ALAC, les deux fonctionnent, mais on préfère celui-ci car son comportement est le
 * même partout — certains décodeurs de constructeurs acceptent le format pour ne rendre que du
 * silence, ce qui est précisément le défaut qu'on cherche à faire disparaître.
 */
@UnstableApi
class RendererAlac(
    poignee: Handler?,
    ecouteur: AudioRendererEventListener?,
    sortie: AudioSink
) : DecoderAudioRenderer<DecodeurAlacExo>(poignee, ecouteur, sortie) {

    override fun getName(): String = "RendererAlac"

    override fun supportsFormatInternal(format: Format): Int {
        if (!MimeTypes.AUDIO_ALAC.equals(format.sampleMimeType, ignoreCase = true)) {
            return C.FORMAT_UNSUPPORTED_TYPE
        }
        // La sortie est du PCM 16 bits : si la chaîne audio ne sait pas la jouer, autant le dire
        // tout de suite plutôt que de décoder pour rien.
        if (!sinkSupportsFormat(formatPcm(format))) return C.FORMAT_UNSUPPORTED_SUBTYPE
        if (format.cryptoType != C.CRYPTO_TYPE_NONE) return C.FORMAT_UNSUPPORTED_DRM
        return C.FORMAT_HANDLED
    }

    override fun createDecoder(format: Format, config: CryptoConfig?): DecodeurAlacExo {
        val cookie = format.initializationData.firstOrNull()
            ?: throw ExceptionAlac("Configuration ALAC absente du fichier")
        val configAlac = ConfigAlac.lire(cookie)
            ?: throw ExceptionAlac("Configuration ALAC illisible (${cookie.size} octets)")
        return DecodeurAlacExo(
            tampons = 4,
            tailleInitiale = configAlac.maxFrameBytes.coerceAtLeast(TAILLE_MINIMALE),
            config = configAlac
        )
    }

    override fun getOutputFormat(decodeur: DecodeurAlacExo): Format = formatPcm(
        Format.Builder()
            .setChannelCount(decodeur.config.canaux)
            .setSampleRate(decodeur.config.echantillonnage)
            .build()
    )

    private fun formatPcm(source: Format): Format = Format.Builder()
        .setSampleMimeType(MimeTypes.AUDIO_RAW)
        .setChannelCount(source.channelCount)
        .setSampleRate(source.sampleRate)
        .setPcmEncoding(C.ENCODING_PCM_16BIT)
        .build()

    private companion object {
        const val TAILLE_MINIMALE = 8 * 1024
    }
}

/**
 * La fabrique de moteurs de rendu de 36Music : celle d'ExoPlayer, plus l'ALAC.
 *
 * Le moteur ALAC est glissé **en tête de la section audio**. ExoPlayer retient le premier qui déclare
 * savoir lire le format ; comme `RendererAlac` ne répond que pour `audio/alac`, aucun autre
 * format n'est détourné — et les fichiers Apple Lossless se lisent désormais sur tous les
 * téléphones, y compris ceux dépourvus de décodeur système.
 */
@UnstableApi
class FabriqueRendus36(contexte: android.content.Context) :
    androidx.media3.exoplayer.DefaultRenderersFactory(contexte) {

    override fun buildAudioRenderers(
        contexte: android.content.Context,
        modeExtension: Int,
        selecteur: androidx.media3.exoplayer.mediacodec.MediaCodecSelector,
        repliDecodeur: Boolean,
        sortie: AudioSink,
        poignee: Handler,
        ecouteur: AudioRendererEventListener,
        rendus: ArrayList<androidx.media3.exoplayer.Renderer>
    ) {
        // ⚠️ On retient où commence la section audio **avant** d'appeler la fabrique d'origine :
        // la liste contient déjà les moteurs vidéo. Insérer en tête, comme on le faisait, plaçait
        // un moteur audio devant eux — un ordre que la fabrique d'ExoPlayer ne produit jamais.
        val debutAudio = rendus.size
        super.buildAudioRenderers(
            contexte, modeExtension, selecteur, repliDecodeur, sortie, poignee, ecouteur, rendus
        )
        rendus.add(debutAudio, RendererAlac(poignee, ecouteur, sortie))
    }
}

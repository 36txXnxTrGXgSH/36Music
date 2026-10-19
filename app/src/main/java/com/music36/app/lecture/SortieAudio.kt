package com.music36.app.lecture

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import com.music36.app.data.formaterFrequence
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Ce que le décodeur reçoit vraiment, par opposition à ce que les étiquettes du fichier annoncent. */
data class InfoSortie(
    val codec: String = "",
    val echantillonnage: Int = 0,
    val canaux: Int = 0,
    val debit: Int = 0,
    val bits: Int = 0,
    val sortieFlottante: Boolean = false
) {
    /** « FLAC · 24 bits · 96 kHz · stéréo ». */
    fun resume(): String = buildList {
        if (codec.isNotBlank()) add(codec)
        if (bits > 0) add("$bits bits")
        if (echantillonnage > 0) add(formaterFrequence(echantillonnage))
        if (debit > 0) add("$debit kbit/s")
        add(
            when (canaux) {
                0 -> "canaux inconnus"
                1 -> "mono"
                2 -> "stéréo"
                else -> "$canaux canaux"
            }
        )
    }.joinToString(" · ")
}

/**
 * La qualité réellement décodée, publiée par le service et lue par l'écran du lecteur.
 *
 * Un objet global plutôt qu'un aller-retour par la `MediaSession` : service et interface vivent
 * dans le même processus, et faire transiter cette information par des `Bundle` de session
 * n'apporterait rien qu'un délai.
 */
@UnstableApi
object SortieAudio {

    private val _info = MutableStateFlow<InfoSortie?>(null)
    val info: StateFlow<InfoSortie?> = _info.asStateFlow()

    /** Le matériel qui reçoit le flux : DAC USB, casque filaire, Bluetooth, haut-parleur. */
    private val _materiel = MutableStateFlow<InfoSortieMaterielle?>(null)
    val materiel: StateFlow<InfoSortieMaterielle?> = _materiel.asStateFlow()

    fun decrireMateriel(sortie: InfoSortieMaterielle?) {
        _materiel.value = sortie
    }

    /**
     * Le décodeur réellement employé (`c2.android.aac.decoder`, `OMX.qcom…`).
     *
     * Sans cette information, un morceau muet ne se distingue pas d'un morceau qu'aucun décodeur
     * n'a pris en charge : c'est la première question à poser quand le son ne sort pas.
     */
    private val _decodeur = MutableStateFlow<String?>(null)
    val decodeur: StateFlow<String?> = _decodeur.asStateFlow()

    fun decrireDecodeur(nom: String?) {
        _decodeur.value = nom
    }

    /**
     * Le morceau que la chaîne de décodage traite **réellement**.
     *
     * L'écran, lui, affiche ce que le `MediaController` annonce. Les deux devraient toujours
     * coïncider ; quand ils divergent, c'est le son qui a raison, et cette ligne est le seul
     * endroit d'où on peut le voir.
     */
    private val _source = MutableStateFlow<String?>(null)
    val source: StateFlow<String?> = _source.asStateFlow()

    fun decrireSource(titre: String?) {
        if (!titre.isNullOrBlank()) _source.value = titre
    }

    fun decrire(format: Format, sortieFlottante: Boolean) {
        _info.value = InfoSortie(
            codec = nomLisible(format.sampleMimeType),
            echantillonnage = format.sampleRate.takeIf { it != Format.NO_VALUE } ?: 0,
            canaux = format.channelCount.takeIf { it != Format.NO_VALUE } ?: 0,
            debit = (format.bitrate.takeIf { it != Format.NO_VALUE } ?: 0) / 1000,
            bits = bits(format.pcmEncoding),
            sortieFlottante = sortieFlottante
        )
    }

    fun effacer() {
        _info.value = null
        _source.value = null
    }

    private fun bits(encodage: Int): Int = when (encodage) {
        C.ENCODING_PCM_8BIT -> 8
        C.ENCODING_PCM_16BIT, C.ENCODING_PCM_16BIT_BIG_ENDIAN -> 16
        C.ENCODING_PCM_24BIT, C.ENCODING_PCM_24BIT_BIG_ENDIAN -> 24
        C.ENCODING_PCM_32BIT, C.ENCODING_PCM_32BIT_BIG_ENDIAN -> 32
        C.ENCODING_PCM_FLOAT -> 32
        else -> 0
    }

    /** Le nom courant d'un codec, à partir de son type MIME. */
    fun nomDeCodec(mime: String?): String = nomLisible(mime)

    private fun nomLisible(mime: String?): String = when (mime) {
        null -> ""
        androidx.media3.common.MimeTypes.AUDIO_MPEG -> "MP3"
        androidx.media3.common.MimeTypes.AUDIO_AAC -> "AAC"
        androidx.media3.common.MimeTypes.AUDIO_FLAC -> "FLAC"
        androidx.media3.common.MimeTypes.AUDIO_ALAC -> "ALAC"
        androidx.media3.common.MimeTypes.AUDIO_RAW -> "PCM"
        androidx.media3.common.MimeTypes.AUDIO_OPUS -> "Opus"
        androidx.media3.common.MimeTypes.AUDIO_VORBIS -> "Vorbis"
        androidx.media3.common.MimeTypes.AUDIO_AC3 -> "AC3"
        androidx.media3.common.MimeTypes.AUDIO_E_AC3 -> "E-AC3"
        androidx.media3.common.MimeTypes.AUDIO_AMR_NB -> "AMR"
        androidx.media3.common.MimeTypes.AUDIO_AMR_WB -> "AMR-WB"
        else -> mime.substringAfter('/').uppercase()
    }
}

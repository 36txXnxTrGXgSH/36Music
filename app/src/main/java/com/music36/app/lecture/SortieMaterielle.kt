package com.music36.app.lecture

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.music36.app.data.formaterFrequence
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Les familles de sorties que l'on distingue à l'écran. */
enum class TypeSortie(val libelle: String) {
    USB("DAC USB"),
    JACK("Casque filaire"),
    BLUETOOTH("Bluetooth"),
    HDMI("HDMI"),
    HAUT_PARLEUR("Haut-parleur"),
    AUTRE("Sortie audio")
}

/**
 * Ce que le matériel de sortie accepte réellement.
 *
 * Un DAC USB annonce ses fréquences et ses profondeurs ; c'est la seule façon de savoir si le
 * 24 bits / 192 kHz du fichier a une chance d'arriver intact jusqu'aux écouteurs.
 */
data class InfoSortieMaterielle(
    val type: TypeSortie,
    val nom: String,
    /** Fréquences acceptées, en Hz. Vide quand le pilote n'en déclare aucune en particulier. */
    val frequences: List<Int> = emptyList(),
    /** Profondeurs acceptées, en bits (32 pour le flottant). */
    val profondeurs: List<Int> = emptyList(),
    val canaux: Int = 0
) {
    val estUsb: Boolean get() = type == TypeSortie.USB

    val frequenceMax: Int get() = frequences.maxOrNull() ?: 0
    val profondeurMax: Int get() = profondeurs.maxOrNull() ?: 0

    /** « DAC USB · jusqu'à 32 bits / 384 kHz ». */
    fun resume(): String = buildList {
        add(nom.ifBlank { type.libelle })
        val capacite = buildList {
            if (profondeurMax > 0) add("$profondeurMax bits")
            if (frequenceMax > 0) add(formaterFrequence(frequenceMax))
        }.joinToString(" / ")
        if (capacite.isNotBlank()) add("jusqu'à $capacite")
    }.joinToString(" · ")
}

/**
 * Surveille la sortie audio active et publie ce qu'elle sait faire.
 *
 * Android route automatiquement le son vers un DAC USB dès qu'on le branche ; ce que le système
 * ne fait pas, c'est le **dire**. Sans cette surveillance, impossible d'afficher la capacité réelle
 * de la chaîne, ni de forcer le routage quand plusieurs sorties cohabitent (Bluetooth appairé et
 * DAC branché en même temps, cas le plus courant).
 */
class SortieMaterielle(private val contexte: Context) {

    private val gestionnaire =
        contexte.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val poignee = Handler(Looper.getMainLooper())

    private val _sortie = MutableStateFlow<InfoSortieMaterielle?>(null)
    val sortie: StateFlow<InfoSortieMaterielle?> = _sortie.asStateFlow()

    /** Le périphérique retenu, à passer tel quel à `ExoPlayer.setPreferredAudioDevice`. */
    var peripheriqueActif: AudioDeviceInfo? = null
        private set

    private val rappel = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(ajoutes: Array<out AudioDeviceInfo>?) = relire()
        override fun onAudioDevicesRemoved(retires: Array<out AudioDeviceInfo>?) = relire()
    }

    fun demarrer() {
        gestionnaire.registerAudioDeviceCallback(rappel, poignee)
        relire()
    }

    private fun relire() {
        val sorties = runCatching {
            gestionnaire.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
        }.getOrElse { emptyList() }

        // On choisit la sortie que l'utilisateur entend : le système privilégie le dernier
        // périphérique branché, et l'USB passe devant tout le reste.
        val choisi = sorties.minByOrNull { rang(it.type) }
        peripheriqueActif = choisi
        _sortie.value = choisi?.let { decrire(it) }
        Log.i(TAG, "Sortie audio : ${_sortie.value?.resume() ?: "inconnue"}")
    }

    private fun decrire(peripherique: AudioDeviceInfo) = InfoSortieMaterielle(
        type = typeDe(peripherique.type),
        nom = runCatching { peripherique.productName?.toString() }.getOrNull().orEmpty()
            .ifBlank { typeDe(peripherique.type).libelle },
        frequences = runCatching { peripherique.sampleRates.toList() }.getOrDefault(emptyList()),
        profondeurs = runCatching {
            peripherique.encodings.toList().mapNotNull { profondeurDe(it) }.distinct().sorted()
        }.getOrDefault(emptyList()),
        canaux = runCatching { peripherique.channelCounts.maxOrNull() }.getOrNull() ?: 0
    )

    private fun typeDe(type: Int): TypeSortie = when (type) {
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> TypeSortie.USB

        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> TypeSortie.JACK

        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> TypeSortie.BLUETOOTH

        AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC -> TypeSortie.HDMI

        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> TypeSortie.HAUT_PARLEUR

        else -> TypeSortie.AUTRE
    }

    /** Priorité d'écoute : plus le rang est petit, plus la sortie est probablement celle utilisée. */
    private fun rang(type: Int): Int = when (typeDe(type)) {
        TypeSortie.USB -> 0
        TypeSortie.JACK -> 1
        TypeSortie.BLUETOOTH -> 2
        TypeSortie.HDMI -> 3
        TypeSortie.HAUT_PARLEUR -> 4
        TypeSortie.AUTRE -> 5
    }

    private fun profondeurDe(encodage: Int): Int? = when (encodage) {
        AudioFormat.ENCODING_PCM_8BIT -> 8
        AudioFormat.ENCODING_PCM_16BIT -> 16
        AudioFormat.ENCODING_PCM_FLOAT -> 32
        else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            when (encodage) {
                AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24
                AudioFormat.ENCODING_PCM_32BIT -> 32
                else -> null
            }
        } else null
    }

    private companion object {
        const val TAG = "36Music/Sortie"
    }
}

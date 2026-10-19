package com.music36.app.lecture

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.util.Log
import com.music36.app.data.EtatReglages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Ce que le matériel accepte réellement : nombre de bandes, plage de gain, préréglages. */
data class InfoEgaliseur(
    val disponible: Boolean = false,
    val bandes: List<Int> = emptyList(),
    val gainMin: Int = -1500,
    val gainMax: Int = 1500,
    val presets: List<String> = emptyList()
)

/**
 * Égaliseur, correcteur de graves, spatialisation et gain, branchés sur la session audio
 * d'ExoPlayer.
 *
 * Tout est enveloppé de `runCatching` : ces effets sont fournis par le constructeur du téléphone
 * et manquent, ou refusent de s'instancier, sur une partie du parc. Un égaliseur absent doit
 * seulement griser l'écran, jamais faire tomber la lecture.
 *
 * ⚠️ Activer un effet insère un traitement dans la chaîne audio. Tant que l'égaliseur est éteint,
 * aucun effet n'est instancié : le flux part au décodeur sans retouche, ce qui est exactement ce
 * qu'on veut pour un fichier haute résolution.
 */
class Egaliseur {

    private var egaliseur: Equalizer? = null
    private var graves: BassBoost? = null
    private var spatial: Virtualizer? = null
    private var gain: LoudnessEnhancer? = null
    private var session: Int = 0

    private val _infos = MutableStateFlow(InfoEgaliseur())
    val infos: StateFlow<InfoEgaliseur> = _infos.asStateFlow()

    /** Rattache les effets à une nouvelle session audio (à chaque recréation du lecteur). */
    fun attacher(sessionAudio: Int, reglages: EtatReglages) {
        if (sessionAudio == 0) return
        if (session == sessionAudio && egaliseur != null) {
            appliquer(reglages)
            return
        }
        detacher()
        session = sessionAudio
        appliquer(reglages)
    }

    /** Applique les réglages ; crée ou détruit les effets selon qu'ils sont demandés. */
    fun appliquer(reglages: EtatReglages) {
        if (session == 0) return
        if (!reglages.egaliseurActif) {
            detacher()
            _infos.value = _infos.value.copy(disponible = _infos.value.disponible)
            return
        }
        runCatching {
            val eq = egaliseur ?: Equalizer(PRIORITE, session).also { egaliseur = it }
            eq.enabled = true
            val nombre = eq.numberOfBands.toInt()
            _infos.value = InfoEgaliseur(
                disponible = true,
                bandes = (0 until nombre).map { eq.getCenterFreq(it.toShort()) / 1000 },
                gainMin = eq.bandLevelRange[0].toInt(),
                gainMax = eq.bandLevelRange[1].toInt(),
                presets = (0 until eq.numberOfPresets).map { eq.getPresetName(it.toShort()) }
            )
            val bandes = reglages.bandes
            if (reglages.egaliseurPreset >= 0 && bandes.isEmpty()) {
                eq.usePreset(reglages.egaliseurPreset.toShort())
            } else {
                bandes.forEachIndexed { index, niveau ->
                    if (index < nombre) eq.setBandLevel(index.toShort(), niveau.toShort())
                }
            }
        }.onFailure { Log.w(TAG, "Égaliseur indisponible", it) }

        appliquerEffet(reglages.bassBoost, { graves }, { graves = it }, {
            BassBoost(PRIORITE, session)
        }) { effet, force -> effet.setStrength(force.toShort()) }

        appliquerEffet(reglages.virtualiseur, { spatial }, { spatial = it }, {
            Virtualizer(PRIORITE, session)
        }) { effet, force -> effet.setStrength(force.toShort()) }

        runCatching {
            if (reglages.gainSonore > 0) {
                val amplificateur = gain ?: LoudnessEnhancer(session).also { gain = it }
                amplificateur.setTargetGain(reglages.gainSonore)
                amplificateur.enabled = true
            } else {
                gain?.release()
                gain = null
            }
        }.onFailure { Log.w(TAG, "Gain sonore indisponible", it) }
    }

    private fun <T : android.media.audiofx.AudioEffect> appliquerEffet(
        force: Int,
        obtenir: () -> T?,
        poser: (T?) -> Unit,
        creer: () -> T,
        regler: (T, Int) -> Unit
    ) {
        runCatching {
            if (force > 0) {
                val effet = obtenir() ?: creer().also { poser(it) }
                regler(effet, force)
                effet.enabled = true
            } else {
                obtenir()?.release()
                poser(null)
            }
        }.onFailure { Log.w(TAG, "Effet audio indisponible", it) }
    }

    fun detacher() {
        runCatching { egaliseur?.release() }
        runCatching { graves?.release() }
        runCatching { spatial?.release() }
        runCatching { gain?.release() }
        egaliseur = null
        graves = null
        spatial = null
        gain = null
    }

    private companion object {
        const val TAG = "36Music/Egaliseur"

        // Priorité positive : la nôtre passe devant celle des applications système génériques.
        const val PRIORITE = 1
    }
}

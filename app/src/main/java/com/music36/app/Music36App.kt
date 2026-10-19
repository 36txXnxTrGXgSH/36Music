package com.music36.app

import android.app.Application
import androidx.media3.common.util.UnstableApi
import com.music36.app.data.BaseMusique
import com.music36.app.data.Depot
import com.music36.app.data.Reglages
import com.music36.app.lecture.Egaliseur
import com.music36.app.lecture.Lecteur
import com.music36.app.lecture.SortieAudio
import com.music36.app.lecture.SortieMaterielle
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Le point de rassemblement de l'application.
 *
 * Base, réglages, dépôt, égaliseur et lecteur sont créés ici, une fois, et partagés par l'écran
 * comme par le service de lecture — les deux vivent dans le même processus. Pas d'injection de
 * dépendances : à cette taille, un conteneur explicite se lit mieux qu'un graphe engendré.
 */
@UnstableApi
class Music36App : Application() {

    /**
     * La portée de toutes les tâches de fond de l'application.
     *
     * Le `SupervisorJob` empêche l'échec d'une tâche d'emporter les autres, et le gestionnaire
     * d'exceptions empêche celles qui restent de faire tomber le processus : une écriture de
     * réglage qui échoue, une analyse interrompue par un dossier devenu illisible, cela se
     * journalise, cela n'arrête pas la musique.
     */
    /** Le nom de version tel qu'il est installé — lu du système, jamais écrit en dur. */
    val nomVersion: String by lazy {
        runCatching { packageManager.getPackageInfo(packageName, 0).versionName }
            .getOrNull().orEmpty()
    }

    /** Son numéro de construction. */
    val codeVersion: Int by lazy {
        runCatching { packageManager.getPackageInfo(packageName, 0).longVersionCode.toInt() }
            .getOrDefault(0)
    }

    val portee = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate +
            CoroutineExceptionHandler { _, erreur ->
                Log.e("36Music", "Tâche de fond interrompue", erreur)
            }
    )

    val base: BaseMusique by lazy { BaseMusique.ouvrir(this) }
    val reglages: Reglages by lazy { Reglages(this) }
    val depot: Depot by lazy { Depot(this, base, reglages, portee) }
    val egaliseur: Egaliseur by lazy { Egaliseur() }
    val lecteur: Lecteur by lazy { Lecteur(this, depot, portee) }

    /**
     * La sortie audio branchée, surveillée pour toute l'application.
     *
     * Elle vit ici et non dans le service : les paramètres doivent pouvoir annoncer « DAC USB
     * détecté » avant même qu'une note ait été jouée.
     */
    val sortieMaterielle: SortieMaterielle by lazy { SortieMaterielle(this) }

    override fun onCreate() {
        super.onCreate()

        sortieMaterielle.demarrer()
        sortieMaterielle.sortie
            .onEach { SortieAudio.decrireMateriel(it) }
            .launchIn(portee)

        // Rien n'est analysé tant qu'aucun dossier n'a été désigné : au tout premier démarrage,
        // la bibliothèque reste vide et l'écran invite à choisir un emplacement.
        portee.launch {
            val etat = reglages.flux.first()
            if (etat.analyseAuDemarrage && base.racines().toutesMaintenant().isNotEmpty()) {
                depot.analyser(complet = false)
            }
        }
    }
}

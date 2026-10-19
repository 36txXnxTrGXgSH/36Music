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
import com.music36.app.data.StyleInterface
import java.io.File
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

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

        imposerLaVueRetroALaPremiereOuverture()

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

    /**
     * Toute nouvelle installation s'ouvre sur la vue Rétro.
     *
     * ⚠️ La valeur par défaut ne suffit pas : à la réinstallation, Android restaure la sauvegarde
     * des réglages, et un ancien choix « Interface moderne » revenait avec elle. Le témoin vit
     * dans `noBackupFilesDir`, que la sauvegarde ne restaure **jamais** : son absence signe une
     * installation neuve, restaurée ou non. Une simple mise à jour garde le choix de chacun.
     *
     * Bloquant, mais une seule fois par installation : écrit en tâche de fond, le réglage
     * pouvait arriver après le premier écran et le faire changer d'interface sous les yeux.
     */
    internal fun imposerLaVueRetroALaPremiereOuverture() {
        val temoin = File(noBackupFilesDir, TEMOIN_PREMIERE_OUVERTURE)
        if (temoin.exists()) return
        val paquet = packageManager.getPackageInfo(packageName, 0)
        // Une mise à jour (installée ≠ mise à jour) garde le choix de chacun, même sans témoin :
        // c'est le cas de tout le monde au passage à la 5.4.
        if (paquet.firstInstallTime == paquet.lastUpdateTime) {
            runBlocking { reglages.modifier { it.copy(style = StyleInterface.RETRO) } }
        }
        runCatching { temoin.createNewFile() }
    }

    internal companion object {
        const val TEMOIN_PREMIERE_OUVERTURE = "premiere_ouverture_faite"
    }
}


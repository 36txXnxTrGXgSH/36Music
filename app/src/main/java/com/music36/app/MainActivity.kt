package com.music36.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.music36.app.data.Piste
import com.music36.app.ui.EcranRacine
import com.music36.app.ui.ThemeMusique
import com.music36.app.data.StyleInterface
import com.music36.app.data.ThemeApplication
import com.music36.app.data.ThemeCouleur

@UnstableApi
class MainActivity : ComponentActivity() {

    private val demandeNotification =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as Music36App

        // La notification de lecture est le seul moyen de piloter la musique écran éteint :
        // on demande l'autorisation au premier lancement, sans en faire un préalable.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            demandeNotification.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val reglages by app.depot.etatReglages.collectAsStateWithLifecycle()
            // ⚠️ Chaque interface a son apparence. La molette est presque toujours regardée
            // dans le noir, la vue classique en plein jour : leur imposer le même thème
            // desservait l'une ou l'autre.
            val molette = reglages.style == StyleInterface.RETRO
            ThemeMusique(
                theme = if (molette) {
                    ThemeApplication.entries.getOrElse(reglages.themeRetro) {
                        ThemeApplication.SOMBRE
                    }
                } else {
                    reglages.theme
                },
                dynamique = if (molette) reglages.dynamiqueRetro else reglages.couleursDynamiques,
                palette = reglages.palette,
                // L'ambiance ne vaut que pour la molette : donnée, elle l'emporte sur la palette.
                themeCouleur = if (molette) {
                    ThemeCouleur.entries.getOrElse(reglages.themeCouleurRetro) {
                        ThemeCouleur.CLASSIQUE
                    }
                } else {
                    null
                },
                teinte = reglages.teinteRetro
            ) {
                EcranRacine(app)
            }
        }

        ouvrirFichierDemande(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        ouvrirFichierDemande(intent)
    }

    /**
     * Ouverture d'un fichier depuis un explorateur.
     *
     * Le fichier n'appartient pas forcément à la bibliothèque : on le joue quand même, en
     * fabriquant une piste minimale à partir de son URI.
     */
    private fun ouvrirFichierDemande(intent: Intent?) {
        val donnee = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data ?: return
        val app = application as Music36App
        val connue = app.depot.pistes.value.firstOrNull { it.uri == donnee.toString() }
        val piste = connue ?: Piste(
            uri = donnee.toString(),
            racine = "",
            dossier = "",
            nomFichier = donnee.lastPathSegment?.substringAfterLast('/').orEmpty(),
            titre = donnee.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.')
                ?: "Lecture",
            artiste = "",
            album = "",
            artisteAlbum = "",
            genre = "",
            annee = 0,
            numeroPiste = 0,
            disque = 0,
            duree = 0,
            taille = 0,
            dateFichier = 0,
            dateAjout = System.currentTimeMillis(),
            format = donnee.lastPathSegment?.substringAfterLast('.', "").orEmpty().uppercase(),
            debit = 0,
            echantillonnage = 0,
            bits = 0,
            canaux = 0
        )
        app.lecteur.jouer(listOf(piste))
    }
}

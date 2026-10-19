package com.music36.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import com.music36.app.data.EtatReglages
import com.music36.app.data.PaletteApplication
import com.music36.app.data.Piste
import com.music36.app.data.StyleInterface
import com.music36.app.data.ThemeApplication
import com.music36.app.lecture.EtatLecture
import com.music36.app.ui.ActionsUi
import com.music36.app.ui.LocalActions
import com.music36.app.ui.LocalEtatLecture
import com.music36.app.ui.LocalReglages
import com.music36.app.ui.ThemeMusique
import com.music36.app.ui.retro.EcranRetro
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * L'écran « Lecture en cours » de l'interface molette et ses six commandes tactiles :
 * favori, ajout à une playlist, et les quatre boutons posés dans les coins de la molette.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@UnstableApi
class EcranLectureRetroTest {

    @get:Rule
    val regle = createComposeRule()

    @Before
    fun couperLeService() {
        org.robolectric.Shadows.shadowOf(
            ApplicationProvider.getApplicationContext<android.app.Application>()
        ).declareActionUnbindable("androidx.media3.session.MediaSessionService")
    }

    private val piste = Piste(
        uri = "content://test/Musique/Hypnotized",
        racine = "content://test",
        dossier = "Musique",
        nomFichier = "Hypnotized.flac",
        titre = "Hypnotized",
        artiste = "Neoni",
        album = "Static Hearts",
        artisteAlbum = "Neoni",
        genre = "Pop",
        annee = 2021,
        numeroPiste = 1,
        disque = 1,
        duree = 166_000,
        taille = 1_000,
        dateFichier = 1,
        dateAjout = 1,
        format = "FLAC",
        debit = 900,
        echantillonnage = 44_100,
        bits = 16,
        canaux = 2
    )

    private fun afficherLecture() = afficher(com.music36.app.ui.retro.NiveauRetro.Lecture)

    /** Monte l'interface molette directement sur le niveau voulu. */
    private fun afficher(depart: com.music36.app.ui.retro.NiveauRetro) {
        val app = ApplicationProvider.getApplicationContext<Music36App>()
        regle.setContent {
            ThemeMusique(ThemeApplication.SOMBRE, dynamique = false, palette = PaletteApplication.NEON) {
                CompositionLocalProvider(
                    LocalActions provides ActionsUi(app, {}, {}, {}, {}),
                    LocalReglages provides EtatReglages(style = StyleInterface.RETRO),
                    LocalEtatLecture provides EtatLecture(
                        piste = piste,
                        file = listOf(piste),
                        index = 0,
                        enLecture = true,
                        position = 30_000,
                        duree = 166_000,
                        repetition = Player.REPEAT_MODE_OFF,
                        connecte = true
                    )
                ) {
                    // On ouvre directement l'écran visé : y descendre à la molette rendait le
                    // test tributaire de l'arrondi du geste.
                    EcranRetro(depart)
                }
            }
        }
    }

    @Test
    fun `l'ecran de lecture porte ses quatre boutons et ses deux raccourcis`() {
        afficherLecture()
        // Les quatre informations demandées à l'écran : titre, artiste, album et année, plus la
        // qualité du fichier.
        regle.onNodeWithText("Hypnotized").assertIsDisplayed()
        regle.onNodeWithText("Neoni").assertIsDisplayed()
        // Sur l'écran simulé, étroit, ces lignes peuvent tomber sous le pli du défilement :
        // on vérifie qu'elles sont composées, pas qu'elles sont à l'image.
        // Album et année partagent une ligne, comme sur l'appareil : « Album · Année ».
        regle.onNodeWithText("Static Hearts · 2021").assertExists()
        regle.onNodeWithText("FLAC", substring = true).assertExists()

        regle.onNodeWithTag("bouton_position").assertIsDisplayed()
        regle.onNodeWithTag("bouton_vitesse").assertIsDisplayed()
        regle.onNodeWithTag("bouton_aleatoire").assertIsDisplayed()
        regle.onNodeWithTag("bouton_repetition").assertIsDisplayed()

        regle.onNodeWithTag("favori_retro").assertIsDisplayed()
        regle.onNodeWithTag("playlist_retro").assertIsDisplayed()
        regle.onNodeWithContentDescription("Ajouter aux favoris").assertExists()
    }

    /** Fait défiler le menu jusqu'à l'entrée voulue : la liste est paresseuse. */
    private fun jusqua(texte: String) {
        regle.onNodeWithTag("liste_retro").performScrollToNode(hasText(texte))
        regle.onNodeWithText(texte).assertExists()
    }

    @Test
    fun `la file d'attente montre ce qui va etre joue`() {
        afficher(com.music36.app.ui.retro.NiveauRetro.FileAttente)
        regle.onNodeWithText("File d'attente").assertExists()
        // L'état de lecture injecté ne contient qu'un titre : c'est lui, et il est en cours.
        regle.onNodeWithText("Hypnotized").assertExists()
        regle.onNodeWithText("en cours").assertExists()
    }

    @Test
    fun `le bouton information ouvre les actions du morceau`() {
        afficherLecture()
        regle.onNodeWithTag("infos_retro").performClick()
        regle.waitForIdle()

        regle.onNodeWithText("Morceau en cours").assertExists()
        listOf(
            "Lire ensuite",
            "Ajouter à la file d'attente",
            "Aller à l'album",
            "Aller à l'artiste",
            "Informations sur le fichier"
        ).forEach { jusqua(it) }
    }

    @Test
    fun `la fiche du fichier reprend les metadonnees lues`() {
        afficher(com.music36.app.ui.retro.NiveauRetro.InfosPiste)

        regle.onNodeWithText("Informations").assertExists()
        listOf("Hypnotized", "Neoni", "Static Hearts", "2021", "FLAC · 16 bits · 44,1 kHz")
            .forEach { jusqua(it) }
    }

    @Test
    fun `le bouton avance-recul change ce que commande la molette`() {
        afficherLecture()
        regle.onNodeWithText("Tournez pour le volume · centre pour lecture/pause · MENU pour revenir")
            .assertExists()

        regle.onNodeWithTag("bouton_position").performClick()
        regle.waitForIdle()
        regle.onNodeWithText("Tournez pour avancer ou reculer de 5 s · centre pour lecture/pause")
            .assertExists()

        // Réappuyer rend la molette au volume : les modes sont des bascules, pas des allers simples.
        regle.onNodeWithTag("bouton_position").performClick()
        regle.waitForIdle()
        regle.onNodeWithText("Tournez pour le volume · centre pour lecture/pause · MENU pour revenir")
            .assertExists()
    }

    @Test
    fun `le bouton vitesse remplace le mode avance-recul`() {
        afficherLecture()
        regle.onNodeWithTag("bouton_position").performClick()
        regle.onNodeWithTag("bouton_vitesse").performClick()
        regle.waitForIdle()

        // Un seul mode à la fois : la vitesse a chassé l'avance/recul.
        regle.onNodeWithText("Tournez pour la vitesse · centre pour lecture/pause").assertExists()
        // La vitesse s'affiche avec le séparateur décimal de l'appareil : on ne teste que le signe.
        regle.onNodeWithText("×", substring = true).assertExists()
    }
}

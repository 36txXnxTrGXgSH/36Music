package com.music36.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import com.music36.app.data.EtatAnalyse
import com.music36.app.data.EtatReglages
import com.music36.app.data.FormatExport
import com.music36.app.data.ResultatDeplacement
import com.music36.app.data.ResultatEffacement
import com.music36.app.data.ThemeApplication
import com.music36.app.data.dossierLisible
import com.music36.app.data.messageDeplacement
import com.music36.app.data.messageEffacement
import com.music36.app.data.nomDeFichierLisible
import com.music36.app.data.resumeAnalyse
import com.music36.app.lecture.EtatLecture
import com.music36.app.ui.ActionsUi
import com.music36.app.ui.LocalActions
import com.music36.app.ui.LocalEtatLecture
import com.music36.app.ui.LocalReglages
import com.music36.app.ui.ThemeMusique
import com.music36.app.ui.retro.CibleAction
import com.music36.app.ui.retro.EcranRetro
import com.music36.app.ui.retro.NiveauRetro
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Ce que la 4.6 corrige et ajoute autour des playlists et de l'analyse.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@UnstableApi
class PlaylistsEtAnalyseTest {

    @get:Rule
    val regle = createComposeRule()

    @Before
    fun couperLeService() {
        org.robolectric.Shadows.shadowOf(
            ApplicationProvider.getApplicationContext<android.app.Application>()
        ).declareActionUnbindable("androidx.media3.session.MediaSessionService")
    }

    private fun afficher(depart: NiveauRetro) {
        val app = ApplicationProvider.getApplicationContext<Music36App>()
        regle.setContent {
            ThemeMusique(ThemeApplication.SOMBRE, dynamique = false) {
                CompositionLocalProvider(
                    LocalActions provides ActionsUi(app, {}, {}, {}, {}),
                    LocalReglages provides EtatReglages(),
                    LocalEtatLecture provides EtatLecture()
                ) {
                    EcranRetro(depart)
                }
            }
        }
    }

    private fun verifierLaLigne(texte: String) {
        regle.onNodeWithTag("liste_retro").performScrollToNode(hasText(texte))
        regle.onNodeWithText(texte).assertIsDisplayed()
    }

    // ------------------------------------------------------------------ l'effacement refusé

    @Test
    fun `un refus d'ecriture dit quoi faire, pas seulement qu'il a echoue`() {
        // ⚠️ Le défaut de la 4.5 : « Effacement refusé : le fichier est toujours là » laissait
        // croire à une panne, alors que le dossier n'était autorisé qu'en lecture.
        val message = messageEffacement(
            ResultatEffacement(effaces = 0, echecs = 1, lectureSeule = true)
        )
        assertTrue("le message doit parler de lecture seule", message.contains("lecture seule"))
        assertTrue("et dire où aller", message.contains("Dossiers de musique"))

        assertTrue(
            messageDeplacement(ResultatDeplacement(0, 1, lectureSeule = true))
                .contains("lecture seule")
        )
        // Sans ce cas particulier, le message habituel reste celui d'avant.
        assertEquals("Fichier effacé", messageEffacement(ResultatEffacement(1, 0)))
    }

    // ------------------------------------------------------------------ le bilan d'analyse

    @Test
    fun `une analyse sans changement le dit`() {
        assertEquals("Aucune modification", resumeAnalyse(EtatAnalyse(termineeLe = 1L)))
    }

    @Test
    fun `le bilan enumere ce qui a change`() {
        val bilan = resumeAnalyse(
            EtatAnalyse(
                termineeLe = 1L,
                ajoutees = 12,
                retirees = 1,
                playlistsAjoutees = 2,
                playlistsRetirees = 1,
                illisibles = 3
            )
        )
        assertTrue(bilan, bilan.contains("12 titres ajoutés"))
        assertTrue(bilan, bilan.contains("1 titre retiré"))
        assertTrue(bilan, bilan.contains("2 playlists importées"))
        assertTrue(bilan, bilan.contains("1 playlist retirée"))
        assertTrue(bilan, bilan.contains("3 fichiers gardés"))
    }

    @Test
    fun `une analyse en echec annonce l'erreur plutot qu'un bilan vide`() {
        val bilan = resumeAnalyse(EtatAnalyse(erreur = "Autorisation perdue"))
        assertTrue(bilan, bilan.startsWith("Analyse interrompue"))
        assertTrue(bilan, bilan.contains("Autorisation perdue"))
    }

    // ------------------------------------------------------------------ le chemin d'un .m3u

    @Test
    fun `un chemin SAF se lit en francais`() {
        assertEquals(
            "Mémoire interne / Music/Playlists",
            dossierLisible("primary:Music/Playlists/BEST ALL Genre.m3u")
        )
        assertEquals("BEST ALL Genre.m3u", nomDeFichierLisible("primary:Music/Playlists/BEST ALL Genre.m3u"))
        assertEquals("Carte SD (EAB4-6BE0) / Musique", dossierLisible("EAB4-6BE0:Musique/liste.m3u"))
        // Un fichier posé à la racine du volume n'a pas de dossier à annoncer.
        assertEquals("Mémoire interne", dossierLisible("primary:liste.m3u"))
        // Et un identifiant inattendu ne doit jamais rendre une chaîne vide.
        assertTrue(dossierLisible("").isNotBlank())
    }

    // ------------------------------------------------------------------ les écrans

    @Test
    fun `l'ecran d'ajout propose de creer une playlist`() {
        afficher(NiveauRetro.ChoixPlaylist(listOf("content://test/x.flac")))
        verifierLaLigne("Nouvelle playlist")
        verifierLaLigne("Valider")
    }

    @Test
    fun `l'export s'atteint depuis les reglages`() {
        afficher(NiveauRetro.Reglages)
        verifierLaLigne("Exporter les playlists")
    }

    @Test
    fun `l'export propose d'ecrire dans un dossier ou de partager`() {
        afficher(NiveauRetro.ChoixExport(FormatExport.M3U8))
        verifierLaLigne("Exporter")
        verifierLaLigne("Partager")
    }

    @Test
    fun `le menu d'une playlist annonce d'ou elle vient`() {
        afficher(NiveauRetro.ActionsLongues(CibleAction.Playlist(42L, "Ma liste")))
        verifierLaLigne("Fichier")
        // Sans fichier d'origine connu, on le dit plutôt que de laisser la ligne vide.
        verifierLaLigne("Créée dans l'application : aucun fichier sur l'appareil")
    }

    @Test
    fun `l'analyse s'annonce depuis le menu des reglages`() {
        afficher(NiveauRetro.Reglages)
        verifierLaLigne("Analyser les dossiers")
        verifierLaLigne("Le bilan s'affiche à la fin")
    }
}

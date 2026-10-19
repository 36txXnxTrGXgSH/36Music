package com.music36.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import com.music36.app.data.EtatReglages
import com.music36.app.data.FormatExport
import com.music36.app.data.ReglageNumerique
import com.music36.app.data.StyleInterface
import com.music36.app.data.ThemeApplication
import com.music36.app.data.avecBande
import com.music36.app.data.niveauDeBande
import com.music36.app.data.valeurSuivante
import com.music36.app.lecture.EtatLecture
import com.music36.app.ui.ActionsUi
import com.music36.app.ui.LocalActions
import com.music36.app.ui.LocalEtatLecture
import com.music36.app.ui.LocalReglages
import com.music36.app.ui.ThemeMusique
import com.music36.app.ui.retro.EcranRetro
import com.music36.app.ui.retro.NiveauRetro
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Les réglages de l'application, à la molette.
 *
 * ⚠️ Ce qui est vérifié ici, c'est qu'ils sont **atteignables sans le doigt** : depuis la 4.3,
 * l'interface moderne ne sert plus qu'à regarder, et un réglage qu'on ne peut plus toucher
 * qu'en repassant par elle serait un réglage perdu.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@UnstableApi
class ParametresRetroTest {

    @get:Rule
    val regle = createComposeRule()

    @Before
    fun couperLeService() {
        org.robolectric.Shadows.shadowOf(
            ApplicationProvider.getApplicationContext<android.app.Application>()
        ).declareActionUnbindable("androidx.media3.session.MediaSessionService")
    }

    private fun afficher(depart: NiveauRetro, reglages: EtatReglages = EtatReglages()) {
        val app = ApplicationProvider.getApplicationContext<Music36App>()
        regle.setContent {
            ThemeMusique(ThemeApplication.SOMBRE, dynamique = false) {
                CompositionLocalProvider(
                    LocalActions provides ActionsUi(app, {}, {}, {}, {}),
                    LocalReglages provides reglages,
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

    private fun tournerMolette(depuisDegres: Float, versDegres: Float) {
        regle.onNodeWithTag("molette").performTouchInput {
            val rayon = (width.coerceAtMost(height) / 2f) * 0.72f
            swipe(
                curve = { t ->
                    val avancement = (t / 400f).coerceIn(0f, 1f)
                    val angle = Math.toRadians(
                        (depuisDegres + (versDegres - depuisDegres) * avancement).toDouble()
                    )
                    Offset(
                        center.x + rayon * kotlin.math.cos(angle).toFloat(),
                        center.y + rayon * kotlin.math.sin(angle).toFloat()
                    )
                },
                durationMillis = 400
            )
        }
        regle.waitForIdle()
    }

    @Test
    fun `l'application s'ouvre sur la molette`() {
        // ⚠️ C'est la demande de la 4.3 : la molette est l'interface principale. Le défaut se
        // lit dans le modèle, avant toute lecture de DataStore.
        assertEquals(StyleInterface.RETRO, EtatReglages().style)
    }

    @Test
    fun `les reglages de l'application listent toutes les sections`() {
        afficher(NiveauRetro.ParametresApp)
        listOf(
            "Dossiers de musique", "Bibliothèque", "Lecture", "Qualité sonore",
            "Apparence", "Playlists", "Formats reconnus"
        ).forEach { verifierLaLigne(it) }
    }

    @Test
    fun `le menu Reglages mene aux parametres et a l'interface moderne`() {
        afficher(NiveauRetro.Reglages)
        verifierLaLigne("Paramètres de l'application")
        verifierLaLigne("Réglages de la vue Rétro")
        verifierLaLigne("Interface moderne")
    }

    @Test
    fun `un oui-non se lit au bout de la ligne`() {
        afficher(NiveauRetro.ParamLecture, EtatReglages(sauterSilences = false))
        verifierLaLigne("Sauter les silences")
        // Trois « non » au moins dans cet écran : ce qu'on vérifie, c'est que l'état est écrit.
        regle.onNodeWithTag("liste_retro").performScrollToNode(hasText("Sauter les silences"))
        regle.onAllNodesWithTextFr("non")
    }

    @Test
    fun `un cadran s'ouvre sur la valeur enregistree`() {
        afficher(
            NiveauRetro.ParamNombre(ReglageNumerique.VITESSE),
            EtatReglages(vitesse = 1.5f)
        )
        regle.onNodeWithTag("cadran").assertIsDisplayed()
        // ⚠️ Le texte attendu est **calculé**, pas écrit : la virgule décimale suit la
        // langue de l'appareil, et le test tournerait autrement sur une machine en anglais.
        regle.onNodeWithText(ReglageNumerique.VITESSE.texte(1.5f)).assertExists()
    }

    @Test
    fun `tourner la molette deplace la valeur du cadran`() {
        afficher(
            NiveauRetro.ParamNombre(ReglageNumerique.VITESSE),
            EtatReglages(vitesse = 1.5f)
        )
        val depart = ReglageNumerique.VITESSE.texte(1.5f)
        regle.onNodeWithText(depart).assertExists()
        tournerMolette(0f, 90f)
        regle.onNodeWithText(depart).assertDoesNotExist()
    }

    @Test
    fun `l'export propose les trois formats`() {
        afficher(NiveauRetro.ParamFormatExport)
        FormatExport.entries.forEach { verifierLaLigne(it.libelle) }
    }

    @Test
    fun `l'export demande ensuite ce qu'on exporte`() {
        afficher(NiveauRetro.ChoixExport(FormatExport.M3U8))
        // ⚠️ « Exporter » est en **tête**, et non en bas : la molette se pose sur la première
        // ligne, et l'écran doit dire d'emblée ce qu'il attend.
        verifierLaLigne("Exporter")
        verifierLaLigne("Tout cocher")
    }

    @Test
    fun `un cadran ne franchit jamais ses bornes`() {
        // ⚠️ Contrairement au cercle chromatique, un réglage continu ne boucle pas : passer de
        // 2,5× à 0,5× en un cran serait une mauvaise surprise, jamais une intention.
        ReglageNumerique.entries.forEach { reglage ->
            assertEquals(
                "${reglage.libelle} : plancher",
                reglage.minimum,
                valeurSuivante(reglage, reglage.minimum, -1),
                0.0001f
            )
            assertEquals(
                "${reglage.libelle} : plafond",
                reglage.maximum,
                valeurSuivante(reglage, reglage.maximum, 1),
                0.0001f
            )
            // Et le pas doit permettre de traverser la plage en un nombre de crans raisonnable.
            val crans = (reglage.maximum - reglage.minimum) / reglage.pas
            assertTrue(
                "${reglage.libelle} : $crans crans pour traverser la plage",
                crans in 1f..200f
            )
        }
    }

    @Test
    fun `chaque reglage numerique se relit comme il a ete ecrit`() {
        ReglageNumerique.entries.forEach { reglage ->
            val milieu = reglage.minimum + (reglage.maximum - reglage.minimum) / 2f
            val arrondi = reglage.minimum +
                ((milieu - reglage.minimum) / reglage.pas).toInt() * reglage.pas
            val apres = reglage.appliquer(EtatReglages(), arrondi)
            assertEquals(
                "${reglage.libelle} : écrit $arrondi, relu ${reglage.valeurDe(apres)}",
                arrondi,
                reglage.valeurDe(apres),
                reglage.pas
            )
            assertTrue(
                "${reglage.libelle} : la valeur doit s'écrire en toutes lettres",
                reglage.texte(arrondi).isNotBlank()
            )
        }
    }

    @Test
    fun `regler une bande a la main quitte le prereglage`() {
        val depart = EtatReglages(egaliseurPreset = 3, egaliseurBandes = "")
        val apres = avecBande(depart, index = 2, valeur = 600f, nombreDeBandes = 5)
        assertEquals(-1, apres.egaliseurPreset)
        assertEquals(600f, niveauDeBande(apres, 2), 0.001f)
        assertEquals("les cinq bandes doivent exister", 5, apres.bandes.size)
        assertEquals("les autres bandes restent à plat", 0f, niveauDeBande(apres, 0), 0.001f)
    }
}

/** Petit raccourci de lecture : la présence d'au moins un nœud portant ce texte. */
private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextFr(
    texte: String
) {
    onAllNodes(hasText(texte)).fetchSemanticsNodes().let {
        assertTrue("aucune ligne ne porte « $texte »", it.isNotEmpty())
    }
}

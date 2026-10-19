package com.music36.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import com.music36.app.data.EtatReglages
import com.music36.app.data.PaletteApplication
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
import androidx.compose.ui.test.performClick
import com.music36.app.ui.retro.NiveauRetro

/**
 * L'interface molette : se compose-t-elle, et la molette commande-t-elle vraiment les menus ?
 *
 * L'appui au centre est simulé au vrai endroit — au milieu du composant — parce que c'est la
 * géométrie qui décide de l'action, pas un `onClick` posé sur un bouton.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@UnstableApi
class EcranRetroTest {

    @get:Rule
    val regle = createComposeRule()

    @Before
    fun couperLeService() {
        org.robolectric.Shadows.shadowOf(
            ApplicationProvider.getApplicationContext<android.app.Application>()
        ).declareActionUnbindable("androidx.media3.session.MediaSessionService")
    }

    private fun afficher(depart: NiveauRetro = NiveauRetro.Racine) {
        val app = ApplicationProvider.getApplicationContext<Music36App>()
        regle.setContent {
            ThemeMusique(ThemeApplication.SOMBRE, dynamique = false, palette = PaletteApplication.NEON) {
                CompositionLocalProvider(
                    LocalActions provides ActionsUi(
                        app = app,
                        ouvrirMenuPiste = {},
                        ajouterAPlaylist = {},
                        choisirDossier = {},
                        message = {}
                    ),
                    LocalReglages provides EtatReglages(style = StyleInterface.RETRO),
                    LocalEtatLecture provides EtatLecture()
                ) {
                    EcranRetro(depart)
                }
            }
        }
    }

    @Test
    fun `la molette et le menu racine s'affichent`() {
        afficher()
        regle.onNodeWithText("MENU").assertExists()
        regle.onNodeWithText("36Music", substring = true).assertExists()
        regle.onNodeWithText("Musique").assertExists()
        // La liste est paresseuse : on ne vérifie que ce qui tient dans l'écran simulé.
        regle.onNodeWithText("Lecture aléatoire").assertExists()
    }

    @Test
    fun `un appui au centre ouvre l'entree selectionnee`() {
        afficher()
        regle.onNodeWithTag("molette").performTouchInput { click(center) }
        regle.waitForIdle()
        // « Musique » était la première entrée : on doit être entré dans son sous-menu.
        regle.onNodeWithText("Toutes les chansons").assertExists()
        regle.onNodeWithText("Artistes").assertExists()
    }

    /**
     * Fait tourner le doigt sur l'anneau, de [depuisDegres] à [versDegres].
     *
     * Zéro degré est à droite, -90 en haut ; le sens horaire est celui des degrés croissants.
     * Le rayon reste à 72 % pour rester sur l'anneau, seule zone sensible à la rotation.
     */
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
    fun `tourner descend d'autant de lignes que de crans`() {
        afficher()
        // Sans lecture en cours, la racine tient trois entrées : Musique, Lecture aléatoire,
        // Réglages. Un quart de tour vaut six crans : la sélection se pose sur la dernière.
        tournerMolette(depuisDegres = -90f, versDegres = 0f)
        regle.onNodeWithTag("molette").performTouchInput { click(center) }
        regle.waitForIdle()

        // ⚠️ C'est le défaut de la 1.1 : la sélection restait bloquée sur la deuxième ligne,
        // parce que `pointerInput(Unit)` gardait la lambda de la première composition.
        regle.onNodeWithText("Bibliothèque").assertExists()
    }

    @Test
    fun `la molette commande le menu ouvert et non le premier`() {
        afficher()
        regle.onNodeWithTag("molette").performTouchInput { click(center) }
        regle.waitForIdle()
        regle.onNodeWithText("Toutes les chansons").assertExists()

        // Un cran vers le bas dans le sous-menu, puis validation : on doit ouvrir « Artistes ».
        tournerMolette(depuisDegres = -90f, versDegres = -70f)
        regle.onNodeWithTag("molette").performTouchInput { click(center) }
        regle.waitForIdle()

        regle.onNodeWithText("Toutes les chansons").assertDoesNotExist()
        regle.onNodeWithText("Artistes").assertExists()
    }

    @Test
    fun `MENU revient au niveau precedent`() {
        afficher()
        regle.onNodeWithTag("molette").performTouchInput { click(center) }
        regle.waitForIdle()
        regle.onNodeWithText("Toutes les chansons").assertExists()

        // Le quartier MENU occupe le haut de l'anneau.
        regle.onNodeWithTag("molette").performTouchInput {
            click(Offset(width / 2f, height * 0.06f))
        }
        regle.waitForIdle()
        regle.onNodeWithText("Lecture aléatoire").assertExists()
    }

    @Test
    fun `le bouton ABC est present en navigation`() {
        afficher()
        // Il occupe le coin que les boutons de lecture laissent libre hors de l'écran de lecture.
        regle.onNodeWithTag("bouton_abc").assertExists()
        regle.onNodeWithText("ABC").assertExists()
    }

    @Test
    fun `le bouton ABC cede la place sur l'ecran de lecture`() {
        // Ce coin y porte le réglage de vitesse : deux boutons ne peuvent pas s'y superposer.
        afficher(NiveauRetro.Lecture)
        regle.onNodeWithTag("bouton_abc").assertDoesNotExist()
        regle.onNodeWithTag("bouton_vitesse").assertExists()
    }

    @Test
    fun `le bouton ABC annonce le mode dans l'aide du bas`() {
        afficher()
        regle.onNodeWithText("Tournez pour choisir", substring = true).assertExists()
        regle.onNodeWithTag("bouton_abc").performClick()
        regle.waitForIdle()
        regle.onNodeWithText("Tournez pour changer de lettre", substring = true).assertExists()
    }
}

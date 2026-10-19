package com.music36.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import com.music36.app.data.CategoriePalette
import com.music36.app.data.EtatReglages
import com.music36.app.data.StyleInterface
import com.music36.app.data.ThemeApplication
import com.music36.app.data.ThemeCouleur
import com.music36.app.lecture.EtatLecture
import com.music36.app.ui.ActionsUi
import com.music36.app.ui.LocalActions
import com.music36.app.ui.LocalEtatLecture
import com.music36.app.ui.LocalReglages
import com.music36.app.ui.ThemeMusique
import com.music36.app.ui.retro.EcranRetro
import com.music36.app.ui.retro.NiveauRetro
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Le choix des couleurs de la vue molette : catégories, ambiances, puis la barre de teintes.
 *
 * ⚠️ Ce qui est vérifié ici, c'est qu'on **atteint** la barre et qu'elle répond à la molette.
 * Le calcul des couleurs, lui, est mesuré par `ThemesCouleurTest` : un écran qui s'affiche ne
 * prouve rien sur la lisibilité de ce qu'il affiche.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@UnstableApi
class EcranPaletteRetroTest {

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
                    LocalReglages provides EtatReglages(style = StyleInterface.RETRO),
                    LocalEtatLecture provides EtatLecture()
                ) {
                    EcranRetro(depart)
                }
            }
        }
    }

    /** Fait tourner le doigt sur l'anneau, comme dans les autres tests de molette. */
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

    /**
     * Amène la ligne sous les yeux avant de l'affirmer présente.
     *
     * ⚠️ La liste ne compose que ce qu'elle affiche : sur l'écran réduit du test, la sixième
     * ligne n'existe tout simplement pas tant qu'on n'y est pas descendu.
     */
    private fun verifierLaLigne(texte: String) {
        regle.onNodeWithTag("liste_retro").performScrollToNode(hasText(texte))
        regle.onNodeWithText(texte).assertIsDisplayed()
    }

    @Test
    fun `les six categories sont proposees`() {
        afficher(NiveauRetro.PaletteRetro)
        CategoriePalette.entries.forEach { categorie -> verifierLaLigne(categorie.libelle) }
    }

    @Test
    fun `une categorie montre ses ambiances`() {
        afficher(NiveauRetro.CategoriePaletteRetro(CategoriePalette.ENERGIQUES))
        listOf("Vif", "Néon", "Pop", "Électrique").forEach { nom -> verifierLaLigne(nom) }
    }

    @Test
    fun `la barre de couleurs s'ouvre sur la teinte de l'ambiance`() {
        afficher(NiveauRetro.TeintePaletteRetro(ThemeCouleur.VIF))
        regle.onNodeWithTag("barre_teintes").assertIsDisplayed()
        regle.onNodeWithTag("apercu_teinte").assertExists()
        // Vif s'ouvre sur son orange : 28°.
        regle.onNodeWithText("28°").assertExists()
    }

    @Test
    fun `tourner la molette deplace le curseur sur la barre`() {
        afficher(NiveauRetro.TeintePaletteRetro(ThemeCouleur.VIF))
        regle.onNodeWithText("28°").assertExists()

        tournerMolette(0f, 90f)

        // ⚠️ On ne vérifie pas une valeur précise — le nombre de crans dépend de l'arrondi du
        // geste — mais que la teinte a bougé. Un curseur qui ne bouge pas est le seul vrai
        // défaut possible ici.
        regle.onNodeWithText("28°").assertDoesNotExist()
    }
}

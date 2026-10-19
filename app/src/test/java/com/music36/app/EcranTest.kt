package com.music36.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.core.app.ApplicationProvider
import androidx.media3.common.util.UnstableApi
import com.music36.app.ui.EcranRacine
import com.music36.app.ui.EtatAccueil
import com.music36.app.ui.Navigateur
import com.music36.app.ui.ThemeMusique
import com.music36.app.data.StyleInterface
import com.music36.app.data.ThemeApplication
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick

/**
 * L'écran se compose-t-il vraiment ?
 *
 * Sans téléphone, c'est le seul moyen d'attraper ce que le compilateur laisse passer : un
 * `CompositionLocal` non fourni, un état lu trop tôt, une liste sans clé stable. On vérifie le
 * premier démarrage — bibliothèque vide, aucun dossier choisi — qui est exactement ce que voit
 * l'utilisateur en installant l'application.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@UnstableApi
class EcranTest {

    @get:Rule
    val regle = createComposeRule()

    /**
     * Robolectric ne sait pas rendre un service `MediaSessionService` : sa liaison factice
     * renvoie un `ComponentName` nul, sur lequel media3 s'effondre avant même de nous prévenir.
     * On déclare donc la liaison impossible — le lecteur reste simplement « non connecté »,
     * ce qui est exactement l'état de l'écran tant qu'aucune musique n'a démarré.
     */
    @Before
    fun couperLeService() {
        org.robolectric.Shadows.shadowOf(
            ApplicationProvider.getApplicationContext<android.app.Application>()
        ).declareActionUnbindable("androidx.media3.session.MediaSessionService")
    }

    /**
     * Ces trois tests regardent l'**interface moderne**, qui n'est plus celle du démarrage.
     *
     * ⚠️ Depuis la 4.3, l'application s'ouvre sur la molette. Le réglage est donc posé avant de
     * composer, et en `runBlocking` : écrit par la portée de l'application, il arriverait après
     * la première image et le test verrait la molette.
     */
    private fun ouvrirLInterfaceModerne() {
        val app = ApplicationProvider.getApplicationContext<Music36App>()
        kotlinx.coroutines.runBlocking {
            app.depot.reglages.modifier { it.copy(style = StyleInterface.MODERNE) }
        }
    }

    @After
    fun nettoyer() {
        Navigateur.revenirALAccueil()
        EtatAccueil.page = -1
    }

    @Test
    fun `le premier demarrage invite a choisir un dossier`() {
        ouvrirLInterfaceModerne()
        val app = ApplicationProvider.getApplicationContext<Music36App>()
        regle.setContent {
            ThemeMusique(ThemeApplication.SOMBRE, dynamique = false) {
                EcranRacine(app)
            }
        }
        regle.onNodeWithText("Aucun dossier de musique").assertIsDisplayed()
        regle.onNodeWithText("Choisir un dossier").assertIsDisplayed()
    }

    @Test
    fun `les sept onglets sont presents`() {
        ouvrirLInterfaceModerne()
        val app = ApplicationProvider.getApplicationContext<Music36App>()
        regle.setContent {
            ThemeMusique(ThemeApplication.SOMBRE, dynamique = false) {
                EcranRacine(app)
            }
        }
        // `assertExists` et non `assertIsDisplayed` : la barre d'onglets défile, les derniers
        // onglets sont composés mais hors de l'écran.
        listOf("Sons", "Playlists", "Dossiers", "Artistes", "Albums", "Genres", "Favoris")
            .forEach { regle.onNodeWithText(it).assertExists() }
    }

    /**
     * ⚠️ L'interface moderne n'a **plus** d'écran de réglages : « Paramètres » ramène à la
     * molette et y ouvre directement le bon menu. C'est le seul chemin qui reste, et c'est
     * exactement ce qu'il faut vérifier — deux écrans pour les mêmes champs, c'était un de trop.
     */
    @Test
    fun `le menu Parametres ramene a la molette sur les reglages`() {
        ouvrirLInterfaceModerne()
        val app = ApplicationProvider.getApplicationContext<Music36App>()
        regle.setContent {
            ThemeMusique(ThemeApplication.SOMBRE, dynamique = false) {
                EcranRacine(app)
            }
        }
        regle.onNodeWithContentDescription("Menu").performClick()
        regle.onNodeWithText("Paramètres").performClick()

        // Le basculement passe par DataStore : on attend la molette plutôt que de supposer
        // qu'elle est déjà là au retour de `waitForIdle`.
        regle.waitUntil(10_000) {
            regle.onAllNodesWithText("Paramètres de l'application")
                .fetchSemanticsNodes().isNotEmpty()
        }
        regle.onNodeWithText("Dossiers de musique").assertExists()
    }
}

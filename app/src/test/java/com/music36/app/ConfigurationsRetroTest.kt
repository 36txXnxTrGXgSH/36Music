package com.music36.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import com.music36.app.data.ConfigurationApparence
import com.music36.app.data.EtatReglages
import com.music36.app.data.NOMBRE_CONFIGURATIONS
import com.music36.app.data.ThemeApplication
import com.music36.app.data.avecConfiguration
import com.music36.app.data.configurations
import com.music36.app.lecture.EtatLecture
import com.music36.app.ui.ActionsUi
import com.music36.app.ui.LocalActions
import com.music36.app.ui.LocalEtatLecture
import com.music36.app.ui.LocalReglages
import com.music36.app.ui.ThemeMusique
import com.music36.app.ui.retro.EcranRetro
import com.music36.app.ui.retro.NiveauRetro
import com.music36.app.ui.retro.pileDeDepart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * Les configurations d'apparence enregistrées, et le retour depuis l'interface moderne.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@UnstableApi
class ConfigurationsRetroTest {

    @get:Rule
    val regle = createComposeRule()

    @Before
    fun couperLeService() {
        org.robolectric.Shadows.shadowOf(
            ApplicationProvider.getApplicationContext<android.app.Application>()
        ).declareActionUnbindable("androidx.media3.session.MediaSessionService")
    }

    private fun afficher(
        depart: NiveauRetro,
        reglages: EtatReglages = EtatReglages(),
        depuisModerne: Boolean = false
    ) {
        val app = ApplicationProvider.getApplicationContext<Music36App>()
        regle.setContent {
            ThemeMusique(ThemeApplication.SOMBRE, dynamique = false) {
                CompositionLocalProvider(
                    LocalActions provides ActionsUi(app, {}, {}, {}, {}),
                    LocalReglages provides reglages,
                    LocalEtatLecture provides EtatLecture()
                ) {
                    EcranRetro(depart, depuisModerne)
                }
            }
        }
    }

    private fun verifierLaLigne(texte: String) {
        regle.onNodeWithTag("liste_retro").performScrollToNode(hasText(texte))
        regle.onNodeWithText(texte).assertIsDisplayed()
    }

    /**
     * Pose des configurations dans le **vrai** magasin de réglages.
     *
     * ⚠️ Les lignes de menu se construisent sur `depot.etatReglages`, pas sur le
     * `LocalReglages` fourni au test : passer les configurations par ce dernier ne montrerait
     * que des emplacements vides.
     */
    private fun enregistrer(bloc: (EtatReglages) -> EtatReglages) {
        val app = ApplicationProvider.getApplicationContext<Music36App>()
        kotlinx.coroutines.runBlocking { app.depot.reglages.modifier(bloc) }
    }

    /** Attend qu'une ligne apparaisse : le flux des réglages suit l'écriture, sans la précéder. */
    private fun attendreLaLigne(texte: String) {
        regle.waitUntil(10_000) {
            regle.onAllNodes(hasText(texte)).fetchSemanticsNodes().isNotEmpty() ||
                runCatching {
                    regle.onNodeWithTag("liste_retro").performScrollToNode(hasText(texte))
                }.isSuccess
        }
        verifierLaLigne(texte)
    }

    private val apparence = EtatReglages(
        fondLecteurRetro = 1,
        couleurFondRetro = 3,
        styleMolette = 4,
        couleurMolette = 2,
        effetMolette = 3,
        themeRetro = ThemeApplication.AMOLED.ordinal,
        themeCouleurRetro = 5,
        teinteRetro = 212.5f,
        dynamiqueRetro = true,
        lisereLecteur = false,
        styleBarre = 2
    )

    // ------------------------------------------------------------------ le blocage de la 4.4

    @Test
    fun `ouvrir les reglages depuis l'interface moderne laisse de quoi remonter`() {
        // ⚠️ Le défaut de la 4.4 : une pile d'un seul niveau, que ni MENU ni le bouton retour
        // ne pouvaient quitter. Les parents doivent être remis en place.
        assertEquals(
            listOf(NiveauRetro.Racine, NiveauRetro.Reglages, NiveauRetro.ParametresApp),
            pileDeDepart(NiveauRetro.ParametresApp)
        )
        assertEquals(listOf(NiveauRetro.Racine), pileDeDepart(NiveauRetro.Racine))
        // Quel que soit le niveau demandé, la racine est toujours au fond de la pile.
        assertEquals(NiveauRetro.Racine, pileDeDepart(NiveauRetro.Lecture).first())
    }

    @Test
    fun `MENU remonte des reglages ouverts depuis l'interface moderne`() {
        afficher(NiveauRetro.ParametresApp, depuisModerne = true)
        verifierLaLigne("Revenir à l'interface moderne")

        regle.onNodeWithTag("molette").performTouchInput {
            click(Offset(width / 2f, height * 0.06f))
        }
        regle.waitForIdle()
        // On est remonté dans « Réglages », qui mène aussi à l'interface moderne.
        verifierLaLigne("Paramètres de l'application")

        regle.onNodeWithTag("molette").performTouchInput {
            click(Offset(width / 2f, height * 0.06f))
        }
        regle.waitForIdle()
        // Et de là à l'accueil de la molette.
        verifierLaLigne("Musique")
    }

    @Test
    fun `le retour a l'interface moderne n'apparait que si l'on en vient`() {
        afficher(NiveauRetro.ParametresApp, depuisModerne = false)
        verifierLaLigne("Dossiers de musique")
        regle.onNodeWithText("Revenir à l'interface moderne").assertDoesNotExist()
    }

    // ------------------------------------------------------------------ les configurations

    @Test
    fun `une configuration se relit telle qu'elle a ete ecrite`() {
        val config = ConfigurationApparence.depuis(apparence, 1_726_000_000_000L)
        assertEquals(config, ConfigurationApparence.lire(config.enTexte()))
    }

    @Test
    fun `la teinte survit a un telephone en francais`() {
        // ⚠️ Le piège : `format` écrirait « 212,5 », que la relecture ne comprendrait pas.
        val avant = Locale.getDefault()
        try {
            Locale.setDefault(Locale.FRANCE)
            val texte = ConfigurationApparence.depuis(apparence, 1L).enTexte()
            assertEquals(212.5f, ConfigurationApparence.lire(texte)!!.teinteRetro, 0.0001f)
        } finally {
            Locale.setDefault(avant)
        }
    }

    @Test
    fun `un emplacement abime se lit comme vide, sans exception`() {
        assertNull(ConfigurationApparence.lire(""))
        assertNull(ConfigurationApparence.lire("n'importe quoi"))
        assertNull(ConfigurationApparence.lire("a1|1|2|x|4|5|6|7|8.0|1|0|2|3"))
        assertNull(ConfigurationApparence.lire("z9|1|2|3|4|5|6|7|8.0|1|0|2|3"))
    }

    @Test
    fun `rappeler une configuration ne touche qu'a l'apparence`() {
        val config = ConfigurationApparence.depuis(apparence, 1L)
        val ailleurs = EtatReglages(vitesse = 1.75f, egaliseurActif = true, fonduMs = 2000)
        val apres = config.appliquer(ailleurs)

        assertTrue("l'apparence doit être rappelée", config.estEnCours(apres))
        // ⚠️ Changer de couleurs ne doit jamais changer le son.
        assertEquals(1.75f, apres.vitesse, 0.0001f)
        assertTrue(apres.egaliseurActif)
        assertEquals(2000, apres.fonduMs)
    }

    @Test
    fun `la date d'enregistrement n'empeche pas de reconnaitre l'apparence en cours`() {
        val config = ConfigurationApparence.depuis(apparence, 999L)
        assertTrue(config.estEnCours(apparence))
        assertFalse(config.estEnCours(apparence.copy(teinteRetro = 10f)))
    }

    @Test
    fun `trois emplacements, qu'on remplit et qu'on vide`() {
        var reglages = EtatReglages()
        assertEquals(NOMBRE_CONFIGURATIONS, reglages.configurations.size)
        assertTrue(reglages.configurations.all { it == null })

        val config = ConfigurationApparence.depuis(apparence, 5L)
        reglages = reglages.avecConfiguration(1, config)
        assertNull(reglages.configurations[0])
        assertEquals(config, reglages.configurations[1])
        assertNull(reglages.configurations[2])

        reglages = reglages.avecConfiguration(1, null)
        assertTrue(reglages.configurations.all { it == null })
        assertEquals(NOMBRE_CONFIGURATIONS, reglages.configurationsRetro.size)
    }

    @Test
    fun `la liste montre les emplacements vides et remplis`() {
        enregistrer {
            it.avecConfiguration(0, ConfigurationApparence.depuis(apparence, 5L))
                .avecConfiguration(1, null)
                .avecConfiguration(2, null)
        }
        afficher(NiveauRetro.ConfigurationsRetro)
        attendreLaLigne("Configuration 1")
        verifierLaLigne("Emplacement 2")
        verifierLaLigne("Emplacement 3")
    }

    @Test
    fun `un emplacement rempli propose appliquer, remplacer et supprimer`() {
        enregistrer {
            it.avecConfiguration(2, ConfigurationApparence.depuis(apparence, 5L))
        }
        afficher(NiveauRetro.ConfigurationRetro(2))
        attendreLaLigne("Appliquer")
        verifierLaLigne("Remplacer par l'apparence actuelle")
        verifierLaLigne("Supprimer")
    }

    @Test
    fun `les configurations sont accessibles des deux menus de reglages`() {
        afficher(NiveauRetro.Reglages)
        verifierLaLigne("Configurations enregistrées")
    }

    @Test
    fun `et depuis le reglage du menu Rétro`() {
        afficher(NiveauRetro.ReglagesRetro)
        verifierLaLigne("Configurations enregistrées")
    }
}

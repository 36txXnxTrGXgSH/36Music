package com.music36.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import com.music36.app.data.Collection
import com.music36.app.data.EtatReglages
import com.music36.app.data.Piste
import com.music36.app.data.Statistique
import com.music36.app.data.StyleInterface
import com.music36.app.data.ThemeApplication
import com.music36.app.data.plusEcoutes
import com.music36.app.lecture.EtatLecture
import com.music36.app.ui.ActionsUi
import com.music36.app.ui.LocalActions
import com.music36.app.ui.LocalEtatLecture
import com.music36.app.ui.LocalReglages
import com.music36.app.ui.ThemeMusique
import com.music36.app.ui.retro.EcranRetro
import com.music36.app.ui.retro.NiveauRetro
import com.music36.app.ui.retro.VueCoverFlow
import com.music36.app.ui.retro.doitAfficherLaLettre
import com.music36.app.ui.retro.estAlphabetique
import com.music36.app.ui.retro.initialeDe
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Cover Flow, le classement des écoutes et le repère alphabétique.
 *
 * ⚠️ Aucun de ces tests ne passe par la base. Sous Robolectric, attendre un flux Room depuis un
 * test d'interface s'est révélé capricieux d'une classe à l'autre — l'instance d'`Application` est
 * réutilisée, le fichier de base pas toujours, et le test tombait en `ComposeTimeoutException`
 * alors qu'il passait seul. Le carrousel reçoit donc ses albums en main propre, et les règles
 * métier sont vérifiées comme des fonctions pures : c'est exactement pour cela qu'elles en sont.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@UnstableApi
class CoverFlowTest {

    @get:Rule
    val regle = createComposeRule()

    @Before
    fun couperLeService() {
        org.robolectric.Shadows.shadowOf(
            ApplicationProvider.getApplicationContext<android.app.Application>()
        ).declareActionUnbindable("androidx.media3.session.MediaSessionService")
    }

    private fun piste(titre: String, album: String, artiste: String) = Piste(
        uri = "content://test/$album/$titre",
        racine = "content://test",
        dossier = "Musique/$album",
        nomFichier = "$titre.flac",
        titre = titre,
        artiste = artiste,
        album = album,
        artisteAlbum = artiste,
        genre = "Pop",
        annee = 2010,
        numeroPiste = 1,
        disque = 1,
        duree = 200_000,
        taille = 10,
        dateFichier = 1,
        dateAjout = 1,
        format = "FLAC",
        debit = 900,
        echantillonnage = 44_100,
        bits = 16,
        canaux = 2
    )

    private fun album(nom: String, artiste: String) = Collection(
        cle = "$nom|$artiste",
        nom = nom,
        sousTitre = artiste,
        pistes = listOf(piste("Titre", nom, artiste))
    )

    private val albums = listOf(
        album("Amnesiac", "Radiohead"),
        album("Blonde", "Frank Ocean"),
        album("Cosmogramma", "Flying Lotus")
    )

    private fun afficherCarrousel(selection: Int) {
        regle.setContent {
            ThemeMusique(ThemeApplication.SOMBRE, dynamique = false) {
                VueCoverFlow(albums = albums, selection = selection)
            }
        }
    }

    private fun afficherRetro(depart: NiveauRetro) {
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

    @Test
    fun `le carrousel annonce l'album au centre, son artiste et son rang`() {
        afficherCarrousel(selection = 0)
        regle.onNodeWithText("Amnesiac").assertIsDisplayed()
        regle.onNodeWithText("Radiohead").assertIsDisplayed()
        regle.onNodeWithText("1 / 3").assertIsDisplayed()
    }

    @Test
    fun `deplacer la selection change l'album mis en avant`() {
        afficherCarrousel(selection = 1)
        regle.onNodeWithText("Blonde").assertIsDisplayed()
        regle.onNodeWithText("Frank Ocean").assertIsDisplayed()
        regle.onNodeWithText("2 / 3").assertIsDisplayed()
    }

    @Test
    fun `Cover Flow et le classement sont proposes dans le menu Musique`() {
        afficherRetro(NiveauRetro.Racine)
        regle.onNodeWithTag("molette").performTouchInput { click(center) }
        regle.waitForIdle()
        regle.onNodeWithText("Cover Flow").assertExists()
        // La liste est paresseuse : la dernière entrée du menu n'est composée qu'une fois
        // atteinte, on défile donc jusqu'à elle.
        regle.onNodeWithTag("liste_retro")
            .performScrollToNode(hasText("Titres les plus écoutés"))
        regle.onNodeWithText("Titres les plus écoutés").assertExists()
    }

    @Test
    fun `choisir une pochette superpose un sous-menu au carrousel`() {
        afficherRetro(NiveauRetro.CoverFlowAlbum("Blonde|Frank Ocean"))
        // La barre de titre de la superposition porte le nom de l'album choisi.
        regle.onNodeWithText("Blonde").assertExists()
    }

    @Test
    fun `le classement retient les plus ecoutes et ignore les titres jamais joues`() {
        val beta = piste("Beta", "Blonde", "Frank Ocean")
        val delta = piste("Delta", "Cosmogramma", "Flying Lotus")
        val alpha = piste("Alpha", "Amnesiac", "Radiohead")
        val statistiques = mapOf(
            beta.uri to Statistique(beta.uri, lectures = 3, derniereLecture = 30),
            delta.uri to Statistique(delta.uri, lectures = 1, derniereLecture = 90)
        )

        val classement = listOf(alpha, beta, delta).plusEcoutes(statistiques, limite = 100)

        assertEquals(listOf("Beta", "Delta"), classement.map { it.titre })
    }

    @Test
    fun `a egalite d'ecoutes, le plus recemment joue passe devant`() {
        val a = piste("A", "Album", "Artiste")
        val b = piste("B", "Album", "Artiste")
        val statistiques = mapOf(
            a.uri to Statistique(a.uri, lectures = 2, derniereLecture = 10),
            b.uri to Statistique(b.uri, lectures = 2, derniereLecture = 500)
        )
        assertEquals(
            listOf("B", "A"),
            listOf(a, b).plusEcoutes(statistiques).map { it.titre }
        )
    }

    @Test
    fun `le classement s'arrete a la limite demandee`() {
        val pistes = (1..150).map { piste("Titre $it", "Album", "Artiste") }
        val statistiques = pistes.associate { it.uri to Statistique(it.uri, lectures = 1) }
        assertEquals(100, pistes.plusEcoutes(statistiques, limite = 100).size)
    }

    @Test
    fun `le repere n'existe que sur les niveaux ranges par nom`() {
        // Ces listes sont triées alphabétiquement dans la vue molette, exprès pour le repère.
        listOf(
            NiveauRetro.Sons,
            NiveauRetro.Favoris,
            NiveauRetro.Artistes,
            NiveauRetro.Genres,
            NiveauRetro.Dossiers,
            NiveauRetro.Artiste("Radiohead"),
            NiveauRetro.Dossier("Musique/Rock")
        ).forEach { assertEquals(it.toString(), true, estAlphabetique(it)) }

        // Un album garde l'ordre du disque, une playlist l'ordre voulu, le classement celui des
        // écoutes : afficher une lettre y désignerait n'importe quoi.
        listOf(
            NiveauRetro.Album("Amnesiac|Radiohead"),
            NiveauRetro.Playlist(1L),
            NiveauRetro.PlusEcoutes,
            NiveauRetro.FileAttente,
            NiveauRetro.CoverFlow,
            NiveauRetro.Lecture,
            NiveauRetro.Racine
        ).forEach { assertEquals(it.toString(), false, estAlphabetique(it)) }
    }

    @Test
    fun `la lettre ne sort que sur une longue liste parcourue vite`() {
        // Un cran isolé : on lit le libellé, la lettre masquerait l'écran pour rien.
        assertEquals(false, doitAfficherLaLettre(tailleListe = 300, ecartDepuisLeCranPrecedent = 900))
        // Un défilement rapide dans une longue liste : c'est là qu'elle sert.
        assertEquals(true, doitAfficherLaLettre(tailleListe = 300, ecartDepuisLeCranPrecedent = 60))
        // Une liste courte tient à l'écran, quelle que soit la vitesse.
        assertEquals(false, doitAfficherLaLettre(tailleListe = 6, ecartDepuisLeCranPrecedent = 60))
    }

    @Test
    fun `l'initiale ignore les accents et range le reste sous le diese`() {
        assertEquals("E", initialeDe("Éléphant"))
        assertEquals("E", initialeDe("  elephant"))
        assertEquals("#", initialeDe("36 Chandelles"))
        assertEquals("#", initialeDe(""))
        assertEquals("A", initialeDe("Amnesiac"))
    }
}

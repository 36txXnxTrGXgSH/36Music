package com.music36.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import com.music36.app.data.EtatReglages
import com.music36.app.data.Piste
import com.music36.app.data.ThemeApplication
import com.music36.app.data.TriPlaylist
import com.music36.app.data.avecTri
import com.music36.app.data.MotifRecherche
import com.music36.app.data.correspond
import com.music36.app.data.indexDeLApparition
import com.music36.app.data.numeroterLesApparitions
import com.music36.app.data.selonTri
import com.music36.app.data.triDe
import com.music36.app.lecture.EtatLecture
import com.music36.app.ui.ActionsUi
import com.music36.app.ui.LocalActions
import com.music36.app.ui.LocalEtatLecture
import com.music36.app.ui.LocalReglages
import com.music36.app.ui.ThemeMusique
import com.music36.app.ui.retro.EcranRetro
import com.music36.app.ui.retro.NiveauRetro
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * La recherche à jokers et le tri des playlists de la vue molette.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@UnstableApi
class RechercheEtTriTest {

    @get:Rule
    val regle = createComposeRule()

    @Before
    fun couperLeService() {
        org.robolectric.Shadows.shadowOf(
            ApplicationProvider.getApplicationContext<android.app.Application>()
        ).declareActionUnbindable("androidx.media3.session.MediaSessionService")
    }

    // ------------------------------------------------------------------ les jokers

    @Test
    fun `emi etoile trouve ce qui commence par emi`() {
        assertTrue(correspond("emi*", "Eminem"))
        assertTrue(correspond("emi*", "Émilie Simon"))
        assertFalse("« emi » au milieu ne suffit pas", correspond("emi*", "Jeremih"))
    }

    @Test
    fun `etoile emi trouve ce qui finit par emi`() {
        assertTrue(correspond("*emi", "Ratemi"))
        assertTrue(correspond("*emi", "Kemi"))
        assertFalse(correspond("*emi", "Eminem"))
    }

    @Test
    fun `rat etoile emi trouve ce qui commence par rat et finit par emi`() {
        assertTrue(correspond("rat*emi", "Ratemi"))
        assertTrue(correspond("rat*emi", "Rataplan de Kemi"))
        assertFalse(correspond("rat*emi", "Ratatouille"))
        assertFalse(correspond("rat*emi", "Kemi Rat"))
        // ⚠️ Le même bout ne sert pas deux fois : « rat*at » n'accepte pas « rat ».
        assertFalse(correspond("rat*at", "rat"))
    }

    @Test
    fun `sans etoile on cherche partout dans le nom`() {
        assertTrue(correspond("emi", "Jeremih"))
        assertTrue(correspond("emi", "Eminem"))
        assertFalse(correspond("emi", "Portishead"))
    }

    @Test
    fun `casse et accents sont ignores`() {
        assertTrue(correspond("EMI*", "éminence"))
        assertTrue(correspond("rep*", "Répétition"))
        assertTrue(correspond("*ETE", "L'été"))
    }

    @Test
    fun `plusieurs etoiles se combinent dans l'ordre`() {
        assertTrue(correspond("a*b*c", "a--b--c"))
        assertFalse("dans l'ordre seulement", correspond("a*b*c", "a--c--b"))
        assertTrue("une étoile seule accepte tout", correspond("*", "n'importe quoi"))
        assertTrue("un motif vide aussi", correspond("  ", "n'importe quoi"))
    }

    // ------------------------------------------------------------------ le tri

    @Test
    fun `le bouton fait le tour des quatre tris`() {
        // L'ordre demandé : titre, artiste, date d'ajout, ordre du fichier — en partant de
        // l'ordre du fichier, qui est le défaut.
        assertEquals(TriPlaylist.TITRE, TriPlaylist.ORIGINE.suivant())
        assertEquals(TriPlaylist.ARTISTE, TriPlaylist.TITRE.suivant())
        assertEquals(TriPlaylist.AJOUT, TriPlaylist.ARTISTE.suivant())
        assertEquals(TriPlaylist.ORIGINE, TriPlaylist.AJOUT.suivant())
    }

    @Test
    fun `chaque tri range les titres comme annonce`() {
        val glory = piste("Glory Box", "Portishead", ajout = 10)
        val lose = piste("Lose Yourself", "Eminem", ajout = 30)
        val bug = piste("Bug A Boo", "Joe Stone", ajout = 20)
        val origine = listOf(glory, lose, bug)

        assertEquals(origine, origine.selonTri(TriPlaylist.ORIGINE))
        assertEquals(listOf(bug, glory, lose), origine.selonTri(TriPlaylist.TITRE))
        assertEquals(listOf(lose, bug, glory), origine.selonTri(TriPlaylist.ARTISTE))
        // La plus récemment entrée en tête.
        assertEquals(listOf(lose, bug, glory), origine.selonTri(TriPlaylist.AJOUT))
    }

    @Test
    fun `le tri se retient playlist par playlist`() {
        var reglages = EtatReglages()
        assertEquals(TriPlaylist.ORIGINE, triDe(reglages, 3))

        reglages = reglages.avecTri(3, TriPlaylist.ARTISTE).avecTri(8, TriPlaylist.TITRE)
        assertEquals(TriPlaylist.ARTISTE, triDe(reglages, 3))
        assertEquals(TriPlaylist.TITRE, triDe(reglages, 8))
        // ⚠️ « 3 » ne doit pas se confondre avec « 33 ».
        assertEquals(TriPlaylist.ORIGINE, triDe(reglages, 33))

        // Revenir à l'ordre du fichier efface la ligne, sans toucher aux autres.
        reglages = reglages.avecTri(3, TriPlaylist.ORIGINE)
        assertEquals(TriPlaylist.ORIGINE, triDe(reglages, 3))
        assertEquals(TriPlaylist.TITRE, triDe(reglages, 8))
        assertFalse(reglages.trisPlaylists.contains("3:"))
    }

    // ------------------------------------------------------------------ les doublons d'une playlist

    @Test
    fun `chaque apparition d'un titre a son rang`() {
        // ⚠️ Le cas signalé : « (00) » de Rone, deux fois dans la même playlist.
        val rone = piste("(00)", "Rone", ajout = 1)
        val autre = piste("Glory Box", "Portishead", ajout = 2)
        val numerotees = numeroterLesApparitions(listOf(rone, autre, rone)) { it.uri }
        assertEquals(listOf(0, 0, 1), numerotees.map { it.second })
    }

    @Test
    fun `le rang survit au tri`() {
        val rone = piste("(00)", "Rone", ajout = 1)
        val autre = piste("Glory Box", "Portishead", ajout = 2)
        val numerotees = numeroterLesApparitions(listOf(rone, autre, rone)) { it.uri }
        // Trié par artiste, l'ordre affiché change ; le rang de chaque ligne, lui, reste.
        val triees = numerotees.selonTri(TriPlaylist.ARTISTE) { it.first }
        assertEquals(listOf("Portishead", "Rone", "Rone"), triees.map { it.first.artiste })
        assertEquals(setOf(0, 1), triees.filter { it.first == rone }.map { it.second }.toSet())
    }

    @Test
    fun `retirer une apparition ne touche pas a l'autre`() {
        val uris = listOf("a", "rone", "b", "rone", "c")
        assertEquals("la première", 1, indexDeLApparition(uris, "rone", 0))
        assertEquals("la seconde", 3, indexDeLApparition(uris, "rone", 1))
        // Une apparition qui n'existe plus ne désigne rien — on ne retire pas au hasard.
        assertEquals(null, indexDeLApparition(uris, "rone", 2))
        assertEquals(null, indexDeLApparition(uris, "absent", 0))
    }

    @Test
    fun `un motif prepare donne la meme reponse que la recherche directe`() {
        // ⚠️ Le motif est préparé une fois pour tout un menu, pour la vitesse ; il ne doit pas
        // répondre autrement que la fonction d'origine.
        val noms = listOf("Eminem", "Jeremih", "Ratemi", "Répétition", "Fear the Night")
        listOf("emi*", "*emi", "rat*emi", "emi", "rep*", "*f*t*t").forEach { motif ->
            val prepare = MotifRecherche(motif)
            noms.forEach { nom ->
                assertEquals("$motif / $nom", correspond(motif, nom), prepare.correspond(nom))
            }
        }
    }

    @Test
    fun `chercher dans des milliers de noms reste rapide`() {
        // Un garde-fou grossier : trois mille six cents noms, comme la bibliothèque du
        // téléphone, doivent se filtrer en bien moins d'une seconde.
        val noms = List(3600) { "Titre numéro $it — Artiste $it" }
        val motif = MotifRecherche("*f*t*t")
        val debut = System.nanoTime()
        repeat(5) { noms.filter { motif.correspond(it) } }
        val ms = (System.nanoTime() - debut) / 1_000_000 / 5
        assertTrue("$ms ms pour un filtrage", ms < 400)
    }

    // ------------------------------------------------------------------ l'écran

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

    @Test
    fun `la loupe filtre le menu ouvert`() {
        afficher(NiveauRetro.Reglages)
        regle.onNodeWithText("Répétition").assertExists()
        regle.onNodeWithText("Bibliothèque").assertExists()

        regle.onNodeWithTag("bouton_recherche").performClick()
        regle.onNodeWithTag("champ_recherche").performTextInput("rep*")
        regle.waitForIdle()

        // « rep* » trouve « Répétition », accent compris, et rien d'autre.
        regle.onNodeWithText("Répétition").assertIsDisplayed()
        regle.onNodeWithText("Bibliothèque").assertDoesNotExist()
    }

    @Test
    fun `une recherche sans resultat le dit`() {
        afficher(NiveauRetro.Reglages)
        regle.onNodeWithTag("bouton_recherche").performClick()
        regle.onNodeWithTag("champ_recherche").performTextInput("zzz*")
        regle.waitForIdle()
        regle.onNodeWithTag("aucun_resultat").assertIsDisplayed()
    }

    @Test
    fun `fermer la recherche rend le menu entier`() {
        afficher(NiveauRetro.Reglages)
        regle.onNodeWithTag("bouton_recherche").performClick()
        regle.onNodeWithTag("champ_recherche").performTextInput("rep*")
        regle.waitForIdle()
        regle.onNodeWithText("Bibliothèque").assertDoesNotExist()

        regle.onNodeWithTag("fermer_recherche").performClick()
        regle.waitForIdle()
        regle.onNodeWithText("Bibliothèque").assertExists()
    }

    @Test
    fun `une playlist porte le bouton de tri`() {
        afficher(NiveauRetro.Playlist(1L))
        regle.onNodeWithTag("bouton_tri").assertIsDisplayed()
        regle.onNodeWithTag("bouton_recherche").assertIsDisplayed()
    }

    @Test
    fun `un autre menu n'a pas de bouton de tri`() {
        afficher(NiveauRetro.Musique)
        regle.onNodeWithTag("bouton_recherche").assertIsDisplayed()
        regle.onNodeWithTag("bouton_tri").assertDoesNotExist()
    }

    private fun piste(titre: String, artiste: String, ajout: Long) = Piste(
        uri = "content://test/$titre",
        racine = "content://test",
        dossier = "Music",
        nomFichier = "$titre.flac",
        titre = titre,
        artiste = artiste,
        album = "Album",
        artisteAlbum = artiste,
        genre = "Pop",
        annee = 2020,
        numeroPiste = 1,
        disque = 1,
        duree = 200_000,
        taille = 10,
        dateFichier = 1,
        dateAjout = ajout,
        format = "FLAC",
        debit = 900,
        echantillonnage = 44_100,
        bits = 16,
        canaux = 2
    )
}

package com.music36.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.music36.app.data.BaseMusique
import com.music36.app.data.Favori
import com.music36.app.data.Piste
import com.music36.app.data.Playlist
import com.music36.app.data.Racine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.music36.app.data.Depot
import com.music36.app.data.Reglages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * La base : ce qui doit survivre à une réanalyse.
 *
 * Ces tests tournent sous `runBlocking` et non `runTest` : ils font de vraies écritures Room, le
 * temps virtuel n'apporte rien, et `runTest` échouerait dès qu'une coroutine laissée par un autre
 * test de la suite a levé une exception.
 *
 * Le point sensible n'est pas l'insertion mais la **conservation** : favoris et playlists sont
 * accrochés à l'URI du fichier, une réanalyse doit les laisser intacts.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BaseTest {

    private lateinit var base: BaseMusique

    @Before
    fun ouvrir() {
        base = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BaseMusique::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun fermer() = base.close()

    private fun piste(nom: String, dossier: String = "Musique") = Piste(
        uri = "content://test/$dossier/$nom",
        racine = "content://test",
        dossier = dossier,
        nomFichier = "$nom.flac",
        titre = nom,
        artiste = "Artiste",
        album = "Album",
        artisteAlbum = "Artiste",
        genre = "Rock",
        annee = 2001,
        numeroPiste = 1,
        disque = 1,
        duree = 200_000,
        taille = 500,
        dateFichier = 1,
        dateAjout = 1,
        format = "FLAC",
        debit = 900,
        echantillonnage = 96_000,
        bits = 24,
        canaux = 2
    )

    @Test
    fun `une reanalyse ne perd ni les favoris ni les playlists`() = runBlocking {
        val a = piste("A")
        val b = piste("B")
        base.pistes().inserer(listOf(a, b))
        base.favoris().ajouter(Favori(a.uri, 0))
        val playlist = base.playlists().creer(Playlist(nom = "Soirée", dateCreation = 0))
        base.playlists().ajouterAuBout(playlist, listOf(a.uri, b.uri))

        // Réanalyse : les mêmes URI sont réinsérées avec des métadonnées rafraîchies.
        base.pistes().inserer(listOf(a.copy(titre = "A (remasterisé)"), b))

        assertTrue(base.favoris().estFavori(a.uri))
        assertEquals(2, base.playlists().liens(playlist).size)
        assertEquals("A (remasterisé)", base.pistes().parUri(a.uri)?.titre)
    }

    @Test
    fun `retirer une piste d'une playlist renumerote les positions`() = runBlocking {
        val pistes = listOf(piste("A"), piste("B"), piste("C"))
        base.pistes().inserer(pistes)
        val id = base.playlists().creer(Playlist(nom = "Liste", dateCreation = 0))
        base.playlists().ajouterAuBout(id, pistes.map { it.uri })

        val restantes = base.playlists().liens(id).sortedBy { it.position }
            .filterIndexed { index, _ -> index != 1 }
            .map { it.uri }
        base.playlists().remplacerContenu(id, restantes)

        val apres = base.playlists().liens(id).sortedBy { it.position }
        assertEquals(listOf(0, 1), apres.map { it.position })
        assertEquals(listOf(pistes[0].uri, pistes[2].uri), apres.map { it.uri })
    }

    @Test
    fun `retirer une racine efface ses pistes et laisse les autres`() = runBlocking {
        val sienne = piste("A")
        val autre = piste("B").copy(racine = "content://autre")
        base.pistes().inserer(listOf(sienne, autre))
        base.racines().ajouter(Racine("content://test", "Musique", 0))

        base.pistes().viderRacine("content://test")

        val restantes = base.pistes().toutesMaintenant()
        assertEquals(1, restantes.size)
        assertEquals(autre.uri, restantes.first().uri)
    }

    @Test
    fun `le compteur d'ecoutes s'incremente et remet la reprise a zero`() = runBlocking {
        val a = piste("A")
        base.pistes().inserer(listOf(a))
        base.statistiques().enregistrerReprise(a.uri, 42_000)
        base.statistiques().compterUneLecture(a.uri, 1_700_000)

        val stat = base.statistiques().parUri(a.uri)!!
        assertEquals(1, stat.lectures)
        assertEquals(0, stat.reprise)
        assertEquals(1_700_000, stat.derniereLecture)
    }

    @Test
    fun `les empreintes servent a sauter les fichiers inchanges`() = runBlocking {
        val a = piste("A")
        base.pistes().inserer(listOf(a))
        val empreintes = base.pistes().empreintes("content://test")
        assertEquals(1, empreintes.size)
        assertEquals(a.taille, empreintes.first().taille)
        assertEquals(a.dateFichier, empreintes.first().dateFichier)
    }

    @Test
    fun `un favori retire disparait du flux`() = runBlocking {
        val a = piste("A")
        base.pistes().inserer(listOf(a))
        base.favoris().ajouter(Favori(a.uri, 0))
        assertEquals(listOf(a.uri), base.favoris().uris().first())
        base.favoris().retirer(a.uri)
        assertTrue(base.favoris().uris().first().isEmpty())
        assertFalse(base.favoris().estFavori(a.uri))
    }

    @Test
    fun `un titre deja dans la playlist n'y est pas ajoute deux fois`() = runBlocking {
        val depot = Depot(
            ApplicationProvider.getApplicationContext(),
            base,
            Reglages(ApplicationProvider.getApplicationContext()),
            CoroutineScope(Dispatchers.Unconfined)
        )
        val a = piste("Alpha")
        val b = piste("Beta")
        base.pistes().inserer(listOf(a, b))
        val id = depot.creerPlaylist("Route", listOf(a.uri))

        val resultat = depot.ajouterAPlaylist(id, listOf(a.uri, b.uri))

        assertEquals(1, resultat.ajoutees)
        assertEquals(1, resultat.deja)
        // ⚠️ Le contenu, pas seulement le compte rendu : la playlist ne doit porter qu'une seule
        // fois chaque titre.
        val contenu = base.playlists().liens(id).map { it.uri }
        assertEquals(listOf(a.uri, b.uri), contenu.sorted())
        assertEquals(2, contenu.size)
    }

    @Test
    fun `une creation de playlist ne garde pas les doublons qu'on lui donne`() = runBlocking {
        val depot = Depot(
            ApplicationProvider.getApplicationContext(),
            base,
            Reglages(ApplicationProvider.getApplicationContext()),
            CoroutineScope(Dispatchers.Unconfined)
        )
        val a = piste("Alpha")
        base.pistes().inserer(listOf(a))

        val id = depot.creerPlaylist("Doublons", listOf(a.uri, a.uri, a.uri))

        assertEquals(1, base.playlists().liens(id).size)
    }
}

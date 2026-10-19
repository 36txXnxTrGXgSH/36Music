package com.music36.app

import com.music36.app.data.Collection
import com.music36.app.data.Piste
import com.music36.app.data.Racine
import com.music36.app.data.enAlbums
import com.music36.app.data.enArtistes
import com.music36.app.data.enGenres
import com.music36.app.data.regrouperRacines
import com.music36.app.data.sousDossiersDe
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Les clés des listes doivent être uniques — sous peine de plantage.
 *
 * ⚠️ `LazyColumn` ne se contente pas d'afficher de travers quand deux éléments partagent une clé :
 * il lève `IllegalArgumentException` et l'application tombe. C'est arrivé en vrai, avec deux
 * dossiers racines nommés `Music` — l'un en mémoire interne, l'autre sur la carte SD.
 */
class ClesUniquesTest {

    private fun piste(racine: String, dossier: String, titre: String, artiste: String, album: String) =
        Piste(
            uri = "$racine/$dossier/$titre",
            racine = racine,
            dossier = dossier,
            nomFichier = "$titre.flac",
            titre = titre,
            artiste = artiste,
            album = album,
            artisteAlbum = artiste,
            genre = "Pop",
            annee = 2013,
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

    private fun uniques(nom: String, collections: List<Collection>) {
        val cles = collections.map { it.cle }
        assertEquals("$nom : clés en double ${cles.groupBy { it }.filterValues { it.size > 1 }.keys}",
            cles.size, cles.toSet().size)
    }

    /** Le cas réel : mémoire interne et carte SD, chacune avec un dossier « Music ». */
    private val interne = Racine(uri = "content://tree/primary%3AMusic", nom = "Music", dateAjout = 1)
    private val carteSd = Racine(uri = "content://tree/EAB4-6BE0%3AMusic", nom = "Music", dateAjout = 2)

    private val pistes = listOf(
        piste(carteSd.uri, "Music/FR", "Alpha", "Artiste A", "Album A"),
        piste(carteSd.uri, "Music/FR", "Beta", "Artiste B", "Album B"),
        piste(interne.uri, "Music/Vieux", "Gamma", "Artiste A", "Album A")
    )

    @Test
    fun `deux racines de meme nom ne font qu'une entree`() {
        val racines = regrouperRacines(listOf(interne, carteSd), pistes)

        assertEquals(1, racines.size)
        assertEquals("Music", racines[0].cle)
        // ⚠️ L'entrée réunit bien les pistes des deux supports : la navigation, qui filtre sur le
        // chemin, les confondrait de toute façon.
        assertEquals(3, racines[0].pistes.size)
        uniques("racines", racines)
    }

    @Test
    fun `des racines de noms differents restent distinctes`() {
        val musique = Racine(uri = "content://tree/a", nom = "Musique", dateAjout = 1)
        val podcasts = Racine(uri = "content://tree/b", nom = "Podcasts", dateAjout = 2)
        val racines = regrouperRacines(listOf(podcasts, musique), emptyList())

        assertEquals(listOf("Musique", "Podcasts"), racines.map { it.nom })
        uniques("racines", racines)
    }

    @Test
    fun `une racine vide garde sa ligne, avec zero titre`() {
        // La racine ajoutée puis vidée doit rester visible : sinon l'utilisateur ne comprend pas
        // pourquoi elle n'apparaît plus dans les réglages.
        val racines = regrouperRacines(listOf(Racine(uri = "content://tree/c", nom = "Vide", dateAjout = 1)), pistes)
        assertEquals(1, racines.size)
        assertEquals(0, racines[0].pistes.size)
    }

    @Test
    fun `les autres regroupements produisent aussi des cles uniques`() {
        uniques("artistes", pistes.enArtistes())
        uniques("albums", pistes.enAlbums())
        uniques("genres", pistes.enGenres())
        uniques("sous-dossiers", pistes.sousDossiersDe("Music"))
    }
}

package com.music36.app

import com.music36.app.data.Piste
import com.music36.app.data.enAlbums
import com.music36.app.data.enArtistes
import com.music36.app.data.enGenres
import com.music36.app.data.sousDossiersDe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ce que voient les onglets Artistes, Albums, Genres et Dossiers. */
class RegroupementTest {

    private fun piste(
        titre: String,
        artiste: String = "",
        artisteAlbum: String = "",
        album: String = "Album",
        genre: String = "Rock",
        dossier: String = "Musique",
        numeroPiste: Int = 1,
        annee: Int = 1999
    ) = Piste(
        uri = "content://test/$dossier/$titre",
        racine = "content://test",
        dossier = dossier,
        nomFichier = "$titre.mp3",
        titre = titre,
        artiste = artiste,
        album = album,
        artisteAlbum = artisteAlbum,
        genre = genre,
        annee = annee,
        numeroPiste = numeroPiste,
        disque = 1,
        duree = 120_000,
        taille = 1,
        dateFichier = 1,
        dateAjout = 1,
        format = "MP3",
        debit = 320,
        echantillonnage = 44_100,
        bits = 0,
        canaux = 2
    )

    @Test
    fun `l'artiste de l'album prend le relais quand la piste n'en declare pas`() {
        val liste = listOf(piste("A", artiste = "", artisteAlbum = "Groupe"))
        assertEquals(listOf("Groupe"), liste.enArtistes().map { it.nom })
    }

    @Test
    fun `une piste sans aucun artiste tombe dans Inconnu`() {
        val liste = listOf(piste("A"))
        assertEquals(listOf(Piste.INCONNU), liste.enArtistes().map { it.nom })
    }

    @Test
    fun `deux albums homonymes d'artistes differents restent distincts`() {
        val liste = listOf(
            piste("A", artiste = "Alpha", album = "Greatest Hits"),
            piste("B", artiste = "Beta", album = "Greatest Hits")
        )
        val albums = liste.enAlbums()
        assertEquals(2, albums.size)
        assertEquals(setOf("Greatest Hits|Alpha", "Greatest Hits|Beta"), albums.map { it.cle }.toSet())
    }

    @Test
    fun `les titres d'un album sortent dans l'ordre du disque`() {
        val liste = listOf(
            piste("Trois", artiste = "Alpha", numeroPiste = 3),
            piste("Un", artiste = "Alpha", numeroPiste = 1),
            piste("Deux", artiste = "Alpha", numeroPiste = 2)
        )
        assertEquals(
            listOf("Un", "Deux", "Trois"),
            liste.enAlbums().first().pistes.map { it.titre }
        )
    }

    @Test
    fun `un genre absent devient Inconnu`() {
        val liste = listOf(piste("A", genre = ""), piste("B", genre = "Jazz"))
        assertEquals(setOf(Piste.INCONNU, "Jazz"), liste.enGenres().map { it.nom }.toSet())
    }

    @Test
    fun `les sous-dossiers immediats portent tout leur contenu`() {
        val liste = listOf(
            piste("A", dossier = "Musique/Rock"),
            piste("B", dossier = "Musique/Rock/Live"),
            piste("C", dossier = "Musique/Jazz"),
            piste("D", dossier = "Musique")
        )
        val sous = liste.sousDossiersDe("Musique")
        assertEquals(listOf("Jazz", "Rock"), sous.map { it.nom })
        // Rock compte le titre de Rock/Live : « Tout lire » sur un dossier doit être récursif.
        assertEquals(2, sous.first { it.nom == "Rock" }.pistes.size)
        assertTrue(sous.none { it.nom == "Live" })
    }

    @Test
    fun `l'entete d'un album annonce l'artiste, l'annee et le nombre de titres`() {
        val liste = listOf(piste("A", artiste = "Alpha", annee = 1994))
        val album = liste.enAlbums().first()
        assertEquals("Alpha · 1994 · 1 titre", album.sousTitre)
    }
}

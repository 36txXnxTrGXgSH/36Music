package com.music36.app

import com.music36.app.data.FormatExport
import com.music36.app.data.Piste
import com.music36.app.data.cheminExport
import com.music36.app.data.contenuExport
import com.music36.app.data.echapperXml
import com.music36.app.data.nomFichierExport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'export des playlists vers les formats que d'autres lecteurs savent relire.
 *
 * ⚠️ Le piège est le chemin : une playlist qui désignerait les titres par leur URI `content://`
 * ne vaudrait que pour cette application, sur ce téléphone. On écrit donc des chemins relatifs,
 * ceux-là mêmes que 36Music sait relire à l'import.
 */
class ExportPlaylistTest {

    private fun piste(titre: String, artiste: String, dossier: String = "Music/FR") = Piste(
        uri = "content://tree/primary%3AMusic/document/$dossier/$titre",
        racine = "content://tree/primary%3AMusic",
        dossier = dossier,
        nomFichier = "$titre.flac",
        titre = titre,
        artiste = artiste,
        album = "Album",
        artisteAlbum = artiste,
        genre = "Rap",
        annee = 2013,
        numeroPiste = 1,
        disque = 1,
        duree = 215_000,
        taille = 10,
        dateFichier = 1,
        dateAjout = 1,
        format = "FLAC",
        debit = 900,
        echantillonnage = 44_100,
        bits = 16,
        canaux = 2
    )

    private val pistes = listOf(piste("06h16", "Casseurs Flowters"))

    @Test
    fun `le chemin ecrit est relatif, jamais une URI de cette application`() {
        assertEquals("Music/FR/06h16.flac", cheminExport(pistes.first()))
    }

    @Test
    fun `le M3U porte la duree en secondes et le chemin`() {
        val contenu = contenuExport("Route", pistes, FormatExport.M3U8)
        assertEquals(
            "#EXTM3U\n#EXTINF:215,Casseurs Flowters - 06h16\nMusic/FR/06h16.flac\n",
            contenu
        )
    }

    @Test
    fun `le XSPF echappe ce qui casserait le XML`() {
        // Les noms de groupes sont pleins d'esperluettes : sans échappement, plus rien ne relit.
        val contenu = contenuExport("R&B", listOf(piste("Toi & Moi", "A<B")), FormatExport.XSPF)
        assertTrue(contenu.contains("<title>R&amp;B</title>"))
        assertTrue(contenu.contains("<title>Toi &amp; Moi</title>"))
        assertTrue(contenu.contains("<creator>A&lt;B</creator>"))
        assertTrue("le XML doit être complet", contenu.trimEnd().endsWith("</playlist>"))
    }

    @Test
    fun `l'echappement couvre les cinq caracteres du XML`() {
        assertEquals("&amp;&lt;&gt;&quot;&apos;", echapperXml("&<>\"'"))
    }

    @Test
    fun `le nom de fichier perd ce qu'un systeme de fichiers refuse`() {
        assertEquals("Route 66.m3u8", nomFichierExport("Route 66", FormatExport.M3U8))
        assertEquals("AC_DC.xspf", nomFichierExport("AC/DC", FormatExport.XSPF))
        assertEquals("a_b_c.m3u", nomFichierExport("a:b?c", FormatExport.M3U))
        assertEquals("playlist.m3u8", nomFichierExport("   ", FormatExport.M3U8))
    }

    @Test
    fun `chaque format annonce le codage dans lequel il doit etre ecrit`() {
        assertEquals("UTF-8", FormatExport.M3U8.codage)
        assertEquals("UTF-8", FormatExport.XSPF.codage)
        // ⚠️ Le M3U d'époque n'est pas de l'UTF-8 : c'est toute sa raison d'être.
        assertEquals("windows-1252", FormatExport.M3U.codage)
    }

    @Test
    fun `une playlist vide produit un fichier valide, pas une erreur`() {
        assertEquals("#EXTM3U\n", contenuExport("Vide", emptyList(), FormatExport.M3U8))
        assertTrue(contenuExport("Vide", emptyList(), FormatExport.XSPF).contains("<trackList>"))
    }
}

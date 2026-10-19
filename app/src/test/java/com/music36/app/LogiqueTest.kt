package com.music36.app

import com.music36.app.data.Analyseur
import com.music36.app.data.M3u
import com.music36.app.data.Piste
import com.music36.app.data.Tri
import com.music36.app.data.formaterDuree
import com.music36.app.data.normaliser
import com.music36.app.data.rechercher
import com.music36.app.data.trier
import com.music36.app.data.typeConteneurDe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Les règles qui ne dépendent ni d'Android ni du lecteur : tri, recherche, formats, `.m3u`. */
class LogiqueTest {

    private fun piste(
        titre: String,
        artiste: String = "Artiste",
        album: String = "Album",
        nomFichier: String = "$titre.flac",
        dossier: String = "Musique/Album",
        annee: Int = 2000,
        duree: Long = 180_000,
        dateAjout: Long = 100,
        dateFichier: Long = 100,
        format: String = "FLAC",
        bits: Int = 16,
        echantillonnage: Int = 44_100,
        numeroPiste: Int = 1
    ) = Piste(
        uri = "content://test/$dossier/$nomFichier",
        racine = "content://test",
        dossier = dossier,
        nomFichier = nomFichier,
        titre = titre,
        artiste = artiste,
        album = album,
        artisteAlbum = artiste,
        genre = "Rock",
        annee = annee,
        numeroPiste = numeroPiste,
        disque = 1,
        duree = duree,
        taille = 1_000,
        dateFichier = dateFichier,
        dateAjout = dateAjout,
        format = format,
        debit = 900,
        echantillonnage = echantillonnage,
        bits = bits,
        canaux = 2
    )

    @Test
    fun `le tri alphabetique ignore la casse`() {
        val liste = listOf(piste("banane"), piste("Abricot"), piste("cerise"))
        val triee = liste.trier(Tri.ALPHABETIQUE, croissant = true).map { it.titre }
        assertEquals(listOf("Abricot", "banane", "cerise"), triee)
    }

    @Test
    fun `le tri decroissant inverse l'ordre`() {
        val liste = listOf(piste("A"), piste("B"))
        assertEquals(listOf("B", "A"), liste.trier(Tri.ALPHABETIQUE, false).map { it.titre })
    }

    @Test
    fun `le tri par date d'ajout et par date de creation sont distincts`() {
        val ancienneAjoutee = piste("Ancienne", dateAjout = 10, dateFichier = 900)
        val recenteAjoutee = piste("Recente", dateAjout = 900, dateFichier = 10)
        val liste = listOf(recenteAjoutee, ancienneAjoutee)

        assertEquals(
            listOf("Ancienne", "Recente"),
            liste.trier(Tri.AJOUT, true).map { it.titre }
        )
        assertEquals(
            listOf("Recente", "Ancienne"),
            liste.trier(Tri.CREATION, true).map { it.titre }
        )
    }

    @Test
    fun `la recherche ignore accents et casse et exige tous les mots`() {
        val liste = listOf(
            piste("Éléphant", artiste = "Les Bêtes"),
            piste("Souris", artiste = "Les Bêtes"),
            piste("Éléphant blanc", artiste = "Autre")
        )
        assertEquals(2, liste.rechercher("elephant").size)
        assertEquals(1, liste.rechercher("elephant betes").size)
        assertEquals(0, liste.rechercher("elephant inexistant").size)
        assertTrue(liste.rechercher("   ").isEmpty())
    }

    @Test
    fun `la normalisation retire les diacritiques`() {
        assertEquals("ecoute ca", normaliser("Écoute ÇA"))
    }

    @Test
    fun `la haute resolution suppose un format sans perte`() {
        assertTrue(piste("a", format = "FLAC", bits = 24, echantillonnage = 96_000).hauteResolution)
        assertFalse(piste("b", format = "FLAC", bits = 16, echantillonnage = 44_100).hauteResolution)
        // Un MP3 à 48 kHz reste de la compression avec perte : jamais « Hi-Res ».
        assertFalse(piste("c", format = "MP3", bits = 24, echantillonnage = 96_000).hauteResolution)
    }

    @Test
    fun `l'etiquette de qualite masque le debit des formats sans perte`() {
        val flac = piste("a", format = "FLAC", bits = 24, echantillonnage = 96_000).qualite()
        assertEquals("FLAC · 24 bits · 96 kHz", flac)
        val mp3 = piste("b", format = "MP3", bits = 0, echantillonnage = 44_100).qualite()
        assertTrue(mp3.contains("kbit/s"))
    }

    @Test
    fun `la duree se formate en minutes puis en heures`() {
        assertEquals("0:00", formaterDuree(0))
        assertEquals("3:05", formaterDuree(185_000))
        assertEquals("1:01:05", formaterDuree(3_665_000))
    }

    @Test
    fun `les extensions audio sont reconnues meme sans type MIME`() {
        assertTrue(Analyseur.estAudio("titre.flac", ""))
        assertTrue(Analyseur.estAudio("titre.DSF", ""))
        assertTrue(Analyseur.estAudio("titre.inconnu", "audio/x-exotique"))
        assertFalse(Analyseur.estAudio("pochette.jpg", "image/jpeg"))
    }

    @Test
    fun `le type de conteneur est deduit de l'extension`() {
        // Les M4A et consorts sont des conteneurs MP4 : c'est ce qui aiguille l'extracteur quand
        // l'URI SAF ne laisse pas voir l'extension.
        assertEquals("audio/mp4", typeConteneurDe("morceau.m4a"))
        assertEquals("audio/mp4", typeConteneurDe("livre.M4B"))
        assertEquals("audio/mp4", typeConteneurDe("bande son.mp4"))
        assertEquals("audio/mpeg", typeConteneurDe("morceau.mp3"))
        assertEquals("audio/flac", typeConteneurDe("morceau.flac"))
        assertEquals("audio/ogg", typeConteneurDe("morceau.opus"))
        // Dans le doute, aucune indication : une fausse piste vaut moins que pas de piste.
        assertEquals(null, typeConteneurDe("morceau.aac"))
        assertEquals(null, typeConteneurDe("morceau"))
    }

    @Test
    fun `les fichiers m4a et aac sont reconnus par l'analyse`() {
        assertTrue(Analyseur.estAudio("titre.m4a", ""))
        assertTrue(Analyseur.estAudio("titre.M4A", "application/octet-stream"))
        assertTrue(Analyseur.estAudio("titre.aac", ""))
        assertTrue(Analyseur.estAudio("titre.m4b", ""))
        assertTrue(Analyseur.estAudio("sans-extension", "audio/mp4"))
        assertEquals("AAC", Analyseur.FORMATS["m4a"])
    }

    @Test
    fun `un m4a peut cacher de l'ALAC, donc son codec se lit dans le fichier`() {
        // Le point de départ du défaut : l'extension .m4a ne dit pas si c'est de l'AAC (avec
        // perte) ou de l'ALAC (sans perte). Ces conteneurs sont donc toujours ouverts.
        assertTrue("m4a" in Analyseur.EXTENSIONS_AMBIGUES)
        assertTrue("mp4" in Analyseur.EXTENSIONS_AMBIGUES)
        assertTrue("mka" in Analyseur.EXTENSIONS_AMBIGUES)
        assertFalse("flac" in Analyseur.EXTENSIONS_AMBIGUES)
        assertFalse("mp3" in Analyseur.EXTENSIONS_AMBIGUES)

        assertEquals("ALAC", Analyseur.nomDuCodec("audio/alac"))
        assertEquals("AAC", Analyseur.nomDuCodec("audio/mp4a-latm"))
        assertEquals("FLAC", Analyseur.nomDuCodec("audio/flac"))
        assertEquals("", Analyseur.nomDuCodec(""))
    }

    @Test
    fun `l'ALAC compte comme un format sans perte`() {
        // Conséquence directe : un ALAC 24 bits mérite son badge Hi-Res, et son débit ne
        // s'affiche pas — un débit n'a de sens que pour une compression avec perte.
        val alac = piste("a", format = "ALAC", bits = 24, echantillonnage = 96_000)
        assertTrue(alac.sansPerte)
        assertTrue(alac.hauteResolution)
        assertFalse(alac.qualite().contains("kbit/s"))
    }

    @Test
    fun `l'annee se lit dans le champ annee ou dans la date`() {
        assertEquals(1997, Analyseur.analyserAnnee("1997"))
        assertEquals(2014, Analyseur.analyserAnnee("", "2014-03-11T00:00:00Z"))
        assertEquals(0, Analyseur.analyserAnnee("", ""))
    }

    @Test
    fun `le numero de piste accepte la notation 3 sur 12`() {
        assertEquals(3, Analyseur.premierNombre("3/12"))
        assertEquals(7, Analyseur.premierNombre("7"))
        assertEquals(0, Analyseur.premierNombre(""))
    }

    @Test
    fun `un m3u est rapproche des pistes par le nom de fichier`() {
        val pistes = listOf(
            piste("Intro", nomFichier = "01 Intro.mp3", dossier = "Musique/Live"),
            piste("Intro", nomFichier = "01 Intro.mp3", dossier = "Musique/Studio"),
            piste("Final", nomFichier = "09 Final.mp3", dossier = "Musique/Studio")
        )
        val contenu = """
            #EXTM3U
            #EXTINF:120,Groupe - Intro
            Studio/01 Intro.mp3
            Studio/09 Final.mp3
        """.trimIndent()

        val resolues = M3u.resoudre(contenu, pistes)
        assertEquals(2, resolues.size)
        // L'homonyme du dossier Live ne doit pas l'emporter.
        assertTrue(resolues[0].contains("Studio"))
    }

    @Test
    fun `un m3u ecrit se relit`() {
        val pistes = listOf(piste("Intro", nomFichier = "01 Intro.mp3"))
        val texte = M3u.ecrire("Ma liste", pistes)
        assertTrue(texte.startsWith("#EXTM3U"))
        assertEquals(pistes.map { it.uri }, M3u.resoudre(texte, pistes))
    }

    @Test
    fun `les segments communs se comptent depuis la fin du chemin`() {
        assertEquals(2, M3u.partieCommune("Musique/Rock/Live", "autre/Rock/Live"))
        assertEquals(0, M3u.partieCommune("Musique/Rock", "Musique/Jazz"))
    }
}

package com.music36.app

import com.music36.app.data.ORDRE_ALPHABETIQUE
import com.music36.app.data.Piste
import com.music36.app.data.Tri
import com.music36.app.data.trier
import com.music36.app.ui.retro.initialeDe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'ordre alphabétique d'une bibliothèque française.
 *
 * ⚠️ **Condition posée par l'utilisateur, et rompue pendant des mois sans que je le voie.**
 * `String.CASE_INSENSITIVE_ORDER` compare les caractères par leur code numérique : « É » y vaut
 * 201, « Z » seulement 90. Sa bibliothèque se terminait donc par « Ça fait plaisir »,
 * « Élément Eau » et « Éternel », rejetés après le Z — relevés sur son propre appareil.
 */
class OrdreAlphabetiqueTest {

    private fun piste(titre: String) = Piste(
        uri = "content://test/$titre",
        racine = "content://test",
        dossier = "Music",
        nomFichier = "$titre.flac",
        titre = titre,
        artiste = "Artiste",
        album = "Album",
        artisteAlbum = "Artiste",
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

    @Test
    fun `les titres accentues ne sont plus rejetes apres le Z`() {
        // Les vrais titres relevés sur l'appareil de l'utilisateur.
        val titres = listOf("Zombie", "Éternel", "Ça va trop vite", "Avenir", "Comme avant")
        val ordre = titres.map(::piste).trier(Tri.ALPHABETIQUE, true).map { it.titre }

        assertEquals(
            listOf("Avenir", "Ça va trop vite", "Comme avant", "Éternel", "Zombie"),
            ordre
        )
    }

    @Test
    fun `un titre accentue se range avec sa lettre, pas ailleurs`() {
        val titres = listOf("Étoile", "Eté", "Examen", "Facile")
        val ordre = titres.map(::piste).trier(Tri.ALPHABETIQUE, true).map { it.titre }
        assertEquals(listOf("Eté", "Étoile", "Examen", "Facile"), ordre)
    }

    @Test
    fun `le tri et le repere alphabetique disent la meme chose`() {
        // ⚠️ C'est l'incohérence que voyait l'utilisateur : le bouton ABC annonçait « E » pour un
        // titre que le tri avait relégué tout en bas.
        val titres = listOf("Éternel", "Elle", "Écho", "Enfin")
        val ordre = titres.map(::piste).trier(Tri.ALPHABETIQUE, true).map { it.titre }
        assertEquals(listOf("E", "E", "E", "E"), ordre.map(::initialeDe))
    }

    @Test
    fun `la casse ne separe pas`() {
        val titres = listOf("banane", "Avion", "ananas", "Bateau")
        val ordre = titres.map(::piste).trier(Tri.ALPHABETIQUE, true).map { it.titre }
        assertEquals(listOf("ananas", "Avion", "banane", "Bateau"), ordre)
    }

    @Test
    fun `l'ordre decroissant est exactement l'inverse`() {
        val titres = listOf("Zombie", "Éternel", "Avenir")
        val pistes = titres.map(::piste)
        assertEquals(
            pistes.trier(Tri.ALPHABETIQUE, true).map { it.titre }.reversed(),
            pistes.trier(Tri.ALPHABETIQUE, false).map { it.titre }
        )
    }

    @Test
    fun `le comparateur range le francais comme un dictionnaire`() {
        assertTrue("É doit précéder Z", ORDRE_ALPHABETIQUE.compare("Éternel", "Zombie") < 0)
        assertTrue("Ç doit précéder D", ORDRE_ALPHABETIQUE.compare("Ça", "Dans") < 0)
        assertTrue("les chiffres passent avant", ORDRE_ALPHABETIQUE.compare("06h16", "Avion") < 0)
    }
}

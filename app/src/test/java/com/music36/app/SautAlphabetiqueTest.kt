package com.music36.app

import com.music36.app.ui.retro.sautAlphabetique
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Le bouton ABC : la molette saute d'une lettre à l'autre.
 *
 * Sur trois mille titres, atteindre le M demandait des centaines de crans. Le saut vise toujours
 * le **premier** titre de la lettre, jamais un titre quelconque du groupe.
 */
class SautAlphabetiqueTest {

    // Une liste déjà triée, comme l'est toute liste de la vue molette.
    private val titres = listOf(
        "All For One",   // 0  A
        "All For U",     // 1  A
        "All Night",     // 2  A
        "Baby",          // 3  B
        "Better",        // 4  B
        "Comme avant",   // 5  C
        "Dernier"        // 6  D
    )

    @Test
    fun `en avant, on atterrit sur le premier titre de la lettre suivante`() {
        assertEquals(3, sautAlphabetique(titres, depuis = 0, sens = 1))
        assertEquals(5, sautAlphabetique(titres, depuis = 3, sens = 1))
        assertEquals(6, sautAlphabetique(titres, depuis = 5, sens = 1))
    }

    @Test
    fun `depuis le milieu d'un groupe, on saute quand meme au groupe suivant`() {
        assertEquals(3, sautAlphabetique(titres, depuis = 1, sens = 1))
        assertEquals(3, sautAlphabetique(titres, depuis = 2, sens = 1))
    }

    @Test
    fun `en arriere depuis le milieu, on revient au debut du groupe courant`() {
        // Le geste attendu quand on s'est arrêté au milieu d'une lettre.
        assertEquals(0, sautAlphabetique(titres, depuis = 2, sens = -1))
        assertEquals(3, sautAlphabetique(titres, depuis = 4, sens = -1))
    }

    @Test
    fun `en arriere depuis un debut de groupe, on passe au groupe precedent`() {
        assertEquals(0, sautAlphabetique(titres, depuis = 3, sens = -1))
        assertEquals(3, sautAlphabetique(titres, depuis = 5, sens = -1))
        assertEquals(5, sautAlphabetique(titres, depuis = 6, sens = -1))
    }

    @Test
    fun `aux deux bouts, on ne boucle pas`() {
        // Boucler ferait repartir la molette à l'autre extrémité sans prévenir.
        assertEquals(0, sautAlphabetique(titres, depuis = 0, sens = -1))
        assertEquals(6, sautAlphabetique(titres, depuis = 6, sens = 1))
    }

    @Test
    fun `les chiffres et les accents suivent les regles du repere`() {
        // `initialeDe` range les chiffres sous « # » et ignore les accents.
        val melange = listOf("06h16", "10 Hours", "Élégie", "Eléphant", "Zombie")
        assertEquals(2, sautAlphabetique(melange, depuis = 0, sens = 1))
        assertEquals(4, sautAlphabetique(melange, depuis = 2, sens = 1))
        assertEquals(0, sautAlphabetique(melange, depuis = 2, sens = -1))
    }

    @Test
    fun `une liste vide ou d'un seul element ne bouge pas`() {
        assertEquals(0, sautAlphabetique(emptyList(), depuis = 0, sens = 1))
        assertEquals(0, sautAlphabetique(listOf("Seul"), depuis = 0, sens = 1))
        assertEquals(0, sautAlphabetique(listOf("Seul"), depuis = 0, sens = -1))
    }
}

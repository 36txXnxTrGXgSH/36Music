package com.music36.app

import androidx.media3.common.util.UnstableApi
import com.music36.app.ui.retro.MemoRetro
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Le souvenir des listes triées de la vue molette.
 *
 * `EcranRetro` lit l'état de lecture, donc recompose deux fois par seconde pendant qu'un morceau
 * joue. Sans souvenir, « Toutes les chansons » retriait la bibliothèque entière à chacune de ces
 * recompositions — un tri complet de plusieurs milliers de titres pour faire avancer une barre.
 */
@UnstableApi
class MemoRetroTest {

    @Test
    fun `le calcul n'a lieu qu'une fois tant que la source ne change pas`() {
        val memo = MemoRetro()
        val source = listOf("a", "b")
        var calculs = 0

        repeat(5) { memo.valeur("tri", source) { calculs++; source.sorted() } }

        assertEquals(1, calculs)
    }

    @Test
    fun `une nouvelle liste au meme contenu relance le calcul`() {
        // ⚠️ Les listes se comparent par **identité** : comparer leur contenu coûterait
        // exactement ce que le souvenir cherche à éviter. Un `StateFlow` ne republie de toute
        // façon une liste que lorsqu'elle a changé.
        val memo = MemoRetro()
        var calculs = 0
        memo.valeur("tri", listOf("a")) { calculs++; "x" }
        memo.valeur("tri", listOf("a")) { calculs++; "x" }
        assertEquals(2, calculs)
    }

    @Test
    fun `une cle simple se compare par valeur`() {
        // Un identifiant d'album reconstruit à l'identique désigne bien le même niveau.
        val memo = MemoRetro()
        val source = listOf("a")
        var calculs = 0
        memo.valeur("album", source, "Blonde|Frank Ocean") { calculs++; "x" }
        memo.valeur("album", source, "Blonde" + "|" + "Frank Ocean") { calculs++; "x" }
        assertEquals(1, calculs)

        memo.valeur("album", source, "Amnesiac|Radiohead") { calculs++; "x" }
        assertEquals(2, calculs)
    }

    @Test
    fun `chaque nom garde son propre souvenir`() {
        val memo = MemoRetro()
        val source = listOf("a")
        val sons = memo.valeur("sons", source) { listOf("tri des sons") }
        val favoris = memo.valeur("favoris", source) { listOf("tri des favoris") }

        assertNotSame(sons, favoris)
        assertSame(sons, memo.valeur<List<String>>("sons", source) { error("ne doit pas recalculer") })
        assertSame(favoris, memo.valeur<List<String>>("favoris", source) { error("ne doit pas recalculer") })
    }

    @Test
    fun `le souvenir rend exactement le meme objet`() {
        val memo = MemoRetro()
        val source = listOf("b", "a")
        val premier = memo.valeur("tri", source) { source.sorted() }
        val second = memo.valeur("tri", source) { source.sorted() }
        assertSame(premier, second)
        assertEquals(listOf("a", "b"), premier)
    }
}

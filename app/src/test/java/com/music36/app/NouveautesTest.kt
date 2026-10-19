package com.music36.app

import com.music36.app.data.GenreNouveaute
import com.music36.app.data.LigneNouveaute
import com.music36.app.data.Nouveaute
import com.music36.app.data.Nouveautes
import com.music36.app.data.doitAnnoncerLesNouveautes
import com.music36.app.data.nouveautesDepuis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'annonce des nouveautés au premier démarrage suivant une mise à jour.
 *
 * Le piège n'est pas de l'afficher, c'est de ne pas l'afficher à tort : une installation neuve
 * n'a rien de nouveau à annoncer, et une fenêtre qui revient à chaque démarrage devient un
 * obstacle plutôt qu'une information.
 */
class NouveautesTest {

    private fun version(code: Int) = Nouveaute(
        code = code, nom = "v$code", date = "2026",
        lignes = listOf(LigneNouveaute(GenreNouveaute.AJOUT, "quelque chose"))
    )

    private val journal = listOf(version(23), version(22), version(21), version(20))

    @Test
    fun `une installation neuve n'annonce rien`() {
        assertFalse(
            doitAnnoncerLesNouveautes(
                derniereVue = 0, versionActuelle = 23, premiereInstallation = true
            )
        )
    }

    @Test
    fun `une mise a jour annonce`() {
        assertTrue(
            doitAnnoncerLesNouveautes(
                derniereVue = 21, versionActuelle = 23, premiereInstallation = false
            )
        )
    }

    @Test
    fun `une version deja vue ne revient pas a chaque demarrage`() {
        assertFalse(
            doitAnnoncerLesNouveautes(
                derniereVue = 23, versionActuelle = 23, premiereInstallation = false
            )
        )
    }

    @Test
    fun `une mise a jour venue d'avant cette mecanique annonce quand meme`() {
        // `derniereVue` vaut zéro, mais l'application a bien été mise à jour : le système le dit.
        assertTrue(
            doitAnnoncerLesNouveautes(
                derniereVue = 0, versionActuelle = 23, premiereInstallation = false
            )
        )
    }

    @Test
    fun `on voit toutes les versions sautees, de la plus recente a la plus ancienne`() {
        val vues = nouveautesDepuis(journal, derniereVue = 20, versionActuelle = 23)
        assertEquals(listOf(23, 22, 21), vues.map { it.code })
    }

    @Test
    fun `sans souvenir, on s'en tient a la version installee`() {
        // Dérouler tout l'historique à quelqu'un qui vient de mettre à jour serait illisible.
        val vues = nouveautesDepuis(journal, derniereVue = 0, versionActuelle = 22)
        assertEquals(listOf(22), vues.map { it.code })
    }

    @Test
    fun `une version sans entree au journal montre la plus proche plutot que rien`() {
        val vues = nouveautesDepuis(journal, derniereVue = 0, versionActuelle = 99)
        assertEquals(1, vues.size)
        assertEquals(23, vues.first().code)
    }

    @Test
    fun `le journal reel decrit la version que l'application porte`() {
        // ⚠️ Ce test est le garde-fou de la livraison : une version publiée sans son entrée ici
        // ne pourrait rien annoncer.
        val tetes = Nouveautes.journal.map { it.code }
        assertEquals("le journal doit être trié du plus récent au plus ancien",
            tetes.sortedDescending(), tetes)
        assertEquals("des codes de version en double", tetes.size, tetes.toSet().size)
        Nouveautes.journal.forEach { version ->
            assertTrue("${version.nom} n'a aucune ligne", version.lignes.isNotEmpty())
            version.lignes.forEach {
                assertTrue("${version.nom} : ligne vide", it.texte.isNotBlank())
            }
        }
    }

    @Test
    fun `la version livree a son entree au journal`() {
        // ⚠️ **Le garde-fou de la livraison.** Publier une version sans décrire ce qu'elle change
        // revient à ne rien annoncer du tout : mieux vaut que la compilation le refuse.
        val tete = Nouveautes.journal.first()
        assertEquals(
            "le journal doit commencer par la version construite",
            BuildConfig.VERSION_CODE, tete.code
        )
        assertEquals(
            "le nom de version du journal doit suivre celui de la construction",
            BuildConfig.VERSION_NAME, tete.nom
        )
    }
}

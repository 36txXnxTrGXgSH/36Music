package com.music36.app

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.music36.app.data.CategoriePalette
import com.music36.app.data.PAS_TEINTE
import com.music36.app.data.PaletteApplication
import com.music36.app.data.ThemeCouleur
import com.music36.app.ui.enNoirAbsolu
import com.music36.app.ui.retro.nomDeTeinte
import com.music36.app.ui.retro.teinteSuivante
import com.music36.app.ui.paletteDe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les ambiances de couleurs de la vue molette restent-elles lisibles, **quelle que soit la
 * teinte** choisie à la barre ?
 *
 * ⚠️ C'est la question que pose la 4.2 : la teinte n'est plus écrite dans le code, elle se
 * choisit à la molette sur tout le cercle chromatique. Vérifier vingt-trois ambiances ne suffit
 * donc plus — il faut les vérifier sur tout le tour, sans quoi une ambiance parfaitement lisible
 * en bleu deviendrait illisible en jaune sans que rien ne le signale.
 */
class ThemesCouleurTest {

    /** Un tour complet, de trente en trente degrés : douze teintes par ambiance. */
    private val teintesTestees = (0 until 12).map { it * 30f }

    private fun verifier(nom: String, couleurs: ColorScheme) {
        val controles = listOf(
            Triple("texte principal", Contraste.entre(couleurs.onSurface, couleurs.surface), 4.5),
            Triple(
                "texte secondaire",
                Contraste.entre(couleurs.onSurfaceVariant, couleurs.surface), 3.0
            ),
            Triple("accent sur le fond", Contraste.entre(couleurs.primary, couleurs.surface), 3.0),
            Triple("texte sur l'accent", Contraste.entre(couleurs.onPrimary, couleurs.primary), 4.0),
            Triple(
                "texte sur la barre de lecture",
                Contraste.entre(couleurs.onSurface, couleurs.surfaceContainerHigh), 4.5
            )
        )
        controles.forEach { (quoi, mesure, seuil) ->
            assertTrue(
                "$nom : $quoi — contraste ${"%.2f".format(mesure)} < $seuil",
                mesure >= seuil
            )
        }
    }

    @Test
    fun `chaque ambiance reste lisible sur tout le cercle chromatique`() {
        ThemeCouleur.entries.forEach { ambiance ->
            teintesTestees.forEach { teinte ->
                val nom = "${ambiance.libelle} à ${teinte.toInt()}°"
                verifier("$nom clair", paletteDe(ambiance, teinte, sombre = false))
                verifier("$nom sombre", paletteDe(ambiance, teinte, sombre = true))
                verifier("$nom AMOLED", enNoirAbsolu(paletteDe(ambiance, teinte, sombre = true)))
            }
        }
    }

    @Test
    fun `les six categories sont toutes garnies`() {
        assertEquals("six catégories attendues", 6, CategoriePalette.entries.size)
        assertEquals("vingt-trois ambiances attendues", 23, ThemeCouleur.entries.size)
        CategoriePalette.entries.forEach { categorie ->
            val siennes = ThemeCouleur.parCategorie(categorie)
            assertTrue(
                "${categorie.libelle} : ${siennes.size} ambiance(s), il en faut au moins trois",
                siennes.size >= 3
            )
        }
        // Chaque ambiance appartient à une catégorie et à une seule : la somme doit retomber
        // sur le compte, sinon l'une d'elles serait inatteignable depuis le menu.
        val rangees = CategoriePalette.entries.sumOf { ThemeCouleur.parCategorie(it).size }
        assertEquals("aucune ambiance ne doit rester hors catégorie", ThemeCouleur.entries.size, rangees)
    }

    @Test
    fun `les ambiances demandees sont bien la`() {
        val attendues = listOf(
            "Vif", "Néon", "Pop", "Électrique",
            "Sombre", "Gothique", "Cyberpunk",
            "Pastel", "Doux", "Aquarelle",
            "Classique", "Monochrome", "Neutre", "Minimaliste", "Luxe",
            "Vintage", "Rétro 70s", "Rétro 80s", "Art déco",
            "Naturelles", "Tropical", "Océan", "Forêt"
        )
        val presentes = ThemeCouleur.entries.map { it.libelle }
        attendues.forEach { nom ->
            assertTrue("l'ambiance « $nom » manque", nom in presentes)
        }
    }

    @Test
    fun `une ambiance change vraiment de couleur d'un bout a l'autre de la barre`() {
        // ⚠️ Une barre de couleurs qui ne changerait pas de couleur n'aurait aucun sens. Les
        // deux ambiances volontairement grises sont exclues : leur barre est un dégradé de gris,
        // et c'est ce qu'on leur demande.
        ThemeCouleur.entries
            .filter { it.saturationAccent > 0.15f }
            .forEach { ambiance ->
                val accents = teintesTestees.map { paletteDe(ambiance, it, sombre = true).primary }
                assertTrue(
                    "${ambiance.libelle} : la barre doit parcourir des couleurs distinctes",
                    accents.toSet().size >= 10
                )
            }
    }

    @Test
    fun `les ambiances grises ne portent presque aucune couleur`() {
        listOf(ThemeCouleur.MONOCHROME, ThemeCouleur.NEUTRE).forEach { ambiance ->
            teintesTestees.forEach { teinte ->
                val accent = paletteDe(ambiance, teinte, sombre = true).primary
                val ecart = maxOf(accent.red, accent.green, accent.blue) -
                    minOf(accent.red, accent.green, accent.blue)
                assertTrue(
                    "${ambiance.libelle} à ${teinte.toInt()}° : écart de couleur $ecart",
                    ecart < 0.12f
                )
            }
        }
    }

    @Test
    fun `un cran de molette fait le tour du cercle en un tour de main`() {
        // Le pas doit diviser le cercle : sinon, en tournant, on ne retomberait jamais
        // exactement sur la teinte d'où l'on est parti.
        assertEquals("le pas doit diviser 360", 0f, 360f % PAS_TEINTE, 0.001f)
        assertTrue("un tour complet ne doit pas demander des centaines de crans", 360f / PAS_TEINTE <= 90f)
    }

    @Test
    fun `la barre de couleurs boucle dans les deux sens`() {
        // ⚠️ Le cercle doit boucler : sans cela, il faudrait revenir sur ses pas pour comparer
        // un rouge orangé et un rouge rosé, qui se trouvent de part et d'autre du zéro.
        assertEquals(PAS_TEINTE, teinteSuivante(0f, 1), 0.001f)
        assertEquals(360f - PAS_TEINTE, teinteSuivante(0f, -1), 0.001f)
        assertEquals(0f, teinteSuivante(360f - PAS_TEINTE, 1), 0.001f)

        // Un tour complet dans un sens puis dans l'autre ramène exactement au point de départ.
        val crans = (360f / PAS_TEINTE).toInt()
        var teinte = 205f
        repeat(crans) { teinte = teinteSuivante(teinte, 1) }
        assertEquals("un tour complet doit revenir au même point", 205f, teinte, 0.001f)
        repeat(crans) { teinte = teinteSuivante(teinte, -1) }
        assertEquals(205f, teinte, 0.001f)
    }

    @Test
    fun `chaque degre du cercle porte un nom`() {
        (0 until 360).forEach { degre ->
            assertTrue("aucun nom pour $degre°", nomDeTeinte(degre.toFloat()).isNotBlank())
        }
        // Les valeurs hors bornes sont ramenées sur le cercle, pas rejetées.
        assertEquals(nomDeTeinte(10f), nomDeTeinte(370f))
        assertEquals(nomDeTeinte(350f), nomDeTeinte(-10f))
    }

    @Test
    fun `les palettes d'avant la 4-2 sont reprises, pas effacees`() {
        // ⚠️ Le vrai risque de cette version : ramener au bleu nuit tous ceux qui avaient choisi
        // autre chose. L'ancienne famille décide de l'ambiance, l'ancienne teinte la suit.
        PaletteApplication.entries.forEach { ancienne ->
            val reprise = ThemeCouleur.depuisPaletteHeritee(ancienne.ordinal)
            assertEquals(
                "${ancienne.libelle} : la teinte doit être conservée",
                ancienne.teinte,
                ThemeCouleur.teintePaletteHeritee(ancienne.ordinal),
                0.001f
            )
            val avant = paletteDe(ancienne, sombre = true).primary
            val apres = paletteDe(reprise, ancienne.teinte, sombre = true).primary
            assertTrue(
                "${ancienne.libelle} : l'accent repris doit rester de la même couleur",
                proche(avant, apres)
            )
        }
    }

    /** Deux accents se ressemblent-ils assez pour qu'on ne remarque pas la reprise ? */
    private fun proche(a: Color, b: Color): Boolean =
        kotlin.math.abs(a.red - b.red) < 0.30f &&
            kotlin.math.abs(a.green - b.green) < 0.30f &&
            kotlin.math.abs(a.blue - b.blue) < 0.30f
}

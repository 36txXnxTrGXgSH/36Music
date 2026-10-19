package com.music36.app

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.music36.app.data.PaletteApplication
import com.music36.app.ui.enNoirAbsolu
import com.music36.app.ui.paletteDe
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.assertEquals

/**
 * Le texte est-il lisible sur son fond, dans chaque palette et chaque mode ?
 *
 * C'est le défaut de la 1.0 : le lecteur héritait d'une couleur de contenu noire, invisible sur
 * les thèmes sombres. Un écran mal contrasté ne fait échouer aucun test d'affichage — il faut
 * mesurer les couleurs elles-mêmes. Les seuils sont ceux du WCAG : 4,5 pour un texte courant,
 * 3,0 pour un grand texte, une icône ou un élément d'interface.
 */
class ContrasteTest {



    private fun verifier(nom: String, couleurs: ColorScheme) {
        val controles = listOf(
            Triple("texte principal", Contraste.entre(couleurs.onSurface, couleurs.surface), 4.5),
            Triple("texte principal sur fond", Contraste.entre(couleurs.onBackground, couleurs.background), 4.5),
            Triple("texte secondaire", Contraste.entre(couleurs.onSurfaceVariant, couleurs.surface), 3.0),
            Triple(
                "texte secondaire sur son conteneur",
                Contraste.entre(couleurs.onSurfaceVariant, couleurs.surfaceVariant), 3.0
            ),
            Triple("accent sur le fond", Contraste.entre(couleurs.primary, couleurs.surface), 3.0),
            Triple("texte sur l'accent", Contraste.entre(couleurs.onPrimary, couleurs.primary), 4.0),
            Triple(
                "texte sur la barre de lecture",
                Contraste.entre(couleurs.onSurface, couleurs.surfaceContainerHigh), 4.5
            ),
            Triple("erreur", Contraste.entre(couleurs.error, couleurs.surface), 3.0)
        )
        controles.forEach { (quoi, mesure, seuil) ->
            assertTrue(
                "$nom : $quoi — contraste ${"%.2f".format(mesure)} < $seuil",
                mesure >= seuil
            )
        }
    }

    @Test
    fun `chaque palette reste lisible en clair comme en sombre`() {
        PaletteApplication.entries.forEach { palette ->
            val nom = "${palette.famille.libelle} / ${palette.libelle}"
            verifier("$nom clair", paletteDe(palette, sombre = false))
            verifier("$nom sombre", paletteDe(palette, sombre = true))
        }
    }

    @Test
    fun `chaque famille propose de quoi choisir`() {
        assertTrue("21 palettes attendues, ${PaletteApplication.entries.size} trouvées",
            PaletteApplication.entries.size == 21)
        com.music36.app.data.FamillePalette.entries.forEach { famille ->
            val siennes = PaletteApplication.parFamille(famille)
            assertTrue("${famille.libelle} : ${siennes.size} palettes", siennes.size >= 3)
        }
    }

    @Test
    fun `les palettes neutres n'ont aucune couleur et restent lisibles`() {
        // ⚠️ Saturation nulle : leur accent ne doit être qu'une valeur de gris, et les trois
        // doivent malgré tout se distinguer — sans quoi en proposer trois n'a pas de sens.
        val neutres = PaletteApplication.parFamille(com.music36.app.data.FamillePalette.NEUTRE)
        listOf(false, true).forEach { sombre ->
            val accents = neutres.map { paletteDe(it, sombre).primary }
            accents.forEach { couleur ->
                assertTrue(
                    "un accent neutre ne doit porter aucune teinte",
                    maxOf(couleur.red, couleur.green, couleur.blue) -
                        minOf(couleur.red, couleur.green, couleur.blue) < 0.02f
                )
            }
            assertEquals("les trois neutres doivent différer", 3, accents.toSet().size)
        }
    }

    @Test
    fun `le mode AMOLED noircit les fonds sans abimer les textes`() {
        PaletteApplication.entries.forEach { palette ->
            val amoled = enNoirAbsolu(paletteDe(palette, sombre = true))
            assertTrue("${palette.libelle} : le fond AMOLED doit être noir", amoled.surface == Color.Black)
            verifier("${palette.libelle} AMOLED", amoled)
        }
    }
}

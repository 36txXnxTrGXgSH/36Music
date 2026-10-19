package com.music36.app

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Le rapport de contraste du WCAG, partagé par les tests qui mesurent les couleurs.
 *
 * Un écran mal contrasté ne fait échouer aucun test d'affichage : il faut mesurer les couleurs
 * elles-mêmes. Les seuils sont 4,5 pour un texte courant et 3,0 pour un grand texte, une icône ou
 * un élément d'interface.
 */
object Contraste {

    fun luminance(couleur: Color): Double {
        fun canal(v: Float): Double {
            val x = v.toDouble()
            return if (x <= 0.03928) x / 12.92 else ((x + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * canal(couleur.red) +
            0.7152 * canal(couleur.green) +
            0.0722 * canal(couleur.blue)
    }

    fun entre(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }
}

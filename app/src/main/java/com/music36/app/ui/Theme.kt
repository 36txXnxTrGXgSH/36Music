package com.music36.app.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import com.music36.app.data.PaletteApplication
import com.music36.app.data.RangQualite
import com.music36.app.data.ThemeApplication
import com.music36.app.data.ThemeCouleur
import kotlin.math.abs

/*
 * Les palettes sont **calculées**, pas écrites à la main.
 *
 * Dix-huit palettes × deux modes × une vingtaine de couleurs, cela ferait sept cents constantes
 * dont personne ne vérifierait les contrastes — c'est exactement ainsi qu'est né le défaut de
 * lisibilité de la 1.0. Ici chaque palette ne déclare que deux teintes ; tout le reste est
 * construit à des **luminances cibles** choisies pour tenir les seuils WCAG, et `ContrasteTest`
 * mesure les dix-huit palettes dans les trois modes.
 */

/** Les luminances visées, en clair et en sombre. Elles seules décident du contraste. */
private object Cibles {
    // Sombre
    const val FOND_SOMBRE = 0.012f
    const val CONTENEUR_SOMBRE = 0.030f
    const val CONTENEUR_HAUT_SOMBRE = 0.055f
    const val VARIANTE_SOMBRE = 0.040f
    const val TEXTE_SOMBRE = 0.72f
    const val TEXTE_SECOND_SOMBRE = 0.36f
    const val ACCENT_SOMBRE = 0.44f
    const val SUR_ACCENT_SOMBRE = 0.020f

    // Clair
    const val FOND_CLAIR = 0.92f
    const val CONTENEUR_CLAIR = 0.86f
    const val CONTENEUR_HAUT_CLAIR = 0.80f
    const val VARIANTE_CLAIR = 0.70f
    const val TEXTE_CLAIR = 0.045f
    const val TEXTE_SECOND_CLAIR = 0.150f
    const val ACCENT_CLAIR = 0.155f
    const val SUR_ACCENT_CLAIR = 0.95f
}

/**
 * Une couleur à la teinte et à la saturation données, ramenée à la **luminance relative** voulue.
 *
 * On ne fixe pas la clarté HSL : à clarté égale, un jaune éclaire deux fois plus qu'un bleu, et
 * c'est la luminance relative — celle du calcul de contraste WCAG — qui décide de la lisibilité.
 * D'où la recherche par dichotomie : c'est ce qui rend les dix-huit palettes lisibles par
 * construction, sans avoir à les régler une par une.
 */
internal fun teinter(teinteDegres: Float, saturation: Float, luminanceVisee: Float): Color {
    var bas = 0f
    var haut = 1f
    var resultat = depuisTsl(teinteDegres, saturation, 0.5f)
    repeat(24) {
        val milieu = (bas + haut) / 2f
        resultat = depuisTsl(teinteDegres, saturation, milieu)
        if (resultat.luminance() < luminanceVisee) bas = milieu else haut = milieu
    }
    return resultat
}

/** Conversion teinte / saturation / clarté vers RVB. */
internal fun depuisTsl(teinteDegres: Float, saturation: Float, clarte: Float): Color {
    val h = ((teinteDegres % 360f) + 360f) % 360f / 60f
    val c = (1f - abs(2f * clarte - 1f)) * saturation.coerceIn(0f, 1f)
    val x = c * (1f - abs(h % 2f - 1f))
    val m = clarte - c / 2f
    val (r, v, b) = when (h.toInt()) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color((r + m).coerceIn(0f, 1f), (v + m).coerceIn(0f, 1f), (b + m).coerceIn(0f, 1f))
}

/** Construit le jeu de couleurs complet d'une palette de l'interface classique. */
fun paletteDe(palette: PaletteApplication, sombre: Boolean): ColorScheme = construire(
    teinte = palette.teinte,
    teinteSecondaire = palette.teinteSecondaire,
    saturationAccent = palette.famille.saturationAccent,
    saturationFond = palette.famille.saturationFond,
    clarteAccent = palette.clarteAccent,
    sombre = sombre
)

/**
 * Construit le jeu de couleurs d'une **ambiance** et d'une teinte choisie à la molette.
 *
 * C'est le même calcul : l'ambiance apporte le caractère — saturations, lumière de l'accent,
 * écart entre les deux teintes — et la barre de couleurs apporte la teinte.
 */
fun paletteDe(theme: ThemeCouleur, teinte: Float, sombre: Boolean): ColorScheme = construire(
    teinte = teinte,
    teinteSecondaire = teinte + theme.ecartSecondaire,
    saturationAccent = theme.saturationAccent,
    saturationFond = theme.saturationFond,
    clarteAccent = theme.clarteAccent,
    sombre = sombre
)

private fun construire(
    teinte: Float,
    teinteSecondaire: Float,
    saturationAccent: Float,
    saturationFond: Float,
    clarteAccent: Float,
    sombre: Boolean
): ColorScheme {
    val h = ((teinte % 360f) + 360f) % 360f
    val h2 = ((teinteSecondaire % 360f) + 360f) % 360f
    val h3 = (h + 150f) % 360f
    val sAccent = saturationAccent
    val sFond = saturationFond

    // ⚠️ La clarté de l'accent est **bornée** : trop clair, le texte posé dessus ne passe plus ;
    // trop sombre, l'accent lui-même disparaît dans le fond. Les bornes viennent des seuils de
    // contraste, pas d'un réglage esthétique.
    val lAccent = if (sombre) {
        (Cibles.ACCENT_SOMBRE * clarteAccent).coerceIn(0.24f, 0.78f)
    } else {
        (Cibles.ACCENT_CLAIR * clarteAccent).coerceIn(0.07f, 0.26f)
    }

    // Le texte posé sur l'accent suit sa lumière, jamais celle du thème : un accent clair
    // réclame un texte sombre, et l'inverse.
    val lSurAccent = if (lAccent > 0.20f) Cibles.SUR_ACCENT_SOMBRE else Cibles.SUR_ACCENT_CLAIR

    return if (sombre) {
        val fond = teinter(h, sFond, Cibles.FOND_SOMBRE)
        darkColorScheme(
            primary = teinter(h, sAccent, lAccent),
            onPrimary = teinter(h, sAccent * 0.7f, lSurAccent),
            primaryContainer = teinter(h, sAccent * 0.8f, 0.08f),
            onPrimaryContainer = teinter(h, sAccent * 0.35f, 0.78f),
            secondary = teinter(h2, sAccent * 0.85f, lAccent),
            onSecondary = teinter(h2, sAccent * 0.7f, lSurAccent),
            secondaryContainer = teinter(h2, sAccent * 0.8f, 0.07f),
            onSecondaryContainer = teinter(h2, sAccent * 0.35f, 0.78f),
            tertiary = teinter(h3, sAccent * 0.8f, lAccent),
            onTertiary = teinter(h3, sAccent * 0.7f, lSurAccent),
            background = fond,
            onBackground = teinter(h, sFond * 0.6f, Cibles.TEXTE_SOMBRE),
            surface = fond,
            onSurface = teinter(h, sFond * 0.6f, Cibles.TEXTE_SOMBRE),
            surfaceVariant = teinter(h, sFond * 0.9f, Cibles.VARIANTE_SOMBRE),
            onSurfaceVariant = teinter(h, sFond * 0.7f, Cibles.TEXTE_SECOND_SOMBRE),
            surfaceContainerLowest = teinter(h, sFond, 0.006f),
            surfaceContainerLow = teinter(h, sFond, 0.020f),
            surfaceContainer = teinter(h, sFond, Cibles.CONTENEUR_SOMBRE),
            surfaceContainerHigh = teinter(h, sFond, Cibles.CONTENEUR_HAUT_SOMBRE),
            surfaceContainerHighest = teinter(h, sFond, 0.085f),
            outline = teinter(h, sFond * 0.8f, 0.26f),
            outlineVariant = teinter(h, sFond * 0.9f, 0.055f),
            error = teinter(8f, 0.85f, Cibles.ACCENT_SOMBRE),
            onError = teinter(8f, 0.6f, Cibles.SUR_ACCENT_SOMBRE),
            scrim = Color.Black
        )
    } else {
        val fond = teinter(h, sFond * 0.55f, Cibles.FOND_CLAIR)
        lightColorScheme(
            primary = teinter(h, sAccent, lAccent),
            onPrimary = teinter(h, sAccent * 0.2f, lSurAccent),
            primaryContainer = teinter(h, sAccent * 0.55f, 0.72f),
            onPrimaryContainer = teinter(h, sAccent, 0.05f),
            secondary = teinter(h2, sAccent * 0.85f, lAccent),
            onSecondary = teinter(h2, sAccent * 0.2f, lSurAccent),
            secondaryContainer = teinter(h2, sAccent * 0.5f, 0.72f),
            onSecondaryContainer = teinter(h2, sAccent, 0.05f),
            tertiary = teinter(h3, sAccent * 0.8f, lAccent),
            onTertiary = teinter(h3, sAccent * 0.2f, lSurAccent),
            background = fond,
            onBackground = teinter(h, sFond * 0.9f, Cibles.TEXTE_CLAIR),
            surface = fond,
            onSurface = teinter(h, sFond * 0.9f, Cibles.TEXTE_CLAIR),
            surfaceVariant = teinter(h, sFond * 0.5f, Cibles.VARIANTE_CLAIR),
            onSurfaceVariant = teinter(h, sFond * 0.8f, Cibles.TEXTE_SECOND_CLAIR),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = teinter(h, sFond * 0.4f, 0.90f),
            surfaceContainer = teinter(h, sFond * 0.45f, Cibles.CONTENEUR_CLAIR),
            surfaceContainerHigh = teinter(h, sFond * 0.5f, Cibles.CONTENEUR_HAUT_CLAIR),
            surfaceContainerHighest = teinter(h, sFond * 0.5f, 0.75f),
            outline = teinter(h, sFond * 0.7f, 0.28f),
            outlineVariant = teinter(h, sFond * 0.5f, 0.62f),
            error = teinter(8f, 0.85f, Cibles.ACCENT_CLAIR),
            onError = Color.White,
            scrim = Color.Black
        )
    }
}

/**
 * Le thème de l'application.
 *
 * Trois réglages se combinent :
 * - le **thème** : suivre le système, clair, sombre, ou noir AMOLED ;
 * - la **palette** : vingt et une variantes pour l'interface classique, ou — vue molette — une
 *   des vingt-trois ambiances de [ThemeCouleur] accompagnée de la teinte choisie à la molette ;
 * - les **couleurs dynamiques** (Material You, Android 12+), qui prennent le pas sur la palette
 *   quand elles sont allumées.
 *
 * Le mode AMOLED n'est pas une palette de plus : c'est la palette sombre choisie, dont les fonds
 * passent au noir absolu — ce qui éteint réellement les pixels de ces dalles — sans toucher aux
 * couleurs de texte, qui sont calculées une fois pour toutes.
 */
@Composable
fun ThemeMusique(
    theme: ThemeApplication,
    dynamique: Boolean,
    palette: PaletteApplication = PaletteApplication.CLASSIQUE,
    /** L'ambiance de la vue molette ; quand elle est donnée, elle l'emporte sur [palette]. */
    themeCouleur: ThemeCouleur? = null,
    teinte: Float = 0f,
    contenu: @Composable () -> Unit
) {
    val sombre = when (theme) {
        ThemeApplication.SYSTEME -> isSystemInDarkTheme()
        ThemeApplication.CLAIR -> false
        ThemeApplication.SOMBRE, ThemeApplication.AMOLED -> true
    }
    val contexte = LocalContext.current

    val couleurs = when {
        dynamique && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (sombre) dynamicDarkColorScheme(contexte) else dynamicLightColorScheme(contexte)

        themeCouleur != null -> paletteDe(themeCouleur, teinte, sombre)

        else -> paletteDe(palette, sombre)
    }

    MaterialTheme(
        colorScheme = if (theme == ThemeApplication.AMOLED) enNoirAbsolu(couleurs) else couleurs,
        content = contenu
    )
}

/** Fonds au noir pur, textes inchangés. */
fun enNoirAbsolu(couleurs: ColorScheme): ColorScheme = couleurs.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF080808),
    surfaceContainer = Color(0xFF0D0D0D),
    surfaceContainerHigh = Color(0xFF151515),
    surfaceContainerHighest = Color(0xFF1E1E1E)
)

/**
 * Les couleurs de rang de qualité, pour la vue molette.
 *
 * Elles suivent la règle du reste du fichier : on ne fixe pas une teinte « or » une fois pour
 * toutes, on vise une **luminance** — sinon l'or lisible sur fond sombre devient illisible sur
 * fond clair, ce qui était précisément le défaut de la 1.0. Les cibles sont choisies pour tenir
 * 4,5:1 contre le fond des lignes dans les dix-huit palettes, en clair comme en sombre, et
 * `QualiteCouleurTest` le vérifie.
 */
private object CiblesQualite {
    const val CLAIR = 0.13f
    const val SOMBRE = 0.52f
}

/** Teinte et saturation de chaque rang : or, argent, turquoise. */
private fun tonDe(rang: RangQualite): Pair<Float, Float>? = when (rang) {
    RangQualite.HAUTE_RESOLUTION -> 184f to 0.62f
    RangQualite.SANS_PERTE -> 44f to 0.80f
    RangQualite.COMPRESSE_EVOLUE -> 222f to 0.16f
    RangQualite.ORDINAIRE -> null
}

/**
 * La couleur d'un rang de qualité, ou `null` pour le rang ordinaire — qui garde la couleur de
 * texte habituelle plutôt qu'une teinte de plus à déchiffrer.
 */
fun couleurQualite(rang: RangQualite, sombre: Boolean): Color? {
    val (teinte, saturation) = tonDe(rang) ?: return null
    return teinter(teinte, saturation, if (sombre) CiblesQualite.SOMBRE else CiblesQualite.CLAIR)
}

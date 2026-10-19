package com.music36.app.data

/**
 * Les réglages qui se règlent au **nombre** plutôt qu'au choix.
 *
 * ⚠️ Ils existent parce que la molette n'a pas de curseur : un réglage continu se parcourt cran
 * par cran, et le pas doit être choisi pour qu'un tour de roue couvre la plage sans la survoler.
 * D'où [pas], qui n'est pas une commodité d'affichage mais la granularité réelle du geste.
 *
 * Les mêmes valeurs servent à l'interface moderne, où elles sont posées sur un `Slider` : les
 * deux interfaces règlent la même chose, et il n'y a qu'un endroit où en changer les bornes.
 */
enum class ReglageNumerique(
    val libelle: String,
    val explication: String,
    val minimum: Float,
    val maximum: Float,
    val pas: Float
) {
    DUREE_MINIMALE(
        "Durée minimale d'un titre",
        "Les sons plus courts que cela sont ignorés par l'analyse : jingles, bruitages, " +
            "messages vocaux.",
        0f, 120f, 1f
    ),
    FONDU(
        "Fondu au début et à la fin",
        "La musique monte et descend en douceur au lieu de démarrer et de couper net.",
        0f, 8_000f, 250f
    ),
    VITESSE(
        "Vitesse de lecture",
        "Un podcast se suit très bien à 1,25×. La tonalité se conserve séparément.",
        0.5f, 2.5f, 0.05f
    ),
    BASS_BOOST(
        "Correcteur de graves",
        "N'agit que si l'égaliseur est allumé.",
        0f, 1_000f, 25f
    ),
    VIRTUALISEUR(
        "Spatialisation",
        "Élargit la scène sonore. N'agit que si l'égaliseur est allumé.",
        0f, 1_000f, 25f
    ),
    GAIN(
        "Gain sonore",
        "Remonte le niveau des enregistrements trop discrets, en millibels.",
        0f, 2_000f, 50f
    );

    /** La valeur en cours, lue dans les réglages. */
    fun valeurDe(reglages: EtatReglages): Float = when (this) {
        DUREE_MINIMALE -> reglages.dureeMinimale.toFloat()
        FONDU -> reglages.fonduMs.toFloat()
        VITESSE -> reglages.vitesse
        BASS_BOOST -> reglages.bassBoost.toFloat()
        VIRTUALISEUR -> reglages.virtualiseur.toFloat()
        GAIN -> reglages.gainSonore.toFloat()
    }

    /** Les réglages, une fois cette valeur retenue. */
    fun appliquer(reglages: EtatReglages, valeur: Float): EtatReglages = when (this) {
        DUREE_MINIMALE -> reglages.copy(dureeMinimale = valeur.toInt())
        FONDU -> reglages.copy(fonduMs = valeur.toInt())
        // ⚠️ Arrondi au vingtième : la vitesse est ensuite multipliée par la durée d'un morceau,
        // et un 1,0499999 traîné par les flottants se lirait « 1,05× » en affichant autre chose.
        VITESSE -> reglages.copy(vitesse = (valeur * 20).toInt() / 20f)
        BASS_BOOST -> reglages.copy(bassBoost = valeur.toInt())
        VIRTUALISEUR -> reglages.copy(virtualiseur = valeur.toInt())
        GAIN -> reglages.copy(gainSonore = valeur.toInt())
    }

    /** Ce que la valeur donne à lire, dans l'unité de la chose réglée. */
    fun texte(valeur: Float): String = when (this) {
        DUREE_MINIMALE -> if (valeur < 1f) "aucun filtre" else "${valeur.toInt()} s"
        FONDU -> if (valeur < 1f) "désactivé" else "%.2f s".format(valeur / 1000f)
        VITESSE -> "%.2f×".format(valeur)
        BASS_BOOST, VIRTUALISEUR, GAIN ->
            if (valeur < 1f) "aucun" else "${(valeur * 100 / maximum).toInt()} %"
    }
}

/**
 * La valeur atteinte après un cran de molette.
 *
 * ⚠️ Bornée, **sans boucler** : contrairement au cercle chromatique, une vitesse de lecture ou un
 * fondu ont un plancher et un plafond qui veulent dire quelque chose. Passer de 2,5× à 0,5× en un
 * cran serait une mauvaise surprise, jamais une intention.
 */
fun valeurSuivante(reglage: ReglageNumerique, depart: Float, sens: Int): Float =
    (depart + sens * reglage.pas).coerceIn(reglage.minimum, reglage.maximum)

/** Le niveau d'une bande d'égaliseur, en millibels, ou zéro si elle n'a jamais été touchée. */
fun niveauDeBande(reglages: EtatReglages, index: Int): Float =
    reglages.bandes.getOrElse(index) { 0 }.toFloat()

/**
 * Les réglages, une bande d'égaliseur ayant changé de niveau.
 *
 * ⚠️ Le préréglage passe à −1 : une bande réglée à la main ne relève plus d'un préréglage, et
 * laisser les deux cohabiter ferait revenir la courbe du préréglage au prochain démarrage.
 */
fun avecBande(reglages: EtatReglages, index: Int, valeur: Float, nombreDeBandes: Int): EtatReglages {
    val niveaux = reglages.bandes.toMutableList()
    while (niveaux.size < maxOf(nombreDeBandes, index + 1)) niveaux.add(0)
    niveaux[index] = valeur.toInt()
    return reglages.copy(
        egaliseurBandes = niveaux.joinToString(","),
        egaliseurPreset = -1
    )
}

/** Une fréquence de bande, telle qu'on l'écrit sur un égaliseur. */
fun libelleDeBande(frequence: Int): String =
    if (frequence >= 1000) "${frequence / 1000} kHz" else "$frequence Hz"

/** Un niveau de bande, en décibels signés — l'unité qu'attend l'œil sur un égaliseur. */
fun texteDeBande(millibels: Float): String = "%+.1f dB".format(millibels / 100f)

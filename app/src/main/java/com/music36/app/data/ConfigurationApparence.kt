package com.music36.app.data

/** Le nombre d'emplacements de sauvegarde proposés. */
const val NOMBRE_CONFIGURATIONS = 3

/**
 * L'apparence de la vue molette, mise de côté pour être rappelée d'un geste.
 *
 * Ce sont **tous** les réglages de « Réglages de la vue Rétro » qui touchent à l'aspect : fond du
 * lecteur, molette (style, couleur, effet), thème, ambiance et teinte, liseré, barre de
 * progression. Rien d'autre — la bibliothèque, la lecture ou l'égaliseur n'ont rien à faire
 * dans une configuration de couleurs, et les y mettre ferait changer le son en changeant de
 * thème.
 */
data class ConfigurationApparence(
    val fondLecteurRetro: Int,
    val couleurFondRetro: Int,
    val styleMolette: Int,
    val couleurMolette: Int,
    val effetMolette: Int,
    val themeRetro: Int,
    val themeCouleurRetro: Int,
    val teinteRetro: Float,
    val dynamiqueRetro: Boolean,
    val lisereLecteur: Boolean,
    val styleBarre: Int,
    /** Quand elle a été enregistrée, en millisecondes. N'entre pas dans la comparaison. */
    val enregistreeLe: Long = 0L
) {
    /** Les réglages, une fois cette configuration rappelée. */
    fun appliquer(reglages: EtatReglages): EtatReglages = reglages.copy(
        fondLecteurRetro = fondLecteurRetro,
        couleurFondRetro = couleurFondRetro,
        styleMolette = styleMolette,
        couleurMolette = couleurMolette,
        effetMolette = effetMolette,
        themeRetro = themeRetro,
        themeCouleurRetro = themeCouleurRetro,
        teinteRetro = teinteRetro,
        dynamiqueRetro = dynamiqueRetro,
        lisereLecteur = lisereLecteur,
        styleBarre = styleBarre
    )

    /** Vrai si l'apparence en cours est exactement celle-ci, date d'enregistrement mise à part. */
    fun estEnCours(reglages: EtatReglages): Boolean =
        copy(enregistreeLe = 0L) == depuis(reglages, 0L)

    /**
     * La forme enregistrée : des valeurs séparées par `|`, précédées d'un numéro de format.
     *
     * ⚠️ La teinte s'écrit avec `Float.toString()`, jamais avec `format` : `"%.1f".format`
     * suit la langue du téléphone et écrirait « 205,0 », que la relecture ne comprendrait plus.
     */
    fun enTexte(): String = listOf(
        FORMAT,
        fondLecteurRetro, couleurFondRetro, styleMolette, couleurMolette, effetMolette,
        themeRetro, themeCouleurRetro, teinteRetro.toString(),
        if (dynamiqueRetro) 1 else 0, if (lisereLecteur) 1 else 0, styleBarre,
        enregistreeLe
    ).joinToString("|")

    companion object {
        private const val FORMAT = "a1"

        /** L'apparence en cours, prête à être mise de côté. */
        fun depuis(reglages: EtatReglages, maintenant: Long): ConfigurationApparence =
            ConfigurationApparence(
                fondLecteurRetro = reglages.fondLecteurRetro,
                couleurFondRetro = reglages.couleurFondRetro,
                styleMolette = reglages.styleMolette,
                couleurMolette = reglages.couleurMolette,
                effetMolette = reglages.effetMolette,
                themeRetro = reglages.themeRetro,
                themeCouleurRetro = reglages.themeCouleurRetro,
                teinteRetro = reglages.teinteRetro,
                dynamiqueRetro = reglages.dynamiqueRetro,
                lisereLecteur = reglages.lisereLecteur,
                styleBarre = reglages.styleBarre,
                enregistreeLe = maintenant
            )

        /**
         * Relit une configuration enregistrée, ou `null` si l'emplacement est vide ou illisible.
         *
         * ⚠️ Un texte abîmé donne un emplacement **vide**, jamais une exception : une sauvegarde
         * corrompue ne doit pas empêcher d'ouvrir le menu qui permet de la remplacer.
         */
        fun lire(texte: String): ConfigurationApparence? {
            val v = texte.split('|')
            if (v.size != 13 || v[0] != FORMAT) return null
            return runCatching {
                ConfigurationApparence(
                    fondLecteurRetro = v[1].toInt(),
                    couleurFondRetro = v[2].toInt(),
                    styleMolette = v[3].toInt(),
                    couleurMolette = v[4].toInt(),
                    effetMolette = v[5].toInt(),
                    themeRetro = v[6].toInt(),
                    themeCouleurRetro = v[7].toInt(),
                    teinteRetro = v[8].toFloat(),
                    dynamiqueRetro = v[9] == "1",
                    lisereLecteur = v[10] == "1",
                    styleBarre = v[11].toInt(),
                    enregistreeLe = v[12].toLong()
                )
            }.getOrNull()
        }
    }
}

/** Les configurations enregistrées, emplacement par emplacement (`null` = vide). */
val EtatReglages.configurations: List<ConfigurationApparence?>
    get() = List(NOMBRE_CONFIGURATIONS) { index ->
        configurationsRetro.getOrNull(index)?.let { ConfigurationApparence.lire(it) }
    }

/**
 * Les réglages, un emplacement ayant été rempli (ou vidé si [configuration] est `null`).
 *
 * La liste est toujours ramenée à [NOMBRE_CONFIGURATIONS] : un réglage relu d'une version
 * future plus généreuse ne doit pas faire apparaître d'emplacement fantôme.
 */
fun EtatReglages.avecConfiguration(
    emplacement: Int,
    configuration: ConfigurationApparence?
): EtatReglages {
    val textes = MutableList(NOMBRE_CONFIGURATIONS) { configurationsRetro.getOrElse(it) { "" } }
    textes[emplacement] = configuration?.enTexte() ?: ""
    return copy(configurationsRetro = textes)
}

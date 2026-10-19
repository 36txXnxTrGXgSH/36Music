package com.music36.app.data

/**
 * La recherche de la vue molette, avec ses jokers.
 *
 * - `emi`     : le nom **contient** « emi » (sans joker, on cherche partout) ;
 * - `emi*`    : le nom **commence** par « emi » ;
 * - `*emi`    : le nom **finit** par « emi » ;
 * - `rat*emi` : le nom commence par « rat » **et** finit par « emi ».
 *
 * Plusieurs jokers se combinent : `a*b*c` veut dire « commence par a, contient b plus loin,
 * finit par c ». Casse et accents sont ignorés — « Éminem », « eminem » et « EMINEM » se
 * valent, comme dans le reste de l'application.
 *
 * ⚠️ Sans joker, on cherche **partout** dans le nom et non le nom exact : c'est ce qu'on attend
 * d'une recherche, et taper « eminem » en entier pour trouver « Eminem » serait absurde.
 */
fun correspond(motif: String, texte: String): Boolean = MotifRecherche(motif).correspond(texte)

/**
 * Un motif préparé une fois, puis confronté à autant de noms qu'on veut.
 *
 * ⚠️ Préparer le motif à chaque nom revenait à le normaliser des milliers de fois par
 * recherche ; il ne change pas d'un nom à l'autre.
 */
class MotifRecherche(motif: String) {
    private val m = normaliser(motif.trim())

    fun correspond(texte: String): Boolean {
        if (m.isEmpty()) return true
        return correspondNormalise(m, normaliser(texte))
    }
}

private fun correspondNormalise(m: String, t: String): Boolean {
    if ('*' !in m) return m in t

    val morceaux = m.split('*')
    val ancreDebut = !m.startsWith('*')
    val ancreFin = !m.endsWith('*')
    var depuis = 0

    morceaux.forEachIndexed { index, morceau ->
        if (morceau.isEmpty()) return@forEachIndexed
        when {
            index == 0 && ancreDebut -> {
                if (!t.startsWith(morceau)) return false
                depuis = morceau.length
            }
            // ⚠️ La fin se vérifie **après** ce qui précède : « rat*at » ne doit pas accepter
            // « rat », où le même « at » servirait deux fois.
            index == morceaux.lastIndex && ancreFin -> {
                if (!t.endsWith(morceau) || t.length - morceau.length < depuis) return false
                depuis = t.length
            }
            else -> {
                val trouve = t.indexOf(morceau, depuis)
                if (trouve < 0) return false
                depuis = trouve + morceau.length
            }
        }
    }
    return true
}

/**
 * Comment ranger les titres d'une playlist.
 *
 * L'ordre des entrées est celui du bouton : chaque appui passe à la suivante, et la dernière
 * ramène à la première. ⚠️ `ORIGINE` vient **en premier** parce qu'il est le défaut : une
 * playlist s'ouvre dans l'ordre voulu par celui qui l'a composée.
 */
enum class TriPlaylist(val libelle: String, val court: String) {
    ORIGINE("Ordre du fichier", "Fichier"),
    TITRE("Titre", "Titre"),
    ARTISTE("Artiste", "Artiste"),
    AJOUT("Date d'ajout", "Ajout");

    /** Le tri suivant, pour le bouton qui les fait défiler. */
    fun suivant(): TriPlaylist = entries[(ordinal + 1) % entries.size]
}

/**
 * Les titres d'une playlist, rangés selon [tri].
 *
 * ⚠️ « Date d'ajout » est la date d'entrée du titre **dans la bibliothèque**, la plus récente en
 * tête : une playlist ne retient pas quand chaque titre y a été rangé, et un tri qui reprendrait
 * l'ordre du fichier sous un autre nom ne servirait à rien.
 */
fun List<Piste>.selonTri(tri: TriPlaylist): List<Piste> = selonTri(tri) { it }

/**
 * Le même tri, pour des éléments qui **portent** une piste sans en être une.
 *
 * Sert à trier une playlist sans perdre, pour chaque ligne, quelle apparition du titre elle
 * représente — sans quoi « retirer cette ligne » ne saurait plus laquelle retirer.
 */
fun <T> List<T>.selonTri(tri: TriPlaylist, piste: (T) -> Piste): List<T> = when (tri) {
    TriPlaylist.ORIGINE -> this
    TriPlaylist.TITRE -> sortedWith(compareBy(ORDRE_ALPHABETIQUE) { piste(it).titre })
    TriPlaylist.ARTISTE -> sortedWith(
        compareBy<T, String>(ORDRE_ALPHABETIQUE) { piste(it).artisteAffiche }
            .thenBy(ORDRE_ALPHABETIQUE) { piste(it).titre }
    )
    TriPlaylist.AJOUT -> sortedByDescending { piste(it).dateAjout }
}

/** Le tri retenu pour une playlist, `ORIGINE` tant qu'on n'y a pas touché. */
fun triDe(reglages: EtatReglages, idPlaylist: Long): TriPlaylist =
    reglages.trisPlaylists.lineSequence()
        .map { it.split(':') }
        .firstOrNull { it.size == 2 && it[0] == idPlaylist.toString() }
        ?.let { TriPlaylist.entries.getOrNull(it[1].toIntOrNull() ?: -1) }
        ?: TriPlaylist.ORIGINE

/**
 * Les réglages, le tri d'une playlist ayant changé.
 *
 * Retenu **par playlist** : ranger « Rap FR » par artiste ne doit pas déranger « Électro ».
 * Revenir à l'ordre du fichier efface la ligne plutôt que de l'écrire, pour que la liste ne
 * grossisse pas des playlists qu'on ne trie pas.
 */
fun EtatReglages.avecTri(idPlaylist: Long, tri: TriPlaylist): EtatReglages {
    val autres = trisPlaylists.lineSequence()
        .filter { it.isNotBlank() && it.substringBefore(':') != idPlaylist.toString() }
        .toList()
    val lignes = if (tri == TriPlaylist.ORIGINE) autres else autres + "$idPlaylist:${tri.ordinal}"
    return copy(trisPlaylists = lignes.joinToString("\n"))
}

/**
 * Chaque titre d'une playlist, avec le rang de son apparition (0 pour la première fois qu'il
 * figure, 1 pour la deuxième…), compté dans l'ordre du fichier.
 *
 * ⚠️ C'est ce rang, et non la position affichée, qui désigne une ligne : la position change
 * avec le tri, le rang jamais.
 */
fun <T> numeroterLesApparitions(elements: List<T>, cle: (T) -> String): List<Pair<T, Int>> {
    val vues = HashMap<String, Int>()
    return elements.map { element ->
        val rang = vues[cle(element)] ?: 0
        vues[cle(element)] = rang + 1
        element to rang
    }
}

/** L'index, dans [uris], de la [occurrence]-ième apparition de [uri] — ou `null`. */
fun indexDeLApparition(uris: List<String>, uri: String, occurrence: Int): Int? =
    uris.withIndex().filter { it.value == uri }.getOrNull(occurrence)?.index

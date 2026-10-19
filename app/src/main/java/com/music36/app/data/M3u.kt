package com.music36.app.data

/**
 * Lecture et écriture des playlists `.m3u`.
 *
 * Un `.m3u` désigne ses pistes par des chemins de fichiers, alors que 36Music travaille sur des
 * URI SAF : on ne peut donc pas les rapprocher directement. On associe chaque ligne à la piste
 * connue dont le nom de fichier correspond, en départageant les homonymes par la fin du chemin —
 * c'est ce qui distingue `Live/01 Intro.mp3` de `Studio/01 Intro.mp3`.
 */
object M3u {

    fun resoudre(contenu: String, pistes: List<Piste>): List<String> {
        val parNom = pistes.groupBy { it.nomFichier.lowercase() }
        val resultat = mutableListOf<String>()

        contenu.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .forEach { ligne ->
                val chemin = ligne.replace('\\', '/')
                val nom = chemin.substringAfterLast('/').lowercase()
                val candidats = parNom[nom] ?: return@forEach
                val choisie = if (candidats.size == 1) candidats.first()
                else candidats.maxByOrNull { partieCommune(it.dossier, chemin.substringBeforeLast('/')) }
                    ?: candidats.first()
                resultat += choisie.uri
            }
        return resultat
    }

    /** Nombre de segments communs, en partant de la fin des deux chemins. */
    fun partieCommune(a: String, b: String): Int {
        val ga = a.replace('\\', '/').split('/').filter { it.isNotBlank() }.asReversed()
        val gb = b.replace('\\', '/').split('/').filter { it.isNotBlank() }.asReversed()
        var n = 0
        while (n < ga.size && n < gb.size && ga[n].equals(gb[n], ignoreCase = true)) n++
        return n
    }

    /**
     * Écrit une playlist au format `#EXTM3U`, avec durées et titres.
     *
     * ⚠️ **Le format est toujours réécrit en entier**, quel qu'ait été celui du fichier
     * d'origine : une liste de chemins nus, un fichier sans en-tête ou avec des antislashs
     * ressort normalisée. C'est voulu — un fichier à moitié conforme se relit mal, chez nous
     * comme chez les autres lecteurs.
     *
     * [chemin] décide de la façon de désigner chaque piste ; par défaut le chemin lisible du
     * dossier, qui convient à un export posé à côté de la musique. Pour réécrire un `.m3u` à sa
     * place, passer un chemin **relatif au dossier de ce fichier** (voir `cheminRelatif`).
     */
    fun ecrire(
        nom: String,
        pistes: List<Piste>,
        chemin: (Piste) -> String = { "${it.dossier}/${it.nomFichier}" }
    ): String = buildString {
        appendLine("#EXTM3U")
        appendLine("#PLAYLIST:$nom")
        for (piste in pistes) {
            appendLine("#EXTINF:${piste.duree / 1000},${piste.artisteAffiche} - ${piste.titre}")
            appendLine(chemin(piste))
        }
    }
}

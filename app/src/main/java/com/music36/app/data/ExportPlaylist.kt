package com.music36.app.data

/**
 * Les formats de playlist qu'on sait écrire.
 *
 * Trois, pas dix : un format de plus qui ne serait lu par rien n'aide personne. Chacun répond à
 * un usage précis, et le premier est celui qu'on conseille.
 */
enum class FormatExport(
    val libelle: String,
    val extension: String,
    val mime: String,
    val description: String
) {
    /** Le format universel, en UTF-8 : VLC, foobar2000, Poweramp, la plupart des autoradios. */
    M3U8(
        libelle = "M3U8",
        extension = "m3u8",
        mime = "audio/x-mpegurl",
        description = "Universel, UTF-8. C'est aussi celui que 36Music sait relire."
    ),

    /** Le même, en codage d'époque : pour les appareils qui ne comprennent pas l'UTF-8. */
    M3U(
        libelle = "M3U",
        extension = "m3u",
        mime = "audio/x-mpegurl",
        description = "Ancien codage. Pour de vieux appareils ; les accents peuvent s'abîmer."
    ),

    /** Le standard ouvert, en XML : VLC, Clementine. Le plus complet, le moins répandu. */
    XSPF(
        libelle = "XSPF",
        extension = "xspf",
        mime = "application/xspf+xml",
        description = "Standard ouvert en XML, le plus complet. Lu par VLC et Clementine."
    );

    /** Le codage dans lequel le fichier doit être écrit. */
    val codage: String get() = if (this == M3U) "windows-1252" else "UTF-8"
}

/**
 * Le chemin d'un titre tel qu'un autre lecteur pourra le retrouver.
 *
 * ⚠️ On écrit un chemin **relatif**, jamais l'URI `content://` : celle-ci ne vaut que pour cette
 * application, sur ce téléphone. `Music/FR/titre.flac` se retrouve partout, et c'est aussi la
 * forme que 36Music sait relire à l'import.
 */
fun cheminExport(piste: Piste): String =
    if (piste.dossier.isBlank()) piste.nomFichier else "${piste.dossier}/${piste.nomFichier}"

/** Le nom du fichier produit, débarrassé de ce qu'un système de fichiers refuse. */
fun nomFichierExport(nom: String, format: FormatExport): String {
    val propre = nom.map { if (it in "\\/:*?\"<>|" || it.code < 32) '_' else it }
        .joinToString("")
        .trim()
        .ifBlank { "playlist" }
    return "$propre.${format.extension}"
}

/** Le contenu du fichier, prêt à écrire. */
fun contenuExport(nom: String, pistes: List<Piste>, format: FormatExport): String =
    when (format) {
        FormatExport.M3U8, FormatExport.M3U -> contenuM3u(pistes)
        FormatExport.XSPF -> contenuXspf(nom, pistes)
    }

/**
 * Le format M3U étendu : une ligne `#EXTINF` qui porte durée et titre, puis le chemin.
 *
 * La durée s'écrit en **secondes**, comme le veut le format — la nôtre est en millisecondes.
 */
private fun contenuM3u(pistes: List<Piste>): String = buildString {
    append("#EXTM3U\n")
    pistes.forEach { piste ->
        val secondes = (piste.duree / 1000).toInt()
        append("#EXTINF:$secondes,${piste.artisteAffiche} - ${piste.titre}\n")
        append(cheminExport(piste)).append('\n')
    }
}

private fun contenuXspf(nom: String, pistes: List<Piste>): String = buildString {
    append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
    append("<playlist version=\"1\" xmlns=\"http://xspf.org/ns/0/\">\n")
    append("  <title>").append(echapperXml(nom)).append("</title>\n")
    append("  <creator>36Music</creator>\n")
    append("  <trackList>\n")
    pistes.forEach { piste ->
        append("    <track>\n")
        append("      <location>").append(echapperXml(cheminExport(piste))).append("</location>\n")
        append("      <title>").append(echapperXml(piste.titre)).append("</title>\n")
        append("      <creator>").append(echapperXml(piste.artisteAffiche)).append("</creator>\n")
        if (piste.album.isNotBlank()) {
            append("      <album>").append(echapperXml(piste.album)).append("</album>\n")
        }
        if (piste.duree > 0) append("      <duration>").append(piste.duree).append("</duration>\n")
        append("    </track>\n")
    }
    append("  </trackList>\n")
    append("</playlist>\n")
}

/**
 * ⚠️ Sans échappement, un titre contenant `&` ou `<` produit un XML que rien ne relit — et les
 * noms de groupes en sont pleins.
 */
internal fun echapperXml(texte: String): String = buildString(texte.length) {
    texte.forEach { c ->
        when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            else -> append(c)
        }
    }
}

/** Ce qu'un export a réellement produit. */
data class ResultatExport(val ecrites: Int, val echecs: Int, val dossier: String)

/** Ce qu'on annonce après un export. */
fun messageExport(resultat: ResultatExport): String = when {
    resultat.ecrites == 0 && resultat.echecs == 0 -> "Aucune playlist à exporter"
    resultat.echecs == 0 && resultat.ecrites == 1 -> "Playlist exportée"
    resultat.echecs == 0 -> "${resultat.ecrites} playlists exportées"
    resultat.ecrites == 0 -> "Écriture refusée : vérifiez le dossier choisi"
    else -> "${resultat.ecrites} exportée${pluriel(resultat.ecrites)}, " +
        "${resultat.echecs} refusée${pluriel(resultat.echecs)}"
}

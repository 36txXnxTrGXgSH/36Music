package com.music36.app.data

import android.net.Uri
import android.provider.DocumentsContract
import com.music36.app.data.EtatReglages

/**
 * Le chemin d'un document SAF, écrit comme on le dirait à voix haute.
 *
 * ⚠️ Un URI de document ne se lit pas : `content://com.android.externalstorage.documents/tree/
 * primary%3AMusic/document/primary%3AMusic%2FPlaylists%2Fx.m3u` ne dit rien à personne, alors
 * que « Mémoire interne / Music/Playlists » se comprend d'un coup d'œil. La fonction travaille
 * sur l'**identifiant de document** (`volume:chemin/vers/fichier`), qui est la seule partie
 * porteuse de sens — et qui se teste sans Android.
 */
fun dossierLisible(documentId: String): String {
    val volume = documentId.substringBefore(':', "")
    val chemin = documentId.substringAfter(':', documentId)
    val dossier = chemin.substringBeforeLast('/', "")
    val support = when (volume) {
        "primary" -> "Mémoire interne"
        "home" -> "Documents"
        "" -> ""
        // Un identifiant de volume amovible ressemble à « EAB4-6BE0 » : le dire tel quel est
        // plus honnête que d'inventer un nom de carte que l'appareil ne donne pas.
        else -> if (volume.matches(Regex("[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}"))) {
            "Carte SD ($volume)"
        } else {
            volume
        }
    }
    return listOf(support, dossier).filter { it.isNotBlank() }.joinToString(" / ")
        .ifBlank { "Emplacement inconnu" }
}

/** Le nom du fichier, tel qu'il apparaît dans l'explorateur. */
fun nomDeFichierLisible(documentId: String): String =
    documentId.substringAfterLast('/').substringAfterLast(':')

/** Le dossier d'un URI SAF, ou `null` si l'URI n'en est pas un. */
fun dossierDeUri(uri: String): String? = runCatching {
    dossierLisible(DocumentsContract.getDocumentId(Uri.parse(uri)))
}.getOrNull()

/** Le nom de fichier d'un URI SAF, ou `null`. */
fun nomDeFichierDeUri(uri: String): String? = runCatching {
    nomDeFichierLisible(DocumentsContract.getDocumentId(Uri.parse(uri)))
}.getOrNull()

/**
 * Le chemin d'un fichier **vu depuis le dossier** où se trouve la playlist.
 *
 * ⚠️ C'est la seule écriture qu'un autre lecteur saura suivre. Un `.m3u` désigne ses pistes
 * relativement à **sa propre place** : une playlist rangée dans `Music/Playlists` qui annonce
 * `Music/FR/titre.flac` fait chercher `Music/Playlists/Music/FR/titre.flac`, c'est-à-dire rien.
 * Les deux identifiants viennent de SAF (`volume:chemin`), donc du fournisseur lui-même.
 *
 * Quand les deux ne sont pas sur le même volume, aucun chemin relatif n'a de sens : on écrit
 * alors le chemin depuis la racine du volume, ce que la plupart des lecteurs retrouvent.
 */
fun cheminRelatif(dossierId: String, fichierId: String): String {
    val volumeDossier = dossierId.substringBefore(':', "")
    val volumeFichier = fichierId.substringBefore(':', "")
    val cheminFichier = fichierId.substringAfter(':', fichierId).trim('/')
    if (volumeDossier != volumeFichier) return "/$cheminFichier"

    val depuis = dossierId.substringAfter(':', "").split('/').filter { it.isNotBlank() }
    val vers = cheminFichier.split('/').filter { it.isNotBlank() }
    var commun = 0
    while (commun < depuis.size && commun < vers.size - 1 &&
        depuis[commun].equals(vers[commun], ignoreCase = true)
    ) {
        commun++
    }
    val remontees = List(depuis.size - commun) { ".." }
    return (remontees + vers.drop(commun)).joinToString("/")
}

/**
 * Le dossier [parent] contient-il [enfant] — ou est-ce le même ?
 *
 * ⚠️ Sert à empêcher qu'un **sous-dossier** d'un dossier déjà surveillé soit ajouté comme
 * dossier à part : la même musique serait alors analysée deux fois et chaque playlist
 * apparaîtrait en double. C'est exactement ce qui est arrivé le 28/09/2026 en choisissant
 * `Music/Playlists` alors que `Music` était déjà surveillé : quatorze playlists devenues
 * vingt-sept.
 *
 * La comparaison se fait segment par segment, jamais sur le texte : sans cela, `Music2` passerait
 * pour un enfant de `Music`.
 */
fun contient(parentId: String, enfantId: String): Boolean {
    if (parentId.substringBefore(':', "") != enfantId.substringBefore(':', "")) return false
    val p = parentId.substringAfter(':', "").split('/').filter { it.isNotBlank() }
    val e = enfantId.substringAfter(':', "").split('/').filter { it.isNotBlank() }
    if (p.size > e.size) return false
    return p.indices.all { p[it].equals(e[it], ignoreCase = true) }
}

/** Les dossiers d'export retenus, tels qu'ils sont enregistrés (une URI par ligne). */
fun dossiersExportesDe(reglages: EtatReglages): List<String> =
    reglages.dossiersExportes.lines().map { it.trim() }.filter { it.isNotEmpty() }

/** Les réglages, un dossier d'export de plus (sans doublon). */
fun EtatReglages.avecDossierExporte(uri: String): EtatReglages =
    copy(dossiersExportes = (dossiersExportesDe(this) + uri).distinct().joinToString("\n"))

/**
 * Le nom qu'une playlist réécrite doit porter.
 *
 * ⚠️ 36Music écrit toujours en **UTF-8**, et un fichier `.m3u` est censé ne pas l'être : les
 * accents y ressortent en charabia chez les autres lecteurs. `.m3u8` **est** le M3U en UTF-8 —
 * c'est le même contenu sous le nom qui le décrit. Les fichiers déjà en `.m3u8`, et tout ce qui
 * porte une autre extension, ne sont pas touchés.
 */
fun nomEnM3u8(nomFichier: String): String? {
    if (!nomFichier.endsWith(".m3u", ignoreCase = true)) return null
    return nomFichier.dropLast(4) + ".m3u8"
}

/**
 * Ce qui désigne un fichier **sans dépendre du chemin par lequel on y accède**.
 *
 * ⚠️ Un même document se lit par autant d'URI qu'il existe d'arbres qui le contiennent :
 * `tree/…:Music/document/…:Music/Playlists/X.m3u8` et
 * `tree/…:Music%2FPlaylists/document/…:Music/Playlists/X.m3u8` sont **le même fichier**. Comparer
 * les URI entières les fait passer pour deux, et chaque playlist apparaît alors en double — c'est
 * ce qui est arrivé le 28/09/2026 quand un sous-dossier a été ajouté comme second dossier
 * surveillé. Seul l'identifiant de document compte.
 */
fun cleDeFichier(uri: String): String = runCatching {
    DocumentsContract.getDocumentId(Uri.parse(uri))
}.getOrDefault(uri)

package com.music36.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Une piste audio de la bibliothèque.
 *
 * La clé primaire est l'URI SAF du fichier : c'est la seule chose stable entre deux analyses.
 * Favoris, playlists et statistiques référencent cette même URI, si bien qu'une réanalyse ne
 * perd jamais ce que l'utilisateur a construit.
 */
@Entity(
    tableName = "piste",
    indices = [Index("album"), Index("artiste"), Index("dossier"), Index("racine")]
)
data class Piste(
    @PrimaryKey val uri: String,
    /** URI de l'arbre SAF (le dossier racine choisi) d'où vient la piste. */
    val racine: String,
    /** Chemin lisible du dossier parent, par exemple `Music/Rock/Live`. */
    val dossier: String,
    val nomFichier: String,
    val titre: String,
    val artiste: String,
    val album: String,
    val artisteAlbum: String,
    val genre: String,
    val annee: Int,
    val numeroPiste: Int,
    val disque: Int,
    /** Durée en millisecondes. */
    val duree: Long,
    val taille: Long,
    /** Date du fichier telle que rapportée par le système (date de création / modification). */
    val dateFichier: Long,
    /** Date d'entrée dans la bibliothèque 36Music. */
    val dateAjout: Long,
    /** Format lisible : FLAC, MP3, ALAC… */
    val format: String,
    /** Débit en kbit/s, 0 si inconnu. */
    val debit: Int,
    /** Fréquence d'échantillonnage en Hz, 0 si inconnue. */
    val echantillonnage: Int,
    /** Bits par échantillon, 0 si inconnu. */
    val bits: Int,
    val canaux: Int,
    /** Chemin du fichier de pochette en cache, `null` si la piste n'en a pas. */
    val pochette: String? = null
) {
    /** Le nom d'artiste à afficher : celui de l'album quand la piste n'en déclare pas. */
    val artisteAffiche: String get() = artiste.ifBlank { artisteAlbum }.ifBlank { INCONNU }

    /**
     * Haute résolution au sens habituel du terme : au-delà du CD (16 bits / 44,1 kHz).
     *
     * On ne se fie pas au seul débit — un MP3 320 kbit/s reste de la compression avec perte.
     */
    val hauteResolution: Boolean
        get() = sansPerte && (echantillonnage > 48_000 || bits > 16)

    val sansPerte: Boolean
        get() = format.uppercase() in FORMATS_SANS_PERTE

    /** Étiquette compacte affichée sous la pochette : `FLAC 24 bits · 96 kHz`. */
    fun qualite(): String = buildList {
        if (format.isNotBlank()) add(format.uppercase())
        if (bits > 0) add("$bits bits")
        if (echantillonnage > 0) add(formaterFrequence(echantillonnage))
        if (debit > 0 && !sansPerte) add("$debit kbit/s")
        if (canaux == 1) add("mono")
    }.joinToString(" · ")

    /** Le rang de qualité, qui donne sa couleur à la ligne dans la vue molette. */
    val rangQualite: RangQualite
        get() = when {
            hauteResolution -> RangQualite.HAUTE_RESOLUTION
            sansPerte -> RangQualite.SANS_PERTE
            format.uppercase() in FORMATS_COMPRESSES_EVOLUES -> RangQualite.COMPRESSE_EVOLUE
            else -> RangQualite.ORDINAIRE
        }

    companion object {
        const val INCONNU = "Inconnu"
        val FORMATS_SANS_PERTE = setOf("FLAC", "ALAC", "WAV", "AIFF", "APE", "PCM", "DSD", "WV")

        /**
         * Les compressions avec perte de génération récente.
         *
         * À débit égal elles conservent nettement plus que le MP3 ; les ranger avec lui
         * effacerait la seule distinction utile entre deux fichiers compressés.
         */
        val FORMATS_COMPRESSES_EVOLUES = setOf("AAC", "M4A", "MP4", "OPUS", "VORBIS", "OGG")
    }
}

/**
 * Le type MIME du **conteneur**, déduit de l'extension.
 *
 * Il est posé sur chaque `MediaItem` pour aiguiller ExoPlayer : avec une URI SAF, l'extension
 * n'est pas toujours visible du lecteur, et le choix de l'extracteur repose alors sur le seul
 * flair du renifleur. Ce n'est qu'une **priorité**, pas une exclusive : un fichier mal nommé
 * reste lu par l'extracteur qui le reconnaît.
 *
 * `null` pour tout ce dont on n'est pas certain — une indication fausse vaut moins que pas
 * d'indication du tout.
 */
fun typeConteneurDe(nomFichier: String): String? =
    when (nomFichier.substringAfterLast('.', "").lowercase()) {
        "m4a", "m4b", "m4r", "mp4", "alac" -> "audio/mp4"
        "mp3", "mp2", "mpga" -> "audio/mpeg"
        "flac" -> "audio/flac"
        "wav", "wave" -> "audio/wav"
        "ogg", "oga", "opus" -> "audio/ogg"
        "mka" -> "audio/x-matroska"
        "amr", "awb" -> "audio/amr"
        else -> null
    }

fun formaterFrequence(hz: Int): String {
    val khz = hz / 1000.0
    return if (khz % 1.0 == 0.0) "${khz.toInt()} kHz"
    else String.format(java.util.Locale.FRANCE, "%.1f kHz", khz)
}

/** Dossier racine choisi par l'utilisateur (URI d'arbre SAF, permission persistée). */
@Entity(tableName = "racine")
data class Racine(
    @PrimaryKey val uri: String,
    val nom: String,
    val dateAjout: Long,
    /** Faux pour ignorer temporairement la racine sans oublier son autorisation. */
    val active: Boolean = true
)

@Entity(tableName = "favori")
data class Favori(
    @PrimaryKey val uri: String,
    val dateAjout: Long
)

@Entity(tableName = "playlist")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nom: String,
    val dateCreation: Long,
    /** Chemin du `.m3u` d'origine quand la playlist a été trouvée sur l'appareil. */
    val source: String? = null,
    /**
     * Taille et date du fichier d'origine au dernier import, sous la forme `taille:date`.
     *
     * ⚠️ C'est ce qui empêche l'analyse d'écraser vos modifications. Sans elle, chaque analyse
     * relisait le `.m3u` et remplaçait le contenu de la playlist : un titre retiré dans
     * l'application revenait au scan suivant, sans un mot.
     */
    val empreinte: String? = null
)

@Entity(
    tableName = "playlist_piste",
    primaryKeys = ["playlistId", "position"],
    indices = [Index("playlistId"), Index("uri")]
)
data class PlaylistPiste(
    val playlistId: Long,
    val uri: String,
    val position: Int
)

/** Compteurs de lecture, et position de reprise pour les pistes longues. */
@Entity(tableName = "statistique")
data class Statistique(
    @PrimaryKey val uri: String,
    val lectures: Int = 0,
    val derniereLecture: Long = 0,
    val reprise: Long = 0
)

/** Une playlist accompagnée de son contenu résolu. */
data class PlaylistGarnie(
    val playlist: Playlist,
    val pistes: List<Piste>
)

/** Regroupement générique (artiste, album, genre, dossier). */
data class Collection(
    val cle: String,
    val nom: String,
    val sousTitre: String,
    val pistes: List<Piste>,
    val pochette: String? = pistes.firstOrNull { it.pochette != null }?.pochette
) {
    val duree: Long get() = pistes.sumOf { it.duree }
}

/** Critères de tri proposés dans la barre du haut. */
enum class Tri(val libelle: String) {
    ALPHABETIQUE("Ordre alphabétique"),
    AJOUT("Date d'ajout"),
    CREATION("Date de création"),
    ARTISTE("Artiste"),
    ALBUM("Album"),
    DUREE("Durée"),
    PISTE("Numéro de piste");
}

enum class Onglet(val libelle: String) {
    SONS("Sons"),
    PLAYLISTS("Playlists"),
    DOSSIERS("Dossiers"),
    ARTISTES("Artistes"),
    ALBUMS("Albums"),
    GENRES("Genres"),
    FAVORIS("Favoris");
}

/**
 * Les trois familles de palettes.
 *
 * La famille ne choisit pas les teintes mais le **caractère** : à quel point les accents sont
 * saturés, et à quel point les fonds sont teintés. C'est ce qui fait qu'un « Néon » reste un néon
 * quelle que soit sa couleur.
 */
enum class FamillePalette(
    val libelle: String,
    val description: String,
    /** Saturation des accents, de 0 à 1. */
    val saturationAccent: Float,
    /** Saturation des fonds : au-delà de quelques pour cent, le gris devient une couleur. */
    val saturationFond: Float
) {
    CLASSIQUE("Classique", "Teintes sobres, fonds presque neutres", 0.52f, 0.10f),
    VIF("Vif", "Couleurs franches et fonds chaleureux", 0.78f, 0.20f),
    NEON("Néon", "Accents électriques sur fonds très colorés", 0.95f, 0.34f),

    /**
     * ⚠️ Saturation nulle : c'est ce qui fait un vrai gris. La teinte reste déclarée, elle ne
     * sert qu'à départager les entrées — aucune couleur n'en ressort.
     */
    NEUTRE("Neutre", "Blanc, gris et noir, sans aucune couleur", 0f, 0f);
}

/**
 * Les palettes proposées quand les couleurs dynamiques (Material You) sont éteintes.
 *
 * Chacune est décrite par deux teintes seulement ; tout le reste — fonds, conteneurs et surtout
 * **couleurs de texte** — est calculé par [com.music36.app.ui.paletteDe] à des luminances
 * choisies pour tenir les contrastes, en clair comme en sombre. Ajouter une palette revient donc
 * à ajouter une ligne ici, et `ContrasteTest` vérifiera qu'elle reste lisible.
 *
 * ⚠️ Les noms `CLASSIQUE`, `VIF` et `NEON` sont conservés : ce sont eux qu'ont enregistrés les
 * réglages des versions 1.1 et 1.2.
 */
enum class PaletteApplication(
    val libelle: String,
    val famille: FamillePalette,
    /** Teinte principale, en degrés sur le cercle chromatique. */
    val teinte: Float,
    /** Teinte du second accent. */
    val teinteSecondaire: Float,
    /**
     * De combien on éclaircit ou assombrit l'accent par rapport à la cible du thème.
     *
     * ⚠️ C'est ce qui distingue « Blanc », « Gris » et « Anthracite » : à teinte et saturation
     * nulles, seule la lumière peut encore les séparer. La valeur est bornée à l'usage, pour que
     * l'accent reste lisible sur son fond quoi qu'on demande.
     */
    val clarteAccent: Float = 1f
) {
    // --- Classique
    CLASSIQUE("Bleu nuit", FamillePalette.CLASSIQUE, 205f, 265f),
    CLASSIQUE_ARDOISE("Ardoise", FamillePalette.CLASSIQUE, 220f, 190f),
    CLASSIQUE_FORET("Forêt", FamillePalette.CLASSIQUE, 150f, 95f),
    CLASSIQUE_BORDEAUX("Bordeaux", FamillePalette.CLASSIQUE, 355f, 25f),
    CLASSIQUE_SEPIA("Sépia", FamillePalette.CLASSIQUE, 35f, 15f),
    CLASSIQUE_ENCRE("Encre", FamillePalette.CLASSIQUE, 270f, 215f),

    // --- Vif
    VIF("Mandarine", FamillePalette.VIF, 28f, 5f),
    VIF_FRAMBOISE("Framboise", FamillePalette.VIF, 340f, 300f),
    VIF_CITRON("Citron vert", FamillePalette.VIF, 90f, 50f),
    VIF_TURQUOISE("Turquoise", FamillePalette.VIF, 175f, 205f),
    VIF_COBALT("Cobalt", FamillePalette.VIF, 225f, 320f),
    VIF_PRUNE("Prune", FamillePalette.VIF, 290f, 345f),

    // --- Néon
    NEON("Cyan néon", FamillePalette.NEON, 185f, 320f),
    NEON_MAGENTA("Magenta", FamillePalette.NEON, 320f, 190f),
    NEON_ACIDE("Vert acide", FamillePalette.NEON, 105f, 175f),
    NEON_ORANGE("Orange néon", FamillePalette.NEON, 20f, 300f),
    NEON_ULTRAVIOLET("Ultraviolet", FamillePalette.NEON, 260f, 195f),
    NEON_LASER("Rouge laser", FamillePalette.NEON, 350f, 30f),

    // --- Neutre : aucune couleur, seulement la lumière
    NEUTRE_BLANC("Blanc", FamillePalette.NEUTRE, 0f, 0f, clarteAccent = 1.6f),
    NEUTRE_GRIS("Gris", FamillePalette.NEUTRE, 0f, 0f),
    NEUTRE_ANTHRACITE("Anthracite", FamillePalette.NEUTRE, 0f, 0f, clarteAccent = 0.62f);

    companion object {
        fun parFamille(famille: FamillePalette): List<PaletteApplication> =
            entries.filter { it.famille == famille }
    }
}

/** L'allure générale de l'application. */
enum class StyleInterface(val libelle: String, val description: String) {
    MODERNE("Moderne", "Onglets, listes et lecteur plein écran"),
    RETRO("Vue Rétro", "Écran unique et molette cliquable, comme les baladeurs d'autrefois");
}

enum class ThemeApplication(val libelle: String) {
    SYSTEME("Suivre le système"),
    CLAIR("Clair"),
    SOMBRE("Sombre"),
    AMOLED("Noir AMOLED");
}

/**
 * Les regroupements des onglets, en fonctions pures.
 *
 * Ils vivent ici plutôt que dans [Depot] pour rester vérifiables sans base ni contexte Android :
 * ce sont eux qui décident de ce que voient les onglets Artistes, Albums, Genres et Dossiers.
 */
fun List<Piste>.enArtistes(): List<Collection> =
    groupBy { it.artisteAffiche }
        .map { (nom, pistes) ->
            val albums = pistes.map { it.album }.distinct().size
            Collection(
                cle = nom,
                nom = nom,
                sousTitre = "${pistes.size} titre${pluriel(pistes.size)} · " +
                    "$albums album${pluriel(albums)}",
                pistes = pistes.trier(Tri.ALBUM, true)
            )
        }
        .sortedWith(compareBy(ORDRE_ALPHABETIQUE) { it.nom })

/**
 * Un album est identifié par son nom **et** son artiste d'album : deux disques appelés
 * « Greatest Hits » n'en font pas un seul.
 */
fun List<Piste>.enAlbums(): List<Collection> =
    groupBy { it.album to it.artisteAlbum.ifBlank { it.artisteAffiche } }
        .map { (cle, pistes) ->
            val (album, artiste) = cle
            val annee = pistes.firstOrNull { it.annee > 0 }?.annee ?: 0
            Collection(
                cle = "$album|$artiste",
                nom = album.ifBlank { Piste.INCONNU },
                sousTitre = listOfNotNull(
                    artiste.takeIf { it.isNotBlank() },
                    annee.takeIf { it > 0 }?.toString(),
                    "${pistes.size} titre${pluriel(pistes.size)}"
                ).joinToString(" · "),
                pistes = pistes.trier(Tri.PISTE, true)
            )
        }
        .sortedWith(compareBy(ORDRE_ALPHABETIQUE) { it.nom })

fun List<Piste>.enGenres(): List<Collection> =
    groupBy { it.genre.ifBlank { Piste.INCONNU } }
        .map { (genre, pistes) ->
            Collection(
                cle = genre,
                nom = genre,
                sousTitre = "${pistes.size} titre${pluriel(pistes.size)}",
                pistes = pistes.trier(Tri.ARTISTE, true)
            )
        }
        .sortedWith(compareBy(ORDRE_ALPHABETIQUE) { it.nom })

/** Les sous-dossiers immédiats de [chemin], chacun portant tout ce qu'il contient. */
fun List<Piste>.sousDossiersDe(chemin: String): List<Collection> {
    val prefixe = if (chemin.isEmpty()) "" else "$chemin/"
    return filter { it.dossier.startsWith(prefixe) && it.dossier != chemin }
        .groupBy { it.dossier.removePrefix(prefixe).substringBefore('/') }
        .map { (nom, pistes) ->
            Collection(
                cle = prefixe + nom,
                nom = nom,
                sousTitre = "${pistes.size} titre${pluriel(pistes.size)}",
                pistes = pistes
            )
        }
        .sortedWith(compareBy(ORDRE_ALPHABETIQUE) { it.nom })
}

fun pluriel(n: Int) = if (n > 1) "s" else ""

/**
 * Le classement des titres les plus écoutés.
 *
 * Les compteurs sont tenus par le service de lecture, qui valide une écoute au-delà de quinze
 * secondes ; ils vivent dans la base et survivent donc aux réanalyses comme aux redémarrages.
 * Un titre jamais joué n'entre pas au classement : une liste complétée par des zéros ne serait
 * plus un classement mais la bibliothèque dans le désordre.
 */
fun List<Piste>.plusEcoutes(
    statistiques: Map<String, Statistique>,
    limite: Int = 100
): List<Piste> = filter { (statistiques[it.uri]?.lectures ?: 0) > 0 }
    .sortedWith(
        compareByDescending<Piste> { statistiques[it.uri]?.lectures ?: 0 }
            .thenByDescending { statistiques[it.uri]?.derniereLecture ?: 0 }
            .thenBy(ORDRE_ALPHABETIQUE) { it.titre }
    )
    .take(limite)

/** Applique un tri à une liste de pistes. */
/**
 * L'ordre alphabétique **français**, celui qu'on attend d'une liste de musique.
 *
 * ⚠️ Surtout pas `String.CASE_INSENSITIVE_ORDER` : il compare les caractères par leur code
 * numérique, où « É » (201) passe après « Z » (90). Une bibliothèque française se terminait donc
 * par « Ça fait plaisir », « Élément Eau », « Éternel » — rangés après le Z, ce qui n'est pas un
 * ordre alphabétique.
 *
 * C'est aussi ce qui le réconcilie avec le repère ABC, qui efface les accents : il annonçait
 * « E » pour un titre relégué tout en bas.
 */
val ORDRE_ALPHABETIQUE: Comparator<String> = Comparator { a, b ->
    collateurFrancais.get()!!.compare(a, b)
}

/**
 * ⚠️ Un `Collator` n'est **pas** réutilisable entre fils d'exécution, et les tris partent aussi
 * bien de l'affichage que des flux de la bibliothèque. Un exemplaire par fil règle la question
 * sans verrou.
 *
 * Force `SECONDARY` : les accents départagent deux titres par ailleurs identiques, ils ne les
 * séparent pas — « Éternel » se range entre « Eté » et « Étoile », pas après le Z.
 */
private val collateurFrancais: ThreadLocal<java.text.Collator> =
    ThreadLocal.withInitial {
        java.text.Collator.getInstance(java.util.Locale.FRENCH).apply {
            strength = java.text.Collator.SECONDARY
        }
    }

fun List<Piste>.trier(tri: Tri, croissant: Boolean): List<Piste> {
    val comparateur: Comparator<Piste> = when (tri) {
        Tri.ALPHABETIQUE -> compareBy(ORDRE_ALPHABETIQUE) { p: Piste -> p.titre }
        Tri.AJOUT -> compareBy { it.dateAjout }
        Tri.CREATION -> compareBy { it.dateFichier }
        Tri.ARTISTE -> compareBy(ORDRE_ALPHABETIQUE) { p: Piste -> p.artisteAffiche }
            .thenBy(ORDRE_ALPHABETIQUE) { it.album }
            .thenBy { it.numeroPiste }
        Tri.ALBUM -> compareBy(ORDRE_ALPHABETIQUE) { p: Piste -> p.album }
            .thenBy { it.disque }
            .thenBy { it.numeroPiste }
        Tri.DUREE -> compareBy { it.duree }
        Tri.PISTE -> compareBy<Piste> { it.disque }.thenBy { it.numeroPiste }
            .thenBy(ORDRE_ALPHABETIQUE) { it.titre }
    }
    return sortedWith(if (croissant) comparateur else comparateur.reversed())
}

/** Durée `mm:ss`, ou `h:mm:ss` au-delà de l'heure. */
fun formaterDuree(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(java.util.Locale.FRANCE, "%d:%02d:%02d", h, m, s)
    else String.format(java.util.Locale.FRANCE, "%d:%02d", m, s)
}

/** Durée bavarde, pour les entêtes de liste : « 12 titres · 48 min ». */
fun formaterDureeLongue(ms: Long): String {
    val minutes = ms / 60_000
    return when {
        minutes < 1 -> "moins d'une minute"
        minutes < 60 -> "$minutes min"
        else -> "${minutes / 60} h ${(minutes % 60).toString().padStart(2, '0')}"
    }
}

/**
 * Le rang de qualité d'un fichier, tel que la vue molette le colore.
 *
 * Quatre rangs, pas davantage : la couleur doit se lire d'un coup d'œil en défilant, et une
 * nuance qu'on doit interpréter ne vaut pas mieux que pas de couleur du tout.
 */
enum class RangQualite(val libelle: String) {
    /** Sans perte au-delà du CD — 24 bits ou plus de 48 kHz. */
    HAUTE_RESOLUTION("Haute résolution"),

    /** Sans perte : FLAC, ALAC, WAV, APE… */
    SANS_PERTE("Sans perte"),

    /** Compression avec perte de génération récente : AAC/M4A, Opus, Vorbis. */
    COMPRESSE_EVOLUE("Compressé"),

    /** Le reste, MP3 en tête : couleur par défaut. */
    ORDINAIRE("Ordinaire")
}

/**
 * Les dossiers de premier niveau, une entrée par **nom** de racine.
 *
 * ⚠️ Deux racines peuvent porter le même nom — un dossier `Music` sur la mémoire interne et un
 * autre sur la carte SD. La navigation les confond déjà, puisqu'elle filtre sur le chemin
 * (`Music/…`), identique de part et d'autre. Les laisser séparés produisait deux lignes
 * indiscernables **et de même clé**, ce que `LazyColumn` refuse en faisant tomber l'application.
 */
fun regrouperRacines(racines: List<Racine>, pistes: List<Piste>): List<Collection> =
    racines
        .groupBy { it.nom }
        .map { (nom, memeNom) ->
            val uris = memeNom.mapTo(HashSet()) { it.uri }
            val siennes = pistes.filter { it.racine in uris }
            Collection(
                cle = nom,
                nom = nom,
                sousTitre = "${siennes.size} titre${if (siennes.size > 1) "s" else ""}",
                pistes = siennes
            )
        }
        .sortedWith(compareBy(ORDRE_ALPHABETIQUE) { it.nom })

/**
 * Ce qu'est devenu le fichier `.m3u` d'origine après une modification dans l'application.
 *
 * ⚠️ Cette réponse existe parce que l'échec était **silencieux** : la playlist changeait dans
 * l'application, le fichier restait tel quel, et rien ne le disait. Un utilisateur a mis
 * plusieurs versions à s'en apercevoir.
 */
enum class ResultatReecriture {
    /** La playlist ne vient d'aucun fichier : il n'y a rien à réécrire. */
    SANS_FICHIER,

    /** Le réglage « Répercuter dans le fichier .m3u » est éteint. */
    DESACTIVE,

    /** Le fichier a été réécrit. */
    ECRIT,

    /** Android refuse d'écrire : le dossier n'est autorisé qu'en lecture. */
    LECTURE_SEULE,

    /** Autre échec : fichier disparu, support retiré, quota… */
    ECHEC
}

/** Ce qu'on annonce après avoir demandé la réécriture d'un fichier. */
fun messageReecriture(resultat: ResultatReecriture): String = when (resultat) {
    ResultatReecriture.ECRIT -> "Fichier .m3u réécrit"
    ResultatReecriture.SANS_FICHIER -> "Cette playlist ne vient d'aucun fichier de l'appareil"
    ResultatReecriture.DESACTIVE ->
        "Réglage « Répercuter dans le fichier .m3u » éteint : Paramètres → Bibliothèque"
    ResultatReecriture.LECTURE_SEULE -> "Dossier en lecture seule : Paramètres de " +
        "l'application → Dossiers de musique → Autoriser l'écriture"
    ResultatReecriture.ECHEC -> "Réécriture impossible : fichier introuvable ou support retiré"
}

/** Ce qu'on annonce après avoir réécrit toutes les playlists d'un coup. */
fun messageReecritureEnLot(ecrits: Int, refuses: Int, sansFichier: Int): String = when {
    ecrits == 0 && refuses == 0 -> "Aucune playlist ne vient d'un fichier"
    refuses == 0 -> "$ecrits fichier${pluriel(ecrits)} .m3u réécrit${pluriel(ecrits)}" +
        if (sansFichier > 0) " ($sansFichier playlist${pluriel(sansFichier)} sans fichier)" else ""
    ecrits == 0 -> "Réécriture refusée : dossier en lecture seule. Paramètres de " +
        "l'application → Dossiers de musique → Autoriser l'écriture"
    else -> "$ecrits réécrit${pluriel(ecrits)}, $refuses refusé${pluriel(refuses)} " +
        "(dossier en lecture seule)"
}

/** L'avertissement à ajouter à un message, quand le fichier n'a pas suivi. */
fun avertissementReecriture(resultat: ResultatReecriture): String? = when (resultat) {
    ResultatReecriture.LECTURE_SEULE ->
        "fichier .m3u non mis à jour : dossier en lecture seule"
    ResultatReecriture.ECHEC -> "fichier .m3u non mis à jour"
    else -> null
}

/** Ce qu'un ajout à une playlist a réellement fait. */
data class ResultatAjout(
    val ajoutees: Int,
    val deja: Int,
    /** Ce qu'est devenu le fichier d'origine. */
    val reecriture: ResultatReecriture = ResultatReecriture.SANS_FICHIER
)

/**
 * Ce qu'on annonce après un ajout à une playlist.
 *
 * Le cas qui compte est celui du titre **déjà présent** : sans message, l'utilisateur croit que
 * l'ajout a échoué et recommence. On nomme donc toujours la playlist, et on distingue le titre
 * unique du lot.
 */
fun messageAjout(resultat: ResultatAjout, nomPlaylist: String): String {
    val ou = "« $nomPlaylist »"
    val fait = when {
        resultat.ajoutees == 0 && resultat.deja == 1 -> "Déjà dans $ou"
        resultat.ajoutees == 0 -> "Ces ${resultat.deja} titres sont déjà dans $ou"
        resultat.deja == 0 && resultat.ajoutees == 1 -> "Ajouté à $ou"
        resultat.deja == 0 -> "${resultat.ajoutees} titres ajoutés à $ou"
        resultat.deja == 1 -> "${resultat.ajoutees} ajouté${pluriel(resultat.ajoutees)} à $ou, " +
            "1 était déjà là"
        else -> "${resultat.ajoutees} ajouté${pluriel(resultat.ajoutees)} à $ou, " +
            "${resultat.deja} étaient déjà là"
    }
    // ⚠️ L'avertissement ne s'ajoute que si la playlist a bougé : quand rien n'a été ajouté,
    // le fichier n'avait aucune raison d'être réécrit, et le signaler inquiéterait pour rien.
    if (resultat.ajoutees == 0) return fait
    val souci = avertissementReecriture(resultat.reecriture) ?: return fait
    return "$fait — $souci"
}

/**
 * Ce qu'on répond quand on demande à quelles playlists un titre appartient.
 *
 * C'est le message de l'icône « n'appartient à aucune playlist » : elle doit répondre dans les
 * deux cas, sinon appuyer dessus sur un titre déjà rangé ne donnerait rien.
 */
fun messageAppartenance(nomsPlaylists: List<String>): String = when {
    nomsPlaylists.isEmpty() -> "Ce titre n'appartient à aucune playlist"
    nomsPlaylists.size == 1 -> "Dans « ${nomsPlaylists.first()} »"
    nomsPlaylists.size <= 3 -> "Dans " + nomsPlaylists.joinToString(", ") { "« $it »" }
    else -> "Dans ${nomsPlaylists.size} playlists : " +
        nomsPlaylists.take(2).joinToString(", ") { "« $it »" } + "…"
}

/** Ce qu'un effacement de fichiers a réellement fait. */
data class ResultatEffacement(
    val effaces: Int,
    val echecs: Int,
    /** Vrai quand un dossier surveillé n'est autorisé qu'en lecture : c'est **la** raison. */
    val lectureSeule: Boolean = false
)

/** Ce qu'un déplacement de fichiers a réellement fait. */
data class ResultatDeplacement(
    val deplaces: Int,
    val echecs: Int,
    val lectureSeule: Boolean = false
)

/**
 * Ce qu'on annonce après un effacement.
 *
 * ⚠️ Les échecs ne sont jamais tus : un fichier qu'Android a refusé d'effacer est toujours là,
 * et laisser croire le contraire est pire que de ne rien dire.
 */
fun messageEffacement(resultat: ResultatEffacement): String = when {
    resultat.effaces == 0 && resultat.echecs == 0 -> "Rien à effacer"
    // ⚠️ La cause avant le constat : « refusé » sans raison laisse croire à une panne, alors
    // qu'il suffit de rechoisir le dossier pour qu'Android accorde l'écriture.
    resultat.lectureSeule -> "Dossier autorisé en lecture seule : Réglages → Paramètres de " +
        "l'application → Dossiers de musique → Autoriser l'écriture"
    resultat.echecs == 0 && resultat.effaces == 1 -> "Fichier effacé"
    resultat.echecs == 0 -> "${resultat.effaces} fichiers effacés"
    resultat.effaces == 0 && resultat.echecs == 1 -> "Effacement refusé : le fichier est toujours là"
    resultat.effaces == 0 -> "Effacement refusé pour ${resultat.echecs} fichiers, toujours en place"
    else -> "${resultat.effaces} effacé${pluriel(resultat.effaces)}, " +
        "${resultat.echecs} refusé${pluriel(resultat.echecs)} et toujours en place"
}

/** Ce qu'on annonce après un déplacement. */
fun messageDeplacementLectureSeule(): String =
    "Dossier autorisé en lecture seule : Réglages → Paramètres de l'application → " +
        "Dossiers de musique → Autoriser l'écriture"

fun messageDeplacement(resultat: ResultatDeplacement): String = when {
    resultat.echecs == 0 && resultat.deplaces == 1 -> "Fichier déplacé"
    resultat.lectureSeule -> messageDeplacementLectureSeule()
    resultat.echecs == 0 -> "${resultat.deplaces} fichiers déplacés"
    resultat.deplaces == 0 -> "Déplacement impossible : Android ne le permet qu'au sein " +
        "d'un même support"
    else -> "${resultat.deplaces} déplacé${pluriel(resultat.deplaces)}, ${resultat.echecs} refusé" +
        pluriel(resultat.echecs)
}

/**
 * Ce qu'on annonce après un rangement dans plusieurs playlists d'un coup.
 *
 * Le détail par playlist deviendrait illisible au-delà de deux ou trois : on additionne, en
 * gardant la distinction qui compte — ce qui est entré, et ce qui y était déjà.
 */
fun messageRangement(resultats: List<Pair<String, ResultatAjout>>): String {
    if (resultats.isEmpty()) return "Aucune playlist choisie"
    if (resultats.size == 1) {
        val (nom, resultat) = resultats.first()
        return messageAjout(resultat, nom)
    }
    val ajoutees = resultats.sumOf { it.second.ajoutees }
    val deja = resultats.sumOf { it.second.deja }
    // Un seul avertissement, même si plusieurs fichiers ont échoué : la cause est la même.
    val fin = resultats.map { it.second.reecriture }
        .firstOrNull { avertissementReecriture(it) != null }
        ?.let { " — ${avertissementReecriture(it)}" } ?: ""
    val ou = "${resultats.size} playlists"
    return when {
        ajoutees == 0 -> "Déjà présent dans ces $ou"
        deja == 0 -> "$ajoutees ajout${if (ajoutees > 1) "s" else ""} dans $ou$fin"
        else -> "$ajoutees ajout${if (ajoutees > 1) "s" else ""} dans $ou, " +
            "$deja déjà présent${pluriel(deja)}$fin"
    }
}

/**
 * Ce qui s'affiche derrière la pochette, sur l'écran de lecture de la vue molette.
 *
 * ⚠️ Seulement derrière la pochette : le reste de l'écran garde le fond du thème, sinon le titre,
 * l'artiste et la barre de progression deviendraient illisibles sur certaines images.
 */
enum class FondLecteur(val libelle: String, val description: String) {
    DEFAUT("Par défaut", "Le fond du thème, sans image"),
    POCHETTE_FLOUE("Pochette floutée", "L'image de l'album, agrandie et adoucie"),
    COULEUR("Couleur unie", "Une teinte au choix, ci-dessous")
}

/** Une teinte de fond, nommée pour être choisie à la molette. */
data class CouleurFond(val nom: String, val argb: Int)

/**
 * Les teintes proposées.
 *
 * Toutes sombres : la pochette se pose dessus, et un fond clair la ferait disparaître autant
 * qu'il fatiguerait l'œil dans le noir.
 */
val COULEURS_FOND: List<CouleurFond> = listOf(
    CouleurFond("Ardoise", 0xFF1C2226.toInt()),
    CouleurFond("Encre", 0xFF10151F.toInt()),
    CouleurFond("Forêt", 0xFF12241B.toInt()),
    CouleurFond("Grenat", 0xFF2A1418.toInt()),
    CouleurFond("Prune", 0xFF201427.toInt()),
    CouleurFond("Sable", 0xFF262018.toInt()),
    CouleurFond("Nuit", 0xFF000000.toInt())
)

/**
 * L'allure de la molette.
 *
 * Le relief se joue sur un dégradé et une ombre portée : la roue des baladeurs d'origine n'était pas plate, et
 * c'est ce léger volume qui donnait envie d'y poser le pouce.
 */
enum class StyleMolette(val libelle: String, val description: String) {
    DEFAUT("Par défaut", "Un dégradé discret, comme à l'origine"),
    PLAT("Plat", "Une seule teinte, aucun relief — fini mat"),
    MATERIAL("Material", "Teinte posée, ombre douce sous le bord"),
    CREUSE("Creusé", "La roue s'enfonce, la lumière vient d'en haut"),
    BOMBE("Bombé", "La roue se soulève sous le doigt"),
    NEOMORPHIQUE("Neumorphique", "Le relief sans contour : clair en haut, sombre en bas"),
    VERRE("Verre", "Translucide, un reflet contenu dans le disque"),
    LUSTRE("Lustré", "Un éclat franc sur le haut, comme un galet mouillé"),
    ARGILE("Argile", "Épais et rebondi, un bourrelet clair sur tout le tour"),
    BRUTALISTE("Brutaliste", "Aplat, contour épais, ombre portée nette"),
    RETRO("Rétro", "Teinte chaude et sillons concentriques"),
    DEGRADE("Dégradé", "Deux teintes qui glissent du haut vers le bas"),
    DUOTONE("Duotone", "Deux moitiés franches, sans transition"),
    METALLIQUE("Métallique", "Un balayage circulaire, comme de l'aluminium brossé"),
    CERCLE("Cerclé", "Un simple anneau tracé, sans remplissage")
}

/**
 * Ce qui se passe sous le doigt quand on tourne.
 *
 * ⚠️ La traînée suit le **doigt**, pas les crans : elle doit répondre au geste même quand la
 * rotation n'a pas encore franchi un cran, sinon elle donne l'impression que rien n'est pris.
 */
enum class EffetMolette(val libelle: String, val description: String) {
    AUCUN("Sans effet", "Rien ne suit le doigt, comme aujourd'hui"),
    UNIFORME("Traînée unie", "Une comète de la couleur d'accent"),
    ARC_EN_CIEL("Traînée arc-en-ciel", "La teinte change au fil du tour"),
    PAILLETTES("Paillettes", "Des éclats qui s'éteignent derrière le doigt"),
    ONDE("Onde", "Un cercle qui s'écarte à chaque cran")
}

/**
 * Les teintes proposées pour la molette.
 *
 * La première suit le thème : c'est l'aspect actuel, et il doit rester atteignable d'un geste
 * après avoir essayé les autres.
 */
val COULEURS_MOLETTE: List<CouleurFond> = listOf(
    CouleurFond("Du thème", 0),
    CouleurFond("Graphite", 0xFF3A4046.toInt()),
    CouleurFond("Ardoise", 0xFF2B3A46.toInt()),
    CouleurFond("Ivoire", 0xFFD8D4C8.toInt()),
    CouleurFond("Acier", 0xFF6B7683.toInt()),
    CouleurFond("Cuivre", 0xFF7A4A2E.toInt()),
    CouleurFond("Nuit", 0xFF14181C.toInt()),
    CouleurFond("Menthe", 0xFF2E5B52.toInt())
)

/**
 * L'allure de la barre de progression du lecteur.
 *
 * ⚠️ Toutes occupent **exactement la même place** : même largeur, même hauteur, même emplacement.
 * Seul le dessin change — sans quoi l'écran se réorganiserait à chaque choix, ce qui était
 * précisément le défaut corrigé en 3.7.
 */
enum class StyleBarre(val libelle: String, val description: String) {
    CLASSIQUE("Classique", "Une barre pleine, comme aujourd'hui"),
    ONDULE("Ondulé animé", "Une vague qui avance devant le curseur, à la manière d'Android"),
    FORME_ONDE("Forme d'onde", "Des barres verticales, comme un enregistrement"),
    SEGMENTS("Segments", "Des tirets réguliers qui se remplissent"),
    POINTS("Points", "Un chapelet de points, discret"),
    FIL("Fil fin", "Un trait d'un pixel et un curseur rond")
}

/**
 * Les six familles d'ambiances proposées dans « Palette de couleurs ».
 *
 * La catégorie ne décide d'aucune couleur : elle range. C'est le seul moyen de proposer une
 * vingtaine d'ambiances sans obliger à faire défiler une liste de vingt lignes indifférenciées.
 */
enum class CategoriePalette(val libelle: String, val description: String) {
    ENERGIQUES("Énergiques / Lumineux", "Couleurs franches, contrastes marqués"),
    SOMBRES("Sombres / Intenses", "Fonds profonds, accents tranchants"),
    DOUX("Doux / Apaisants", "Teintes délavées, rien qui accroche l'œil"),
    ELEGANTS("Élégants / Sérieux", "Sobre, peu de couleur, beaucoup de lisibilité"),
    RETRO("Rétro / Nostalgiques", "Les couleurs des décennies passées"),
    NATURE("Nature / Organique", "Végétal, minéral, marin")
}

/**
 * Une ambiance de couleurs : le **caractère** d'une palette, sans sa teinte.
 *
 * ⚠️ La teinte n'est pas ici, et c'est tout l'intérêt : elle se choisit à la molette sur une
 * barre de couleurs, ce qui donne à chaque ambiance un cercle chromatique entier au lieu des
 * cinq ou six variantes qu'on aurait écrites à la main. Seuls la saturation, la lumière de
 * l'accent et l'écart entre les deux teintes font qu'un « Néon » reste un néon et un « Pastel »
 * un pastel, quelle que soit la couleur retenue.
 *
 * Tout le reste — fonds, conteneurs, couleurs de texte — est calculé par
 * [com.music36.app.ui.paletteDe] à des luminances qui tiennent les seuils de contraste, et
 * `ThemesCouleurTest` mesure les vingt-trois ambiances sur tout le tour du cercle.
 */
enum class ThemeCouleur(
    val libelle: String,
    val categorie: CategoriePalette,
    val description: String,
    /** Saturation des accents, de 0 à 1. */
    val saturationAccent: Float,
    /** Saturation des fonds : au-delà de quelques pour cent, le gris devient une couleur. */
    val saturationFond: Float,
    /**
     * De combien on éclaircit ou assombrit l'accent par rapport à la cible du thème.
     *
     * ⚠️ Bornée à l'usage : au-delà, le texte posé sur l'accent ne passe plus, ou l'accent
     * lui-même disparaît dans le fond.
     */
    val clarteAccent: Float = 1f,
    /** L'écart, en degrés, entre la teinte choisie et celle du second accent. */
    val ecartSecondaire: Float = 40f,
    /** La teinte proposée d'emblée, avant d'avoir touché la barre de couleurs. */
    val teinteParDefaut: Float = 205f
) {
    // --- Énergiques / Lumineux
    VIF("Vif", CategoriePalette.ENERGIQUES, "Couleurs pleines, sans détour", 0.78f, 0.20f,
        teinteParDefaut = 28f),
    NEON("Néon", CategoriePalette.ENERGIQUES, "Accents électriques sur fonds colorés",
        0.95f, 0.34f, clarteAccent = 1.10f, ecartSecondaire = 135f, teinteParDefaut = 185f),
    POP("Pop", CategoriePalette.ENERGIQUES, "Deux couleurs qui se répondent", 0.86f, 0.24f,
        ecartSecondaire = 170f, teinteParDefaut = 330f),
    ELECTRIQUE("Électrique", CategoriePalette.ENERGIQUES, "Bleus et violets sous tension",
        0.92f, 0.30f, clarteAccent = 1.08f, ecartSecondaire = 60f, teinteParDefaut = 250f),

    // --- Sombres / Intenses
    SOMBRE("Sombre", CategoriePalette.SOMBRES, "Fond profond, accent retenu", 0.55f, 0.16f,
        clarteAccent = 0.86f, teinteParDefaut = 220f),
    GOTHIQUE("Gothique", CategoriePalette.SOMBRES, "Rouge sourd sur noir", 0.62f, 0.22f,
        clarteAccent = 0.78f, ecartSecondaire = 20f, teinteParDefaut = 350f),
    CYBERPUNK("Cyberpunk", CategoriePalette.SOMBRES, "Enseignes dans la nuit", 0.98f, 0.36f,
        clarteAccent = 1.18f, ecartSecondaire = 160f, teinteParDefaut = 300f),

    // --- Doux / Apaisants
    PASTEL("Pastel", CategoriePalette.DOUX, "Couleurs poudrées, peu saturées", 0.42f, 0.12f,
        clarteAccent = 1.22f, ecartSecondaire = 55f, teinteParDefaut = 200f),
    DOUX("Doux", CategoriePalette.DOUX, "À peine coloré, reposant", 0.34f, 0.09f,
        clarteAccent = 1.12f, teinteParDefaut = 150f),
    AQUARELLE("Aquarelle", CategoriePalette.DOUX, "Lavis transparents", 0.48f, 0.14f,
        clarteAccent = 1.18f, ecartSecondaire = 75f, teinteParDefaut = 190f),

    // --- Élégants / Sérieux
    CLASSIQUE("Classique", CategoriePalette.ELEGANTS, "Teintes sobres, fonds presque neutres",
        0.52f, 0.10f, ecartSecondaire = 60f, teinteParDefaut = 205f),
    MONOCHROME("Monochrome", CategoriePalette.ELEGANTS, "Une seule teinte, à peine posée",
        0.08f, 0.03f, ecartSecondaire = 0f, teinteParDefaut = 210f),
    NEUTRE("Neutre", CategoriePalette.ELEGANTS, "Des gris, et rien d'autre", 0.04f, 0.02f,
        ecartSecondaire = 0f, teinteParDefaut = 30f),
    MINIMALISTE("Minimaliste", CategoriePalette.ELEGANTS, "Un accent discret sur du gris",
        0.24f, 0.05f, ecartSecondaire = 30f, teinteParDefaut = 210f),
    LUXE("Luxe", CategoriePalette.ELEGANTS, "Un métal chaud sur fond dense", 0.58f, 0.13f,
        clarteAccent = 0.94f, ecartSecondaire = 25f, teinteParDefaut = 45f),

    // --- Rétro / Nostalgiques
    VINTAGE("Vintage", CategoriePalette.RETRO, "Papier jauni et encres passées", 0.44f, 0.16f,
        clarteAccent = 0.94f, ecartSecondaire = 30f, teinteParDefaut = 35f),
    RETRO_70("Rétro 70s", CategoriePalette.RETRO, "Orange brûlé, moutarde, marron", 0.68f, 0.22f,
        clarteAccent = 0.90f, ecartSecondaire = 25f, teinteParDefaut = 25f),
    RETRO_80("Rétro 80s", CategoriePalette.RETRO, "Magenta et cyan des cassettes", 0.86f, 0.28f,
        clarteAccent = 1.08f, ecartSecondaire = 150f, teinteParDefaut = 315f),
    ART_DECO("Art déco", CategoriePalette.RETRO, "Or et laque, lignes droites", 0.54f, 0.12f,
        clarteAccent = 0.96f, ecartSecondaire = 180f, teinteParDefaut = 45f),

    // --- Nature / Organique
    NATURELLES("Naturelles", CategoriePalette.NATURE, "Terre, bois, lichen", 0.40f, 0.12f,
        ecartSecondaire = 45f, teinteParDefaut = 95f),
    TROPICAL("Tropical", CategoriePalette.NATURE, "Feuillage dense et fleurs vives", 0.80f, 0.22f,
        ecartSecondaire = 120f, teinteParDefaut = 160f),
    OCEAN("Océan", CategoriePalette.NATURE, "Du turquoise au bleu profond", 0.64f, 0.18f,
        ecartSecondaire = 35f, teinteParDefaut = 195f),
    FORET("Forêt", CategoriePalette.NATURE, "Verts sombres et mousse", 0.50f, 0.14f,
        ecartSecondaire = 40f, teinteParDefaut = 140f);

    companion object {
        fun parCategorie(categorie: CategoriePalette): List<ThemeCouleur> =
            entries.filter { it.categorie == categorie }

        /**
         * L'ambiance qui remplace une palette enregistrée avant la 4.2.
         *
         * ⚠️ Sans cela, tous ceux qui avaient choisi « Magenta » ou « Prune » se seraient
         * retrouvés en bleu nuit après la mise à jour, sans comprendre pourquoi.
         */
        fun depuisPaletteHeritee(ordinal: Int): ThemeCouleur =
            when (PaletteApplication.entries.getOrNull(ordinal)?.famille) {
                FamillePalette.VIF -> VIF
                FamillePalette.NEON -> NEON
                FamillePalette.NEUTRE -> MONOCHROME
                else -> CLASSIQUE
            }

        /** La teinte qui accompagne cette reprise : celle de l'ancienne palette. */
        fun teintePaletteHeritee(ordinal: Int): Float =
            PaletteApplication.entries.getOrNull(ordinal)?.teinte ?: CLASSIQUE.teinteParDefaut
    }
}

/** De combien la molette déplace la teinte à chaque cran : un tour complet fait le tour du cercle. */
const val PAS_TEINTE = 5f

/** Ce qu'a donné l'ajout d'un dossier de musique. */
sealed interface ResultatRacine {
    data object AJOUTE : ResultatRacine

    /** Le dossier était déjà surveillé, et l'écriture y est maintenant accordée. */
    data object ECRITURE_AUTORISEE : ResultatRacine

    /** Le dossier était déjà surveillé, toujours sans droit d'écrire. */
    data object DEJA_SURVEILLE : ResultatRacine

    /** Le dossier est **dans** un dossier déjà surveillé : l'ajouter compterait tout en double. */
    data class DEJA_COUVERT(val parent: String) : ResultatRacine
}

/** Ce qu'on annonce après avoir choisi un dossier. */
fun messageRacine(resultat: ResultatRacine): String = when (resultat) {
    ResultatRacine.AJOUTE -> "Dossier ajouté : analyse en cours"
    ResultatRacine.ECRITURE_AUTORISEE ->
        "Écriture autorisée : les fichiers .m3u peuvent maintenant être mis à jour"
    ResultatRacine.DEJA_SURVEILLE -> "Dossier déjà surveillé, toujours en lecture seule"
    is ResultatRacine.DEJA_COUVERT ->
        "Déjà couvert par « ${resultat.parent} » : ce dossier n'a pas été ajouté, " +
            "sa musique y est déjà"
}

/**
 * Ce que le ménage doit garder, fusionner et retirer — décidé **sans toucher à rien**.
 *
 * Séparer la décision de l'exécution rend le cas réel mesurable : le 28/09/2026, la
 * bibliothèque portait vingt-huit lignes pour quatorze fichiers.
 */
data class MenagePlaylists(
    /** Les playlists à garder, dont le fichier doit être réécrit avant tout retrait. */
    val aReecrire: List<Long>,
    /** Les lignes en trop, qui désignent un fichier déjà tenu par une autre. */
    val doublons: List<Long>,
    /** Les playlists dont le fichier n'existe plus sur l'appareil. */
    val disparues: List<Long>
)

/**
 * Qui garder quand plusieurs playlists désignent le même fichier.
 *
 * ⚠️ **La plus fournie l'emporte**, et à égalité la plus récemment entrée. C'est elle qui porte
 * les ajouts faits dans l'application ; garder l'autre reviendrait à les perdre au moment même
 * où l'on réécrit le fichier.
 *
 * ⚠️ Une playlist sans fichier (créée dans l'application) n'est **jamais** concernée : ni par les
 * doublons, ni par le retrait des disparues.
 */
fun deciderLeMenage(
    playlists: List<Playlist>,
    nombreDePistes: (Long) -> Int,
    fichierExiste: (String) -> Boolean,
    /**
     * Ce qui identifie un fichier. ⚠️ **Pas l'URI** : un même document se lit par autant d'URI
     * qu'il existe d'arbres qui le contiennent, et les comparer entières fait passer un fichier
     * pour deux.
     */
    cleDuFichier: (String) -> String = { it }
): MenagePlaylists {
    val aReecrire = mutableListOf<Long>()
    val doublons = mutableListOf<Long>()

    playlists.filter { it.source != null }
        .groupBy { cleDuFichier(it.source!!) }
        .forEach { (_, memeFichier) ->
            if (memeFichier.size < 2) return@forEach
            val gardee = memeFichier.maxWithOrNull(
                compareBy({ nombreDePistes(it.id) }, { it.id })
            ) ?: return@forEach
            aReecrire += gardee.id
            doublons += memeFichier.filter { it.id != gardee.id }.map { it.id }
        }

    val disparues = playlists
        .filter { it.source != null && it.id !in doublons }
        .filterNot { fichierExiste(it.source!!) }
        .map { it.id }

    return MenagePlaylists(aReecrire = aReecrire, doublons = doublons, disparues = disparues)
}

/** Ce qu'a donné le ménage des playlists en double. */
data class ResultatDoublons(
    /** Playlists dont le fichier a été remis à jour avant le ménage. */
    val reecrites: Int,
    /** Lignes en double retirées de la bibliothèque — aucun fichier n'est touché. */
    val retirees: Int,
    /** Playlists retirées parce que leur fichier n'existe plus. */
    val disparues: Int
)

/** Ce qu'on annonce après le ménage. */
fun messageDoublons(resultat: ResultatDoublons): String {
    val morceaux = buildList {
        if (resultat.reecrites > 0) {
            add("${resultat.reecrites} fichier${pluriel(resultat.reecrites)} mis à jour")
        }
        if (resultat.retirees > 0) {
            add("${resultat.retirees} doublon${pluriel(resultat.retirees)} retiré${pluriel(resultat.retirees)}")
        }
        if (resultat.disparues > 0) {
            add(
                "${resultat.disparues} playlist${pluriel(resultat.disparues)} sans fichier " +
                    "retirée${pluriel(resultat.disparues)}"
            )
        }
    }
    return if (morceaux.isEmpty()) "Aucun doublon" else morceaux.joinToString(", ")
}

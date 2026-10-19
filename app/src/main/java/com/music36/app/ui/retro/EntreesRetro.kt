package com.music36.app.ui.retro

import androidx.compose.foundation.layout.size
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.music36.app.data.Piste
import com.music36.app.data.RangQualite
import com.music36.app.data.StyleInterface
import com.music36.app.data.Tri
import com.music36.app.data.formaterDuree
import com.music36.app.data.plusEcoutes
import com.music36.app.data.trier
import com.music36.app.lecture.SortieAudio
import com.music36.app.ui.ActionsUi
import com.music36.app.data.pluriel
import com.music36.app.data.FondLecteur
import com.music36.app.data.COULEURS_FOND
import com.music36.app.data.StyleMolette
import com.music36.app.data.EffetMolette
import com.music36.app.data.COULEURS_MOLETTE
import com.music36.app.data.Nouveautes
import com.music36.app.data.ThemeApplication
import com.music36.app.data.StyleBarre
import com.music36.app.data.CategoriePalette
import com.music36.app.data.ThemeCouleur
import com.music36.app.data.EtatReglages
import com.music36.app.data.ReglageNumerique
import com.music36.app.data.FormatExport
import com.music36.app.data.PaletteApplication
import com.music36.app.data.Onglet
import com.music36.app.data.libelleDeBande
import com.music36.app.data.texteDeBande
import com.music36.app.data.niveauDeBande
import com.music36.app.data.ConfigurationApparence
import com.music36.app.data.NOMBRE_CONFIGURATIONS
import com.music36.app.data.avecConfiguration
import com.music36.app.data.configurations
import com.music36.app.data.PlaylistGarnie
import com.music36.app.data.dossierDeUri
import com.music36.app.data.nomDeFichierDeUri
import com.music36.app.data.selonTri
import com.music36.app.data.triDe
import com.music36.app.data.numeroterLesApparitions

/**
 * Ce que contient chaque niveau : une liste de lignes, construite à la demande.
 *
 * ⚠️ `entreesPour` est **sans état** et appelée aussi bien à la composition que depuis les gestes
 * de la molette. Une version antérieure gardait les entrées dans un `MutableState` : la molette
 * agissait alors sur une liste périmée, et l'on ne pouvait plus entrer dans les sous-menus.
 */
/** Une ligne de menu : un libellé, sa valeur au bout de la ligne, et ce qu'elle déclenche. */
internal data class EntreeRetro(
    val libelle: String,
    /** Sous-titre, sous le libellé — l'artiste d'un titre, par exemple. */
    val detail: String? = null,
    /** Valeur alignée **au bout de la ligne** : un décompte, une durée, un état. */
    val valeur: String? = null,
    val fleche: Boolean = false,
    /** Le rang de qualité du fichier, qui colore la ligne. `null` pour tout ce qui n'est pas une piste. */
    val rang: RangQualite? = null,
    /** Vrai quand le titre n'appartient à aucune playlist — c'est ce que signale l'icône. */
    val orpheline: Boolean = false,
    /** Ce sur quoi porte un appui long. `null` quand la ligne n'est pas une chose qu'on peut gérer. */
    val cible: CibleAction? = null,
    val action: () -> Unit
)
/** Un classement plus long ne se parcourt plus : au-delà de cent, on cherche par le nom. */
internal const val TAILLE_CLASSEMENT = 100
/**
 * La fiche technique d'un fichier, en lignes de menu.
 *
 * Les mêmes informations que le dialogue de l'interface moderne, mais rangées comme le reste de
 * la vue molette : libellé à gauche, valeur au bout de la ligne, et la molette pour faire défiler.
 */
internal fun infosDe(piste: Piste, decodeur: String?, flux: String?): List<EntreeRetro> = buildList {
    // Une valeur courte se lit au bout de la ligne ; un titre ou un chemin s'écrirait alors sur
    // le libellé, il passe donc en dessous.
    add(EntreeRetro("Titre", detail = piste.titre) {})
    add(EntreeRetro("Artiste", detail = piste.artisteAffiche) {})
    if (piste.album.isNotBlank()) add(EntreeRetro("Album", detail = piste.album) {})
    if (piste.artisteAlbum.isNotBlank() && piste.artisteAlbum != piste.artisteAffiche) {
        add(EntreeRetro("Artiste de l'album", detail = piste.artisteAlbum) {})
    }
    if (piste.genre.isNotBlank()) add(EntreeRetro("Genre", valeur = piste.genre) {})
    if (piste.annee > 0) add(EntreeRetro("Année", valeur = piste.annee.toString()) {})
    if (piste.numeroPiste > 0) add(EntreeRetro("Piste", valeur = piste.numeroPiste.toString()) {})
    add(EntreeRetro("Durée", valeur = formaterDuree(piste.duree)) {})
    add(EntreeRetro("Qualité", detail = piste.qualite().ifBlank { "inconnue" }) {})
    // Le nom du rang, pour que la couleur des lignes se laisse apprendre.
    add(EntreeRetro("Rang", valeur = piste.rangQualite.libelle, rang = piste.rangQualite) {})
    if (piste.hauteResolution) add(EntreeRetro("Haute résolution", valeur = "oui") {})
    add(EntreeRetro("Taille", valeur = "%.1f Mo".format(piste.taille / 1_048_576.0)) {})
    add(EntreeRetro("Décodeur", detail = decodeur ?: "aucun — format non pris en charge") {})
    // Ce que la chaîne audio traite vraiment. Le reste de cet écran décrit ce que l'application
    // *croit* jouer ; cette ligne-ci vient du lecteur lui-même.
    if (flux != null && flux != piste.titre) {
        add(EntreeRetro("Flux décodé", detail = flux) {})
    }
    add(EntreeRetro("Fichier", detail = piste.nomFichier) {})
    add(EntreeRetro("Dossier", detail = piste.dossier) {})
}
internal fun entreesDePistes(
    pistes: List<Piste>,
    enPlaylist: Set<String> = emptySet(),
    onJouer: (Int) -> Unit
): List<EntreeRetro> =
    pistes.mapIndexed { index, piste ->
        EntreeRetro(
            libelle = piste.titre,
            detail = piste.artisteAffiche,
            valeur = formaterDuree(piste.duree),
            rang = piste.rangQualite,
            orpheline = piste.uri !in enPlaylist,
            cible = CibleAction.Titre(piste.uri, piste.titre)
        ) { onJouer(index) }
    }
/**
 * Les entrées d'un niveau, construites **à la demande**.
 *
 * ⚠️ Cette fonction est appelée à deux moments : à la composition, pour l'affichage, et **au
 * moment précis où le doigt agit**, depuis `tourner` et `appuyer`. C'est ce qui garantit que la
 * commande porte sur le menu réellement ouvert.
 *
 * La version précédente gardait les entrées dans un état rafraîchi après coup : le doigt pouvait
 * alors valider une entrée d'un menu quitté, et l'on lançait la liste d'un genre en croyant
 * lancer celle qu'on avait sous les yeux. Ici rien n'est capturé : le niveau vient de la pile, les
 * données des `StateFlow` du dépôt, tous lus à l'instant de l'appel.
 */
@UnstableApi
/**
 * Un souvenir d'un seul résultat par liste, gardé d'une composition à l'autre.
 *
 * ⚠️ `EcranRetro` lit l'état de lecture, donc **recompose deux fois par seconde** pendant qu'un
 * morceau joue. Sans ce souvenir, « Toutes les chansons » retriait la bibliothèque entière à
 * chaque battement : à cinq mille titres, c'est un tri complet deux fois par seconde pour
 * afficher une barre de progression qui avance.
 *
 * Les listes se comparent par **identité** : leur contenu ne change jamais sans que le `StateFlow`
 * n'en publie une nouvelle. Les clés simples — l'identifiant d'un album, d'un genre — se comparent
 * par valeur, deux chaînes égales désignant bien le même niveau.
 */
internal class MemoRetro {
    private val souvenirs = HashMap<String, Pair<List<Any?>, Any?>>()

    @Suppress("UNCHECKED_CAST")
    fun <E> valeur(nom: String, vararg cles: Any?, calcul: () -> E): E {
        val precedent = souvenirs[nom]
        if (precedent != null && memesCles(precedent.first, cles)) return precedent.second as E
        val calcule = calcul()
        souvenirs[nom] = cles.toList() to calcule
        return calcule
    }

    private fun memesCles(anciennes: List<Any?>, nouvelles: Array<out Any?>): Boolean {
        if (anciennes.size != nouvelles.size) return false
        return anciennes.indices.all { i ->
            val a = anciennes[i]
            val b = nouvelles[i]
            if (a is Collection<*> || b is Collection<*> || a is Map<*, *> || b is Map<*, *>) {
                a === b
            } else {
                a == b
            }
        }
    }
}

internal fun entreesPour(
    niveau: NiveauRetro,
    actions: ActionsUi,
    /** L'état de lecture est passé, jamais deviné : l'écran et les commandes lisent le même. */
    etat: com.music36.app.lecture.EtatLecture,
    ouvrir: (NiveauRetro) -> Unit,
    fermer: () -> Unit,
    jouer: (List<Piste>, Int, Boolean) -> Unit,
    /** Les playlists cochées dans l'écran de choix, vide ailleurs. */
    choixPlaylists: Set<Long> = emptySet(),
    /** Ce que la molette demande d'exécuter — l'écran s'en charge, pas cette fonction. */
    agir: (ActionRetro) -> Unit = {},
    /** Le souvenir des listes triées. Le passer, plutôt qu'un état global, le garde testable. */
    memo: MemoRetro = MemoRetro(),
    /** Vrai quand la molette a été ouverte depuis l'interface moderne. */
    depuisModerne: Boolean = false
): List<EntreeRetro> {
    val decodeur = com.music36.app.lecture.SortieAudio.decodeur.value
    val depot = actions.depot
    val pistes = depot.pistes.value
    val artistes = depot.artistes.value
    val albums = depot.albums.value
    val genres = depot.genres.value
    val playlists = depot.playlists.value
    val dossiers = depot.dossiersRacines.value
    val favoris = depot.pistesFavorites.value
    val racines = depot.racines.value
    val analyse = depot.analyseur.etat.value
    val statistiques = depot.statistiques.value
    val enPlaylist = depot.urisEnPlaylist.value
    val reglages = depot.etatReglages.value
    val ambiance = ThemeCouleur.entries.getOrElse(reglages.themeCouleurRetro) {
        ThemeCouleur.CLASSIQUE
    }

    return when (niveau) {
        NiveauRetro.Racine -> buildList {
            add(EntreeRetro("Musique", fleche = true) { ouvrir(NiveauRetro.Musique) })
            if (etat.actif) {
                add(EntreeRetro("Lecture en cours", fleche = true) { ouvrir(NiveauRetro.Lecture) })
                add(
                    EntreeRetro(
                        "File d'attente",
                        valeur = "${etat.file.size}",
                        fleche = true
                    ) { ouvrir(NiveauRetro.FileAttente) }
                )
            }
            add(EntreeRetro("Lecture aléatoire", valeur = "${pistes.size} titres") {
                jouer(pistes, 0, true)
            })
            // ⚠️ Un seul chemin vers les réglages depuis la racine. « Réglages de la vue Rétro » y
            // figurait aussi : deux entrées de réglages côte à côte, dont l'une menait à un
            // sous-ensemble de l'autre, ne faisaient qu'obliger à se souvenir de laquelle.
            add(EntreeRetro("Réglages", fleche = true) { ouvrir(NiveauRetro.Reglages) })
        }

        NiveauRetro.Musique -> listOf(
            EntreeRetro("Toutes les chansons", valeur = "${pistes.size}", fleche = true) {
                ouvrir(NiveauRetro.Sons)
            },
            EntreeRetro("Artistes", valeur = "${artistes.size}", fleche = true) {
                ouvrir(NiveauRetro.Artistes)
            },
            EntreeRetro("Albums", valeur = "${albums.size}", fleche = true) {
                ouvrir(NiveauRetro.Albums)
            },
            EntreeRetro("Cover Flow", valeur = "${albums.size}", fleche = true) {
                ouvrir(NiveauRetro.CoverFlow)
            },
            EntreeRetro("Genres", valeur = "${genres.size}", fleche = true) {
                ouvrir(NiveauRetro.Genres)
            },
            EntreeRetro("Playlists", valeur = "${playlists.size}", fleche = true) {
                ouvrir(NiveauRetro.Playlists)
            },
            EntreeRetro("Dossiers", valeur = "${dossiers.size}", fleche = true) {
                ouvrir(NiveauRetro.Dossiers)
            },
            EntreeRetro("Favoris", valeur = "${favoris.size}", fleche = true) {
                ouvrir(NiveauRetro.Favoris)
            },
            EntreeRetro(
                "Musique sans playlist",
                valeur = "${pistes.count { it.uri !in enPlaylist }}",
                fleche = true
            ) { ouvrir(NiveauRetro.SansPlaylist) },
            EntreeRetro(
                "Titres les plus écoutés",
                valeur = "${statistiques.values.count { it.lectures > 0 }}",
                fleche = true
            ) { ouvrir(NiveauRetro.PlusEcoutes) }
        )

        // ⚠️ La liste triée sert **aussi** à la lecture : afficher un ordre et jouer l'autre
        // lancerait le mauvais morceau, l'index étant commun aux deux.
        NiveauRetro.Sons -> {
            val liste = memo.valeur("sons", pistes) { pistes.trier(Tri.ALPHABETIQUE, true) }
            memo.valeur("entreesSons", liste, enPlaylist) {
                entreesDePistes(liste, enPlaylist) { index -> jouer(liste, index, false) }
            }
        }

        NiveauRetro.Favoris -> {
            val liste = memo.valeur("favoris", favoris) { favoris.trier(Tri.ALPHABETIQUE, true) }
            memo.valeur("entreesFavoris", liste, enPlaylist) {
                entreesDePistes(liste, enPlaylist) { index -> jouer(liste, index, false) }
            }
        }

        // Le classement des écoutes : les compteurs sont tenus par le service de lecture, qui
        // valide une écoute au-delà de quinze secondes, et vivent dans la base — ils survivent
        // donc aux réanalyses comme aux redémarrages.
        NiveauRetro.PlusEcoutes -> {
            val liste = memo.valeur("classement", pistes, statistiques) {
                pistes.plusEcoutes(statistiques, TAILLE_CLASSEMENT)
            }
            liste.mapIndexed { index, piste ->
                val ecoutes = statistiques[piste.uri]?.lectures ?: 0
                EntreeRetro(
                    libelle = "${index + 1}. ${piste.titre}",
                    detail = piste.artisteAffiche,
                    valeur = if (ecoutes > 1) "$ecoutes écoutes" else "$ecoutes écoute",
                    rang = piste.rangQualite,
                    orpheline = piste.uri !in enPlaylist
                ) { jouer(liste, index, false) }
            }
        }

        NiveauRetro.Artistes -> memo.valeur("entreesArtistes", artistes) { artistes.map { artiste ->
            EntreeRetro(
                artiste.nom,
                valeur = "${artiste.pistes.size}",
                fleche = true,
                cible = CibleAction.Groupe(
                    artiste.pistes.map { it.uri }, artiste.nom, "artiste"
                )
            ) { ouvrir(NiveauRetro.Artiste(artiste.cle)) }
        } }

        is NiveauRetro.Artiste -> {
            val liste = memo.valeur("artiste", artistes, niveau.cle) {
                artistes.firstOrNull { it.cle == niveau.cle }?.pistes.orEmpty()
                    .trier(Tri.ALPHABETIQUE, true)
            }
            memo.valeur("entreesArtiste", liste, enPlaylist) {
                entreesDePistes(liste, enPlaylist) { index -> jouer(liste, index, false) }
            }
        }

        NiveauRetro.Albums -> memo.valeur("entreesAlbums", albums) { albums.map { album ->
            EntreeRetro(
                album.nom,
                valeur = "${album.pistes.size}",
                fleche = true,
                cible = CibleAction.Groupe(album.pistes.map { it.uri }, album.nom, "album")
            ) { ouvrir(NiveauRetro.Album(album.cle)) }
        } }

        is NiveauRetro.Album -> {
            val liste = albums.firstOrNull { it.cle == niveau.cle }?.pistes.orEmpty()
            entreesDePistes(liste, enPlaylist) { index -> jouer(liste, index, false) }
        }

        // Le carrousel se pilote exactement comme un menu : ses « entrées » sont les albums,
        // la molette y déplace la même sélection, seul l'affichage change.
        NiveauRetro.CoverFlow -> memo.valeur("entreesCoverFlow", albums) { albums.map { album ->
            EntreeRetro(album.nom) { ouvrir(NiveauRetro.CoverFlowAlbum(album.cle)) }
        } }

        is NiveauRetro.CoverFlowAlbum -> {
            val liste = albums.firstOrNull { it.cle == niveau.cle }?.pistes.orEmpty()
            entreesDePistes(liste, enPlaylist) { index -> jouer(liste, index, false) }
        }

        NiveauRetro.Genres -> memo.valeur("entreesGenres", genres) { genres.map { genre ->
            EntreeRetro(
                genre.nom,
                valeur = "${genre.pistes.size}",
                fleche = true,
                cible = CibleAction.Groupe(genre.pistes.map { it.uri }, genre.nom, "genre")
            ) { ouvrir(NiveauRetro.Genre(genre.cle)) }
        } }

        is NiveauRetro.Genre -> {
            val liste = memo.valeur("genre", genres, niveau.cle) {
                genres.firstOrNull { it.cle == niveau.cle }?.pistes.orEmpty()
                    .trier(Tri.ALPHABETIQUE, true)
            }
            memo.valeur("entreesGenre", liste, enPlaylist) {
                entreesDePistes(liste, enPlaylist) { index -> jouer(liste, index, false) }
            }
        }

        NiveauRetro.Playlists -> memo.valeur("entreesPlaylists", playlists) {
            playlists.map { garnie ->
            EntreeRetro(
                garnie.playlist.nom,
                valeur = "${garnie.pistes.size}",
                fleche = true,
                cible = CibleAction.Playlist(garnie.playlist.id, garnie.playlist.nom)
                ) { ouvrir(NiveauRetro.Playlist(garnie.playlist.id)) }
            }
        }

        is NiveauRetro.Playlist -> {
            val contenu = playlists.firstOrNull { it.playlist.id == niveau.id }?.pistes.orEmpty()
            val tri = triDe(reglages, niveau.id)
            // ⚠️ **Mémorisée.** Sans souvenir, chaque cran de molette reconstruisait et
            // retriait la playlist entière — huit cents lignes et un tri à la française par
            // cran, d'où la lenteur signalée pendant une recherche.
            memo.valeur("entreesPlaylist", contenu, niveau.id, tri, enPlaylist) {
                // Chaque ligne retient **laquelle** de ses apparitions elle est : une playlist
                // peut porter deux fois le même titre, et retirer l'une ne doit pas emporter
                // l'autre. Le rang se compte dans l'ordre du fichier, avant tout tri.
                val numerotees = numeroterLesApparitions(contenu) { it.uri }
                // ⚠️ La liste **triée** est aussi celle qu'on donne à jouer : le titre suivant
                // est celui qu'on voit sous le curseur, et non celui du fichier.
                val triees = numerotees.selonTri(tri) { it.first }
                val liste = triees.map { it.first }
                entreesDePistes(liste, enPlaylist) { index -> jouer(liste, index, false) }
                    .mapIndexed { index, entree ->
                        entree.copy(
                            cible = CibleAction.Titre(
                                uri = liste[index].uri,
                                libelle = liste[index].titre,
                                playlistId = niveau.id,
                                occurrence = triees[index].second
                            )
                        )
                    }
            }
        }

        NiveauRetro.Dossiers -> memo.valeur("entreesDossiers", dossiers) { dossiers.map { dossier ->
            EntreeRetro(
                dossier.nom,
                valeur = "${dossier.pistes.size}",
                fleche = true,
                cible = CibleAction.Groupe(dossier.pistes.map { it.uri }, dossier.nom, "dossier")
            ) { ouvrir(NiveauRetro.Dossier(dossier.cle)) }
        } }

        is NiveauRetro.Dossier -> {
            // Chacun de ces trois appels parcourt la bibliothèque entière : sans souvenir, un
            // dossier ouvert la relisait trois fois par battement de lecture.
            val sous = memo.valeur("sousDossiers", pistes, niveau.chemin) {
                actions.depot.sousDossiers(niveau.chemin)
            }
            val propres = memo.valeur("dossierPropres", pistes, niveau.chemin) {
                actions.depot.pistesDuDossier(niveau.chemin).trier(Tri.ALPHABETIQUE, true)
            }
            val recursives = memo.valeur("dossierRecursives", pistes, niveau.chemin) {
                actions.depot.pistesRecursives(niveau.chemin)
            }
            buildList {
                add(EntreeRetro("Tout lire", valeur = "${recursives.size}") { jouer(recursives, 0, false) })
                sous.forEach { enfant ->
                    add(
                        EntreeRetro(enfant.nom, valeur = "${enfant.pistes.size}", fleche = true) {
                            ouvrir(NiveauRetro.Dossier(enfant.cle))
                        }
                    )
                }
                addAll(entreesDePistes(propres, enPlaylist) { index -> jouer(propres, index, false) })
            }
        }

        NiveauRetro.Reglages -> listOf(
            // L'interface molette n'a pas de bandeau de messages : l'état de l'analyse se lit ici.
            EntreeRetro(
                "Bibliothèque",
                valeur = when {
                    analyse.enCours -> "${analyse.fichiersLus}/${analyse.total}"
                    analyse.erreur != null -> "échec"
                    analyse.illisibles > 0 -> "${pistes.size} titres · ${analyse.illisibles} sans étiquette"
                    else -> "${pistes.size} titres"
                }
            ) {},
            EntreeRetro(
                "Lecture aléatoire",
                valeur = if (etat.aleatoire) "Oui" else "Non"
            ) { actions.lecteur.basculerAleatoire() },
            EntreeRetro(
                "Répétition",
                valeur = when (etat.repetition) {
                    Player.REPEAT_MODE_ONE -> "Un titre"
                    Player.REPEAT_MODE_ALL -> "Tout"
                    else -> "Non"
                }
            ) { actions.lecteur.cyclerRepetition() },
            EntreeRetro("Dossiers surveillés", valeur = "${racines.size}") {
                actions.choisirDossier(null)
            },
            EntreeRetro(
                "Analyser les dossiers",
                detail = "Le bilan s'affiche à la fin"
            ) { agir(ActionRetro.Analyser(complet = false)) },
            // ⚠️ Tous les réglages de l'application sont **ici**, à la molette. L'interface
            // moderne ne sert plus qu'à regarder : listes et lecteur plein écran.
            EntreeRetro(
                "Paramètres de l'application",
                detail = "Dossiers, bibliothèque, lecture, son, apparence",
                fleche = true
            ) { ouvrir(NiveauRetro.ParametresApp) },
            EntreeRetro("Réglages de la vue Rétro", fleche = true) { ouvrir(NiveauRetro.ReglagesRetro) },
            EntreeRetro(
                "Exporter les playlists",
                detail = "Vers un dossier, ou à partager avec une autre application",
                valeur = "${playlists.size}",
                fleche = true
            ) { ouvrir(NiveauRetro.ParamFormatExport) },
            EntreeRetro(
                "Configurations enregistrées",
                detail = "Couleurs, thème et effets, rappelés d'un geste",
                valeur = "${reglages.configurations.count { it != null }}/$NOMBRE_CONFIGURATIONS",
                fleche = true
            ) { ouvrir(NiveauRetro.ConfigurationsRetro) },
            EntreeRetro(
                "Interface moderne",
                detail = "Les listes et le lecteur plein écran, au doigt"
            ) {
                actions.depot.modifierReglages { it.copy(style = StyleInterface.MODERNE) }
            }
        )

        // ------------------------------------------------------------------ paramètres
        //
        // ⚠️ Ces branches reprennent, une pour une, les sections de l'écran « Paramètres » de
        // l'interface moderne. Elles règlent **les mêmes champs** d'`EtatReglages` : il n'y a
        // pas deux jeux de réglages, seulement deux façons de les atteindre.
        NiveauRetro.ParametresApp -> listOfNotNull(
            // ⚠️ En tête quand on arrive de l'interface moderne : c'est là qu'on s'attend à
            // revenir, et la chercher deux niveaux plus haut n'aurait rien d'évident.
            if (depuisModerne) {
                EntreeRetro(
                    "Revenir à l'interface moderne",
                    detail = "Les listes et le lecteur plein écran"
                ) { actions.depot.modifierReglages { it.copy(style = StyleInterface.MODERNE) } }
            } else {
                null
            },
            EntreeRetro(
                "Dossiers de musique",
                detail = "Ce que l'application a le droit de lire",
                valeur = "${racines.size}",
                fleche = true
            ) { ouvrir(NiveauRetro.ParamDossiers) },
            EntreeRetro(
                "Bibliothèque",
                detail = "Analyse, filtres, playlists .m3u",
                fleche = true
            ) { ouvrir(NiveauRetro.ParamBibliotheque) },
            EntreeRetro(
                "Lecture",
                detail = "Fondu, silences, vitesse, reprise",
                fleche = true
            ) { ouvrir(NiveauRetro.ParamLecture) },
            EntreeRetro(
                "Qualité sonore",
                detail = "Sortie 32 bits, DAC USB, égaliseur",
                fleche = true
            ) { ouvrir(NiveauRetro.ParamSon) },
            EntreeRetro(
                "Apparence",
                detail = "Ce que voit l'interface moderne",
                fleche = true
            ) { ouvrir(NiveauRetro.ParamApparence) },
            EntreeRetro("Playlists", detail = "Export", fleche = true) {
                ouvrir(NiveauRetro.ParamPlaylists)
            },
            EntreeRetro("Formats reconnus", fleche = true) { ouvrir(NiveauRetro.ParamFormats) }
        )

        // ------------------------------------------------------------------ configurations
        NiveauRetro.ConfigurationsRetro -> reglages.configurations.mapIndexed { index, config ->
            if (config == null) {
                // ⚠️ Un emplacement vide s'enregistre **sans confirmation** : il n'y a rien à
                // perdre. Remplacer ou supprimer, en revanche, passent par un écran de plus.
                EntreeRetro(
                    "Emplacement ${index + 1}",
                    detail = "Vide — appuyez pour y enregistrer l'apparence actuelle",
                    valeur = "vide"
                ) {
                    actions.depot.modifierReglages {
                        it.avecConfiguration(
                            index,
                            ConfigurationApparence.depuis(it, System.currentTimeMillis())
                        )
                    }
                    actions.message("Apparence enregistrée dans l'emplacement ${index + 1}")
                }
            } else {
                EntreeRetro(
                    "Configuration ${index + 1}",
                    detail = resumeDe(config),
                    valeur = if (config.estEnCours(reglages)) "en cours" else null,
                    fleche = true,
                    cible = CibleAction.Configuration(index, "Configuration ${index + 1}")
                ) { ouvrir(NiveauRetro.ConfigurationRetro(index)) }
            }
        }

        is NiveauRetro.ConfigurationRetro -> {
            val config = reglages.configurations.getOrNull(niveau.emplacement)
            val cible = CibleAction.Configuration(
                niveau.emplacement,
                "Configuration ${niveau.emplacement + 1}"
            )
            if (config == null) {
                emptyList()
            } else {
                // ⚠️ « Appliquer » en tête : c'est le geste de tous les jours. Supprimer vient en
                // dernier, pour que la molette ne s'y pose jamais d'elle-même.
                listOf(
                    EntreeRetro(
                        "Appliquer",
                        detail = resumeDe(config),
                        valeur = if (config.estEnCours(reglages)) "en cours" else null
                    ) {
                        actions.depot.modifierReglages { config.appliquer(it) }
                        actions.message("Configuration ${niveau.emplacement + 1} appliquée")
                        fermer()
                    },
                    EntreeRetro(
                        "Remplacer par l'apparence actuelle",
                        detail = "Pour modifier cette configuration : réglez d'abord, puis " +
                            "remplacez"
                    ) { ouvrir(NiveauRetro.Confirmation(remplacementDe(cible))) },
                    EntreeRetro("Supprimer", detail = "L'emplacement redevient libre") {
                        ouvrir(NiveauRetro.Confirmation(suppressionDe(cible)))
                    }
                )
            }
        }

        NiveauRetro.ParamDossiers -> buildList {
            add(
                EntreeRetro(
                    "Ajouter un dossier",
                    detail = "36Music ne lit que les dossiers listés ici"
                ) { actions.choisirDossier(null) }
            )
            add(
                EntreeRetro(
                    "Analyser les nouveautés",
                    detail = "Repère ce qui a été ajouté depuis la dernière fois"
                ) { agir(ActionRetro.Analyser(complet = false)) }
            )
            add(
                EntreeRetro(
                    "Analyse complète",
                    detail = "Relit les métadonnées de tous les fichiers"
                ) { agir(ActionRetro.Analyser(complet = true)) }
            )
            add(
                EntreeRetro(
                    "État de l'analyse",
                    detail = when {
                        analyse.enCours -> analyse.dossierCourant
                        analyse.erreur != null -> analyse.erreur
                        else -> "${analyse.ajoutees} ajoutés, ${analyse.retirees} retirés " +
                            "à la dernière analyse"
                    },
                    valeur = when {
                        analyse.enCours -> "${analyse.fichiersLus}/${analyse.total}"
                        analyse.erreur != null -> "échec"
                        else -> "${pistes.size} titres"
                    }
                ) {}
            )
            // ⚠️ Le retrait passe par la confirmation, comme tout geste qui vide une liste :
            // les titres quittent la bibliothèque, même si aucun fichier n'est effacé.
            // ⚠️ Un dossier ajouté avant la 4.6 n'est autorisé qu'en lecture : Android refuse
            // alors d'y effacer, d'y déplacer ou d'y réécrire un `.m3u`. Une permission
            // persistante ne se complète pas après coup — il faut rechoisir le dossier.
            val lectureSeule = actions.depot.racinesEnLectureSeule().map { it.uri }.toSet()
            if (lectureSeule.isNotEmpty()) {
                add(
                    EntreeRetro(
                        "Autoriser l'écriture",
                        detail = "Confirmez le dossier proposé : sans cela Android refuse " +
                            "d'effacer, de déplacer et de réécrire les playlists",
                        valeur = "${lectureSeule.size} dossier${pluriel(lectureSeule.size)}"
                    ) {
                        // ⚠️ Le sélecteur s'ouvre **sur le dossier à réautoriser** : il n'y a
                        // qu'à confirmer. Choisir soi-même mène à prendre un sous-dossier, que
                        // l'application compterait alors en double.
                        actions.choisirDossier(
                            actions.depot.racinesEnLectureSeule().firstOrNull()?.uri
                                ?.let { android.net.Uri.parse(it) }
                        )
                    }
                )
            }
            val enDouble = actions.depot.racinesEnDouble()
            racines.forEach { racine ->
                add(
                    EntreeRetro(
                        racine.nom,
                        detail = when {
                            // ⚠️ Un dossier contenu dans un autre fait tout compter deux fois :
                            // c'est le premier défaut à signaler, avant tout le reste.
                            racine.uri in enDouble ->
                                "Déjà contenu dans « ${enDouble[racine.uri]} » — tout y compte " +
                                    "en double, appuyez pour le retirer"

                            racine.uri in lectureSeule ->
                                "Lecture seule — effacer et déplacer sont refusés ici"

                            else -> "Appuyez pour retirer ce dossier"
                        },
                        valeur = "${pistes.count { it.racine == racine.uri }} titres",
                        cible = CibleAction.Racine(racine.uri, racine.nom)
                    ) {
                        ouvrir(
                            NiveauRetro.Confirmation(
                                retraitDeRacine(CibleAction.Racine(racine.uri, racine.nom))
                            )
                        )
                    }
                )
            }
        }

        NiveauRetro.ParamBibliotheque -> listOf(
            bascule(
                "Analyser au démarrage",
                "Repère les fichiers ajoutés depuis la dernière fois",
                reglages.analyseAuDemarrage
            ) { actions.depot.modifierReglages { r -> r.copy(analyseAuDemarrage = it) } },
            bascule(
                "Ignorer les fichiers cachés",
                "Les dossiers et fichiers commençant par un point",
                reglages.ignorerFichiersCaches
            ) { actions.depot.modifierReglages { r -> r.copy(ignorerFichiersCaches = it) } },
            bascule(
                "Analyse approfondie de la qualité",
                "Ouvre chaque fichier pour lire la fréquence et la profondeur réelles",
                reglages.analyseApprofondie
            ) { actions.depot.modifierReglages { r -> r.copy(analyseApprofondie = it) } },
            nombre(NiveauRetro.ParamNombre(ReglageNumerique.DUREE_MINIMALE), reglages, ouvrir),
            bascule(
                "Importer les playlists .m3u trouvées",
                "Les fichiers .m3u posés dans vos dossiers deviennent des playlists",
                reglages.importerM3u
            ) { actions.depot.modifierReglages { r -> r.copy(importerM3u = it) } },
            bascule(
                "Répercuter dans le fichier .m3u",
                "Une playlist modifiée ici réécrit le fichier d'origine",
                reglages.repercuterM3u
            ) { actions.depot.modifierReglages { r -> r.copy(repercuterM3u = it) } },
            EntreeRetro("Importer un fichier .m3u") { agir(ActionRetro.ImporterM3u) }
        )

        NiveauRetro.ParamLecture -> listOf(
            nombre(NiveauRetro.ParamNombre(ReglageNumerique.FONDU), reglages, ouvrir),
            bascule(
                "Appliquer le fondu à la pause",
                "La musique s'éteint et revient en douceur au lieu de couper net",
                reglages.fonduPause
            ) { actions.depot.modifierReglages { r -> r.copy(fonduPause = it) } },
            bascule(
                "Sauter les silences",
                "Passe les blancs en début et fin de piste",
                reglages.sauterSilences
            ) { actions.depot.modifierReglages { r -> r.copy(sauterSilences = it) } },
            nombre(NiveauRetro.ParamNombre(ReglageNumerique.VITESSE), reglages, ouvrir),
            // ⚠️ Le réglage enregistré dit l'inverse de ce qu'on lit : `tonaliteSuitVitesse`
            // vrai, c'est la tonalité **non** conservée. On présente la question à l'endroit.
            bascule(
                "Conserver la tonalité",
                "Sinon la hauteur du son suit la vitesse, comme un vinyle accéléré",
                !reglages.tonaliteSuitVitesse
            ) { actions.depot.modifierReglages { r -> r.copy(tonaliteSuitVitesse = !it) } },
            bascule(
                "Reprendre au démarrage",
                "Retrouve la file d'attente et la position de la dernière session",
                reglages.reprendreAuDemarrage
            ) { actions.depot.modifierReglages { r -> r.copy(reprendreAuDemarrage = it) } },
            bascule(
                "Mémoriser la position des pistes longues",
                "Au-delà de dix minutes, la lecture repart où elle s'était arrêtée",
                reglages.memoriserPositionLongues
            ) { actions.depot.modifierReglages { r -> r.copy(memoriserPositionLongues = it) } }
        )

        NiveauRetro.ParamSon -> listOf(
            bascule(
                "Sortie 32 bits flottante",
                "Évite de tronquer les fichiers 24 ou 32 bits. Prend effet au prochain démarrage",
                reglages.sortieFlottante
            ) { actions.depot.modifierReglages { r -> r.copy(sortieFlottante = it) } },
            bascule(
                "Déchargement audio",
                "Confie le décodage au processeur de signal. Incompatible avec l'égaliseur",
                reglages.dechargementAudio
            ) { actions.depot.modifierReglages { r -> r.copy(dechargementAudio = it) } },
            bascule(
                "Router vers le DAC USB",
                "Un adaptateur ou un casque USB-C audio reçoit la lecture directement",
                reglages.routageUsb
            ) { actions.depot.modifierReglages { r -> r.copy(routageUsb = it) } },
            EntreeRetro(
                "Sortie détectée",
                valeur = SortieAudio.materiel.value?.resume() ?: "aucune"
            ) {},
            EntreeRetro(
                "Égaliseur et effets",
                detail = if (reglages.egaliseurActif) "allumé" else "éteint",
                fleche = true
            ) { ouvrir(NiveauRetro.ParamEgaliseur) }
        )

        NiveauRetro.ParamEgaliseur -> buildList {
            val infos = actions.app.egaliseur.infos.value
            add(
                bascule(
                    "Activer l'égaliseur",
                    if (infos.disponible) "Insère un traitement dans la chaîne audio"
                    else "Cet appareil ne propose pas d'égaliseur",
                    reglages.egaliseurActif
                ) { actions.depot.modifierReglages { r -> r.copy(egaliseurActif = it) } }
            )
            if (infos.presets.isNotEmpty()) {
                add(
                    EntreeRetro(
                        "Préréglages",
                        detail = infos.presets.getOrNull(reglages.egaliseurPreset)
                            ?: "réglage manuel",
                        fleche = true
                    ) { ouvrir(NiveauRetro.ParamPresets) }
                )
            }
            if (infos.bandes.isNotEmpty()) {
                add(
                    EntreeRetro(
                        "Bandes de l'égaliseur",
                        valeur = "${infos.bandes.size}",
                        fleche = true
                    ) { ouvrir(NiveauRetro.ParamBandes) }
                )
            }
            add(nombre(NiveauRetro.ParamNombre(ReglageNumerique.BASS_BOOST), reglages, ouvrir))
            add(nombre(NiveauRetro.ParamNombre(ReglageNumerique.VIRTUALISEUR), reglages, ouvrir))
            add(nombre(NiveauRetro.ParamNombre(ReglageNumerique.GAIN), reglages, ouvrir))
        }

        NiveauRetro.ParamPresets -> actions.app.egaliseur.infos.value.presets
            .mapIndexed { index, nom ->
                EntreeRetro(
                    libelle = nom,
                    valeur = if (index == reglages.egaliseurPreset) "choisi" else null
                ) {
                    // Un préréglage remplace la courbe : les niveaux réglés à la main s'effacent.
                    actions.depot.modifierReglages {
                        it.copy(egaliseurPreset = index, egaliseurBandes = "")
                    }
                }
            }

        NiveauRetro.ParamBandes -> actions.app.egaliseur.infos.value.bandes
            .mapIndexed { index, frequence ->
                EntreeRetro(
                    libelle = libelleDeBande(frequence),
                    valeur = texteDeBande(niveauDeBande(reglages, index)),
                    fleche = true
                ) { ouvrir(NiveauRetro.ParamBande(index, frequence)) }
            }

        NiveauRetro.ParamApparence -> listOf(
            EntreeRetro(
                "Thème de l'interface moderne",
                detail = reglages.theme.libelle,
                fleche = true
            ) { ouvrir(NiveauRetro.ParamThemeClassique) },
            EntreeRetro(
                "Palette de l'interface moderne",
                detail = if (reglages.couleursDynamiques) "Couleurs dynamiques (Material You)"
                else "${reglages.palette.libelle} · ${reglages.palette.famille.libelle}",
                fleche = true
            ) { ouvrir(NiveauRetro.ParamPaletteClassique) },
            bascule(
                "Couleurs dynamiques (Material You)",
                "Les couleurs du fond d'écran l'emportent sur la palette",
                reglages.couleursDynamiques
            ) { actions.depot.modifierReglages { r -> r.copy(couleursDynamiques = it) } },
            bascule(
                "Pochette floutée en fond du lecteur",
                "Dans l'interface moderne",
                reglages.fondPochette
            ) { actions.depot.modifierReglages { r -> r.copy(fondPochette = it) } },
            bascule(
                "Afficher la qualité dans les listes",
                "Format, profondeur et fréquence sous chaque titre",
                reglages.afficherQualite
            ) { actions.depot.modifierReglages { r -> r.copy(afficherQualite = it) } },
            bascule(
                "Garder l'écran allumé sur le lecteur",
                "",
                reglages.ecranAllume
            ) { actions.depot.modifierReglages { r -> r.copy(ecranAllume = it) } },
            EntreeRetro(
                "Onglet ouvert au démarrage",
                detail = reglages.ongletDefaut.libelle,
                fleche = true
            ) { ouvrir(NiveauRetro.ParamOngletDefaut) }
        )

        NiveauRetro.ParamThemeClassique -> ThemeApplication.entries.map { candidat ->
            EntreeRetro(
                libelle = candidat.libelle,
                valeur = if (candidat == reglages.theme) "choisi" else null
            ) { actions.depot.modifierReglages { it.copy(theme = candidat) } }
        }

        NiveauRetro.ParamPaletteClassique -> PaletteApplication.entries.map { candidate ->
            EntreeRetro(
                libelle = candidate.libelle,
                detail = candidate.famille.libelle,
                valeur = if (candidate == reglages.palette) "choisie" else null
            ) {
                // Choisir une palette éteint Material You : sinon le choix resterait sans effet.
                actions.depot.modifierReglages {
                    it.copy(palette = candidate, couleursDynamiques = false)
                }
            }
        }

        NiveauRetro.ParamOngletDefaut -> Onglet.entries.map { candidat ->
            EntreeRetro(
                libelle = candidat.libelle,
                valeur = if (candidat == reglages.ongletDefaut) "choisi" else null
            ) { actions.depot.modifierReglages { it.copy(ongletDefaut = candidat) } }
        }

        NiveauRetro.ParamPlaylists -> listOf(
            // ⚠️ Le geste à faire après avoir autorisé l'écriture : les modifications faites
            // pendant que le dossier était en lecture seule ne sont pas perdues, elles n'ont
            // simplement jamais atteint les fichiers.
            EntreeRetro(
                "Retirer les doublons",
                detail = "Une playlist par fichier, et rien qui n'existe plus sur l'appareil",
                valeur = "${playlists.size}"
            ) { agir(ActionRetro.NettoyerPlaylists) },
            EntreeRetro(
                "Réécrire tous les fichiers .m3u",
                detail = "Remet chaque fichier d'origine au contenu de l'application",
                valeur = "${playlists.count { it.playlist.source != null }}"
            ) { agir(ActionRetro.ReecrireToutes) },
            EntreeRetro(
                "Exporter les playlists",
                detail = "Le format, puis ce qu'on exporte, puis le dossier",
                valeur = "${playlists.size}",
                fleche = true
            ) { ouvrir(NiveauRetro.ParamFormatExport) }
        )

        NiveauRetro.ParamFormatExport -> FormatExport.entries.map { format ->
            EntreeRetro(
                libelle = format.libelle,
                detail = format.description,
                fleche = true
            ) { ouvrir(NiveauRetro.ChoixExport(format)) }
        }

        is NiveauRetro.ChoixExport -> buildList {
            add(
                EntreeRetro(
                    libelle = "Exporter",
                    detail = if (choixPlaylists.isEmpty()) "Cochez au moins une playlist"
                    else "Vous choisirez ensuite le dossier de destination",
                    valeur = "${choixPlaylists.size} cochée${pluriel(choixPlaylists.size)}"
                ) {
                    agir(ActionRetro.ExporterPlaylists(niveau.format, choixPlaylists.toList()))
                }
            )
            add(
                EntreeRetro(
                    libelle = "Partager",
                    detail = if (choixPlaylists.isEmpty()) "Cochez au moins une playlist"
                    else "Par message, nuage, ou toute application qui accepte des fichiers"
                ) {
                    agir(ActionRetro.PartagerPlaylists(niveau.format, choixPlaylists.toList()))
                }
            )
            add(
                EntreeRetro(
                    libelle = "Tout cocher",
                    valeur = "${playlists.size}"
                ) { agir(ActionRetro.ToutCocher(playlists.map { it.playlist.id })) }
            )
            playlists.forEach { garnie ->
                add(
                    EntreeRetro(
                        libelle = garnie.playlist.nom,
                        detail = "${garnie.pistes.size} titre${pluriel(garnie.pistes.size)}",
                        valeur = if (garnie.playlist.id in choixPlaylists) "coché" else "—"
                    ) { agir(ActionRetro.BasculerChoix(garnie.playlist.id)) }
                )
            }
            if (playlists.isEmpty()) {
                add(EntreeRetro("Aucune playlist à exporter") {})
            }
        }

        NiveauRetro.ParamFormats -> listOf(
            EntreeRetro(
                "Lus",
                detail = "MP3, AAC/M4A, MP4, FLAC, ALAC, WAV, AIFF, Ogg Vorbis, Opus, " +
                    "Matroska (MKA/MKV), AC3, AMR, MIDI"
            ) {},
            EntreeRetro(
                "Jamais décodés",
                detail = "WMA et WMV : aucun appareil Android ne les lit. Ils apparaissent " +
                    "dans la bibliothèque, mais la lecture échouera."
            ) {},
            EntreeRetro(
                "Réseau",
                detail = "Aucun. 36Music n'ouvre aucune connexion et ne demande aucun compte."
            ) {}
        )

        // Les écrans à cadran n'ont pas de liste : la molette y déplace une valeur.
        is NiveauRetro.ParamNombre -> emptyList()
        is NiveauRetro.ParamBande -> emptyList()

        // Le menu du morceau en cours. Il s'ouvre par le bouton « i » de l'écran de lecture,
        // mais se parcourt à la molette comme tout le reste : un menu tactile au milieu d'une
        // interface à molette serait un corps étranger.
        NiveauRetro.ActionsPiste -> {
            val piste = etat.piste
            if (piste == null) emptyList() else listOf(
                EntreeRetro("Lire ensuite") {
                    actions.lecteur.jouerEnsuite(listOf(piste))
                    fermer()
                },
                EntreeRetro("Ajouter à la file d'attente") {
                    actions.lecteur.ajouterALaFile(listOf(piste))
                    fermer()
                },
                EntreeRetro("Aller à l'album", detail = piste.album, fleche = true) {
                    ouvrir(
                        NiveauRetro.Album(
                            "${piste.album}|${piste.artisteAlbum.ifBlank { piste.artisteAffiche }}"
                        )
                    )
                },
                EntreeRetro("Aller à l'artiste", detail = piste.artisteAffiche, fleche = true) {
                    ouvrir(NiveauRetro.Artiste(piste.artisteAffiche))
                },
                EntreeRetro("Informations sur le fichier", fleche = true) {
                    ouvrir(NiveauRetro.InfosPiste)
                }
            )
        }

        // La file telle qu'elle est réellement : ce qui va être joué, dans l'ordre, et d'où
        // qu'elle vienne. C'est aussi la réponse à « pourquoi ça enchaîne sur autre chose ? ».
        // ⚠️ Mémorisée sur la file **et** sur la position : sans cela, chaque cran de molette
        // reconstruisait des milliers de lignes, d'où la lenteur dans cet écran.
        NiveauRetro.FileAttente -> memo.valeur("entreesFile", etat.file, etat.index) {
            etat.file.mapIndexed { index, piste ->
                EntreeRetro(
                    libelle = piste.titre,
                    detail = piste.artisteAffiche,
                    valeur = when {
                        index == etat.index -> "en cours"
                        index < etat.index -> "écouté"
                        else -> formaterDuree(piste.duree)
                    },
                    rang = piste.rangQualite,
                    orpheline = piste.uri !in enPlaylist
                ) { actions.lecteur.allerALIndex(index) }
            }
        }

        // Les réglages propres à cette interface — ceux qui n'ont de sens que la molette
        // en main.
        NiveauRetro.ReglagesRetro -> listOf(
            EntreeRetro(
                "Configurations enregistrées",
                detail = "Mettre de côté ou rappeler cette apparence",
                valeur = "${reglages.configurations.count { it != null }}/$NOMBRE_CONFIGURATIONS",
                fleche = true
            ) { ouvrir(NiveauRetro.ConfigurationsRetro) },
            EntreeRetro(
                "Fond du lecteur",
                detail = FondLecteur.entries.getOrElse(reglages.fondLecteurRetro) {
                    FondLecteur.DEFAUT
                }.libelle,
                fleche = true
            ) { ouvrir(NiveauRetro.FondLecteurRetro) },
            EntreeRetro(
                "Style molette",
                detail = StyleMolette.entries.getOrElse(reglages.styleMolette) {
                    StyleMolette.DEFAUT
                }.libelle,
                fleche = true
            ) { ouvrir(NiveauRetro.StyleMoletteRetro) },
            EntreeRetro(
                "Couleur molette",
                detail = COULEURS_MOLETTE.getOrNull(reglages.couleurMolette)?.nom,
                fleche = true
            ) { ouvrir(NiveauRetro.CouleurMoletteRetro) },
            EntreeRetro(
                "Effet molette",
                detail = EffetMolette.entries.getOrElse(reglages.effetMolette) {
                    EffetMolette.AUCUN
                }.libelle,
                fleche = true
            ) { ouvrir(NiveauRetro.EffetMoletteRetro) },
            EntreeRetro(
                "Thème",
                detail = ThemeApplication.entries.getOrElse(reglages.themeRetro) {
                    ThemeApplication.SOMBRE
                }.libelle,
                fleche = true
            ) { ouvrir(NiveauRetro.ThemeRetro) },
            EntreeRetro(
                "Palette de couleurs",
                detail = ambiance.libelle,
                valeur = nomDeTeinte(reglages.teinteRetro),
                fleche = true
            ) { ouvrir(NiveauRetro.PaletteRetro) },
            EntreeRetro(
                "Style barre de progression",
                detail = StyleBarre.entries.getOrElse(reglages.styleBarre) {
                    StyleBarre.CLASSIQUE
                }.libelle,
                fleche = true
            ) { ouvrir(NiveauRetro.StyleBarreRetro) },
            EntreeRetro(
                "Affichage liseré sur le lecteur",
                detail = "Le trait de couleur sur le cadre, la molette et les boutons",
                valeur = if (reglages.lisereLecteur) "affiché" else "masqué"
            ) { agir(ActionRetro.BasculerLisere(!reglages.lisereLecteur)) },
            EntreeRetro("À propos", fleche = true) { ouvrir(NiveauRetro.AProposRetro) }
        )

        NiveauRetro.StyleBarreRetro -> StyleBarre.entries.map { candidat ->
            EntreeRetro(
                libelle = candidat.libelle,
                detail = candidat.description,
                valeur = if (candidat.ordinal == reglages.styleBarre) "choisi" else null
            ) { agir(ActionRetro.ChoisirStyleBarre(candidat.ordinal)) }
        }

        // ⚠️ Ces deux-là ne valent que pour la molette : l'interface classique garde les siens,
        // réglés ailleurs. Ce qui va aux listes en plein jour dessert souvent la roue, regardée
        // presque toujours dans le noir.
        NiveauRetro.ThemeRetro -> ThemeApplication.entries.map { candidat ->
            EntreeRetro(
                libelle = candidat.libelle,
                valeur = if (candidat.ordinal == reglages.themeRetro) "choisi" else null
            ) { agir(ActionRetro.ChoisirThemeRetro(candidat.ordinal)) }
        }

        // ⚠️ Trois étages : la catégorie, l'ambiance, puis la teinte à la molette. Vingt-trois
        // ambiances dans une seule liste se ressembleraient toutes ; rangées par caractère
        // général, « Rétro 80s » se trouve sans lire les vingt-deux autres.
        NiveauRetro.PaletteRetro -> CategoriePalette.entries.map { categorie ->
            EntreeRetro(
                libelle = categorie.libelle,
                detail = categorie.description,
                valeur = if (categorie == ambiance.categorie) "en cours" else null,
                fleche = true
            ) { ouvrir(NiveauRetro.CategoriePaletteRetro(categorie)) }
        }

        is NiveauRetro.CategoriePaletteRetro ->
            ThemeCouleur.parCategorie(niveau.categorie).map { candidate ->
                EntreeRetro(
                    libelle = candidate.libelle,
                    detail = candidate.description,
                    valeur = if (candidate == ambiance) nomDeTeinte(reglages.teinteRetro) else null,
                    fleche = true
                ) { ouvrir(NiveauRetro.TeintePaletteRetro(candidate)) }
            }

        // Pas de liste ici : l'écran est une barre de couleurs, et la molette y déplace un
        // curseur. Voir `EcranTeinteRetro`.
        is NiveauRetro.TeintePaletteRetro -> emptyList()

        NiveauRetro.StyleMoletteRetro -> StyleMolette.entries.map { candidat ->
            EntreeRetro(
                libelle = candidat.libelle,
                detail = candidat.description,
                valeur = if (candidat.ordinal == reglages.styleMolette) "choisi" else null
            ) { agir(ActionRetro.ChoisirStyleMolette(candidat)) }
        }

        NiveauRetro.CouleurMoletteRetro -> COULEURS_MOLETTE.mapIndexed { index, couleur ->
            EntreeRetro(
                libelle = couleur.nom,
                detail = if (index == 0) "L'aspect actuel, qui suit le thème" else null,
                valeur = if (index == reglages.couleurMolette) "choisie" else null
            ) { agir(ActionRetro.ChoisirCouleurMolette(index)) }
        }

        NiveauRetro.EffetMoletteRetro -> EffetMolette.entries.map { candidat ->
            EntreeRetro(
                libelle = candidat.libelle,
                detail = candidat.description,
                valeur = if (candidat.ordinal == reglages.effetMolette) "choisi" else null
            ) { agir(ActionRetro.ChoisirEffetMolette(candidat)) }
        }

        // ⚠️ Cette section manquait ici : l'utilisateur la cherchait dans les réglages de la
        // molette, où elle n'existait que dans l'interface classique.
        NiveauRetro.AProposRetro -> listOf(
            EntreeRetro("Version", valeur = actions.app.nomVersion) {},
            EntreeRetro("Build", valeur = "${actions.app.codeVersion}") {},
            EntreeRetro("Auteur", detail = "36txXnxTrGXgSH") {},
            EntreeRetro("Nouveautés de cette version", fleche = true) {
                ouvrir(NiveauRetro.NouveautesRetro)
            },
            EntreeRetro("Internet", detail = "aucune connexion : l'application n'a aucune permission réseau") {},
            EntreeRetro("Titres", valeur = "${pistes.size}") {}
        )

        NiveauRetro.NouveautesRetro -> Nouveautes.journal
            .firstOrNull { it.code == actions.app.codeVersion }
            ?.lignes
            ?.map { ligne -> EntreeRetro(ligne.genre.libelle, detail = ligne.texte) {} }
            ?: listOf(EntreeRetro("Aucune note pour cette version") {})

        // ⚠️ N'agit que sur l'arrière-plan **derrière la pochette**, jamais sur le reste de
        // l'écran : le texte doit rester lisible quelle que soit l'image.
        NiveauRetro.FondLecteurRetro -> buildList {
            FondLecteur.entries.forEach { fond ->
                add(
                    EntreeRetro(
                        libelle = fond.libelle,
                        detail = fond.description,
                        valeur = if (fond.ordinal == reglages.fondLecteurRetro) "choisi" else null
                    ) { agir(ActionRetro.ChoisirFond(fond)) }
                )
            }
            if (reglages.fondLecteurRetro == FondLecteur.COULEUR.ordinal) {
                COULEURS_FOND.forEachIndexed { index, couleur ->
                    add(
                        EntreeRetro(
                            libelle = couleur.nom,
                            valeur = if (index == reglages.couleurFondRetro) "choisie" else null
                        ) { agir(ActionRetro.ChoisirCouleurFond(index)) }
                    )
                }
            }
        }

        NiveauRetro.SansPlaylist -> {
            val liste = memo.valeur("sansPlaylist", pistes, enPlaylist) {
                pistes.filterNot { it.uri in enPlaylist }.trier(Tri.ALPHABETIQUE, true)
            }
            memo.valeur("entreesSansPlaylist", liste) {
                entreesDePistes(liste, enPlaylist) { index -> jouer(liste, index, false) }
            }
        }

        // Le choix des playlists, à la molette : on coche, puis on valide. Plusieurs listes d'un
        // coup — ranger un morceau dans trois playlists ne doit pas demander trois fois le même
        // chemin.
        is NiveauRetro.ChoixPlaylist -> buildList {
            add(
                EntreeRetro(
                    libelle = "Nouvelle playlist",
                    detail = "Vous choisirez le dossier et le nom du fichier"
                ) { agir(ActionRetro.NouvellePlaylist(niveau.uris)) }
            )
            add(
                EntreeRetro(
                    libelle = "Valider",
                    detail = if (choixPlaylists.isEmpty()) "Cochez au moins une playlist" else null,
                    valeur = "${choixPlaylists.size} cochée${pluriel(choixPlaylists.size)}"
                ) { agir(ActionRetro.ValiderChoix(niveau.uris, choixPlaylists)) }
            )
            playlists.forEach { garnie ->
                val deja = garnie.pistes.count { it.uri in niveau.uris }
                add(
                    EntreeRetro(
                        libelle = garnie.playlist.nom,
                        detail = when {
                            deja > 0 && deja == niveau.uris.size -> "déjà dedans"
                            deja > 0 -> "$deja déjà dedans"
                            else -> "${garnie.pistes.size} titre${pluriel(garnie.pistes.size)}"
                        },
                        valeur = if (garnie.playlist.id in choixPlaylists) "coché" else "—"
                    ) { agir(ActionRetro.BasculerChoix(garnie.playlist.id)) }
                )
            }
            if (playlists.isEmpty()) {
                add(
                    EntreeRetro(
                        "Aucune playlist",
                        detail = "Créez-en une depuis l'autre interface"
                    ) {}
                )
            }
        }

        // Où ce titre est rangé — et de quelles listes on peut l'en retirer.
        is NiveauRetro.Appartenance -> {
            val siennes = playlists.filter { g -> g.pistes.any { it.uri == niveau.uri } }
            if (siennes.isEmpty()) {
                listOf(
                    EntreeRetro(
                        "N'appartient à aucune playlist",
                        detail = "Utilisez « Ajouter à une playlist »"
                    ) {}
                )
            } else buildList {
                siennes.forEach { garnie ->
                    add(
                        EntreeRetro(
                            libelle = garnie.playlist.nom,
                            detail = "Appuyer pour l'en retirer",
                            valeur = "${garnie.pistes.size}"
                        ) {
                            ouvrir(
                                NiveauRetro.Confirmation(
                                    DemandeAction(
                                        titre = "Retirer de « ${garnie.playlist.nom} » ?",
                                        explication = "Le fichier reste sur la carte et dans la " +
                                            "bibliothèque : seule son appartenance à cette " +
                                            "playlist disparaît.",
                                        libelleConfirmation = "Retirer de la playlist",
                                        cible = CibleAction.Titre(niveau.uri, garnie.playlist.nom),
                                        genre = GenreAction.RETIRER_DE_PLAYLISTS,
                                        playlists = listOf(garnie.playlist.id)
                                    )
                                )
                            )
                        }
                    )
                }
                if (siennes.size > 1) {
                    add(
                        EntreeRetro("Retirer de toutes", valeur = "${siennes.size}") {
                            ouvrir(
                                NiveauRetro.Confirmation(
                                    DemandeAction(
                                        titre = "Retirer des ${siennes.size} playlists ?",
                                        explication = "Le fichier reste sur la carte et dans la " +
                                            "bibliothèque.",
                                        libelleConfirmation = "Retirer de toutes",
                                        cible = CibleAction.Titre(niveau.uri, "ce titre"),
                                        genre = GenreAction.RETIRER_DE_PLAYLISTS,
                                        playlists = siennes.map { it.playlist.id }
                                    )
                                )
                            )
                        }
                    )
                }
            }
        }

        is NiveauRetro.ActionsLongues ->
            actionsPour(niveau.cible, ouvrir, agir, jouer, pistes, playlists)

        // ⚠️ Deux lignes, et la dangereuse n'est **pas** la première : la molette se pose sur la
        // ligne du haut, et une confirmation qui commence par « effacer » invite à l'accident.
        is NiveauRetro.Confirmation -> listOf(
            EntreeRetro("Annuler") { fermer() },
            EntreeRetro(
                niveau.demande.libelleConfirmation,
                detail = niveau.demande.explication
            ) { agir(ActionRetro.Executer(niveau.demande)) }
        )

        NiveauRetro.InfosPiste ->
            etat.piste?.let { infosDe(it, decodeur, com.music36.app.lecture.SortieAudio.source.value) }
                .orEmpty()

        NiveauRetro.Lecture -> emptyList()
    }
}

/**
 * Ce qu'on peut faire de l'élément sur lequel la molette a été maintenue.
 *
 * ⚠️ L'effacement est toujours en **dernier**, jamais en tête : la molette se pose sur la
 * première ligne, et une liste qui commence par « effacer » invite à l'accident.
 */
private fun actionsPour(
    cible: CibleAction,
    ouvrir: (NiveauRetro) -> Unit,
    agir: (ActionRetro) -> Unit,
    jouer: (List<Piste>, Int, Boolean) -> Unit,
    bibliotheque: List<Piste>,
    playlists: List<PlaylistGarnie>
): List<EntreeRetro> = buildList {
    val uris = when (cible) {
        is CibleAction.Titre -> listOf(cible.uri)
        is CibleAction.Groupe -> cible.uris
        is CibleAction.Playlist, is CibleAction.Racine, is CibleAction.Configuration ->
            emptyList()
    }
    val visees = if (uris.isEmpty()) emptyList() else {
        val ensemble = uris.toHashSet()
        bibliotheque.filter { it.uri in ensemble }
    }

    if (visees.isNotEmpty()) {
        add(EntreeRetro("Tout lire", valeur = "${visees.size}") { jouer(visees, 0, false) })
        add(EntreeRetro("Lire en aléatoire", valeur = "${visees.size}") { jouer(visees, 0, true) })
        add(
            EntreeRetro("Ajouter à une playlist", fleche = true) {
                ouvrir(NiveauRetro.ChoixPlaylist(uris))
            }
        )
    }
    if (cible is CibleAction.Titre) {
        add(
            EntreeRetro("Dans quelles playlists", fleche = true) {
                ouvrir(NiveauRetro.Appartenance(cible.uri))
            }
        )
        // Vu depuis une playlist : la retirer de celle-ci, et seulement cette apparition-là.
        val idPlaylist = cible.playlistId
        if (idPlaylist != null) {
            val nom = playlists.firstOrNull { it.playlist.id == idPlaylist }?.playlist?.nom
                ?: "cette playlist"
            val fois = playlists.firstOrNull { it.playlist.id == idPlaylist }
                ?.pistes?.count { it.uri == cible.uri } ?: 1
            add(
                EntreeRetro(
                    "Retirer de cette playlist",
                    detail = if (fois > 1) "Cette ligne seulement — le titre y est $fois fois"
                    else "Le fichier reste sur l'appareil"
                ) {
                    ouvrir(
                        NiveauRetro.Confirmation(
                            DemandeAction(
                                titre = "Retirer de « $nom » ?",
                                explication = if (fois > 1) {
                                    "Cette apparition de « ${cible.libelle} » quitte la " +
                                        "playlist. Les ${fois - 1} autre${pluriel(fois - 1)} " +
                                        "reste${if (fois - 1 > 1) "nt" else ""}."
                                } else {
                                    "« ${cible.libelle} » quitte la playlist. Le fichier, lui, " +
                                        "reste sur l'appareil et dans la bibliothèque."
                                },
                                libelleConfirmation = "Retirer",
                                cible = cible,
                                genre = GenreAction.RETIRER_OCCURRENCE,
                                playlists = listOf(idPlaylist)
                            )
                        )
                    )
                }
            )
        }
    }
    if (visees.isNotEmpty()) {
        add(
            EntreeRetro("Déplacer vers un dossier", detail = "même support uniquement") {
                agir(ActionRetro.Deplacer(uris))
            }
        )
    }

    if (cible is CibleAction.Configuration) {
        add(
            EntreeRetro("Remplacer par l'apparence actuelle") {
                ouvrir(NiveauRetro.Confirmation(remplacementDe(cible)))
            }
        )
        add(EntreeRetro("Supprimer") { ouvrir(NiveauRetro.Confirmation(suppressionDe(cible))) })
    }

    if (cible is CibleAction.Racine) {
        add(
            EntreeRetro("Retirer ce dossier", detail = "aucun fichier n'est effacé") {
                ouvrir(NiveauRetro.Confirmation(retraitDeRacine(cible)))
            }
        )
    }

    if (cible is CibleAction.Playlist) {
        // ⚠️ D'où vient cette playlist : sans cette ligne, deux playlists de même nom rangées
        // dans deux dossiers sont impossibles à distinguer, et l'on supprime la mauvaise.
        val source = playlists.firstOrNull { it.playlist.id == cible.id }?.playlist?.source
        add(
            EntreeRetro(
                "Fichier",
                detail = if (source == null) {
                    "Créée dans l'application : aucun fichier sur l'appareil"
                } else {
                    listOfNotNull(dossierDeUri(source), nomDeFichierDeUri(source))
                        .joinToString(" / ")
                }
            ) {}
        )
        if (source != null) {
            add(
                EntreeRetro(
                    "Réécrire le fichier",
                    detail = "Remet le .m3u d'origine au contenu de l'application"
                ) { agir(ActionRetro.ReecrirePlaylist(cible.id)) }
            )
        }
        add(
            EntreeRetro("Supprimer la playlist") {
                ouvrir(
                    NiveauRetro.Confirmation(
                        DemandeAction(
                            titre = "Supprimer « ${cible.libelle} » ?",
                            explication = "La playlist disparaît. Les fichiers, eux, restent sur " +
                                "la carte et dans la bibliothèque.",
                            libelleConfirmation = "Supprimer la playlist",
                            cible = cible,
                            genre = GenreAction.SUPPRIMER_PLAYLIST
                        )
                    )
                )
            }
        )
    } else if (visees.isNotEmpty()) {
        add(
            EntreeRetro(
                if (visees.size == 1) "Effacer le fichier"
                else "Effacer les ${visees.size} fichiers",
                detail = "définitif"
            ) {
                ouvrir(
                    NiveauRetro.Confirmation(
                        DemandeAction(
                            titre = if (visees.size == 1) "Effacer ce fichier ?"
                            else "Effacer ${visees.size} fichiers ?",
                            explication = "Ils seront effacés de la carte. Il n'y a ni corbeille " +
                                "ni annulation : c'est définitif.",
                            libelleConfirmation = if (visees.size == 1) "Effacer définitivement"
                            else "Effacer les ${visees.size} définitivement",
                            cible = cible,
                            genre = GenreAction.EFFACER_FICHIERS
                        )
                    )
                )
            }
        )
    }
}

/**
 * Le nom courant d'une teinte, pour l'afficher au bout d'une ligne.
 *
 * Un nombre de degrés ne dit rien à personne : « 205° » est exact et illisible, « Bleu » se
 * comprend d'un coup d'œil. Les bornes suivent les noms usuels du cercle chromatique.
 */
internal fun nomDeTeinte(teinte: Float): String {
    val t = ((teinte % 360f) + 360f) % 360f
    return when {
        t < 15f -> "Rouge"
        t < 40f -> "Orange"
        t < 65f -> "Jaune"
        t < 95f -> "Vert clair"
        t < 150f -> "Vert"
        t < 175f -> "Émeraude"
        t < 195f -> "Turquoise"
        t < 225f -> "Bleu"
        t < 255f -> "Indigo"
        t < 285f -> "Violet"
        t < 315f -> "Magenta"
        t < 345f -> "Rose"
        else -> "Rouge"
    }
}

/**
 * Une ligne qui bascule un oui/non d'un appui au centre.
 *
 * ⚠️ Pas de case à cocher : dans cette interface, rien ne se touche. L'état se lit **au bout de
 * la ligne**, comme partout ailleurs dans les menus de la molette.
 */
internal fun bascule(
    libelle: String,
    detail: String,
    actif: Boolean,
    changer: (Boolean) -> Unit
): EntreeRetro = EntreeRetro(
    libelle = libelle,
    detail = detail.ifBlank { null },
    valeur = if (actif) "oui" else "non"
) { changer(!actif) }

/** Une ligne qui ouvre un cadran : la valeur en cours se lit au bout de la ligne. */
internal fun nombre(
    niveau: NiveauRetro.ParamNombre,
    reglages: EtatReglages,
    ouvrir: (NiveauRetro) -> Unit
): EntreeRetro = EntreeRetro(
    libelle = niveau.reglage.libelle,
    detail = niveau.reglage.explication,
    valeur = niveau.reglage.texte(niveau.reglage.valeurDe(reglages)),
    fleche = true
) { ouvrir(niveau) }

/**
 * La confirmation du retrait d'un dossier surveillé.
 *
 * ⚠️ Le texte insiste sur ce qui **ne** se passe pas : aucun fichier n'est effacé. Sans cela,
 * « retirer » se confond avec « supprimer », et l'on n'ose plus toucher à la liste.
 */
internal fun retraitDeRacine(cible: CibleAction.Racine): DemandeAction = DemandeAction(
    titre = "Retirer ce dossier ?",
    explication = "Les titres de « ${cible.libelle} » quittent la bibliothèque. " +
        "Aucun fichier n'est effacé du téléphone.",
    libelleConfirmation = "Retirer le dossier",
    cible = cible,
    genre = GenreAction.RETIRER_RACINE
)

/**
 * Ce qu'une configuration donne à lire en une ligne : l'ambiance, sa teinte, la molette.
 *
 * Pas de nom à saisir : écrire à la molette serait une punition. Le résumé suffit à reconnaître
 * « celle en bleu avec les paillettes ».
 */
internal fun resumeDe(config: ConfigurationApparence): String = listOfNotNull(
    ThemeCouleur.entries.getOrNull(config.themeCouleurRetro)?.libelle,
    nomDeTeinte(config.teinteRetro),
    StyleMolette.entries.getOrNull(config.styleMolette)?.libelle,
    EffetMolette.entries.getOrNull(config.effetMolette)
        ?.takeIf { it != EffetMolette.AUCUN }?.libelle
).joinToString(" · ")

/** La confirmation du remplacement d'un emplacement par l'apparence en cours. */
internal fun remplacementDe(cible: CibleAction.Configuration): DemandeAction = DemandeAction(
    titre = "Remplacer la ${cible.libelle.lowercase()} ?",
    explication = "Elle prend l'apparence actuelle de la molette. Ce qu'elle contenait est perdu.",
    libelleConfirmation = "Remplacer",
    cible = cible,
    genre = GenreAction.REMPLACER_CONFIGURATION
)

/** La confirmation de la suppression d'un emplacement. */
internal fun suppressionDe(cible: CibleAction.Configuration): DemandeAction = DemandeAction(
    titre = "Supprimer la ${cible.libelle.lowercase()} ?",
    explication = "L'emplacement redevient libre. L'apparence actuelle de la molette, elle, ne " +
        "change pas.",
    libelleConfirmation = "Supprimer",
    cible = cible,
    genre = GenreAction.SUPPRIMER_CONFIGURATION
)

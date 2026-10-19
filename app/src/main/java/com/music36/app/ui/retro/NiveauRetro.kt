package com.music36.app.ui.retro

import com.music36.app.data.normaliser
import com.music36.app.data.FondLecteur
import com.music36.app.data.StyleMolette
import com.music36.app.data.EffetMolette
import com.music36.app.data.CategoriePalette
import com.music36.app.data.ThemeCouleur
import com.music36.app.data.PAS_TEINTE
import com.music36.app.data.ReglageNumerique
import com.music36.app.data.FormatExport
import com.music36.app.data.libelleDeBande

/**
 * Les niveaux de la pile de menus, et le repère alphabétique qui les accompagne.
 *
 * Le niveau est une donnée, pas un écran : c'est lui qui décide du titre, du contenu de la liste
 * et de la pertinence du repère. Les trois fonctions de repère sont pures, donc mesurées.
 */
/** Un niveau du menu. La pile de ces niveaux tient lieu de navigation. */
sealed interface NiveauRetro {

    /**
     * La clé sous laquelle on retient la ligne sélectionnée.
     *
     * ⚠️ Pas `toString()` : un niveau qui porte des milliers d'URI produirait une clé de plusieurs
     * centaines de kilo-octets, et **changerait à chaque coche**, ramenant la molette en haut de
     * la liste à chaque geste.
     */
    val cleSelection: String get() = toString()

    data object Racine : NiveauRetro
    data object Musique : NiveauRetro
    data object Sons : NiveauRetro
    data object Artistes : NiveauRetro
    data class Artiste(val cle: String) : NiveauRetro
    data object Albums : NiveauRetro
    data class Album(val cle: String) : NiveauRetro
    data object CoverFlow : NiveauRetro
    data class CoverFlowAlbum(val cle: String) : NiveauRetro
    data object Genres : NiveauRetro
    data class Genre(val cle: String) : NiveauRetro
    data object Playlists : NiveauRetro
    data class Playlist(val id: Long) : NiveauRetro
    data object Dossiers : NiveauRetro
    data class Dossier(val chemin: String) : NiveauRetro
    data object Favoris : NiveauRetro
    data object PlusEcoutes : NiveauRetro

    /** Les titres rangés dans aucune playlist — ceux qu'on a oubliés. */
    data object SansPlaylist : NiveauRetro
    data object Reglages : NiveauRetro

    /**
     * Les réglages de l'application entière, à la molette.
     *
     * ⚠️ Ce ne sont **pas** les réglages de la molette (`ReglagesRetro`) : ce sont ceux que
     * l'interface moderne présentait dans son écran « Paramètres ». Ils vivent ici pour que la
     * vue moderne ne serve plus qu'à regarder — listes et lecteur plein écran.
     */
    data object ParametresApp : NiveauRetro

    /** Les trois emplacements où mettre de côté l'apparence de la molette. */
    data object ConfigurationsRetro : NiveauRetro

    /** Ce qu'on peut faire d'un emplacement déjà rempli : rappeler, remplacer, supprimer. */
    data class ConfigurationRetro(val emplacement: Int) : NiveauRetro
    data object ParamDossiers : NiveauRetro
    data object ParamBibliotheque : NiveauRetro
    data object ParamLecture : NiveauRetro
    data object ParamSon : NiveauRetro
    data object ParamApparence : NiveauRetro
    data object ParamPlaylists : NiveauRetro
    data object ParamEgaliseur : NiveauRetro
    data object ParamPresets : NiveauRetro
    data object ParamBandes : NiveauRetro
    data object ParamThemeClassique : NiveauRetro
    data object ParamPaletteClassique : NiveauRetro
    data object ParamOngletDefaut : NiveauRetro
    data object ParamFormatExport : NiveauRetro

    /**
     * Les playlists à exporter, cochées une à une.
     *
     * ⚠️ La sélection existe parce qu'elle existait dans l'interface moderne : exporter
     * seize playlists quand on n'en voulait qu'une n'est pas la même chose.
     */
    data class ChoixExport(val format: FormatExport) : NiveauRetro {
        override val cleSelection: String get() = "ChoixExport"
    }
    data object ParamFormats : NiveauRetro

    /** Un réglage continu : la molette y déplace une valeur entre deux bornes. */
    data class ParamNombre(val reglage: ReglageNumerique) : NiveauRetro

    /** Une bande de l'égaliseur, réglée de la même manière. */
    data class ParamBande(val index: Int, val frequence: Int) : NiveauRetro

    /** Les réglages propres à l'interface molette. */
    data object ReglagesRetro : NiveauRetro
    data object FondLecteurRetro : NiveauRetro
    data object StyleMoletteRetro : NiveauRetro
    data object CouleurMoletteRetro : NiveauRetro
    data object EffetMoletteRetro : NiveauRetro
    data object StyleBarreRetro : NiveauRetro
    data object ThemeRetro : NiveauRetro

    /** Les six ambiances générales : le premier étage du choix des couleurs. */
    data object PaletteRetro : NiveauRetro

    /** Les ambiances d'une catégorie. */
    data class CategoriePaletteRetro(val categorie: CategoriePalette) : NiveauRetro

    /**
     * La barre de couleurs d'une ambiance.
     *
     * ⚠️ Ce niveau n'a **pas** de liste : l'écran se dessine seul et la molette y déplace un
     * curseur. Le choix ne s'enregistre qu'au bouton central — MENU laisse les couleurs
     * d'avant, ce qui permet d'essayer sans rien casser.
     */
    data class TeintePaletteRetro(val theme: ThemeCouleur) : NiveauRetro
    data object AProposRetro : NiveauRetro
    data object NouveautesRetro : NiveauRetro
    data object Lecture : NiveauRetro
    data object FileAttente : NiveauRetro

    /** Ce qu'on peut faire du morceau en cours, sans quitter l'écran de lecture. */
    data object ActionsPiste : NiveauRetro
    data object InfosPiste : NiveauRetro

    /**
     * Le choix des playlists où ranger des titres — à la molette, pas dans une boîte de dialogue.
     *
     * Plusieurs playlists peuvent être cochées avant de valider : ranger un morceau dans trois
     * listes ne doit pas demander de refaire trois fois le même chemin.
     */
    data class ChoixPlaylist(val uris: List<String>) : NiveauRetro {
        override val cleSelection: String get() = "ChoixPlaylist"
    }

    /** Où est rangé ce titre, et de quelles playlists on peut le retirer. */
    data class Appartenance(val uri: String) : NiveauRetro

    /** Le menu d'un appui long : ce qu'on peut faire de l'élément sélectionné. */
    data class ActionsLongues(val cible: CibleAction) : NiveauRetro {
        override val cleSelection: String get() = "ActionsLongues/${cible.libelle}"
    }

    /** La confirmation d'un geste irréversible. */
    data class Confirmation(val demande: DemandeAction) : NiveauRetro {
        override val cleSelection: String get() = "Confirmation/${demande.titre}"
    }
}

/** Ce sur quoi porte un appui long : un titre, une playlist, un artiste, un album, un dossier. */
sealed interface CibleAction {
    val libelle: String

    data class Titre(
        val uri: String,
        override val libelle: String,
        /** La playlist d'où l'on regarde ce titre, quand c'est le cas. */
        val playlistId: Long? = null,
        /** Laquelle de ses apparitions dans cette playlist (à partir de zéro). */
        val occurrence: Int = 0
    ) : CibleAction
    data class Playlist(val id: Long, override val libelle: String) : CibleAction

    /** Un emplacement de configuration d'apparence (numéroté à partir de zéro). */
    data class Configuration(val emplacement: Int, override val libelle: String) : CibleAction

    /** Un dossier surveillé, qu'on retire de la bibliothèque sans toucher aux fichiers. */
    data class Racine(val uri: String, override val libelle: String) : CibleAction
    data class Groupe(
        val uris: List<String>,
        override val libelle: String,
        val genre: String
    ) : CibleAction
}

/**
 * Ce que la molette demande à l'écran de faire.
 *
 * `entreesPour` reste sans état : elle ne fait rien elle-même, elle décrit le geste. C'est
 * `EcranRetro` qui l'exécute, avec la portée et les dialogues dont il dispose.
 */
sealed interface ActionRetro {
    /** Coche ou décoche une playlist dans l'écran de choix. */
    data class BasculerChoix(val id: Long) : ActionRetro

    /** Coche tout : l'export de seize playlists ne doit pas demander seize crans. */
    data class ToutCocher(val ids: List<Long>) : ActionRetro

    /** Valide l'écran de choix : range les titres dans les playlists cochées. */
    data class ValiderChoix(val uris: List<String>, val ids: Set<Long>) : ActionRetro

    /** Exécute un geste déjà confirmé. */
    data class Executer(val demande: DemandeAction) : ActionRetro

    /** Demande à l'utilisateur le dossier de destination, puis déplace. */
    data class Deplacer(val uris: List<String>) : ActionRetro

    /** Change ce qui s'affiche derrière la pochette, sur l'écran de lecture. */
    data class ChoisirFond(val fond: FondLecteur) : ActionRetro

    /** Choisit la teinte, quand le fond est une couleur unie. */
    data class ChoisirCouleurFond(val index: Int) : ActionRetro

    /** Change l'allure de la molette. */
    data class ChoisirStyleMolette(val style: StyleMolette) : ActionRetro

    /** Change sa teinte. */
    data class ChoisirCouleurMolette(val index: Int) : ActionRetro

    /** Change ce qui suit le doigt. */
    data class ChoisirEffetMolette(val effet: EffetMolette) : ActionRetro

    /** Change l'allure de la barre de progression. */
    data class ChoisirStyleBarre(val ordinal: Int) : ActionRetro

    /** Affiche ou retire le liseré de couleur du lecteur. */
    data class BasculerLisere(val actif: Boolean) : ActionRetro

    /** Change le thème de la seule interface molette. */
    data class ChoisirThemeRetro(val ordinal: Int) : ActionRetro

    /** Retient l'ambiance et la teinte choisies à la barre de couleurs. */
    data class ChoisirAmbiance(val theme: ThemeCouleur, val teinte: Float) : ActionRetro

    /** Lance une analyse et annonce son bilan quand elle se termine. */
    data class Analyser(val complet: Boolean) : ActionRetro

    /** Ouvre le sélecteur de fichier pour importer une playlist `.m3u`. */
    data object ImporterM3u : ActionRetro

    /** Réécrit le fichier `.m3u` d'une playlist, à la demande. */
    data class ReecrirePlaylist(val id: Long) : ActionRetro

    /** Fait correspondre la liste des playlists aux fichiers présents sur l'appareil. */
    data object NettoyerPlaylists : ActionRetro

    /** Réécrit tous les fichiers `.m3u` des playlists qui en ont un. */
    data object ReecrireToutes : ActionRetro

    /** Demande où créer un `.m3u`, puis y range les titres visés. */
    data class NouvellePlaylist(val uris: List<String>) : ActionRetro

    /** Prépare les playlists choisies et ouvre le partage d'Android. */
    data class PartagerPlaylists(val format: FormatExport, val ids: List<Long>) : ActionRetro

    /** Demande le dossier de destination, puis exporte toutes les playlists. */
    data class ExporterPlaylists(val format: FormatExport, val ids: List<Long>) : ActionRetro

}

/** Un geste destructeur en attente de confirmation. */
data class DemandeAction(
    val titre: String,
    val explication: String,
    val libelleConfirmation: String,
    val cible: CibleAction,
    val genre: GenreAction,
    /** Les playlists concernées, quand le geste porte sur une appartenance. */
    val playlists: List<Long> = emptyList()
)

/** La nature d'un geste destructeur. */
enum class GenreAction {
    EFFACER_FICHIERS, SUPPRIMER_PLAYLIST, RETIRER_DE_PLAYLISTS, RETIRER_RACINE,
    REMPLACER_CONFIGURATION, SUPPRIMER_CONFIGURATION, RETIRER_OCCURRENCE
}
internal fun titreDe(niveau: NiveauRetro, bibliothequeVide: Boolean): String = when (niveau) {
    NiveauRetro.Racine -> if (bibliothequeVide) "36Music — aucun dossier" else "36Music"
    NiveauRetro.Musique -> "Musique"
    NiveauRetro.Sons -> "Chansons"
    NiveauRetro.Artistes -> "Artistes"
    is NiveauRetro.Artiste -> niveau.cle
    NiveauRetro.Albums -> "Albums"
    is NiveauRetro.Album -> niveau.cle.substringBefore('|')
    NiveauRetro.CoverFlow -> "Cover Flow"
    is NiveauRetro.CoverFlowAlbum -> niveau.cle.substringBefore('|')
    NiveauRetro.Genres -> "Genres"
    is NiveauRetro.Genre -> niveau.cle
    NiveauRetro.Playlists -> "Playlists"
    is NiveauRetro.Playlist -> "Playlist"
    NiveauRetro.Dossiers -> "Dossiers"
    is NiveauRetro.Dossier -> niveau.chemin.substringAfterLast('/')
    NiveauRetro.Favoris -> "Favoris"
    NiveauRetro.SansPlaylist -> "Sans playlist"
    NiveauRetro.PlusEcoutes -> "Les plus écoutés"
    NiveauRetro.Reglages -> "Réglages"
    NiveauRetro.ParametresApp -> "Paramètres de l'application"
    NiveauRetro.ConfigurationsRetro -> "Configurations enregistrées"
    is NiveauRetro.ConfigurationRetro -> "Configuration ${niveau.emplacement + 1}"
    NiveauRetro.ParamDossiers -> "Dossiers de musique"
    NiveauRetro.ParamBibliotheque -> "Bibliothèque"
    NiveauRetro.ParamLecture -> "Lecture"
    NiveauRetro.ParamSon -> "Qualité sonore"
    NiveauRetro.ParamApparence -> "Apparence"
    NiveauRetro.ParamPlaylists -> "Playlists"
    NiveauRetro.ParamEgaliseur -> "Égaliseur et effets"
    NiveauRetro.ParamPresets -> "Préréglages"
    NiveauRetro.ParamBandes -> "Bandes de l'égaliseur"
    NiveauRetro.ParamThemeClassique -> "Thème de l'interface moderne"
    NiveauRetro.ParamPaletteClassique -> "Palette de l'interface moderne"
    NiveauRetro.ParamOngletDefaut -> "Onglet ouvert au démarrage"
    NiveauRetro.ParamFormatExport -> "Format d'export"
    is NiveauRetro.ChoixExport -> "Exporter en ${niveau.format.libelle}"
    NiveauRetro.ParamFormats -> "Formats reconnus"
    is NiveauRetro.ParamNombre -> niveau.reglage.libelle
    is NiveauRetro.ParamBande -> libelleDeBande(niveau.frequence)
    NiveauRetro.ReglagesRetro -> "Réglages de la vue Rétro"
    NiveauRetro.FondLecteurRetro -> "Fond du lecteur"
    NiveauRetro.StyleMoletteRetro -> "Style molette"
    NiveauRetro.CouleurMoletteRetro -> "Couleur molette"
    NiveauRetro.EffetMoletteRetro -> "Effet molette"
    NiveauRetro.StyleBarreRetro -> "Style barre de progression"
    NiveauRetro.ThemeRetro -> "Thème"
    NiveauRetro.PaletteRetro -> "Palette de couleurs"
    is NiveauRetro.CategoriePaletteRetro -> niveau.categorie.libelle
    is NiveauRetro.TeintePaletteRetro -> niveau.theme.libelle
    NiveauRetro.AProposRetro -> "À propos"
    NiveauRetro.NouveautesRetro -> "Nouveautés"
    NiveauRetro.Lecture -> "Lecture en cours"
    NiveauRetro.FileAttente -> "File d'attente"
    NiveauRetro.ActionsPiste -> "Morceau en cours"
    NiveauRetro.InfosPiste -> "Informations"
    is NiveauRetro.ChoixPlaylist -> "Ajouter à une playlist"
    is NiveauRetro.Appartenance -> "Dans quelles playlists"
    is NiveauRetro.ActionsLongues -> niveau.cible.libelle
    is NiveauRetro.Confirmation -> niveau.demande.titre
}
/** En deçà, la liste tient à l'écran : le repère alphabétique n'apporterait rien. */
internal const val SEUIL_LETTRE = 15

/** Deux crans plus rapprochés que cela, c'est un défilement, pas un choix. */
internal const val DELAI_DEFILEMENT_RAPIDE = 260L
/**
 * Faut-il montrer la grande lettre ?
 *
 * Deux conditions, et la seconde compte autant que la première : sur un cran isolé, l'utilisateur
 * lit le libellé qu'il vient d'atteindre, et un carré posé au milieu de l'écran le masquerait.
 */
internal fun doitAfficherLaLettre(tailleListe: Int, ecartDepuisLeCranPrecedent: Long): Boolean =
    tailleListe >= SEUIL_LETTRE && ecartDepuisLeCranPrecedent < DELAI_DEFILEMENT_RAPIDE
/**
 * Ce niveau est-il rangé par ordre alphabétique ?
 *
 * ⚠️ C'est la condition d'existence du repère alphabétique : sur une liste rangée autrement, les
 * lettres partiraient dans tous les sens et désigneraient n'importe quoi. Un **album** garde
 * l'ordre du disque et une **playlist** l'ordre voulu par l'utilisateur ; ni l'un ni l'autre
 * n'affiche donc de lettre. Tout le reste est trié par nom, exprès pour que le repère ait un sens.
 */
internal fun estAlphabetique(niveau: NiveauRetro): Boolean = when (niveau) {
    NiveauRetro.Sons, NiveauRetro.Favoris, NiveauRetro.Artistes, NiveauRetro.Albums,
    NiveauRetro.Genres, NiveauRetro.Playlists, NiveauRetro.Dossiers -> true

    is NiveauRetro.Artiste, is NiveauRetro.Genre, is NiveauRetro.Dossier -> true

    // Album : ordre du disque. Playlist : ordre choisi. Les plus écoutés : ordre du classement.
    // Cover Flow : on reconnaît une pochette, pas une initiale.
    else -> false
}
/**
 * L'initiale d'un libellé, sans accent ni casse.
 *
 * « Éléphant » et « elephant » partagent la même lettre de repère ; tout ce qui ne commence pas
 * par une lettre est rangé sous « # », comme dans les index de bibliothèque.
 */
internal fun initialeDe(libelle: String): String {
    val lettre = normaliser(libelle).trimStart().firstOrNull() ?: return "#"
    return if (lettre in 'a'..'z') lettre.uppercase() else "#"
}

/**
 * L'index du premier titre de la lettre voisine, dans le sens [sens].
 *
 * C'est ce que fait le bouton ABC : au lieu d'avancer d'une ligne par cran, la molette saute d'un
 * groupe de lettres au suivant. Sur trois mille titres, atteindre le M demandait sinon des
 * centaines de crans.
 *
 * En arrière, on revient d'abord au **début du groupe courant** — le geste attendu quand on s'est
 * arrêté au milieu d'une lettre — et seulement ensuite au groupe précédent.
 */
internal fun sautAlphabetique(libelles: List<String>, depuis: Int, sens: Int): Int {
    if (libelles.isEmpty()) return 0
    val courant = depuis.coerceIn(0, libelles.lastIndex)
    val lettre = initialeDe(libelles[courant])

    if (sens > 0) {
        for (i in courant + 1..libelles.lastIndex) {
            if (initialeDe(libelles[i]) != lettre) return i
        }
        // Déjà dans le dernier groupe : on ne bouge pas plutôt que de revenir au début.
        return courant
    }

    var debutGroupe = courant
    while (debutGroupe > 0 && initialeDe(libelles[debutGroupe - 1]) == lettre) debutGroupe--
    if (debutGroupe < courant || debutGroupe == 0) return debutGroupe

    val precedente = initialeDe(libelles[debutGroupe - 1])
    var debut = debutGroupe - 1
    while (debut > 0 && initialeDe(libelles[debut - 1]) == precedente) debut--
    return debut
}

/**
 * La teinte atteinte après un cran de molette sur la barre de couleurs.
 *
 * ⚠️ Le cercle **boucle** : passé le rouge, on revient au rouge par l'autre bout. Une barre
 * bornée aux deux extrémités obligerait à revenir sur ses pas pour comparer deux couleurs
 * voisines qui se trouvent de part et d'autre du zéro.
 */
internal fun teinteSuivante(depart: Float, sens: Int): Float =
    (((depart + sens * PAS_TEINTE) % 360f) + 360f) % 360f

/**
 * Le niveau sur lequel ouvrir la molette au prochain affichage.
 *
 * ⚠️ Existe pour un seul geste : « Paramètres », dans l'interface moderne, ramène à la molette
 * **et** y ouvre les réglages. Sans cela, le bouton renverrait à la racine du menu et il
 * faudrait redescendre à la main jusqu'à ce qu'on venait justement de demander.
 *
 * La demande se consomme une fois : une rotation d'écran ne doit pas rouvrir les réglages.
 */
object DepartRetro {
    var demande: NiveauRetro? = null

    /**
     * Vrai quand la demande vient de l'interface moderne : les réglages proposent alors d'y
     * retourner en tête de liste. C'est de là qu'on venait, c'est là qu'on s'attend à revenir.
     */
    var depuisModerne: Boolean = false

    fun consommer(): Depart {
        val voulu = Depart(demande ?: NiveauRetro.Racine, depuisModerne)
        demande = null
        depuisModerne = false
        return voulu
    }
}

/** Où ouvrir la molette, et si l'on vient de l'interface moderne. */
data class Depart(val niveau: NiveauRetro, val depuisModerne: Boolean = false)

/**
 * La pile de menus à reconstruire pour ouvrir la molette sur [depart].
 *
 * ⚠️ **Jamais le seul niveau demandé.** Ouvrir directement sur « Paramètres de l'application »
 * avec une pile d'un seul niveau enfermait l'utilisateur : MENU et le bouton retour ne remontent
 * que s'il reste un niveau au-dessus, et il n'y en avait aucun (défaut de la 4.4). Les parents
 * sont donc remis en place, exactement comme si l'on était descendu à la molette.
 */
internal fun pileDeDepart(depart: NiveauRetro): List<NiveauRetro> = when (depart) {
    NiveauRetro.Racine -> listOf(NiveauRetro.Racine)
    NiveauRetro.ParametresApp ->
        listOf(NiveauRetro.Racine, NiveauRetro.Reglages, NiveauRetro.ParametresApp)
    NiveauRetro.Reglages -> listOf(NiveauRetro.Racine, NiveauRetro.Reglages)
    else -> listOf(NiveauRetro.Racine, depart)
}

package com.music36.app.ui.retro

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.music36.app.data.Piste
import com.music36.app.lecture.SortieAudio
import com.music36.app.ui.LocalActions
import com.music36.app.ui.LocalEtatLecture
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.music36.app.data.messageDeplacement
import com.music36.app.data.messageEffacement
import com.music36.app.data.messageRangement
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.RestartAlt
import com.music36.app.ui.LocalReglages
import androidx.compose.foundation.isSystemInDarkTheme
import com.music36.app.data.ThemeApplication
import com.music36.app.data.FormatExport
import com.music36.app.data.messageExport
import com.music36.app.data.libelleDeBande
import com.music36.app.data.texteDeBande
import com.music36.app.data.niveauDeBande
import com.music36.app.data.avecBande
import com.music36.app.data.ConfigurationApparence
import com.music36.app.data.avecConfiguration
import android.content.Intent
import com.music36.app.data.resumeAnalyse
import com.music36.app.data.pluriel
import com.music36.app.data.ResultatReecriture
import com.music36.app.data.messageReecriture
import com.music36.app.data.messageReecritureEnLot
import com.music36.app.data.messageDoublons
import com.music36.app.data.correspond
import com.music36.app.data.triDe
import com.music36.app.data.avecTri
import com.music36.app.data.MotifRecherche
import com.music36.app.data.avertissementReecriture

/**
 * L'interface « molette », dans l'esprit des baladeurs à molette d'autrefois.
 *
 * Un seul écran, une pile de menus, et cinq gestes : tourner, appuyer au centre, MENU, précédent,
 * suivant. Rien n'est cliquable dans les listes — tout passe par la molette, sinon ce serait une
 * application tactile ordinaire déguisée. Seuls l'écran de lecture et les quatre boutons posés
 * autour de la molette répondent au doigt : ils commandent des réglages, pas la navigation.
 */
@UnstableApi
@Composable
fun EcranRetro(
    depart: NiveauRetro = NiveauRetro.Racine,
    /** Vrai quand on arrive de l'interface moderne : les réglages proposent d'y retourner. */
    depuisModerne: Boolean = false
) {
    val actions = LocalActions.current
    val etat = LocalEtatLecture.current
    val reglages = LocalReglages.current
    val contexte = LocalContext.current

    val pistes by actions.depot.pistes.collectAsStateWithLifecycle()
    val artistes by actions.depot.artistes.collectAsStateWithLifecycle()
    val albums by actions.depot.albums.collectAsStateWithLifecycle()
    val genres by actions.depot.genres.collectAsStateWithLifecycle()
    val playlists by actions.depot.playlists.collectAsStateWithLifecycle()
    val dossiers by actions.depot.dossiersRacines.collectAsStateWithLifecycle()
    val favoris by actions.depot.pistesFavorites.collectAsStateWithLifecycle()
    val racines by actions.depot.racines.collectAsStateWithLifecycle()
    val analyse by actions.depot.analyseur.etat.collectAsStateWithLifecycle()

    // Collecté pour la réactivité : sans cela, le classement des plus écoutés ne bougerait
    // qu'au prochain changement de bibliothèque.
    val statistiques by actions.depot.statistiques.collectAsStateWithLifecycle()

    // Le décodeur employé : la première chose à regarder quand un morceau reste muet.
    val decodeur by com.music36.app.lecture.SortieAudio.decodeur.collectAsStateWithLifecycle()

    val pile = remember { mutableStateListOf(*pileDeDepart(depart).toTypedArray()) }
    val selections = remember { mutableStateMapOf<String, Int>() }
    var volumeAffiche by remember { mutableStateOf<Int?>(null) }
    var mode by remember { mutableStateOf(ModeMolette.VOLUME) }
    var lettreRepere by remember { mutableStateOf<String?>(null) }
    // Le bouton ABC : la molette saute alors d'une lettre à l'autre au lieu d'avancer ligne à
    // ligne. Sur trois mille titres, atteindre le M demandait des centaines de crans.
    var modeAlphabet by remember { mutableStateOf(false) }
    // Les fichiers dont on attend le dossier de destination : le sélecteur d'Android ne
    // rend la main qu'après coup, il faut donc garder la cible entre-temps.
    var deplacementEnAttente by remember { mutableStateOf<List<String>>(emptyList()) }
    var remiseAZeroDemandee by remember { mutableStateOf(false) }
    // Le bilan d'analyse ne s'affiche que pour une analyse **demandée ici** : celle du
    // démarrage ne doit pas ouvrir une fenêtre par-dessus la bibliothèque.
    var analyseAttendue by remember { mutableStateOf(false) }
    var bilanAnalyse by remember { mutableStateOf<String?>(null) }
    // ⚠️ Proposé **au moment du refus**, et non enfoui dans un réglage : c'est là que l'on
    // comprend de quoi il s'agit. Sans cela, la playlist change dans l'application, le fichier
    // reste en l'état, et il faut deviner où aller.
    var ecritureADemander by remember { mutableStateOf(false) }
    // ⚠️ La teinte en cours d'essai n'est **pas** enregistrée : tant qu'on tourne, elle ne vit
    // qu'ici. C'est ce qui permet de parcourir le cercle entier puis de repartir avec MENU sans
    // avoir rien changé — et ce qui évite d'écrire dans les réglages à chaque cran.
    var teinteApercu by remember { mutableStateOf<Float?>(null) }
    // Même principe pour les réglages continus : la valeur essayée ne quitte pas l'écran tant
    // qu'on n'a pas validé.
    var valeurApercu by remember { mutableStateOf<Float?>(null) }
    // La recherche du menu ouvert : `null` quand elle est fermée. Elle ne survit pas à un
    // changement de menu — chercher « emi » dans les artistes ne veut rien dire dans les albums.
    var recherche by remember { mutableStateOf<String?>(null) }
    val dernierFiltre = remember { MemoRetro() }
    val infosEgaliseur by actions.app.egaliseur.infos.collectAsStateWithLifecycle()
    var momentLettre by remember { mutableLongStateOf(0L) }
    var dernierCran by remember { mutableLongStateOf(0L) }

    val audio = remember { contexte.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val volumeMax = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

    val niveau = pile.last()
    val cleNiveau = niveau.cleSelection

    /**
     * Le cadran d'un niveau, quand ce niveau en est un.
     *
     * ⚠️ Les bandes de l'égaliseur n'ont pas de bornes écrites d'avance : elles viennent du
     * matériel, un téléphone en propose cinq et un autre dix, avec des plages de gain
     * différentes. C'est pourquoi le cadran est construit ici et non déclaré dans le modèle.
     */
    fun cadranDe(ouvert: NiveauRetro): CadranRetro? = when (ouvert) {
        is NiveauRetro.ParamNombre -> CadranRetro(
            libelle = ouvert.reglage.libelle,
            explication = ouvert.reglage.explication,
            minimum = ouvert.reglage.minimum,
            maximum = ouvert.reglage.maximum,
            pas = ouvert.reglage.pas,
            texte = { ouvert.reglage.texte(it) }
        )

        is NiveauRetro.ParamBande -> CadranRetro(
            libelle = libelleDeBande(ouvert.frequence),
            explication = "Le niveau de cette bande. Régler une bande à la main quitte le " +
                "préréglage en cours.",
            minimum = infosEgaliseur.gainMin.toFloat(),
            maximum = infosEgaliseur.gainMax.toFloat(),
            pas = 100f,
            texte = { texteDeBande(it) }
        )

        else -> null
    }

    /** La valeur enregistrée pour ce cadran. */
    fun valeurDe(ouvert: NiveauRetro): Float = when (ouvert) {
        is NiveauRetro.ParamNombre -> ouvert.reglage.valeurDe(reglages)
        is NiveauRetro.ParamBande -> niveauDeBande(reglages, ouvert.index)
        else -> 0f
    }

    // L'aperçu de la barre de couleurs doit savoir sur quel fond il se posera : c'est le thème
    // de la vue molette qui le décide, pas celui de l'interface classique.
    val sombreRetro = when (
        ThemeApplication.entries.getOrElse(reglages.themeRetro) { ThemeApplication.SOMBRE }
    ) {
        ThemeApplication.SYSTEME -> isSystemInDarkTheme()
        ThemeApplication.CLAIR -> false
        else -> true
    }

    fun ouvrir(suivant: NiveauRetro) {
        if (pile.lastOrNull() != suivant) pile.add(suivant)
    }

    /** Referme le niveau courant, comme le bouton MENU. */
    fun fermer() {
        if (pile.size > 1) pile.removeAt(pile.lastIndex)
    }

    fun jouer(liste: List<Piste>, index: Int, aleatoire: Boolean) {
        if (liste.isEmpty()) return
        if (aleatoire) actions.lecteur.toutMelanger(liste) else actions.lecteur.jouer(liste, index)
        ouvrir(NiveauRetro.Lecture)
    }

    // Le souvenir des listes triées survit aux recompositions — il y en a deux par seconde
    // pendant la lecture, et la bibliothèque n'a pas à être retriée à chacune.
    val memo = remember { MemoRetro() }
    val portee = rememberCoroutineScope()

    /** Les playlists cochées dans l'écran de choix, remises à zéro à chaque ouverture. */
    val choixPlaylists = remember { mutableStateListOf<Long>() }

    var formatExportEnAttente by remember {
        mutableStateOf<Pair<FormatExport, List<Long>>?>(null)
    }

    var urisNouvellePlaylist by remember { mutableStateOf<List<String>>(emptyList()) }

    val creerM3u = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("audio/x-mpegurl")
    ) { destination ->
        val uris = urisNouvellePlaylist
        urisNouvellePlaylist = emptyList()
        if (destination == null) return@rememberLauncherForActivityResult
        portee.launch {
            val nom = actions.depot.creerPlaylistDansFichier(destination, uris)
            actions.message(
                if (uris.isEmpty()) "Playlist « $nom » créée"
                else "Playlist « $nom » créée avec ${uris.size} titre${pluriel(uris.size)}"
            )
            fermer()
        }
    }

    val choixM3u = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { fichier ->
        if (fichier == null) return@rememberLauncherForActivityResult
        portee.launch {
            actions.depot.importerPlaylist(
                fichier,
                fichier.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.')
                    ?: "Playlist importée"
            )
            actions.message("Playlist importée")
        }
    }

    val dossierExport = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { destination ->
        val demande = formatExportEnAttente
        formatExportEnAttente = null
        if (destination == null || demande == null) return@rememberLauncherForActivityResult
        val (format, ids) = demande
        portee.launch {
            actions.message(
                messageExport(actions.depot.exporterPlaylists(ids, destination, format))
            )
        }
    }

    val choixDossier = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { destination ->
        val aDeplacer = deplacementEnAttente
        deplacementEnAttente = emptyList()
        if (destination == null || aDeplacer.isEmpty()) return@rememberLauncherForActivityResult
        val visees = actions.depot.pistes.value.filter { it.uri in aDeplacer.toHashSet() }
        portee.launch {
            actions.message(messageDeplacement(actions.depot.deplacerFichiers(visees, destination)))
        }
    }

    fun agir(action: ActionRetro) {
        when (action) {
            is ActionRetro.ToutCocher -> {
                // Tout cocher, ou tout décocher si tout l'était déjà : une seule ligne pour les
                // deux gestes, comme la case d'en-tête d'un tableau.
                val complet = action.ids.isNotEmpty() && choixPlaylists.containsAll(action.ids)
                choixPlaylists.clear()
                if (!complet) choixPlaylists.addAll(action.ids)
            }

            is ActionRetro.BasculerChoix ->
                if (action.id in choixPlaylists) choixPlaylists.remove(action.id)
                else choixPlaylists.add(action.id)

            is ActionRetro.ValiderChoix -> {
                if (action.ids.isEmpty()) {
                    actions.message("Cochez au moins une playlist")
                } else {
                    val ids = action.ids.toList()
                    portee.launch {
                        val resultats = ids.map { id ->
                            val nom = actions.depot.playlists.value
                                .firstOrNull { it.playlist.id == id }?.playlist?.nom
                                .orEmpty()
                            nom to actions.depot.ajouterAPlaylist(id, action.uris)
                        }
                        actions.message(messageRangement(resultats))
                        if (resultats.any {
                                it.second.reecriture == ResultatReecriture.LECTURE_SEULE
                            }
                        ) {
                            ecritureADemander = true
                        }
                    }
                    choixPlaylists.clear()
                    fermer()
                }
            }

            is ActionRetro.ChoisirFond ->
                actions.depot.modifierReglages { it.copy(fondLecteurRetro = action.fond.ordinal) }

            is ActionRetro.ChoisirCouleurFond ->
                actions.depot.modifierReglages { it.copy(couleurFondRetro = action.index) }

            is ActionRetro.ChoisirStyleMolette ->
                actions.depot.modifierReglages { it.copy(styleMolette = action.style.ordinal) }

            is ActionRetro.ChoisirCouleurMolette ->
                actions.depot.modifierReglages { it.copy(couleurMolette = action.index) }

            is ActionRetro.ChoisirEffetMolette ->
                actions.depot.modifierReglages { it.copy(effetMolette = action.effet.ordinal) }

            is ActionRetro.ChoisirThemeRetro ->
                actions.depot.modifierReglages { it.copy(themeRetro = action.ordinal) }

            is ActionRetro.ChoisirAmbiance ->
                actions.depot.modifierReglages {
                    it.copy(
                        themeCouleurRetro = action.theme.ordinal,
                        teinteRetro = action.teinte
                    )
                }

            is ActionRetro.ChoisirStyleBarre ->
                actions.depot.modifierReglages { it.copy(styleBarre = action.ordinal) }

            is ActionRetro.BasculerLisere ->
                actions.depot.modifierReglages { it.copy(lisereLecteur = action.actif) }

            is ActionRetro.Deplacer -> {
                deplacementEnAttente = action.uris
                choixDossier.launch(null)
            }

            is ActionRetro.Analyser -> {
                analyseAttendue = true
                actions.depot.analyser(complet = action.complet)
                actions.message(
                    if (action.complet) "Analyse complète lancée" else "Analyse lancée"
                )
            }

            is ActionRetro.ReecrirePlaylist -> portee.launch {
                val resultat = actions.depot.repercuterDansLeFichier(action.id)
                actions.message(messageReecriture(resultat))
                if (resultat == ResultatReecriture.LECTURE_SEULE) ecritureADemander = true
            }

            is ActionRetro.ReecrireToutes -> portee.launch {
                val avecFichier = actions.depot.playlists.value
                    .filter { it.playlist.source != null }
                var ecrits = 0
                var refuses = 0
                avecFichier.forEach { garnie ->
                    when (actions.depot.repercuterDansLeFichier(garnie.playlist.id)) {
                        ResultatReecriture.ECRIT -> ecrits++
                        ResultatReecriture.SANS_FICHIER, ResultatReecriture.DESACTIVE -> Unit
                        else -> refuses++
                    }
                }
                actions.message(
                    messageReecritureEnLot(
                        ecrits = ecrits,
                        refuses = refuses,
                        sansFichier = actions.depot.playlists.value.size - avecFichier.size
                    )
                )
                if (refuses > 0) ecritureADemander = true
            }

            is ActionRetro.NettoyerPlaylists -> portee.launch {
                actions.message(messageDoublons(actions.depot.nettoyerLesPlaylists()))
            }

            is ActionRetro.ImporterM3u -> choixM3u.launch(arrayOf("*/*"))

            is ActionRetro.NouvellePlaylist -> {
                urisNouvellePlaylist = action.uris
                // Le sélecteur d'Android demande le dossier **et** le nom : rien à saisir à la
                // molette, et le fichier est créé là où l'utilisateur l'a voulu.
                creerM3u.launch("Ma playlist.m3u")
            }

            is ActionRetro.PartagerPlaylists -> {
                if (action.ids.isEmpty()) {
                    actions.message("Cochez au moins une playlist")
                } else {
                    portee.launch {
                        val fichiers = actions.depot.preparerPartage(action.ids, action.format)
                        if (fichiers.isEmpty()) {
                            actions.message("Partage impossible")
                        } else {
                            partager(contexte, fichiers, action.format.mime)
                        }
                    }
                }
            }

            // ⚠️ L'export part **de toutes** les playlists : cocher une sélection à la molette
            // demanderait un écran de plus pour un geste qu'on fait une fois par an.
            is ActionRetro.ExporterPlaylists -> {
                if (action.ids.isEmpty()) {
                    actions.message("Cochez au moins une playlist")
                } else {
                    formatExportEnAttente = action.format to action.ids
                    dossierExport.launch(null)
                }
            }

            is ActionRetro.Executer -> {
                val demande = action.demande
                // On referme d'abord la confirmation : le geste est parti, l'écran n'a plus à
                // proposer de le refaire.
                fermer()
                portee.launch {
                    when (demande.genre) {
                        GenreAction.RETIRER_DE_PLAYLISTS -> {
                            val uri = (demande.cible as? CibleAction.Titre)?.uri
                            if (uri != null) {
                                actions.depot.retirerDesPlaylists(uri, demande.playlists)
                                actions.message(
                                    if (demande.playlists.size == 1) "Retiré de la playlist"
                                    else "Retiré de ${demande.playlists.size} playlists"
                                )
                            }
                        }

                        GenreAction.SUPPRIMER_PLAYLIST -> {
                            val id = (demande.cible as? CibleAction.Playlist)?.id
                            if (id != null) {
                                actions.depot.supprimerPlaylist(id)
                                actions.message("Playlist supprimée")
                            }
                        }

                        GenreAction.RETIRER_OCCURRENCE -> {
                            val titre = demande.cible as? CibleAction.Titre
                            val id = titre?.playlistId
                            if (titre != null && id != null) {
                                val resultat = actions.depot.retirerOccurrence(
                                    id, titre.uri, titre.occurrence
                                )
                                actions.message(
                                    when (resultat) {
                                        ResultatReecriture.ECHEC ->
                                            "Ce titre n'était déjà plus dans la playlist"
                                        else -> listOfNotNull(
                                            "Retiré de la playlist",
                                            avertissementReecriture(resultat)
                                        ).joinToString(" — ")
                                    }
                                )
                                if (resultat == ResultatReecriture.LECTURE_SEULE) {
                                    ecritureADemander = true
                                }
                            }
                        }

                        GenreAction.REMPLACER_CONFIGURATION -> {
                            val cible = demande.cible as? CibleAction.Configuration
                            if (cible != null) {
                                actions.depot.modifierReglages {
                                    it.avecConfiguration(
                                        cible.emplacement,
                                        ConfigurationApparence.depuis(
                                            it, System.currentTimeMillis()
                                        )
                                    )
                                }
                                actions.message("${cible.libelle} remplacée")
                            }
                        }

                        GenreAction.SUPPRIMER_CONFIGURATION -> {
                            val cible = demande.cible as? CibleAction.Configuration
                            if (cible != null) {
                                actions.depot.modifierReglages {
                                    it.avecConfiguration(cible.emplacement, null)
                                }
                                // L'écran de l'emplacement n'a plus rien à montrer : on remonte
                                // jusqu'à la liste des trois.
                                if (pile.lastOrNull() is NiveauRetro.ConfigurationRetro) fermer()
                                actions.message("${cible.libelle} supprimée")
                            }
                        }

                        GenreAction.RETIRER_RACINE -> {
                            val uri = (demande.cible as? CibleAction.Racine)?.uri
                            val racine = actions.depot.racines.value.firstOrNull {
                                it.uri == uri
                            }
                            if (racine != null) {
                                actions.depot.supprimerRacine(racine)
                                actions.message("Dossier retiré de la bibliothèque")
                            }
                        }

                        GenreAction.EFFACER_FICHIERS -> {
                            val uris = when (val cible = demande.cible) {
                                is CibleAction.Titre -> listOf(cible.uri)
                                is CibleAction.Groupe -> cible.uris
                                // Une playlist ou un dossier surveillé ne portent aucun
                                // fichier à effacer : les retirer n'efface rien du téléphone.
                                is CibleAction.Playlist, is CibleAction.Racine,
                                is CibleAction.Configuration -> emptyList()
                            }
                            actions.message(
                                messageEffacement(actions.depot.effacerFichiers(uris))
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Les lignes du menu, réduites à celles qui répondent à la recherche.
     *
     * ⚠️ **Le même filtre partout** — à l'affichage, à la molette, au bouton central et à
     * l'appui long. Si un seul de ces endroits voyait la liste entière, la ligne surlignée et
     * celle qu'on ouvre ne seraient plus les mêmes.
     */
    fun filtrer(lignes: List<EntreeRetro>): List<EntreeRetro> {
        val texte = recherche?.takeIf { it.isNotBlank() } ?: return lignes
        // ⚠️ Mémorisé sur la liste **et** le motif : chaque cran de molette redemande la liste,
        // et refaire la recherche sur des milliers de noms à chaque cran faisait traîner la
        // molette. On ne recalcule que si le menu ou le texte ont changé.
        return dernierFiltre.valeur("filtre", lignes, texte) {
            val motif = MotifRecherche(texte)
            lignes.filter { ligne ->
                motif.correspond(ligne.libelle) ||
                    ligne.detail?.let { motif.correspond(it) } == true
            }
        }
    }

    val entrees = filtrer(entreesPour(
        niveau, actions, etat, ::ouvrir, ::fermer, ::jouer,
        choixPlaylists = choixPlaylists.toSet(), agir = ::agir, memo = memo,
        depuisModerne = depuisModerne
    ))

    val selection = (selections[cleNiveau] ?: 0).coerceIn(0, maxOf(entrees.lastIndex, 0))


    /** Chaque lettre tapée repart du haut des résultats : l'ancienne position ne veut plus rien dire. */
    fun chercher(texte: String?) {
        recherche = texte
        selections[cleNiveau] = 0
    }

    BackHandler(enabled = recherche != null) { recherche = null }
    BackHandler(enabled = recherche == null && pile.size > 1) { pile.removeAt(pile.lastIndex) }

    // La barre de couleurs s'ouvre sur la teinte en cours quand on y revient, et sur celle que
    // l'ambiance propose quand on la découvre.
    LaunchedEffect(niveau) {
        recherche = null
        val ouvert = niveau
        valeurApercu = if (cadranDe(ouvert) != null) valeurDe(ouvert) else null
        if (ouvert is NiveauRetro.ChoixExport) choixPlaylists.clear()
        teinteApercu = if (ouvert is NiveauRetro.TeintePaletteRetro) {
            if (ouvert.theme.ordinal == reglages.themeCouleurRetro) reglages.teinteRetro
            else ouvert.theme.teinteParDefaut
        } else {
            null
        }
    }

    // La lettre s'efface d'elle-même quand le doigt s'arrête.
    LaunchedEffect(lettreRepere, momentLettre) {
        if (lettreRepere != null) {
            kotlinx.coroutines.delay(900)
            lettreRepere = null
        }
    }

    // La jauge de volume s'efface d'elle-même, comme sur l'appareil d'origine.
    LaunchedEffect(volumeAffiche) {
        if (volumeAffiche != null) {
            kotlinx.coroutines.delay(1_200)
            volumeAffiche = null
        }
    }

    fun tourner(sens: Int) {
        val ouvert = pile.last()
        // La barre de couleurs : la molette y déplace la teinte, et rien d'autre.
        if (ouvert is NiveauRetro.TeintePaletteRetro) {
            val depart = teinteApercu ?: ouvert.theme.teinteParDefaut
            teinteApercu = teinteSuivante(depart, sens)
            return
        }
        // Un cadran : la molette y déplace une valeur entre deux bornes, sans boucler.
        val cadran = cadranDe(ouvert)
        if (cadran != null) {
            val depart = valeurApercu ?: valeurDe(ouvert)
            valeurApercu = (depart + sens * cadran.pas)
                .coerceIn(cadran.minimum, cadran.maximum)
            return
        }
        if (ouvert == NiveauRetro.Lecture) {
            when (mode) {
                ModeMolette.VOLUME -> {
                    audio.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        if (sens > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                        0
                    )
                    volumeAffiche = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                }

                ModeMolette.POSITION -> actions.lecteur.avancerDe(sens * PAS_POSITION)

                ModeMolette.VITESSE -> {
                    val vitesse = (actions.depot.etatReglages.value.vitesse + sens * 0.05f)
                        .coerceIn(0.5f, 2.5f)
                    actions.lecteur.reglerVitesse((vitesse * 20).toInt() / 20f)
                }
            }
            return
        }
        val liste = filtrer(entreesPour(
            ouvert, actions, actions.lecteur.etat.value, ::ouvrir, ::fermer, ::jouer,
            choixPlaylists = choixPlaylists.toSet(), agir = ::agir, memo = memo,
            depuisModerne = depuisModerne
        ))
        if (liste.isEmpty()) return
        // On repart de la sélection réellement affichée : la liste a pu changer de taille
        // (analyse en cours, playlist modifiée) depuis le dernier cran.
        val depart = (selections[ouvert.cleSelection] ?: 0).coerceIn(0, liste.lastIndex)
        val arrivee = if (modeAlphabet) {
            sautAlphabetique(liste.map { it.libelle }, depart, sens)
        } else {
            (depart + sens).coerceIn(0, liste.lastIndex)
        }
        selections[ouvert.cleSelection] = arrivee

        // Repère alphabétique. En mode ABC il est le repère principal, donc toujours affiché ;
        // sinon il n'apparaît que sur une liste assez longue pour qu'on s'y perde, et seulement
        // quand les crans s'enchaînent — un cran isolé se lit très bien.
        val maintenant = SystemClock.elapsedRealtime()
        if (modeAlphabet ||
            (estAlphabetique(ouvert) && doitAfficherLaLettre(liste.size, maintenant - dernierCran))
        ) {
            lettreRepere = initialeDe(liste[arrivee].libelle)
            momentLettre = maintenant
        }
        dernierCran = maintenant
    }

    /**
     * Le menu d'actions, ouvert par deux secondes d'appui au centre.
     *
     * ⚠️ On relit la liste au moment du geste plutôt que de se fier à celle de la composition :
     * la bibliothèque peut avoir changé entre-temps, et agir sur une ligne périmée reviendrait à
     * effacer le mauvais fichier.
     */
    fun appuiLong() {
        val ouvert = pile.last()
        if (ouvert is NiveauRetro.Confirmation || ouvert == NiveauRetro.Lecture) return
        val liste = filtrer(entreesPour(
            ouvert, actions, actions.lecteur.etat.value, ::ouvrir, ::fermer, ::jouer,
            choixPlaylists = choixPlaylists.toSet(), agir = ::agir, memo = memo,
            depuisModerne = depuisModerne
        ))
        val choix = (selections[ouvert.cleSelection] ?: 0).coerceIn(0, maxOf(liste.lastIndex, 0))
        val cible = liste.getOrNull(choix)?.cible ?: return
        ouvrir(NiveauRetro.ActionsLongues(cible))
    }

    fun appuyer(zone: ZoneMolette) {
        val ouvert = pile.last()
        when (zone) {
            // ⚠️ MENU ferme d'abord la recherche : on revient au menu entier, pas au précédent.
            ZoneMolette.MENU -> when {
                recherche != null -> recherche = null
                pile.size > 1 -> pile.removeAt(pile.lastIndex)
            }
            ZoneMolette.CENTRE ->
                if (cadranDe(ouvert) != null) {
                    // C'est ici que la valeur essayée devient la valeur retenue.
                    val valeur = valeurApercu ?: valeurDe(ouvert)
                    when (ouvert) {
                        is NiveauRetro.ParamNombre -> actions.depot.modifierReglages {
                            ouvert.reglage.appliquer(it, valeur)
                        }

                        is NiveauRetro.ParamBande -> actions.depot.modifierReglages {
                            avecBande(it, ouvert.index, valeur, infosEgaliseur.bandes.size)
                        }

                        else -> Unit
                    }
                    fermer()
                } else if (ouvert is NiveauRetro.TeintePaletteRetro) {
                    // C'est ici, et nulle part ailleurs, que le choix s'enregistre.
                    agir(
                        ActionRetro.ChoisirAmbiance(
                            ouvert.theme,
                            teinteApercu ?: ouvert.theme.teinteParDefaut
                        )
                    )
                    fermer()
                } else if (ouvert == NiveauRetro.Lecture) {
                    actions.lecteur.basculerLecture()
                } else {
                    val liste = filtrer(entreesPour(
                        ouvert, actions, actions.lecteur.etat.value, ::ouvrir, ::fermer, ::jouer,
                        choixPlaylists = choixPlaylists.toSet(), agir = ::agir, memo = memo,
                        depuisModerne = depuisModerne
                    ))
                    val choix = (selections[ouvert.cleSelection] ?: 0)
                        .coerceIn(0, maxOf(liste.lastIndex, 0))
                    liste.getOrNull(choix)?.action?.invoke()
                }

            ZoneMolette.PRECEDENT -> actions.lecteur.precedent()
            ZoneMolette.SUIVANT -> actions.lecteur.suivant()
            ZoneMolette.LECTURE -> actions.lecteur.basculerLecture()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .weight(1f)
                // Le liseré du cadre se retire d'un réglage : sur un thème sombre, certains
                // préfèrent que rien ne souligne les bords.
                .border(
                    2.dp,
                    if (reglages.lisereLecteur) MaterialTheme.colorScheme.outlineVariant
                    else Color.Transparent,
                    RoundedCornerShape(10.dp)
                ),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Box(Modifier.fillMaxSize()) {
                when {
                    niveau == NiveauRetro.Lecture ->
                        EcranLectureRetro(
                            volume = volumeAffiche,
                            volumeMax = volumeMax,
                            mode = mode,
                            onInfos = { ouvrir(NiveauRetro.ActionsPiste) },
                            onAjouterAPlaylist = {
                                etat.piste?.let {
                                    choixPlaylists.clear()
                                    ouvrir(NiveauRetro.ChoixPlaylist(listOf(it.uri)))
                                }
                            },
                            onAppartenance = {
                                etat.piste?.let { ouvrir(NiveauRetro.Appartenance(it.uri)) }
                            }
                        )

                    niveau == NiveauRetro.PlusEcoutes -> MenuRetro(
                        titre = titreDe(niveau, racines.isEmpty()),
                        entrees = entrees,
                        selection = selection,
                        enLecture = etat.enLecture,
                        recherche = recherche,
                        onRecherche = ::chercher,
                        actionSupplementaire = {
                            // ⚠️ Au doigt, pas à la molette : c'est un geste qu'on ne doit
                            // pas pouvoir déclencher en tournant distraitement.
                            IconButton(
                                onClick = { remiseAZeroDemandee = true },
                                modifier = Modifier.size(30.dp).testTag("raz_ecoutes")
                            ) {
                                Icon(
                                    Icons.Rounded.RestartAlt,
                                    contentDescription = "Remettre les compteurs à zéro",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    )

                    cadranDe(niveau) != null -> EcranNombreRetro(
                        cadran = cadranDe(niveau)!!,
                        valeur = valeurApercu ?: valeurDe(niveau),
                        enLecture = etat.enLecture
                    )

                    niveau is NiveauRetro.TeintePaletteRetro -> EcranTeinteRetro(
                        theme = niveau.theme,
                        teinte = teinteApercu ?: niveau.theme.teinteParDefaut,
                        sombre = sombreRetro,
                        enLecture = etat.enLecture
                    )

                    niveau == NiveauRetro.CoverFlow -> Column(Modifier.fillMaxSize()) {
                        BarreTitreRetro("Cover Flow", etat.enLecture)
                        VueCoverFlow(albums, selection)
                    }

                    // Le sous-menu d'un album se **superpose** au carrousel, qui reste visible
                    // en arrière-plan : on n'a pas quitté Cover Flow, on regarde dedans.
                    niveau is NiveauRetro.CoverFlowAlbum -> {
                        val index = albums.indexOfFirst { it.cle == niveau.cle }.coerceAtLeast(0)
                        Box(Modifier.fillMaxSize()) {
                            VueCoverFlow(albums, index, attenue = true)
                            Column(Modifier.fillMaxSize()) {
                                BarreRechercheRetro(
                                    titreDe(niveau, racines.isEmpty()),
                                    etat.enLecture,
                                    recherche,
                                    ::chercher,
                                    null
                                )
                                Surface(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                    border = BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant
                                    )
                                ) {
                                    ListeRetro(entrees, selection)
                                }
                            }
                        }
                    }

                    else -> MenuRetro(
                        titre = titreDe(niveau, racines.isEmpty()),
                        entrees = entrees,
                        selection = selection,
                        enLecture = etat.enLecture,
                        // Une confirmation n'a que deux lignes : y chercher n'aurait pas de sens.
                        recherche = recherche,
                        onRecherche = if (niveau is NiveauRetro.Confirmation) null else ::chercher,
                        actionSupplementaire = if (niveau is NiveauRetro.Playlist) {
                            {
                                BoutonTriRetro(triDe(reglages, niveau.id)) {
                                    actions.depot.modifierReglages {
                                        it.avecTri(niveau.id, triDe(it, niveau.id).suivant())
                                    }
                                    // Le haut de la liste : après un tri, la ligne surlignée
                                    // ne désignerait plus le même titre.
                                    selections[cleNiveau] = 0
                                }
                            }
                        } else {
                            null
                        }
                    )
                }

                LettreRepere(lettreRepere)

                // ⚠️ Cette vue n'a pas de bandeau de messages : sans cet encart, un fichier
                // qu'Android refuse de décoder ne produirait qu'un silence inexpliqué.
                etat.erreur?.let { erreur ->
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(6.dp),
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ) {
                        Text(
                            erreur,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Les quatre boutons occupent les coins que la molette laisse libres : avance/recul et
        // vitesse en haut, aléatoire et répétition en bas.
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Molette(
                enLecture = etat.enLecture,
                onCran = ::tourner,
                onZone = ::appuyer,
                onAppuiLong = ::appuiLong,
                modifier = Modifier
                    .testTag("molette")
                    .fillMaxWidth(0.70f)
                    .widthIn(max = 300.dp)
            )

            // Le coin haut-gauche ramène à la lecture en cours, de n'importe quel niveau :
            // remonter la pile menu par menu pour revoir la pochette était une corvée.
            if (niveau != NiveauRetro.Lecture && etat.actif) {
                BoutonCoin(
                    icone = Icons.Rounded.MusicNote,
                    description = "Revenir à la lecture en cours",
                    actif = etat.enLecture,
                    modifier = Modifier.align(Alignment.TopStart).testTag("bouton_lecture_en_cours")
                ) { ouvrir(NiveauRetro.Lecture) }
            }

            // Hors de l'écran de lecture, le coin haut-droit porte le saut alphabétique.
            if (niveau != NiveauRetro.Lecture) {
                BoutonCoinTexte(
                    libelle = "ABC",
                    description = "Sauter de lettre en lettre à la molette",
                    actif = modeAlphabet,
                    modifier = Modifier.align(Alignment.TopEnd).testTag("bouton_abc")
                ) { modeAlphabet = !modeAlphabet }
            }

            if (niveau == NiveauRetro.Lecture) {
                BoutonCoin(
                    icone = Icons.Rounded.FastForward,
                    description = "Avancer ou reculer à la molette",
                    actif = mode == ModeMolette.POSITION,
                    modifier = Modifier.align(Alignment.TopStart).testTag("bouton_position")
                ) {
                    mode = if (mode == ModeMolette.POSITION) ModeMolette.VOLUME
                    else ModeMolette.POSITION
                }

                BoutonCoin(
                    icone = Icons.Rounded.Speed,
                    description = "Régler la vitesse à la molette",
                    actif = mode == ModeMolette.VITESSE,
                    modifier = Modifier.align(Alignment.TopEnd).testTag("bouton_vitesse")
                ) {
                    mode = if (mode == ModeMolette.VITESSE) ModeMolette.VOLUME
                    else ModeMolette.VITESSE
                }

                BoutonCoin(
                    icone = Icons.Rounded.Shuffle,
                    description = "Lecture aléatoire",
                    actif = etat.aleatoire,
                    modifier = Modifier.align(Alignment.BottomStart).testTag("bouton_aleatoire")
                ) { actions.lecteur.basculerAleatoire() }

                BoutonCoin(
                    icone = if (etat.repetition == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne
                    else Icons.Rounded.Repeat,
                    description = "Répétition",
                    actif = etat.repetition != Player.REPEAT_MODE_OFF,
                    modifier = Modifier.align(Alignment.BottomEnd).testTag("bouton_repetition")
                ) { actions.lecteur.cyclerRepetition() }
            }
        }

        // Le bilan de l'analyse : sans lui, « Analyser les dossiers » ne donnait aucun signe
        // de vie, et rien ne disait si quelque chose avait changé.
        LaunchedEffect(analyse.enCours, analyse.termineeLe, analyse.erreur) {
            if (analyseAttendue && !analyse.enCours &&
                (analyse.termineeLe > 0 || analyse.erreur != null)
            ) {
                analyseAttendue = false
                bilanAnalyse = resumeAnalyse(analyse)
            }
        }

        if (ecritureADemander) {
            AlertDialog(
                onDismissRequest = { ecritureADemander = false },
                title = { Text("Fichier .m3u non mis à jour") },
                text = {
                    Text(
                        "Android n'autorise ce dossier qu'en lecture : le fichier d'origine " +
                            "n'a pas pu être réécrit. Choisissez à nouveau le même dossier de " +
                            "musique pour accorder l'écriture — rien n'est perdu au passage."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        ecritureADemander = false
                        actions.choisirDossier(premiereRacineEnLectureSeule(actions))
                    }) { Text("Autoriser l'écriture") }
                },
                dismissButton = {
                    TextButton(onClick = { ecritureADemander = false }) { Text("Plus tard") }
                }
            )
        }

        bilanAnalyse?.let { bilan ->
            AlertDialog(
                onDismissRequest = { bilanAnalyse = null },
                title = { Text("Analyse terminée") },
                text = { Text(bilan) },
                confirmButton = {
                    TextButton(onClick = { bilanAnalyse = null }) { Text("J'ai lu") }
                }
            )
        }

        if (remiseAZeroDemandee) {
            AlertDialog(
                onDismissRequest = { remiseAZeroDemandee = false },
                title = { Text("Remettre les compteurs à zéro ?") },
                text = {
                    Text(
                        "Le classement des plus écoutés repartira de rien. Vos morceaux, vos " +
                            "playlists et vos favoris ne sont pas touchés."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        remiseAZeroDemandee = false
                        portee.launch {
                            actions.depot.reinitialiserStatistiques()
                            actions.message("Compteurs d'écoute remis à zéro")
                        }
                    }) { Text("Remettre à zéro") }
                },
                dismissButton = {
                    TextButton(onClick = { remiseAZeroDemandee = false }) { Text("Annuler") }
                }
            )
        }

        Spacer(Modifier.height(6.dp))
        Text(
            when {
                niveau != NiveauRetro.Lecture && modeAlphabet ->
                    "Tournez pour changer de lettre · centre pour valider · ABC pour revenir"

                niveau != NiveauRetro.Lecture ->
                    "Tournez pour choisir · centre pour valider · MENU pour revenir"

                mode == ModeMolette.POSITION ->
                    "Tournez pour avancer ou reculer de 5 s · centre pour lecture/pause"

                mode == ModeMolette.VITESSE ->
                    "Tournez pour la vitesse · centre pour lecture/pause"

                else -> "Tournez pour le volume · centre pour lecture/pause · MENU pour revenir"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Ouvre le partage d'Android avec des fichiers déjà écrits.
 *
 * ⚠️ `FLAG_GRANT_READ_URI_PERMISSION` n'est pas décoratif : sans lui, l'application choisie
 * reçoit des URI qu'elle n'a pas le droit de lire, et le partage échoue en silence chez elle.
 */
private fun partager(contexte: android.content.Context, fichiers: List<android.net.Uri>, mime: String) {
    val envoi = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = mime
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(fichiers))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val choix = Intent.createChooser(envoi, "Partager les playlists").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { contexte.startActivity(choix) }
}

/**
 * Le dossier sur lequel ouvrir le sélecteur quand on vient réautoriser l'écriture.
 *
 * ⚠️ C'est ce qui évite l'erreur la plus facile à commettre : choisir un sous-dossier en croyant
 * réautoriser le dossier surveillé. Le sélecteur s'ouvre alors **sur ce dossier même**, et il n'y
 * a qu'à confirmer.
 */
private fun premiereRacineEnLectureSeule(actions: com.music36.app.ui.ActionsUi): android.net.Uri? =
    actions.depot.racinesEnLectureSeule().firstOrNull()?.uri?.let { android.net.Uri.parse(it) }

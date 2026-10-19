package com.music36.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.music36.app.Music36App
import com.music36.app.data.Collection
import com.music36.app.data.Depot
import com.music36.app.data.EtatReglages
import com.music36.app.data.Onglet
import com.music36.app.data.Piste
import com.music36.app.data.StyleInterface
import com.music36.app.data.PlaylistGarnie
import com.music36.app.data.trier
import com.music36.app.lecture.EtatLecture
import com.music36.app.lecture.Lecteur
import com.music36.app.ui.retro.EcranRetro
import kotlinx.coroutines.launch
import com.music36.app.data.messageAjout
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.systemBars
import com.music36.app.data.Nouveaute
import com.music36.app.data.Nouveautes
import com.music36.app.data.doitAnnoncerLesNouveautes
import com.music36.app.data.nouveautesDepuis
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.first
import com.music36.app.data.messageRangement
import com.music36.app.data.messageEffacement
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.ui.platform.testTag
import com.music36.app.ui.retro.DepartRetro
import com.music36.app.ui.retro.NiveauRetro
import com.music36.app.data.ResultatRacine
import com.music36.app.data.messageRacine

/**
 * Les actions communes à tous les écrans (menus, dialogues, lecture), fournies par la racine.
 *
 * Sans cela, chaque liste devrait recevoir une dizaine de rappels en cascade : la feuille
 * d'actions d'un titre est ouverte depuis six écrans différents.
 */
@UnstableApi
class ActionsUi(
    val app: Music36App,
    val ouvrirMenuPiste: (Piste) -> Unit,
    val ajouterAPlaylist: (List<Piste>) -> Unit,
    /**
     * Ouvre le sélecteur de dossier.
     *
     * ⚠️ L'URI passée est celle sur laquelle **s'ouvrir** : pour réautoriser un dossier déjà
     * surveillé, il faut proposer ce dossier-là et non le dernier visité. Sans cela, on choisit
     * un sous-dossier en croyant bien faire, et toute la musique est comptée deux fois.
     */
    val choisirDossier: (android.net.Uri?) -> Unit,
    val message: (String) -> Unit
) {
    val depot: Depot get() = app.depot
    val lecteur: Lecteur get() = app.lecteur
}

val LocalActions = staticCompositionLocalOf<ActionsUi> { error("ActionsUi non fourni") }
val LocalReglages = staticCompositionLocalOf { EtatReglages() }
val LocalEtatLecture = staticCompositionLocalOf { EtatLecture() }

/** La racine de l'interface : la pile d'écrans, les dialogues partagés, la barre de lecture. */
@UnstableApi
@Composable
fun EcranRacine(app: Music36App) {
    val portee = rememberCoroutineScope()
    val reglages by app.depot.etatReglages.collectAsStateWithLifecycle()
    val etatLecture by app.lecteur.etat.collectAsStateWithLifecycle()
    val playlists by app.depot.playlists.collectAsStateWithLifecycle()

    var pisteDuMenu by remember { mutableStateOf<Piste?>(null) }
    var pistesAAjouter by remember { mutableStateOf<List<Piste>?>(null) }
    var pisteInfos by remember { mutableStateOf<Piste?>(null) }
    var nouveautes by remember { mutableStateOf<List<Nouveaute>>(emptyList()) }
    val messages = remember { SnackbarHostState() }

    val contexte = LocalContext.current
    val paquet = remember(contexte) {
        contexte.packageManager.getPackageInfo(contexte.packageName, 0)
    }
    val versionActuelle = remember(paquet) { paquet.longVersionCode.toInt() }

    // ⚠️ On lit le réglage **à la source**, pas dans l'état exposé : celui-ci commence par la
    // valeur par défaut (zéro) avant que le disque ne réponde, et la fenêtre se serait ouverte
    // une fraction de seconde à chaque démarrage.
    LaunchedEffect(Unit) {
        val vue = app.depot.reglages.flux.first().derniereVersionVue
        val annoncer = doitAnnoncerLesNouveautes(
            derniereVue = vue,
            versionActuelle = versionActuelle,
            premiereInstallation = paquet.firstInstallTime == paquet.lastUpdateTime
        )
        if (annoncer) {
            nouveautes = nouveautesDepuis(Nouveautes.journal, vue, versionActuelle)
        }
        // Installation neuve comprise : on retient la version pour n'annoncer que la suivante.
        if (!annoncer && vue != versionActuelle) {
            app.depot.modifierReglages { it.copy(derniereVersionVue = versionActuelle) }
        }
    }

    val choixDossier = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) portee.launch {
            val resultat = app.depot.ajouterRacine(uri)
            messages.showSnackbar(messageRacine(resultat))
            if (resultat == ResultatRacine.AJOUTE) app.depot.analyser()
        }
    }

    val actions = remember(app) {
        ActionsUi(
            app = app,
            ouvrirMenuPiste = { pisteDuMenu = it },
            ajouterAPlaylist = { pistesAAjouter = it },
            choisirDossier = { depuis -> choixDossier.launch(depuis) },
            message = { portee.launch { messages.showSnackbar(it) } }
        )
    }

    // Les erreurs de lecture (format refusé, fichier disparu) remontent en bandeau plutôt que
    // dans les journaux : c'est la seule façon pour l'utilisateur de comprendre un silence.
    LaunchedEffect(etatLecture.erreur) {
        etatLecture.erreur?.let { messages.showSnackbar(it) }
    }

    BackHandler(enabled = Navigateur.pile.isNotEmpty()) { Navigateur.retour() }

    CompositionLocalProvider(
        LocalActions provides actions,
        LocalReglages provides reglages,
        LocalEtatLecture provides etatLecture
    ) {
        Box(Modifier.fillMaxSize()) {
            // L'interface molette remplace tout : elle a sa propre pile de menus et ne partage
            // avec l'interface moderne que le lecteur et la bibliothèque.
            if (reglages.style == StyleInterface.RETRO) {
                // ⚠️ `remember` et non une lecture directe : la demande se consomme à la
                // **première** composition de la molette, et une recomposition ne rouvre donc
                // pas les réglages par surprise.
                val depart = remember { DepartRetro.consommer() }
                EcranRetro(depart.niveau, depart.depuisModerne)
                // ⚠️ L'hôte des messages vit dans le `Scaffold` de l'interface moderne, que la
                // vue molette ne compose pas : sans celui-ci, `actions.message(…)` n'aboutissait
                // nulle part. Une seule des deux branches est composée à la fois, donc les deux
                // hôtes ne se marchent jamais dessus.
                SnackbarHost(
                    messages,
                    Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.systemBars)
                )
                return@Box
            }
            when (val ecran = Navigateur.courant) {
                null -> EcranPrincipal(messages)
                Ecran.Recherche -> EcranRecherche()
                Ecran.Egaliseur -> EcranEgaliseur()
                Ecran.Lecteur -> EcranLecteur()
                Ecran.FileAttente -> EcranFileAttente()
                is Ecran.Album -> EcranCollectionAlbum(ecran.cle)
                is Ecran.Artiste -> EcranCollectionArtiste(ecran.cle)
                is Ecran.Genre -> EcranCollectionGenre(ecran.cle)
                is Ecran.Dossier -> EcranDossier(ecran.chemin)
                is Ecran.ListePlaylist -> EcranPlaylist(ecran.id)
            }
        }

        pisteDuMenu?.let { piste ->
            val favori = app.depot.estFavori(piste.uri)
            FeuillePiste(
                piste = piste,
                favori = favori,
                onFermer = { pisteDuMenu = null },
                onJouerEnsuite = {
                    app.lecteur.jouerEnsuite(listOf(piste)); pisteDuMenu = null
                    actions.message("Ajouté à la suite")
                },
                onAjouterALaFile = {
                    app.lecteur.ajouterALaFile(listOf(piste)); pisteDuMenu = null
                    actions.message("Ajouté à la file")
                },
                onAjouterAPlaylist = { pistesAAjouter = listOf(piste); pisteDuMenu = null },
                onFavori = {
                    portee.launch { app.depot.basculerFavori(piste.uri) }
                    pisteDuMenu = null
                },
                onAlbum = {
                    Navigateur.aller(
                        Ecran.Album("${piste.album}|${piste.artisteAlbum.ifBlank { piste.artisteAffiche }}")
                    )
                    pisteDuMenu = null
                },
                onArtiste = {
                    Navigateur.aller(Ecran.Artiste(piste.artisteAffiche)); pisteDuMenu = null
                },
                onInfos = { pisteInfos = piste; pisteDuMenu = null }
            )
        }

        pistesAAjouter?.let { pistes ->
            DialogueChoixPlaylist(
                playlists = playlists,
                onChoisir = { ids ->
                    val uris = pistes.map { it.uri }
                    portee.launch {
                        actions.message(
                            messageRangement(
                                ids.map { id ->
                                    val nom = playlists.firstOrNull { it.playlist.id == id }
                                        ?.playlist?.nom.orEmpty()
                                    nom to app.depot.ajouterAPlaylist(id, uris)
                                }
                            )
                        )
                    }
                    pistesAAjouter = null
                },
                onCreer = { nom ->
                    portee.launch { app.depot.creerPlaylist(nom, pistes.map { it.uri }) }
                    pistesAAjouter = null
                    actions.message("Playlist « $nom » créée")
                },
                onFermer = { pistesAAjouter = null }
            )
        }

        pisteInfos?.let { DialogueInfos(it) { pisteInfos = null } }

        DialogueNouveautes(nouveautes) {
            nouveautes = emptyList()
            app.depot.modifierReglages { it.copy(derniereVersionVue = versionActuelle) }
        }
    }
}

/** L'écran d'accueil : la barre du haut, les sept onglets, la barre de lecture. */
@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun EcranPrincipal(messages: SnackbarHostState) {
    val actions = LocalActions.current
    val reglages = LocalReglages.current
    val etatLecture = LocalEtatLecture.current
    val portee = rememberCoroutineScope()

    val onglets = Onglet.entries
    val pager = rememberPagerState(
        initialPage = EtatAccueil.page.takeIf { it >= 0 }
            ?: onglets.indexOf(reglages.ongletDefaut).coerceAtLeast(0),
        pageCount = { onglets.size }
    )
    LaunchedEffect(pager.currentPage) { EtatAccueil.page = pager.currentPage }

    val analyse by actions.depot.analyseur.etat.collectAsStateWithLifecycle()
    val racines by actions.depot.racines.collectAsStateWithLifecycle()
    var menuOuvert by remember { mutableStateOf(false) }
    var dialogueTri by remember { mutableStateOf(false) }
    var dialogueMinuteur by remember { mutableStateOf(false) }
    val minuteur by actions.lecteur.minuteur.collectAsStateWithLifecycle()

    Scaffold(
        snackbarHost = { SnackbarHost(messages) },
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("36Music") },
                    actions = {
                        // Bascule vers la molette : la faire chercher au fond des réglages
                        // alors qu'on veut juste changer de main n'a pas de sens.
                        IconButton(
                            onClick = {
                                actions.depot.modifierReglages {
                                    it.copy(style = StyleInterface.RETRO)
                                }
                            },
                            modifier = Modifier.testTag("bascule_retro")
                        ) {
                            Icon(
                                Icons.Rounded.DonutLarge,
                                contentDescription = "Passer à l'interface molette"
                            )
                        }
                        IconButton(onClick = { Navigateur.aller(Ecran.Recherche) }) {
                            Icon(Icons.Rounded.Search, contentDescription = "Rechercher")
                        }
                        IconButton(onClick = { dialogueTri = true }) {
                            Icon(Icons.Rounded.SortByAlpha, contentDescription = "Trier")
                        }
                        IconButton(onClick = { menuOuvert = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "Menu")
                        }
                        DropdownMenu(menuOuvert, onDismissRequest = { menuOuvert = false }) {
                            DropdownMenuItem(
                                text = { Text("Ajouter un dossier") },
                                leadingIcon = { Icon(Icons.Rounded.CreateNewFolder, null) },
                                onClick = { menuOuvert = false; actions.choisirDossier(null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Analyser les dossiers") },
                                leadingIcon = { Icon(Icons.Rounded.Refresh, null) },
                                onClick = { menuOuvert = false; actions.depot.analyser() }
                            )
                            DropdownMenuItem(
                                text = { Text("File d'attente") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, null) },
                                onClick = { menuOuvert = false; Navigateur.aller(Ecran.FileAttente) }
                            )
                            DropdownMenuItem(
                                text = { Text("Égaliseur") },
                                leadingIcon = { Icon(Icons.Rounded.GraphicEq, null) },
                                onClick = { menuOuvert = false; Navigateur.aller(Ecran.Egaliseur) }
                            )
                            DropdownMenuItem(
                                text = { Text("Minuteur de sommeil") },
                                leadingIcon = { Icon(Icons.Rounded.Timer, null) },
                                onClick = { menuOuvert = false; dialogueMinuteur = true }
                            )
                            // ⚠️ Les réglages **n'habitent plus ici** : ils se règlent à la
                            // molette, et cette entrée y ramène en ouvrant directement le bon
                            // menu. Deux écrans de réglages pour les mêmes champs, c'était un
                            // de trop — et celui-ci obligeait à quitter la roue pour y revenir.
                            DropdownMenuItem(
                                text = { Text("Paramètres") },
                                leadingIcon = { Icon(Icons.Rounded.Settings, null) },
                                onClick = {
                                    menuOuvert = false
                                    Navigateur.revenirALAccueil()
                                    DepartRetro.demande = NiveauRetro.ParametresApp
                                    DepartRetro.depuisModerne = true
                                    actions.depot.modifierReglages {
                                        it.copy(style = StyleInterface.RETRO)
                                    }
                                }
                            )
                        }
                    }
                )
                ScrollableTabRow(
                    selectedTabIndex = pager.currentPage,
                    edgePadding = 8.dp
                ) {
                    onglets.forEachIndexed { index, onglet ->
                        Tab(
                            selected = pager.currentPage == index,
                            onClick = { portee.launch { pager.animateScrollToPage(index) } },
                            text = { Text(onglet.libelle) }
                        )
                    }
                }
                if (analyse.enCours) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
                }
            }
        },
        bottomBar = {
            MiniLecteur(
                etat = etatLecture,
                onOuvrir = { Navigateur.ouvrirLecteur() },
                onBasculer = { actions.lecteur.basculerLecture() },
                onSuivant = { actions.lecteur.suivant() }
            )
        }
    ) { marges ->
        Box(Modifier.padding(marges)) {
            if (racines.isEmpty()) {
                MessageVide(
                    titre = "Aucun dossier de musique",
                    message = "36Music n'analyse rien tant que vous ne lui avez pas indiqué où " +
                        "chercher. Choisissez un ou plusieurs dossiers ; vous pourrez en ajouter " +
                        "d'autres à tout moment dans les paramètres.",
                    libelleAction = "Choisir un dossier",
                    onAction = { actions.choisirDossier(null) }
                )
            } else {
                HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                    when (onglets[page]) {
                        Onglet.SONS -> OngletSons()
                        Onglet.PLAYLISTS -> OngletPlaylists()
                        Onglet.DOSSIERS -> OngletDossiers()
                        Onglet.ARTISTES -> OngletArtistes()
                        Onglet.ALBUMS -> OngletAlbums()
                        Onglet.GENRES -> OngletGenres()
                        Onglet.FAVORIS -> OngletFavoris()
                    }
                }
            }
        }
    }

    if (dialogueTri) {
        DialogueTri(
            triCourant = reglages.tri,
            croissant = reglages.triCroissant,
            onChoisir = { tri, croissant ->
                actions.depot.modifierReglages { it.copy(tri = tri, triCroissant = croissant) }
            },
            onFermer = { dialogueTri = false }
        )
    }

    if (dialogueMinuteur) {
        DialogueMinuteur(
            actif = minuteur,
            onMinutes = {
                actions.lecteur.armerMinuteur(it)
                dialogueMinuteur = false
                actions.message("Arrêt programmé dans $it minutes")
            },
            onFinDePiste = {
                actions.lecteur.armerMinuteurFinDePiste()
                dialogueMinuteur = false
                actions.message("Arrêt à la fin du morceau")
            },
            onAnnuler = { actions.lecteur.annulerMinuteur(); dialogueMinuteur = false },
            onFermer = { dialogueMinuteur = false }
        )
    }
}

// ---------------------------------------------------------------------------- onglets

@UnstableApi
@Composable
private fun OngletSons() {
    val actions = LocalActions.current
    val reglages = LocalReglages.current
    val pistes by actions.depot.pistes.collectAsStateWithLifecycle()
    val triees = remember(pistes, reglages.tri, reglages.triCroissant) {
        pistes.trier(reglages.tri, reglages.triCroissant)
    }
    ListeDePistes(
        triees,
        "Aucun titre",
        "Lancez une analyse depuis le menu.",
        etatListe = EtatAccueil.liste("sons")
    )
}

@UnstableApi
@Composable
private fun OngletFavoris() {
    val actions = LocalActions.current
    val reglages = LocalReglages.current
    val favoris by actions.depot.pistesFavorites.collectAsStateWithLifecycle()
    val triees = remember(favoris, reglages.tri, reglages.triCroissant) {
        favoris.trier(reglages.tri, reglages.triCroissant)
    }
    ListeDePistes(
        triees,
        "Aucun favori",
        "Appuyez sur le cœur d'un titre pour le retrouver ici.",
        etatListe = EtatAccueil.liste("favoris")
    )
}

@UnstableApi
@Composable
private fun OngletArtistes() {
    val actions = LocalActions.current
    val artistes by actions.depot.artistes.collectAsStateWithLifecycle()
    ListeDeCollections(artistes, Icons.Rounded.Person, EtatAccueil.liste("artistes")) {
        Navigateur.aller(Ecran.Artiste(it.cle))
    }
}

@UnstableApi
@Composable
private fun OngletAlbums() {
    val actions = LocalActions.current
    val albums by actions.depot.albums.collectAsStateWithLifecycle()
    ListeDeCollections(albums, Icons.Rounded.Album, EtatAccueil.liste("albums")) {
        Navigateur.aller(Ecran.Album(it.cle))
    }
}

@UnstableApi
@Composable
private fun OngletGenres() {
    val actions = LocalActions.current
    val genres by actions.depot.genres.collectAsStateWithLifecycle()
    ListeDeCollections(genres, Icons.Rounded.Category, EtatAccueil.liste("genres")) {
        Navigateur.aller(Ecran.Genre(it.cle))
    }
}

@UnstableApi
@Composable
private fun OngletDossiers() {
    val actions = LocalActions.current
    val racines by actions.depot.dossiersRacines.collectAsStateWithLifecycle()
    val toutes = racines.flatMap { it.pistes }

    LazyColumn(Modifier.fillMaxSize(), state = EtatAccueil.liste("dossiers")) {
        item {
            EnteteListe(
                nombre = toutes.size,
                duree = toutes.sumOf { it.duree },
                onToutLire = { actions.lecteur.toutLire(toutes) },
                onMelanger = { actions.lecteur.toutMelanger(toutes) }
            )
        }
        items(racines, key = { it.cle }) { dossier ->
            LigneCollection(
                nom = dossier.nom,
                sousTitre = dossier.sousTitre,
                pochette = dossier.pochette,
                icone = Icons.Rounded.Folder,
                onClick = { Navigateur.aller(Ecran.Dossier(dossier.cle)) }
            )
        }
        item {
            ActionFeuille(Icons.Rounded.CreateNewFolder, "Ajouter un dossier") {
                actions.choisirDossier(null)
            }
        }
    }
}

@UnstableApi
@Composable
private fun OngletPlaylists() {
    val actions = LocalActions.current
    val playlists by actions.depot.playlists.collectAsStateWithLifecycle()
    val portee = rememberCoroutineScope()
    var creation by remember { mutableStateOf(false) }
    var aSupprimer by remember { mutableStateOf<PlaylistGarnie?>(null) }
    val toutes = playlists.flatMap { it.pistes }

    LazyColumn(Modifier.fillMaxSize(), state = EtatAccueil.liste("playlists")) {
        item {
            EnteteListe(
                nombre = toutes.size,
                duree = toutes.sumOf { it.duree },
                onToutLire = { actions.lecteur.toutLire(toutes) },
                onMelanger = { actions.lecteur.toutMelanger(toutes) }
            )
        }
        items(playlists, key = { it.playlist.id }) { garnie ->
            LigneCollection(
                nom = garnie.playlist.nom,
                sousTitre = "${garnie.pistes.size} titre${if (garnie.pistes.size > 1) "s" else ""}" +
                    if (garnie.playlist.source != null) " · importée" else "",
                pochette = garnie.pistes.firstNotNullOfOrNull { it.pochette },
                icone = Icons.AutoMirrored.Rounded.QueueMusic,
                onClick = { Navigateur.aller(Ecran.ListePlaylist(garnie.playlist.id)) },
                onLongClick = { aSupprimer = garnie }
            )
        }
        item {
            ActionFeuille(Icons.Rounded.Add, "Créer une playlist") { creation = true }
        }
    }

    aSupprimer?.let { garnie ->
        AlertDialog(
            onDismissRequest = { aSupprimer = null },
            title = { Text("Supprimer « ${garnie.playlist.nom} » ?") },
            text = {
                // ⚠️ La distinction qui rassure : c'est la liste qui disparaît, pas la musique.
                Text(
                    "La playlist et ses ${garnie.pistes.size} entrées disparaissent. " +
                        "Les fichiers, eux, restent sur la carte et dans la bibliothèque."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = garnie.playlist.id
                    aSupprimer = null
                    portee.launch {
                        actions.depot.supprimerPlaylist(id)
                        actions.message("Playlist supprimée")
                    }
                }) { Text("Supprimer la playlist") }
            },
            dismissButton = { TextButton(onClick = { aSupprimer = null }) { Text("Annuler") } }
        )
    }

    if (creation) {
        DialogueTexte(
            titre = "Nouvelle playlist",
            onValider = { nom ->
                portee.launch { actions.depot.creerPlaylist(nom) }
                creation = false
            },
            onFermer = { creation = false }
        )
    }
}

// ---------------------------------------------------------------------------- listes communes

/** Liste de titres avec ses deux boutons de lecture, réutilisée par tous les écrans. */
@UnstableApi
@Composable
fun ListeDePistes(
    pistes: List<Piste>,
    titreVide: String,
    messageVide: String,
    entete: (@Composable () -> Unit)? = null,
    etatListe: LazyListState = rememberLazyListState()
) {
    val actions = LocalActions.current
    val reglages = LocalReglages.current
    val etatLecture = LocalEtatLecture.current
    val favoris by actions.depot.favoris.collectAsStateWithLifecycle()
    val enPlaylist by actions.depot.urisEnPlaylist.collectAsStateWithLifecycle()
    val portee = rememberCoroutineScope()
    var aEffacer by remember { mutableStateOf<Piste?>(null) }

    // ⚠️ Un appui maintenu est vite déclenché sans le vouloir : il ne fait donc que **proposer**,
    // et c'est la confirmation qui efface.
    aEffacer?.let { piste ->
        AlertDialog(
            onDismissRequest = { aEffacer = null },
            title = { Text("Effacer ce fichier ?") },
            text = {
                Text(
                    "« ${piste.titre} » sera effacé de la carte. Il n'y a ni corbeille ni " +
                        "annulation : c'est définitif."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    aEffacer = null
                    portee.launch {
                        actions.message(
                            messageEffacement(actions.depot.effacerFichiers(listOf(piste.uri)))
                        )
                    }
                }) { Text("Effacer définitivement") }
            },
            dismissButton = { TextButton(onClick = { aEffacer = null }) { Text("Annuler") } }
        )
    }

    if (pistes.isEmpty() && entete == null) {
        MessageVide(titreVide, messageVide)
        return
    }

    LazyColumn(Modifier.fillMaxSize(), state = etatListe) {
        entete?.let { item { it() } }
        item {
            EnteteListe(
                nombre = pistes.size,
                duree = pistes.sumOf { it.duree },
                onToutLire = { actions.lecteur.toutLire(pistes) },
                onMelanger = { actions.lecteur.toutMelanger(pistes) }
            )
        }
        itemsIndexed(pistes, key = { _, piste -> piste.uri }) { index, piste ->
            LignePiste(
                piste = piste,
                enCours = etatLecture.piste?.uri == piste.uri,
                afficherQualite = reglages.afficherQualite,
                favori = piste.uri in favoris,
                orpheline = piste.uri !in enPlaylist,
                onLongClick = { aEffacer = piste },
                onClick = { actions.lecteur.jouer(pistes, index) },
                onMenu = { actions.ouvrirMenuPiste(piste) }
            )
        }
        item { Box(Modifier.height(90.dp)) }
    }
}

@Composable
fun ListeDeCollections(
    collections: List<Collection>,
    icone: androidx.compose.ui.graphics.vector.ImageVector,
    etatListe: LazyListState = rememberLazyListState(),
    onOuvrir: (Collection) -> Unit
) {
    val actions = LocalActions.current
    val toutes = remember(collections) { collections.flatMap { it.pistes } }

    if (collections.isEmpty()) {
        MessageVide("Rien à afficher", "La bibliothèque est vide pour l'instant.")
        return
    }
    LazyColumn(Modifier.fillMaxSize(), state = etatListe) {
        item {
            EnteteListe(
                nombre = toutes.size,
                duree = toutes.sumOf { it.duree },
                onToutLire = { actions.lecteur.toutLire(toutes) },
                onMelanger = { actions.lecteur.toutMelanger(toutes) }
            )
        }
        items(collections, key = { it.cle }) { collection ->
            LigneCollection(
                nom = collection.nom,
                sousTitre = collection.sousTitre,
                pochette = collection.pochette,
                icone = icone,
                onClick = { onOuvrir(collection) }
            )
        }
        item { Box(Modifier.height(90.dp)) }
    }
}

/** Échafaudage commun aux écrans de détail : titre, retour, et la barre de lecture en bas. */
@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun EchafaudageDetail(
    titre: String,
    actionsBarre: @Composable RowScope.() -> Unit = {},
    contenu: @Composable (PaddingValues) -> Unit
) {
    val actions = LocalActions.current
    val etatLecture = LocalEtatLecture.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titre, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = { Navigateur.retour() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = actionsBarre
            )
        },
        bottomBar = {
            MiniLecteur(
                etat = etatLecture,
                onOuvrir = { Navigateur.ouvrirLecteur() },
                onBasculer = { actions.lecteur.basculerLecture() },
                onSuivant = { actions.lecteur.suivant() }
            )
        },
        content = contenu
    )
}

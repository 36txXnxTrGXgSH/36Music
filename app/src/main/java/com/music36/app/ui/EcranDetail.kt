package com.music36.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.music36.app.data.Piste
import com.music36.app.data.formaterDureeLongue
import com.music36.app.data.trier
import kotlinx.coroutines.launch

/** Bandeau d'entête des écrans de détail : grande pochette, nom, sous-titre. */
@Composable
fun EnteteCollection(nom: String, sousTitre: String, pochette: String?, icone: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Pochette(pochette, Modifier.size(104.dp), coins = 14.dp, icone = icone)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(nom, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(
                sousTitre,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@UnstableApi
@Composable
fun EcranCollectionAlbum(cle: String) {
    val actions = LocalActions.current
    val albums by actions.depot.albums.collectAsStateWithLifecycle()
    val album = albums.firstOrNull { it.cle == cle }

    EchafaudageDetail(titre = album?.nom ?: "Album") { marges ->
        Box(Modifier.padding(marges)) {
            if (album == null) {
                MessageVide("Album introuvable", "Il a peut-être disparu lors de la dernière analyse.")
            } else {
                ListeDePistes(
                    pistes = album.pistes,
                    titreVide = "Album vide",
                    messageVide = "",
                    entete = {
                        EnteteCollection(album.nom, album.sousTitre, album.pochette, Icons.Rounded.Album)
                    }
                )
            }
        }
    }
}

@UnstableApi
@Composable
fun EcranCollectionArtiste(cle: String) {
    val actions = LocalActions.current
    val artistes by actions.depot.artistes.collectAsStateWithLifecycle()
    val artiste = artistes.firstOrNull { it.cle == cle }

    EchafaudageDetail(titre = artiste?.nom ?: "Artiste") { marges ->
        Box(Modifier.padding(marges)) {
            if (artiste == null) {
                MessageVide("Artiste introuvable", "Il a peut-être disparu lors de la dernière analyse.")
            } else {
                ListeDePistes(
                    pistes = artiste.pistes,
                    titreVide = "Aucun titre",
                    messageVide = "",
                    entete = {
                        Column {
                            EnteteCollection(
                                artiste.nom, artiste.sousTitre, artiste.pochette, Icons.Rounded.Person
                            )
                            LigneAlbumsDeLArtiste(artiste.pistes)
                        }
                    }
                )
            }
        }
    }
}

/** Les albums d'un artiste, en raccourcis cliquables au-dessus de ses titres. */
@Composable
private fun LigneAlbumsDeLArtiste(pistes: List<Piste>) {
    val albums = remember(pistes) {
        pistes.groupBy { it.album }.entries.sortedBy { it.key.lowercase() }
    }
    if (albums.size <= 1) return
    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(albums, key = { it.key }) { (nom, sesPistes) ->
            val artiste = sesPistes.first().artisteAlbum.ifBlank { sesPistes.first().artisteAffiche }
            Column(
                Modifier
                    .width(112.dp)
                    .clickable { Navigateur.aller(Ecran.Album("$nom|$artiste")) }
            ) {
                Pochette(
                    sesPistes.firstNotNullOfOrNull { it.pochette },
                    Modifier.size(112.dp),
                    icone = Icons.Rounded.Album
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    nom,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@UnstableApi
@Composable
fun EcranCollectionGenre(cle: String) {
    val actions = LocalActions.current
    val genres by actions.depot.genres.collectAsStateWithLifecycle()
    val genre = genres.firstOrNull { it.cle == cle }

    EchafaudageDetail(titre = genre?.nom ?: "Genre") { marges ->
        Box(Modifier.padding(marges)) {
            if (genre == null) {
                MessageVide("Genre introuvable", "")
            } else {
                ListeDePistes(
                    pistes = genre.pistes,
                    titreVide = "Aucun titre",
                    messageVide = "",
                    entete = {
                        EnteteCollection(genre.nom, genre.sousTitre, genre.pochette, Icons.Rounded.Category)
                    }
                )
            }
        }
    }
}

/**
 * Un dossier : ses sous-dossiers d'abord, ses titres ensuite.
 *
 * « Tout lire » et « Aléatoire » portent ici sur le contenu **récursif** — c'est ce qu'on attend
 * en ouvrant un dossier d'artiste rempli de sous-dossiers d'albums.
 */
@UnstableApi
@Composable
fun EcranDossier(chemin: String) {
    val actions = LocalActions.current
    val reglages = LocalReglages.current
    val etatLecture = LocalEtatLecture.current
    val toutes by actions.depot.pistes.collectAsStateWithLifecycle()
    val favoris by actions.depot.favoris.collectAsStateWithLifecycle()
    val enPlaylist by actions.depot.urisEnPlaylist.collectAsStateWithLifecycle()

    val sousDossiers = remember(toutes, chemin) { actions.depot.sousDossiers(chemin) }
    val pistes = remember(toutes, chemin, reglages.tri, reglages.triCroissant) {
        actions.depot.pistesDuDossier(chemin).trier(reglages.tri, reglages.triCroissant)
    }
    val recursives = remember(toutes, chemin) { actions.depot.pistesRecursives(chemin) }

    EchafaudageDetail(titre = chemin.substringAfterLast('/')) { marges ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(marges)
        ) {
            item {
                Text(
                    chemin,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                EnteteListe(
                    nombre = recursives.size,
                    duree = recursives.sumOf { it.duree },
                    onToutLire = { actions.lecteur.toutLire(recursives) },
                    onMelanger = { actions.lecteur.toutMelanger(recursives) }
                )
            }
            items(sousDossiers, key = { it.cle }) { sous ->
                LigneCollection(
                    nom = sous.nom,
                    sousTitre = sous.sousTitre,
                    pochette = sous.pochette,
                    icone = Icons.Rounded.Folder,
                    onClick = { Navigateur.aller(Ecran.Dossier(sous.cle)) }
                )
            }
            itemsIndexed(pistes, key = { _, piste -> piste.uri }) { index, piste ->
                LignePiste(
                    piste = piste,
                    enCours = etatLecture.piste?.uri == piste.uri,
                    afficherQualite = reglages.afficherQualite,
                    favori = piste.uri in favoris,
                    orpheline = piste.uri !in enPlaylist,
                    onClick = { actions.lecteur.jouer(pistes, index) },
                    onMenu = { actions.ouvrirMenuPiste(piste) }
                )
            }
            item { Box(Modifier.height(90.dp)) }
        }
    }
}

/** Une playlist : ordre manuel, renommage, export `.m3u`. */
@UnstableApi
@Composable
fun EcranPlaylist(id: Long) {
    val actions = LocalActions.current
    val reglages = LocalReglages.current
    val etatLecture = LocalEtatLecture.current
    val portee = rememberCoroutineScope()
    val playlists by actions.depot.playlists.collectAsStateWithLifecycle()
    val favoris by actions.depot.favoris.collectAsStateWithLifecycle()
    val enPlaylist by actions.depot.urisEnPlaylist.collectAsStateWithLifecycle()
    val garnie = playlists.firstOrNull { it.playlist.id == id }

    var menu by remember { mutableStateOf(false) }
    var renommage by remember { mutableStateOf(false) }
    var suppression by remember { mutableStateOf(false) }

    val export = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("audio/x-mpegurl")
    ) { uri ->
        if (uri != null && garnie != null) {
            portee.launch {
                actions.depot.exporterPlaylist(garnie, uri)
                actions.message("Playlist exportée")
            }
        }
    }

    EchafaudageDetail(
        titre = garnie?.playlist?.nom ?: "Playlist",
        actionsBarre = {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "Menu")
            }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Renommer") },
                    leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                    onClick = { menu = false; renommage = true }
                )
                DropdownMenuItem(
                    text = { Text("Exporter en .m3u") },
                    leadingIcon = { Icon(Icons.Rounded.Save, null) },
                    onClick = {
                        menu = false
                        export.launch("${garnie?.playlist?.nom ?: "playlist"}.m3u")
                    }
                )
                DropdownMenuItem(
                    text = { Text("Supprimer la playlist") },
                    leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                    onClick = { menu = false; suppression = true }
                )
            }
        }
    ) { marges ->
        if (garnie == null) {
            Box(Modifier.padding(marges)) {
                MessageVide("Playlist introuvable", "")
            }
            return@EchafaudageDetail
        }
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(marges)
        ) {
            item {
                EnteteCollection(
                    garnie.playlist.nom,
                    "${garnie.pistes.size} titres · ${formaterDureeLongue(garnie.pistes.sumOf { it.duree })}",
                    garnie.pistes.firstNotNullOfOrNull { it.pochette },
                    Icons.Rounded.Album
                )
                EnteteListe(
                    nombre = garnie.pistes.size,
                    duree = garnie.pistes.sumOf { it.duree },
                    onToutLire = { actions.lecteur.toutLire(garnie.pistes) },
                    onMelanger = { actions.lecteur.toutMelanger(garnie.pistes) }
                )
            }
            itemsIndexed(garnie.pistes, key = { index, piste -> "$index-${piste.uri}" }) { index, piste ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        LignePiste(
                            piste = piste,
                            enCours = etatLecture.piste?.uri == piste.uri,
                            afficherQualite = reglages.afficherQualite,
                            favori = piste.uri in favoris,
                            orpheline = piste.uri !in enPlaylist,
                            onClick = { actions.lecteur.jouer(garnie.pistes, index) },
                            onMenu = { actions.ouvrirMenuPiste(piste) }
                        )
                    }
                    Column {
                        IconButton(
                            onClick = { portee.launch { actions.depot.deplacerDansPlaylist(id, index, index - 1) } },
                            enabled = index > 0
                        ) { Icon(Icons.Rounded.ArrowUpward, contentDescription = "Monter", Modifier.size(18.dp)) }
                        IconButton(
                            onClick = { portee.launch { actions.depot.deplacerDansPlaylist(id, index, index + 1) } },
                            enabled = index < garnie.pistes.lastIndex
                        ) { Icon(Icons.Rounded.ArrowDownward, contentDescription = "Descendre", Modifier.size(18.dp)) }
                    }
                    IconButton(onClick = { portee.launch { actions.depot.retirerDePlaylist(id, index) } }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Retirer", Modifier.size(18.dp))
                    }
                }
            }
            item { Box(Modifier.height(90.dp)) }
        }
    }

    if (renommage && garnie != null) {
        DialogueTexte(
            titre = "Renommer la playlist",
            valeurInitiale = garnie.playlist.nom,
            onValider = { nom ->
                portee.launch { actions.depot.renommerPlaylist(id, nom) }
                renommage = false
            },
            onFermer = { renommage = false }
        )
    }

    if (suppression) {
        DialogueConfirmation(
            titre = "Supprimer la playlist ?",
            message = "Les fichiers audio ne sont pas touchés, seule la liste disparaît.",
            onConfirmer = {
                portee.launch { actions.depot.supprimerPlaylist(id) }
                suppression = false
                Navigateur.retour()
            },
            onFermer = { suppression = false }
        )
    }
}

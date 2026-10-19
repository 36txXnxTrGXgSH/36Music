package com.music36.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.music36.app.data.rechercher

/**
 * Recherche sur toute la bibliothèque.
 *
 * La comparaison se fait sans accents ni casse, et chaque mot saisi doit se retrouver quelque
 * part dans la fiche : « bea rev » trouve « Revolver » des Beatles.
 */
@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun EcranRecherche() {
    val actions = LocalActions.current
    val reglages = LocalReglages.current
    val etatLecture = LocalEtatLecture.current
    val pistes by actions.depot.pistes.collectAsStateWithLifecycle()
    val favoris by actions.depot.favoris.collectAsStateWithLifecycle()
    val enPlaylist by actions.depot.urisEnPlaylist.collectAsStateWithLifecycle()
    var requete by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val resultats = remember(requete, pistes) { pistes.rechercher(requete) }
    val albums = remember(resultats) {
        resultats.map { it.album to it.artisteAffiche }.distinct().take(8)
    }
    val artistes = remember(resultats) { resultats.map { it.artisteAffiche }.distinct().take(8) }
    val suggestions = remember(pistes) { actions.depot.recemmentJouees(12) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { Navigateur.retour() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
                    }
                },
                title = {
                    TextField(
                        value = requete,
                        onValueChange = { requete = it },
                        placeholder = { Text("Titre, artiste, album, genre…") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focus),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        trailingIcon = {
                            if (requete.isNotEmpty()) {
                                IconButton(onClick = { requete = "" }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Effacer")
                                }
                            } else {
                                Icon(Icons.Rounded.Search, contentDescription = null)
                            }
                        }
                    )
                }
            )
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
            if (requete.isBlank()) {
                if (suggestions.isEmpty()) {
                    MessageVide("Rechercher", "Tapez quelques lettres pour trouver un titre.")
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        item {
                            Titre("Écoutés récemment")
                        }
                        itemsIndexed(suggestions, key = { _, p -> p.uri }) { index, piste ->
                            LignePiste(
                                piste = piste,
                                enCours = etatLecture.piste?.uri == piste.uri,
                                afficherQualite = reglages.afficherQualite,
                                favori = piste.uri in favoris,
                                orpheline = piste.uri !in enPlaylist,
                                onClick = { actions.lecteur.jouer(suggestions, index) },
                                onMenu = { actions.ouvrirMenuPiste(piste) }
                            )
                        }
                        item { Box(Modifier.height(90.dp)) }
                    }
                }
            } else if (resultats.isEmpty()) {
                MessageVide("Aucun résultat", "Rien ne correspond à « $requete ».")
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    if (artistes.isNotEmpty()) {
                        item { Titre("Artistes") }
                        items(artistes, key = { "artiste-$it" }) { nom ->
                            LigneCollection(
                                nom = nom,
                                sousTitre = "Artiste",
                                pochette = resultats.firstOrNull { it.artisteAffiche == nom }?.pochette,
                                icone = Icons.Rounded.Person,
                                onClick = { Navigateur.aller(Ecran.Artiste(nom)) }
                            )
                        }
                    }
                    if (albums.isNotEmpty()) {
                        item { Titre("Albums") }
                        items(albums, key = { "album-${it.first}-${it.second}" }) { (album, artiste) ->
                            LigneCollection(
                                nom = album,
                                sousTitre = artiste,
                                pochette = resultats.firstOrNull { it.album == album }?.pochette,
                                icone = Icons.Rounded.Album,
                                onClick = {
                                    val piste = resultats.first { it.album == album }
                                    Navigateur.aller(
                                        Ecran.Album(
                                            "${piste.album}|${piste.artisteAlbum.ifBlank { piste.artisteAffiche }}"
                                        )
                                    )
                                }
                            )
                        }
                    }
                    item { Titre("Titres") }
                    itemsIndexed(resultats, key = { _, p -> p.uri }) { index, piste ->
                        LignePiste(
                            piste = piste,
                            enCours = etatLecture.piste?.uri == piste.uri,
                            afficherQualite = reglages.afficherQualite,
                            favori = piste.uri in favoris,
                            orpheline = piste.uri !in enPlaylist,
                            onClick = { actions.lecteur.jouer(resultats, index) },
                            onMenu = { actions.ouvrirMenuPiste(piste) }
                        )
                    }
                    item { Box(Modifier.height(90.dp)) }
                }
            }
        }
    }
}

@Composable
private fun Titre(texte: String) {
    Text(
        texte,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

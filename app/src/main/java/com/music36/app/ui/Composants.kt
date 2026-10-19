package com.music36.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.music36.app.data.Piste
import com.music36.app.data.PlaylistGarnie
import com.music36.app.data.Tri
import com.music36.app.data.formaterDuree
import com.music36.app.data.formaterDureeLongue
import com.music36.app.lecture.EtatLecture
import androidx.compose.material.icons.rounded.PlaylistRemove
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi

/** Pochette d'album, avec la note de musique en repli quand le fichier n'en porte pas. */
@Composable
fun Pochette(
    pochette: String?,
    modifier: Modifier = Modifier,
    coins: Dp = 10.dp,
    icone: ImageVector = Icons.Rounded.MusicNote
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(coins))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (pochette != null) {
            AsyncImage(
                model = pochette,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        } else {
            Icon(
                icone,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxSize(0.5f)
            )
        }
    }
}

/** Une ligne de la liste des titres. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LignePiste(
    piste: Piste,
    enCours: Boolean,
    afficherQualite: Boolean,
    favori: Boolean,
    /** Vrai quand le titre n'appartient à aucune playlist. */
    orpheline: Boolean = false,
    /** Appui maintenu : propose d'effacer le fichier, après confirmation. */
    onLongClick: () -> Unit = {},
    onClick: () -> Unit,
    onMenu: () -> Unit
) {
    val couleurTitre = if (enCours) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurface

    ListItem(
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        leadingContent = {
            Box(contentAlignment = Alignment.Center) {
                Pochette(piste.pochette, Modifier.size(48.dp))
                if (enCours) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = "En lecture",
                            tint = androidx.compose.ui.graphics.Color.White
                        )
                    }
                }
            }
        },
        headlineContent = {
            Text(
                piste.titre,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = couleurTitre,
                fontWeight = if (enCours) FontWeight.SemiBold else FontWeight.Normal
            )
        },
        supportingContent = {
            Column {
                Text(
                    "${piste.artisteAffiche} · ${piste.album}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall
                )
                if (afficherQualite) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (piste.hauteResolution) {
                            EtiquetteHauteResolution()
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            listOfNotNull(
                                formaterDuree(piste.duree),
                                piste.qualite().takeIf { it.isNotBlank() }
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Les titres rangés dans aucune playlist se repèrent à cette marque : c'est tout
                // son rôle, elle n'apparaît que sur eux.
                if (orpheline) {
                    Icon(
                        Icons.Rounded.PlaylistRemove,
                        contentDescription = "N'appartient à aucune playlist",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                }
                if (favori) {
                    Icon(
                        Icons.Rounded.Favorite,
                        contentDescription = "Favori",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (!afficherQualite) {
                    Text(
                        formaterDuree(piste.duree),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onMenu) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Autres actions")
                }
            }
        }
    )
}

/** Le petit badge « Hi-Res » des fichiers au-delà du CD. */
@Composable
fun EtiquetteHauteResolution() {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            "Hi-Res",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
        )
    }
}

/**
 * L'entête commun à tous les onglets : le décompte, puis « Tout lire » et « Aléatoire ».
 *
 * Les deux boutons sont posés au même endroit partout — sur les titres comme sur un album ou un
 * dossier — pour qu'on n'ait jamais à les chercher.
 */
@Composable
fun EnteteListe(
    nombre: Int,
    duree: Long,
    onToutLire: () -> Unit,
    onMelanger: () -> Unit
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            "$nombre titre${if (nombre > 1) "s" else ""}" +
                if (duree > 0) " · ${formaterDureeLongue(duree)}" else "",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onToutLire, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Tout lire")
            }
            FilledTonalButton(onClick = onMelanger, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.Shuffle, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Aléatoire")
            }
        }
    }
}

/** Ligne d'un regroupement : album, artiste, genre, dossier ou playlist. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LigneCollection(
    nom: String,
    sousTitre: String,
    pochette: String?,
    icone: ImageVector,
    /** Appui maintenu : propose de supprimer, après confirmation. */
    onLongClick: () -> Unit = {},
    onClick: () -> Unit,
    action: (@Composable () -> Unit)? = null
) {
    ListItem(
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        leadingContent = { Pochette(pochette, Modifier.size(48.dp), icone = icone) },
        headlineContent = { Text(nom, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                sousTitre,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall
            )
        },
        trailingContent = action
    )
}

/** Le bandeau de lecture posé au bas de l'écran principal. */
@Composable
fun MiniLecteur(
    etat: EtatLecture,
    onOuvrir: () -> Unit,
    onBasculer: () -> Unit,
    onSuivant: () -> Unit
) {
    AnimatedVisibility(
        visible = etat.actif,
        enter = slideInVertically { it },
        exit = slideOutVertically { it }
    ) {
        val piste = etat.piste
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .clickable(onClick = onOuvrir),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(8.dp)
                ) {
                    Pochette(piste?.pochette, Modifier.size(44.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            piste?.titre.orEmpty(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            piste?.artisteAffiche.orEmpty(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onBasculer) {
                        Icon(
                            if (etat.enLecture) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (etat.enLecture) "Pause" else "Lecture",
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    IconButton(onClick = onSuivant) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = "Suivant")
                    }
                }
                LinearProgressIndicator(
                    progress = {
                        if (etat.duree > 0) (etat.position.toFloat() / etat.duree).coerceIn(0f, 1f)
                        else 0f
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                )
            }
        }
    }
}

/** Écran vide : ce qu'on voit tant qu'aucun dossier n'a été choisi. */
@Composable
fun MessageVide(
    titre: String,
    message: String,
    libelleAction: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Rounded.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Text(
            titre,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (libelleAction != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            Button(onClick = onAction) { Text(libelleAction) }
        }
    }
}

/** Feuille d'actions d'un titre. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeuillePiste(
    piste: Piste,
    favori: Boolean,
    onFermer: () -> Unit,
    onJouerEnsuite: () -> Unit,
    onAjouterALaFile: () -> Unit,
    onAjouterAPlaylist: () -> Unit,
    onFavori: () -> Unit,
    onAlbum: () -> Unit,
    onArtiste: () -> Unit,
    onInfos: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onFermer) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Pochette(piste.pochette, Modifier.size(52.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(piste.titre, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    piste.artisteAffiche,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        ActionFeuille(Icons.AutoMirrored.Rounded.PlaylistPlay, "Lire ensuite", onJouerEnsuite)
        ActionFeuille(Icons.AutoMirrored.Rounded.QueueMusic, "Ajouter à la file d'attente", onAjouterALaFile)
        ActionFeuille(Icons.AutoMirrored.Rounded.PlaylistAdd, "Ajouter à une playlist", onAjouterAPlaylist)
        ActionFeuille(
            if (favori) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            if (favori) "Retirer des favoris" else "Ajouter aux favoris",
            onFavori
        )
        ActionFeuille(Icons.Rounded.Album, "Aller à l'album", onAlbum)
        ActionFeuille(Icons.Rounded.Person, "Aller à l'artiste", onArtiste)
        ActionFeuille(Icons.Rounded.Info, "Informations sur le fichier", onInfos)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun ActionFeuille(icone: ImageVector, libelle: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Icon(icone, contentDescription = null) },
        headlineContent = { Text(libelle) }
    )
}

/** Fiche technique d'un fichier : ce que l'analyse a lu, et ce que le lecteur en fait. */
@androidx.media3.common.util.UnstableApi
@Composable
fun DialogueInfos(piste: Piste, onFermer: () -> Unit) {
    val decodeur by com.music36.app.lecture.SortieAudio.decodeur
        .collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest = onFermer,
        confirmButton = { TextButton(onClick = onFermer) { Text("Fermer") } },
        title = { Text("Informations") },
        text = {
            Column {
                LigneInfo("Titre", piste.titre)
                LigneInfo("Artiste", piste.artisteAffiche)
                LigneInfo("Album", piste.album)
                if (piste.artisteAlbum.isNotBlank()) LigneInfo("Artiste de l'album", piste.artisteAlbum)
                if (piste.genre.isNotBlank()) LigneInfo("Genre", piste.genre)
                if (piste.annee > 0) LigneInfo("Année", piste.annee.toString())
                if (piste.numeroPiste > 0) LigneInfo("Piste", piste.numeroPiste.toString())
                LigneInfo("Durée", formaterDuree(piste.duree))
                LigneInfo("Qualité", piste.qualite().ifBlank { "inconnue" })
                LigneInfo("Taille", "%.1f Mo".format(piste.taille / 1_048_576.0))
                LigneInfo("Décodeur", decodeur ?: "aucun — format non pris en charge")
                LigneInfo("Fichier", piste.nomFichier)
                LigneInfo("Dossier", piste.dossier)
            }
        }
    )
}

@Composable
private fun LigneInfo(cle: String, valeur: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text(
            "$cle : ",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(valeur, style = MaterialTheme.typography.bodySmall)
    }
}

/** Choix d'une playlist existante, ou création à la volée. */
@Composable
fun DialogueChoixPlaylist(
    playlists: List<PlaylistGarnie>,
    /** Les playlists cochées, toutes à la fois : ranger un titre dans trois listes est un geste. */
    onChoisir: (List<Long>) -> Unit,
    onCreer: (String) -> Unit,
    onFermer: () -> Unit
) {
    var creation by remember { mutableStateOf(false) }
    var nom by remember { mutableStateOf("") }
    val choisies = remember { mutableStateListOf<Long>() }

    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text(if (creation) "Nouvelle playlist" else "Ajouter à une playlist") },
        text = {
            if (creation) {
                OutlinedTextField(
                    value = nom,
                    onValueChange = { nom = it },
                    label = { Text("Nom") },
                    singleLine = true
                )
            } else {
                LazyColumn(Modifier.height(320.dp)) {
                    item {
                        ListItem(
                            modifier = Modifier.clickable { creation = true },
                            leadingContent = { Icon(Icons.Rounded.Add, contentDescription = null) },
                            headlineContent = { Text("Créer une playlist") }
                        )
                    }
                    items(playlists, key = { it.playlist.id }) { garnie ->
                        val id = garnie.playlist.id
                        ListItem(
                            modifier = Modifier.clickable {
                                if (id in choisies) choisies.remove(id) else choisies.add(id)
                            },
                            leadingContent = {
                                Pochette(
                                    garnie.pistes.firstNotNullOfOrNull { it.pochette },
                                    Modifier.size(40.dp),
                                    icone = Icons.AutoMirrored.Rounded.QueueMusic
                                )
                            },
                            headlineContent = { Text(garnie.playlist.nom) },
                            supportingContent = { Text("${garnie.pistes.size} titres") },
                            trailingContent = {
                                Checkbox(
                                    checked = id in choisies,
                                    onCheckedChange = {
                                        if (id in choisies) choisies.remove(id)
                                        else choisies.add(id)
                                    }
                                )
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (creation) {
                TextButton(
                    onClick = { if (nom.isNotBlank()) onCreer(nom.trim()) },
                    enabled = nom.isNotBlank()
                ) { Text("Créer") }
            } else {
                TextButton(
                    onClick = { onChoisir(choisies.toList()) },
                    enabled = choisies.isNotEmpty()
                ) {
                    Text(
                        if (choisies.size <= 1) "Ajouter"
                        else "Ajouter aux ${choisies.size} playlists"
                    )
                }
            }
        },
        dismissButton = { TextButton(onClick = onFermer) { Text("Annuler") } }
    )
}

/** Petit dialogue de saisie, pour créer ou renommer. */
@Composable
fun DialogueTexte(
    titre: String,
    valeurInitiale: String = "",
    libelle: String = "Nom",
    onValider: (String) -> Unit,
    onFermer: () -> Unit
) {
    var texte by remember { mutableStateOf(valeurInitiale) }
    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text(titre) },
        text = {
            OutlinedTextField(
                value = texte,
                onValueChange = { texte = it },
                label = { Text(libelle) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (texte.isNotBlank()) onValider(texte.trim()) },
                enabled = texte.isNotBlank()
            ) { Text("Valider") }
        },
        dismissButton = { TextButton(onClick = onFermer) { Text("Annuler") } }
    )
}

/** Choix du critère de tri et du sens. */
@Composable
fun DialogueTri(
    triCourant: Tri,
    croissant: Boolean,
    onChoisir: (Tri, Boolean) -> Unit,
    onFermer: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text("Trier par") },
        text = {
            Column {
                Tri.entries.forEach { tri ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onChoisir(tri, croissant) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = tri == triCourant,
                            onClick = { onChoisir(tri, croissant) }
                        )
                        Text(tri.libelle)
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onChoisir(triCourant, !croissant) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = croissant, onClick = { onChoisir(triCourant, true) })
                    Text("Croissant")
                    Spacer(Modifier.width(12.dp))
                    RadioButton(selected = !croissant, onClick = { onChoisir(triCourant, false) })
                    Text("Décroissant")
                }
            }
        },
        confirmButton = { TextButton(onClick = onFermer) { Text("Fermer") } }
    )
}

/** Minuteur de sommeil. */
@Composable
fun DialogueMinuteur(
    actif: Long?,
    onMinutes: (Int) -> Unit,
    onFinDePiste: () -> Unit,
    onAnnuler: () -> Unit,
    onFermer: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text("Minuteur de sommeil") },
        text = {
            Column {
                if (actif != null) {
                    val restant = ((actif - System.currentTimeMillis()) / 60_000).coerceAtLeast(0)
                    Text("Arrêt dans environ $restant min")
                    Spacer(Modifier.height(8.dp))
                }
                listOf(5, 10, 15, 30, 45, 60, 90).forEach { minutes ->
                    ListItem(
                        modifier = Modifier.clickable { onMinutes(minutes) },
                        headlineContent = { Text("$minutes minutes") }
                    )
                }
                ListItem(
                    modifier = Modifier.clickable(onClick = onFinDePiste),
                    headlineContent = { Text("À la fin du morceau") }
                )
            }
        },
        confirmButton = {
            if (actif != null) {
                TextButton(onClick = onAnnuler) { Text("Arrêter le minuteur") }
            }
        },
        dismissButton = { TextButton(onClick = onFermer) { Text("Fermer") } }
    )
}

/** Confirmation destructive (suppression d'une playlist, d'un dossier surveillé…). */
@Composable
fun DialogueConfirmation(
    titre: String,
    message: String,
    libelleConfirmer: String = "Supprimer",
    onConfirmer: () -> Unit,
    onFermer: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onFermer,
        icon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
        title = { Text(titre) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirmer) { Text(libelleConfirmer) } },
        dismissButton = { TextButton(onClick = onFermer) { Text("Annuler") } }
    )
}

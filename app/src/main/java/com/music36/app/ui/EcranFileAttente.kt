package com.music36.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi

/** La file d'attente : ce qui va être joué, dans l'ordre où ça va l'être. */
@UnstableApi
@Composable
fun EcranFileAttente() {
    val actions = LocalActions.current
    val reglages = LocalReglages.current
    val etat = LocalEtatLecture.current
    val favoris by actions.depot.favoris.collectAsStateWithLifecycle()
    val enPlaylist by actions.depot.urisEnPlaylist.collectAsStateWithLifecycle()
    val liste = rememberLazyListState()

    LaunchedEffect(etat.index) {
        if (etat.index >= 0 && etat.index < etat.file.size) {
            runCatching { liste.scrollToItem(etat.index) }
        }
    }

    EchafaudageDetail(
        titre = "File d'attente",
        actionsBarre = {
            IconButton(onClick = { actions.lecteur.viderLaFile() }) {
                Icon(Icons.Rounded.DeleteSweep, contentDescription = "Vider la file")
            }
        }
    ) { marges ->
        if (etat.file.isEmpty()) {
            Box(Modifier.padding(marges)) {
                MessageVide("File vide", "Lancez un titre pour remplir la file d'attente.")
            }
            return@EchafaudageDetail
        }
        LazyColumn(
            state = liste,
            modifier = Modifier
                .fillMaxSize()
                .padding(marges)
        ) {
            item {
                Text(
                    "${etat.file.size} titres · lecture " +
                        if (etat.aleatoire) "aléatoire" else "dans l'ordre",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            itemsIndexed(etat.file, key = { index, piste -> "$index-${piste.uri}" }) { index, piste ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        LignePiste(
                            piste = piste,
                            enCours = index == etat.index,
                            afficherQualite = reglages.afficherQualite,
                            favori = piste.uri in favoris,
                            orpheline = piste.uri !in enPlaylist,
                            onClick = { actions.lecteur.allerALIndex(index) },
                            onMenu = { actions.ouvrirMenuPiste(piste) }
                        )
                    }
                    Column {
                        IconButton(
                            onClick = { actions.lecteur.deplacerDansLaFile(index, index - 1) },
                            enabled = index > 0
                        ) {
                            Icon(Icons.Rounded.ArrowUpward, "Monter", Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = { actions.lecteur.deplacerDansLaFile(index, index + 1) },
                            enabled = index < etat.file.lastIndex
                        ) {
                            Icon(Icons.Rounded.ArrowDownward, "Descendre", Modifier.size(18.dp))
                        }
                    }
                    IconButton(onClick = { actions.lecteur.retirerDeLaFile(index) }) {
                        Icon(Icons.Rounded.Close, "Retirer", Modifier.size(18.dp))
                    }
                }
            }
            item { Box(Modifier.height(90.dp)) }
        }
    }
}

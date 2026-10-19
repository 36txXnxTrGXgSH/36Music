package com.music36.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.music36.app.data.GenreNouveaute
import com.music36.app.data.Nouveaute

/**
 * Ce qui a changé depuis la dernière fois.
 *
 * S'ouvre seul au premier démarrage suivant une mise à jour, et se retrouve ensuite dans les
 * réglages. Une seule fenêtre, quel que soit le nombre de versions sautées : dérouler une
 * fenêtre par version enterrerait la plus récente sous les anciennes.
 */
@Composable
fun DialogueNouveautes(
    versions: List<Nouveaute>,
    onFermer: () -> Unit
) {
    if (versions.isEmpty()) return
    val titre = if (versions.size == 1) "Nouveautés de la ${versions.first().nom}"
    else "Nouveautés depuis la dernière fois"

    AlertDialog(
        onDismissRequest = onFermer,
        modifier = Modifier.testTag("dialogue_nouveautes"),
        title = { Text(titre) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                versions.forEach { version ->
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Le numéro de version n'apparaît que s'il y en a plusieurs : sur une
                        // seule, le titre le dit déjà.
                        if (versions.size > 1) {
                            Text(
                                "Version ${version.nom} · ${version.date}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        version.lignes.forEach { ligne ->
                            Row(verticalAlignment = Alignment.Top) {
                                EtiquetteGenre(ligne.genre)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    ligne.texte,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onFermer, modifier = Modifier.testTag("fermer_nouveautes")) {
                Text("J'ai lu")
            }
        }
    )
}

/** La pastille qui dit d'un coup d'œil si la ligne est un ajout, un correctif ou un réglage. */
@Composable
private fun EtiquetteGenre(genre: GenreNouveaute) {
    val couleur = when (genre) {
        GenreNouveaute.AJOUT -> MaterialTheme.colorScheme.primary
        GenreNouveaute.CORRECTION -> MaterialTheme.colorScheme.tertiary
        GenreNouveaute.AMELIORATION -> MaterialTheme.colorScheme.secondary
    }
    Surface(
        color = couleur.copy(alpha = 0.16f),
        contentColor = couleur,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.width(74.dp)
    ) {
        Text(
            genre.libelle,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.Unspecified,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}

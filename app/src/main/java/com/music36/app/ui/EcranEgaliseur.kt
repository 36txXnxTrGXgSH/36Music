package com.music36.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi

/**
 * Égaliseur graphique, correcteur de graves, spatialisation et gain.
 *
 * Les bandes et leur plage viennent du matériel : un téléphone en propose cinq, un autre dix.
 * Tant que l'égaliseur est éteint, aucun effet n'est inséré dans la chaîne audio — c'est ce qui
 * garantit qu'un FLAC 24 bits sort intact par défaut.
 */
@UnstableApi
@Composable
fun EcranEgaliseur() {
    val actions = LocalActions.current
    val reglages = LocalReglages.current
    val infos by actions.app.egaliseur.infos.collectAsStateWithLifecycle()

    EchafaudageDetail(titre = "Égaliseur") { marges ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(marges)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Activer l'égaliseur", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Éteint, le son n'est retouché par rien.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = reglages.egaliseurActif,
                    onCheckedChange = { actif ->
                        actions.depot.modifierReglages { it.copy(egaliseurActif = actif) }
                    }
                )
            }

            if (!reglages.egaliseurActif) {
                Text(
                    "L'égaliseur est désactivé.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
                return@Column
            }

            if (!infos.disponible) {
                Text(
                    "Aucun égaliseur disponible sur cet appareil, ou lecture non démarrée. " +
                        "Lancez un titre puis revenez ici.",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
                return@Column
            }

            if (infos.presets.isNotEmpty()) {
                Text("Préréglages", style = MaterialTheme.typography.titleSmall)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    infos.presets.forEachIndexed { index, nom ->
                        AssistChip(
                            onClick = {
                                actions.depot.modifierReglages {
                                    it.copy(egaliseurPreset = index, egaliseurBandes = "")
                                }
                            },
                            label = { Text(nom) }
                        )
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
            }

            val bandes = reglages.bandes.ifEmpty { List(infos.bandes.size) { 0 } }
            infos.bandes.forEachIndexed { index, frequence ->
                val niveau = bandes.getOrElse(index) { 0 }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (frequence >= 1000) "${frequence / 1000} kHz" else "$frequence Hz",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.width(64.dp)
                    )
                    Slider(
                        value = niveau.toFloat(),
                        onValueChange = { valeur ->
                            val nouvelles = bandes.toMutableList()
                            while (nouvelles.size <= index) nouvelles.add(0)
                            nouvelles[index] = valeur.toInt()
                            actions.depot.modifierReglages {
                                it.copy(
                                    egaliseurBandes = nouvelles.joinToString(","),
                                    egaliseurPreset = -1
                                )
                            }
                        },
                        valueRange = infos.gainMin.toFloat()..infos.gainMax.toFloat(),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "%+.1f dB".format(niveau / 100f),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(64.dp)
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            CurseurEffet(
                "Correcteur de graves",
                reglages.bassBoost,
                0..1000
            ) { valeur -> actions.depot.modifierReglages { it.copy(bassBoost = valeur) } }

            CurseurEffet(
                "Spatialisation",
                reglages.virtualiseur,
                0..1000
            ) { valeur -> actions.depot.modifierReglages { it.copy(virtualiseur = valeur) } }

            CurseurEffet(
                "Gain sonore (mB)",
                reglages.gainSonore,
                0..2000
            ) { valeur -> actions.depot.modifierReglages { it.copy(gainSonore = valeur) } }

            Text(
                "Le déchargement audio vers le processeur de signal est automatiquement " +
                    "désactivé tant que l'égaliseur est allumé : les deux sont incompatibles.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp)
            )
            Spacer(Modifier.height(60.dp))
        }
    }
}

@Composable
private fun CurseurEffet(
    libelle: String,
    valeur: Int,
    plage: IntRange,
    onChange: (Int) -> Unit
) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text("$libelle : ${valeur * 100 / plage.last} %", style = MaterialTheme.typography.labelLarge)
        Slider(
            value = valeur.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = plage.first.toFloat()..plage.last.toFloat()
        )
    }
}

package com.music36.app.ui.retro

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** La hauteur du cadran : la même que la barre de couleurs, pour que les deux se ressemblent. */
private val HAUTEUR_CADRAN = 20.dp

/**
 * Ce qu'un écran à cadran a besoin de savoir : des bornes, un pas, et de quoi écrire la valeur.
 *
 * ⚠️ Le pas est celui du **geste**, pas de l'affichage : c'est lui qui décide du nombre de crans
 * nécessaires pour traverser la plage. Un fondu de huit secondes réglé au millième obligerait à
 * tourner huit mille fois.
 */
internal data class CadranRetro(
    val libelle: String,
    val explication: String,
    val minimum: Float,
    val maximum: Float,
    val pas: Float,
    val texte: (Float) -> String
)

/**
 * Un réglage continu, à la molette.
 *
 * Comme la barre de couleurs : rien n'est enregistré tant que le bouton central n'a pas été
 * pressé, et MENU rend la valeur d'avant. C'est ce qui permet d'essayer une vitesse de lecture
 * sans avoir à retrouver celle qu'on avait.
 */
@Composable
internal fun EcranNombreRetro(cadran: CadranRetro, valeur: Float, enLecture: Boolean) {
    val part = if (cadran.maximum > cadran.minimum) {
        ((valeur - cadran.minimum) / (cadran.maximum - cadran.minimum)).coerceIn(0f, 1f)
    } else {
        0f
    }
    val avance = MaterialTheme.colorScheme.primary
    val reste = MaterialTheme.colorScheme.surfaceContainerHighest

    Column(Modifier.fillMaxSize()) {
        BarreTitreRetro(cadran.libelle, enLecture)

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (cadran.explication.isNotBlank()) {
                Text(
                    cadran.explication,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3
                )
            }

            Text(
                cadran.texte(valeur),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = avance,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("valeur_cadran")
            )

            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(HAUTEUR_CADRAN)
                    .testTag("cadran")
            ) {
                val epaisseur = size.height * 0.45f
                val haut = (size.height - epaisseur) / 2f
                drawRoundRect(
                    color = reste,
                    topLeft = Offset(0f, haut),
                    size = Size(size.width, epaisseur),
                    cornerRadius = CornerRadius(epaisseur / 2f)
                )
                if (part > 0f) {
                    drawRoundRect(
                        color = avance,
                        topLeft = Offset(0f, haut),
                        size = Size(size.width * part, epaisseur),
                        cornerRadius = CornerRadius(epaisseur / 2f)
                    )
                }
                drawCircle(
                    color = avance,
                    radius = size.height * 0.34f,
                    center = Offset(size.width * part, size.height / 2f)
                )
            }

            // Les bornes : sans elles, on ne sait pas s'il reste de la marge à tourner.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    cadran.texte(cadran.minimum),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    cadran.texte(cadran.maximum),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                "Tournez pour régler · centre pour valider · MENU pour revenir sans rien changer",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

package com.music36.app.ui.retro

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.music36.app.data.ThemeCouleur
import com.music36.app.ui.paletteDe

/** La hauteur de la barre de couleurs : assez haute pour viser à la molette sans loupe. */
private val HAUTEUR_BARRE_TEINTE = 46.dp

/** Le nombre de bandes dessinées : une tous les six degrés, soit tout le cercle. */
private const val BANDES = 60

/**
 * La barre de couleurs d'une ambiance, parcourue à la molette.
 *
 * ⚠️ Rien n'est enregistré tant que le bouton central n'est pas pressé. C'est ce qui permet de
 * faire tout le tour du cercle pour voir, puis de repartir avec MENU sans avoir rien changé. Les
 * couleurs montrées ici sont **calculées** avec l'ambiance choisie, pas approchées : ce que l'on
 * voit dans l'aperçu est exactement ce que l'application portera après validation.
 */
@Composable
internal fun EcranTeinteRetro(
    theme: ThemeCouleur,
    teinte: Float,
    sombre: Boolean,
    enLecture: Boolean
) {
    val couleurs = paletteDe(theme, teinte, sombre)

    Column(Modifier.fillMaxSize()) {
        BarreTitreRetro(theme.libelle, enLecture)

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                theme.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )

            BarreDeTeintes(theme, teinte, sombre)

            // Le nom et le degré : le nom se retient, le degré se retrouve. Sans l'un ni
            // l'autre, revenir sur une couleur essayée dix crans plus tôt tient du hasard.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    nomDeTeinte(teinte),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = couleurs.primary,
                    modifier = Modifier.testTag("nom_teinte")
                )
                Text(
                    "${teinte.toInt()}°",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // L'aperçu : une ligne de menu comme celles du reste de l'application, peinte avec
            // les couleurs candidates. Un simple carré de couleur ne dirait pas si le texte
            // reste lisible — or c'est la seule chose qui compte vraiment.
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("apercu_teinte"),
                shape = RoundedCornerShape(8.dp),
                color = couleurs.surface,
                contentColor = couleurs.onSurface
            ) {
                Column(Modifier.padding(10.dp)) {
                    Text(
                        "Aperçu",
                        style = MaterialTheme.typography.labelSmall,
                        color = couleurs.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(26.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(couleurs.primary)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Un titre de morceau",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = couleurs.onSurface,
                                maxLines = 1
                            )
                            Text(
                                "Son artiste",
                                style = MaterialTheme.typography.bodySmall,
                                color = couleurs.primary,
                                maxLines = 1
                            )
                        }
                        Box(
                            Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(couleurs.secondary)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(couleurs.surfaceContainerHighest)
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(0.45f)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(couleurs.primary)
                        )
                    }
                }
            }

            Text(
                "Tournez la molette pour changer de couleur · centre pour valider · " +
                    "MENU pour revenir sans rien changer",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Le cercle chromatique déroulé, à la saturation de l'ambiance.
 *
 * ⚠️ Les bandes sont peintes avec l'accent **réel** de chaque teinte — celui que produirait le
 * générateur de palettes — et non avec un arc-en-ciel décoratif. Sur une ambiance peu saturée,
 * la barre est donc presque grise : c'est exactement ce que l'on obtiendra.
 */
@Composable
private fun BarreDeTeintes(theme: ThemeCouleur, teinte: Float, sombre: Boolean) {
    val bandes = remembrerBandes(theme, sombre)
    val bordure = MaterialTheme.colorScheme.outlineVariant
    val curseur = MaterialTheme.colorScheme.onSurface
    val choisie = ((teinte % 360f) + 360f) % 360f

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(HAUTEUR_BARRE_TEINTE)
            .testTag("barre_teintes")
    ) {
        val largeurBande = size.width / BANDES
        bandes.forEachIndexed { index, couleur ->
            drawRect(
                color = couleur,
                topLeft = Offset(index * largeurBande, 0f),
                size = Size(largeurBande + 1f, size.height)
            )
        }
        drawRoundRect(
            color = bordure,
            cornerRadius = CornerRadius(6f),
            style = Stroke(width = 2f)
        )

        // Le curseur : un cadre clair posé sur la bande retenue. Une flèche sous la barre
        // obligerait à regarder ailleurs que la couleur elle-même.
        val x = (choisie / 360f) * size.width
        drawRoundRect(
            color = curseur,
            topLeft = Offset((x - largeurBande).coerceIn(0f, size.width - largeurBande * 2f), 0f),
            size = Size(largeurBande * 2f, size.height),
            cornerRadius = CornerRadius(4f),
            style = Stroke(width = 4f)
        )
    }
}

/** Les soixante accents du cercle, calculés une fois par ambiance et par mode. */
@Composable
private fun remembrerBandes(theme: ThemeCouleur, sombre: Boolean): List<Color> =
    remember(theme, sombre) {
        (0 until BANDES).map { index ->
            paletteDe(theme, index * 360f / BANDES, sombre).primary
        }
    }

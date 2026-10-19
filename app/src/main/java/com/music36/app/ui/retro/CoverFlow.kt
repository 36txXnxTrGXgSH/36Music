package com.music36.app.ui.retro

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.music36.app.data.Collection
import com.music36.app.ui.Pochette
import kotlin.math.abs
import kotlin.math.sign

/**
 * Le carrousel de pochettes, façon Cover Flow.
 *
 * Les pochettes sont posées à plat dans un `Box` et inclinées en perspective par `graphicsLayer` :
 * pas de liste défilante. C'est ce qui permet de faire glisser la sélection **d'un cran de
 * molette** avec une animation continue, et de laisser les cartes se chevaucher comme sur
 * l'appareil d'origine — deux choses qu'une `LazyRow` rend inutilement compliquées.
 *
 * Seule une fenêtre de quelques pochettes autour de la sélection est dessinée : une discothèque
 * de mille albums ne coûte pas plus cher qu'une de dix.
 */
@Composable
fun VueCoverFlow(
    albums: List<Collection>,
    selection: Int,
    modifier: Modifier = Modifier,
    attenue: Boolean = false
) {
    if (albums.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Aucun album",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    // La position glisse d'un cran à l'autre : c'est elle qui donne le mouvement, la sélection
    // n'étant qu'un entier.
    val position by animateFloatAsState(
        targetValue = selection.toFloat(),
        animationSpec = tween(durationMillis = 220),
        label = "coverflow"
    )
    val courant = albums.getOrNull(selection)

    BoxWithConstraints(modifier.fillMaxSize()) {
        val cote = minOf(maxWidth * 0.52f, (maxHeight - HAUTEUR_TITRE) * 0.72f)

        // ⚠️ `fillMaxSize` et non `fillMaxWidth` : sans hauteur pleine, la boîte se réduit à son
        // contenu et se colle en haut du cadre — c'est ce qui décentrait les pochettes. Le
        // rembourrage du bas réserve la place du titre.
        Box(
            Modifier
                .fillMaxSize()
                .padding(bottom = HAUTEUR_TITRE)
                .alpha(if (attenue) 0.35f else 1f),
            contentAlignment = Alignment.Center
        ) {
            val premier = (selection - FENETRE).coerceAtLeast(0)
            val dernier = (selection + FENETRE).coerceAtMost(albums.lastIndex)

            // Les cartes les plus éloignées sont dessinées en premier : sans cet ordre, une
            // pochette de bord passerait devant celle du centre.
            (premier..dernier)
                .sortedByDescending { abs(it - position) }
                .forEach { index ->
                    val ecart = index - position
                    val ecartBorne = ecart.coerceIn(-1f, 1f)
                    Pochette(
                        albums[index].pochette,
                        Modifier
                            .size(cote)
                            .zIndex(-abs(ecart))
                            .graphicsLayer {
                                cameraDistance = 14f * density
                                rotationY = -sign(ecart) * ROTATION * abs(ecartBorne)
                                val reduction = 1f - 0.24f * abs(ecartBorne)
                                scaleX = reduction
                                scaleY = reduction
                                translationX = ecart * size.width * CHEVAUCHEMENT +
                                    ecartBorne * size.width * ECART_CENTRAL
                                alpha = 1f - 0.12f * (abs(ecart) - 1f).coerceIn(0f, FENETRE.toFloat())
                            },
                        coins = 2.dp,
                        icone = Icons.Rounded.Album
                    )
                }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom
        ) {
            Text(
                courant?.nom.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                courant?.cle?.substringAfter('|').orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${selection + 1} / ${albums.size}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * La grande lettre affichée pendant un défilement rapide.
 *
 * Sur une liste de plusieurs centaines de titres, la seule information utile en cours de
 * défilement est l'initiale : les libellés passent trop vite pour être lus.
 */
@Composable
fun LettreRepere(lettre: String?, modifier: Modifier = Modifier) {
    if (lettre.isNullOrBlank()) return
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
        ) {
            Text(
                lettre,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 26.dp, vertical = 12.dp)
            )
        }
    }
}

/** La place réservée sous les pochettes, pour le nom de l'album et son artiste. */
private val HAUTEUR_TITRE = 58.dp

/** Nombre de pochettes dessinées de part et d'autre de la sélection. */
private const val FENETRE = 4

private const val ROTATION = 58f
private const val CHEVAUCHEMENT = 0.30f
private const val ECART_CENTRAL = 0.22f

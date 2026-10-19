package com.music36.app.ui.retro

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.music36.app.data.formaterDuree
import com.music36.app.lecture.SortieAudio
import com.music36.app.ui.LocalActions
import com.music36.app.ui.LocalEtatLecture
import com.music36.app.ui.Pochette
import kotlinx.coroutines.launch
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material.icons.rounded.PlaylistRemove
import androidx.compose.material.icons.automirrored.rounded.PlaylistAddCheck
import com.music36.app.data.messageAppartenance
import com.music36.app.data.pluriel
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import com.music36.app.data.COULEURS_FOND
import com.music36.app.data.EtatReglages
import com.music36.app.data.FondLecteur
import com.music36.app.ui.LocalReglages
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.music36.app.data.StyleBarre

/**
 * L'écran de lecture de la vue molette, et les quatre boutons posés autour de la roue.
 *
 * Seul cet écran répond au doigt : les boutons commandent des réglages, jamais la navigation —
 * sinon la molette ne serait qu'un décor.
 */
/**
 * Ce que commande la rotation sur l'écran de lecture.
 *
 * Le volume est le comportement des baladeurs d'origine ; les deux autres s'activent par les boutons
 * posés autour de la molette, et rendent la roue utile là où l'appareil demandait un appui long.
 */
enum class ModeMolette(val libelle: String) {
    VOLUME("Volume"),
    POSITION("Avance / recul"),
    VITESSE("Vitesse")
}
/** Cinq secondes par cran : assez pour parcourir un morceau, assez fin pour viser un refrain. */
internal const val PAS_POSITION = 5_000L
/** La hauteur que les textes réclament autour de la pochette, sur l'écran de lecture. */
internal val RESERVE_TEXTES = 150.dp
/** Un des quatre boutons posés dans les coins, autour de la molette. */
@Composable
internal fun BoutonCoin(
    icone: ImageVector,
    description: String,
    actif: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) = PastilleCoin(actif, modifier, onClick) {
    Icon(icone, contentDescription = description, modifier = Modifier.size(22.dp))
}

/**
 * La même pastille, mais portant un mot plutôt qu'une icône.
 *
 * « ABC » ne se dessine pas : aucune icône ne dit « sauter de lettre en lettre » aussi clairement
 * que les trois lettres elles-mêmes.
 */
@Composable
internal fun BoutonCoinTexte(
    libelle: String,
    description: String,
    actif: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) = PastilleCoin(actif, modifier, onClick) {
    Text(
        libelle,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.semantics { contentDescription = description }
    )
}

@Composable
private fun PastilleCoin(
    actif: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    contenu: @Composable () -> Unit
) {
    Surface(
        modifier = modifier
            .size(46.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = if (actif) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (actif) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(
            1.dp,
            when {
                actif -> MaterialTheme.colorScheme.primary
                LocalReglages.current.lisereLecteur -> MaterialTheme.colorScheme.outlineVariant
                else -> Color.Transparent
            }
        )
    ) {
        Box(contentAlignment = Alignment.Center) { contenu() }
    }
}
/** L'écran de lecture de la vue Rétro : pochette, informations, et la jauge du mode en cours. */
@UnstableApi
@Composable
internal fun EcranLectureRetro(
    volume: Int?,
    volumeMax: Int,
    mode: ModeMolette,
    onInfos: () -> Unit,
    /** Ouvre le choix des playlists — dans le thème de la molette, pas dans une boîte Material. */
    onAjouterAPlaylist: () -> Unit = {},
    /** Ouvre la liste des playlists qui contiennent ce titre, et permet de l'en retirer. */
    onAppartenance: () -> Unit = {}
) {
    val actions = LocalActions.current
    val etat = LocalEtatLecture.current
    val portee = rememberCoroutineScope()
    val favoris by actions.depot.favoris.collectAsStateWithLifecycle()
    val sortieDecodee by SortieAudio.info.collectAsStateWithLifecycle()
    val materiel by SortieAudio.materiel.collectAsStateWithLifecycle()
    val piste = etat.piste

    if (piste == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Aucune lecture", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    // ⚠️ La position **annoncée**, décalage compris : c'est elle qui suit le doigt, alors que
    // celle du lecteur n'avancera qu'une fois le geste terminé.
    val position = (etat.position + etat.decalageEnAttente)
        .coerceIn(0L, etat.duree.coerceAtLeast(0L))
    val cible = if (etat.duree > 0) (position.toFloat() / etat.duree).coerceIn(0f, 1f) else 0f
    // ⚠️ Pas d'animation pendant qu'on cherche à la molette : chaque cran relancerait une
    // animation par-dessus la précédente, et la barre traînerait derrière le geste au lieu de
    // le suivre. En lecture normale, en revanche, l'animation lisse l'avancée seconde après seconde.
    val anime by animateFloatAsState(targetValue = cible, label = "avancement")
    val avancement = if (mode == ModeMolette.POSITION) cible else anime
    val favori = piste.uri in favoris
    val reglages = LocalReglages.current
    val playlists by actions.depot.playlists.collectAsStateWithLifecycle()
    val siennes = playlists.filter { garnie -> garnie.pistes.any { it.uri == piste.uri } }
        .map { it.playlist.nom }

    Column(Modifier.fillMaxSize()) {
        BarreTitreRetro("Lecture en cours", etat.enLecture) {
            // Favori et ajout à une playlist : les deux gestes qu'on ne veut pas aller chercher
            // dans un menu pendant qu'un morceau passe.
            IconButton(
                onClick = { portee.launch { actions.depot.basculerFavori(piste.uri) } },
                modifier = Modifier.size(34.dp).testTag("favori_retro")
            ) {
                Icon(
                    if (favori) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (favori) "Retirer des favoris"
                    else "Ajouter aux favoris",
                    tint = if (favori) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp)
                )
            }
            IconButton(
                onClick = onAjouterAPlaylist,
                modifier = Modifier.size(34.dp).testTag("playlist_retro")
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.PlaylistAdd,
                    contentDescription = "Ajouter à une playlist",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp)
                )
            }
            // Appartenance aux playlists : l'icône signale d'un coup d'œil les titres qu'on a
            // oublié de ranger, et répond dans les deux cas quand on la touche.
            IconButton(
                // ⚠️ Un seul bouton pour dire **et** défaire : il annonce l'appartenance et ouvre
                // l'écran d'où l'on retire le titre de ses playlists. Deux icônes de plus dans
                // une barre qui en porte déjà quatre n'aideraient personne.
                onClick = onAppartenance,
                modifier = Modifier.size(34.dp).testTag("appartenance_retro")
            ) {
                Icon(
                    if (siennes.isEmpty()) Icons.Rounded.PlaylistRemove
                    else Icons.AutoMirrored.Rounded.PlaylistAddCheck,
                    contentDescription = if (siennes.isEmpty())
                        "N'appartient à aucune playlist"
                    else "Appartient à ${siennes.size} playlist${pluriel(siennes.size)}",
                    tint = if (siennes.isEmpty()) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp)
                )
            }
            IconButton(
                onClick = onInfos,
                modifier = Modifier.size(34.dp).testTag("infos_retro")
            ) {
                Icon(
                    Icons.Rounded.Info,
                    contentDescription = "Informations et actions du morceau",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp)
                )
            }
        }

        // ⚠️ Le fond occupe **tout** ce qui reste sous la barre de boutons.
        //
        // Il se posait auparavant dans le seul carré de la pochette, et il restait entre
        // la barre et lui une bande vide de la couleur du thème que rien ne justifiait. Il
        // remplit maintenant le cadre entier ; les textes se posent dessus, protégés par le
        // voile que pose `FondDePochette`.
        Box(Modifier.fillMaxWidth().weight(1f)) {
            FondDePochette(piste.pochette, reglages)

            Column(Modifier.fillMaxSize()) {
                // ⚠️ Disposition en **colonne**, avec une pochette de taille calculée.
                //
                // La version précédente posait la pochette et les informations côte à
                // côte, la pochette en `fillMaxHeight().aspectRatio(1f)` : sur un écran
                // haut, elle réclamait une largeur égale à la hauteur disponible, dévorait
                // toute la rangée et la colonne de texte se retrouvait à zéro pixel. On ne
                // voyait plus que la pochette. Ici la place de chacun est décidée d'avance
                // à partir des contraintes réelles.
                BoxWithConstraints(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    // On donne à la pochette **toute** la place que les textes ne réclament
                    // pas : sur un écran haut, une fraction de la hauteur laissait deux
                    // grandes bandes vides.
                    val cote = minOf(maxWidth, maxHeight - RESERVE_TEXTES).coerceAtLeast(96.dp)

                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            piste.titre,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            piste.artisteAffiche,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(Modifier.height(8.dp))
                        Pochette(piste.pochette, Modifier.size(cote), coins = 6.dp)
                        Spacer(Modifier.height(8.dp))

                        Text(
                            listOfNotNull(
                                piste.album.takeIf { it.isNotBlank() },
                                if (piste.annee > 0) piste.annee.toString() else null
                            ).joinToString(" · ").ifBlank { "Album inconnu" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 3.dp)
                        ) {
                            if (piste.hauteResolution) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    shape = RoundedCornerShape(3.dp)
                                ) {
                                    Text(
                                        "Hi-Res",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                }
                                Spacer(Modifier.width(5.dp))
                            }
                            // La qualité décodée l'emporte sur les étiquettes du
                            // fichier : c'est ce qui sort vraiment du décodeur.
                            Text(
                                sortieDecodee?.resume()?.takeIf { it.isNotBlank() }
                                    ?: piste.qualite().ifBlank { "Qualité inconnue" },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        materiel?.let {
                            Text(
                                it.resume(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // ⚠️ **Une seule barre, quelle que soit l'information.** Volume, vitesse
                // et position se dessinaient avec trois largeurs, trois hauteurs et trois
                // placements différents : la barre sautait d'un endroit à l'autre selon le
                // mode, ce qui donnait l'impression que l'écran changeait de mise en page à
                // chaque geste.
                Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    when {
                        volume != null && mode == ModeMolette.VOLUME -> BarreRetro(
                            progression = if (volumeMax > 0) volume.toFloat() / volumeMax else 0f,
                            gauche = "Volume",
                            centre = null,
                            droite = "$volume / $volumeMax",
                            icone = Icons.AutoMirrored.Rounded.VolumeUp,
                            description = "Volume"
                        )

                        mode == ModeMolette.VITESSE -> BarreRetro(
                            progression = ((etat.vitesse - 0.5f) / 2f).coerceIn(0f, 1f),
                            gauche = "Vitesse",
                            centre = null,
                            droite = "%.2f×".format(etat.vitesse),
                            icone = Icons.Rounded.Speed,
                            description = "Vitesse"
                        )

                        else -> BarreRetro(
                            progression = avancement,
                            gauche = formaterDuree(position),
                            centre = if (mode == ModeMolette.POSITION) "± 5 s par cran"
                            else "${etat.index + 1} / ${etat.file.size}",
                            droite = "-" + formaterDuree((etat.duree - position).coerceAtLeast(0)),
                            accentuerCentre = mode == ModeMolette.POSITION
                        )
                    }
                }
            }
        }
    }
}

/**
 * La barre du lecteur : toujours la même, où qu'aille l'information.
 *
 * Pleine largeur, même hauteur, mêmes coins — et trois emplacements de texte **sous** elle, jamais
 * à côté. C'est ce qui fait qu'elle ne bouge pas d'un pixel quand on passe du volume à la
 * position, et le mode se lit alors au texte plutôt qu'à la géométrie.
 */
@Composable
internal fun BarreRetro(
    progression: Float,
    gauche: String,
    centre: String?,
    droite: String,
    icone: ImageVector? = null,
    description: String? = null,
    accentuerCentre: Boolean = false
) {
    val reglages = LocalReglages.current
    val style = StyleBarre.entries.getOrElse(reglages.styleBarre) { StyleBarre.CLASSIQUE }
    val avance = MaterialTheme.colorScheme.primary
    val reste = MaterialTheme.colorScheme.surfaceContainerHighest

    // La vague n'avance que si elle est demandée : une animation perpétuelle pour un style qu'on
    // n'a pas choisi coûterait une image à chaque battement, pour rien.
    val phase = if (style == StyleBarre.ONDULE) {
        val transition = rememberInfiniteTransition(label = "onde")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = (2f * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
            label = "phase"
        ).value
    } else {
        0f
    }

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(HAUTEUR_BARRE)
    ) {
        dessinerBarre(style, progression.coerceIn(0f, 1f), avance, reste, phase)
    }
    Spacer(Modifier.height(5.dp))
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // L'icône descend **sous** la barre au lieu de la rogner sur le côté : la barre garde
            // ainsi toute la largeur, dans tous les modes.
            if (icone != null) {
                Icon(
                    icone,
                    contentDescription = description,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(5.dp))
            }
            Text(
                gauche,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            centre.orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            color = if (accentuerCentre) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            droite,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * La hauteur de la barre, la même pour **tous** les styles.
 *
 * ⚠️ Elle vaut aussi pour le style classique, qui n'occupe que le milieu de cette bande : si
 * chaque style prenait la place qui l'arrange, l'écran se réorganiserait à chaque choix — le
 * défaut corrigé en 3.7.
 */
private val HAUTEUR_BARRE = 16.dp

/**
 * La barre de progression, selon le style choisi.
 *
 * Le curseur est toujours à la même abscisse : seule la matière change autour de lui.
 */
private fun DrawScope.dessinerBarre(
    style: StyleBarre,
    progression: Float,
    avance: Color,
    reste: Color,
    phase: Float
) {
    val largeur = size.width
    val milieu = size.height / 2f
    val curseur = largeur * progression

    when (style) {
        StyleBarre.CLASSIQUE -> {
            val epaisseur = size.height * 0.5f
            drawRoundRect(
                color = reste,
                topLeft = Offset(0f, milieu - epaisseur / 2f),
                size = Size(largeur, epaisseur),
                cornerRadius = CornerRadius(epaisseur / 2f)
            )
            if (curseur > 0f) {
                drawRoundRect(
                    color = avance,
                    topLeft = Offset(0f, milieu - epaisseur / 2f),
                    size = Size(curseur, epaisseur),
                    cornerRadius = CornerRadius(epaisseur / 2f)
                )
            }
        }

        StyleBarre.ONDULE -> {
            // ⚠️ La vague ne couvre que la partie **déjà jouée**, et la ligne reste droite
            // devant : c'est ce qui rend le curseur lisible d'un coup d'œil.
            drawLine(
                color = reste,
                start = Offset(curseur, milieu),
                end = Offset(largeur, milieu),
                strokeWidth = 4f,
                cap = StrokeCap.Round
            )
            if (curseur > 1f) {
                val trace = Path().apply { moveTo(0f, milieu) }
                val amplitude = size.height * 0.34f
                var x = 0f
                while (x <= curseur) {
                    val y = milieu + amplitude * kotlin.math.sin(x / 11f + phase)
                    trace.lineTo(x, y)
                    x += 2f
                }
                drawPath(
                    trace,
                    color = avance,
                    style = Stroke(width = 4f, cap = StrokeCap.Round)
                )
            }
            drawCircle(color = avance, radius = size.height * 0.22f, center = Offset(curseur, milieu))
        }

        StyleBarre.FORME_ONDE -> {
            // Les hauteurs sont tirées de la position du bâton, pas du hasard : elles ne
            // scintillent donc pas d'une image à l'autre.
            val pas = 7f
            var x = pas / 2f
            var graine = 1
            while (x < largeur) {
                graine = graine * 1103515245 + 12345
                val part = ((graine ushr 16) and 0x7FFF) / 32767f
                val hauteur = size.height * (0.25f + 0.70f * part)
                drawLine(
                    color = if (x <= curseur) avance else reste,
                    start = Offset(x, milieu - hauteur / 2f),
                    end = Offset(x, milieu + hauteur / 2f),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round
                )
                x += pas
            }
        }

        StyleBarre.SEGMENTS -> {
            val pas = 10f
            var x = 0f
            while (x < largeur) {
                drawLine(
                    color = if (x <= curseur) avance else reste,
                    start = Offset(x, milieu),
                    end = Offset(minOf(x + 6f, largeur), milieu),
                    strokeWidth = size.height * 0.42f,
                    cap = StrokeCap.Round
                )
                x += pas
            }
        }

        StyleBarre.POINTS -> {
            val pas = 12f
            var x = pas / 2f
            while (x < largeur) {
                drawCircle(
                    color = if (x <= curseur) avance else reste,
                    radius = size.height * 0.13f,
                    center = Offset(x, milieu)
                )
                x += pas
            }
        }

        StyleBarre.FIL -> {
            drawLine(reste, Offset(0f, milieu), Offset(largeur, milieu), strokeWidth = 2f)
            if (curseur > 0f) {
                drawLine(avance, Offset(0f, milieu), Offset(curseur, milieu), strokeWidth = 2f)
            }
            drawCircle(color = avance, radius = size.height * 0.26f, center = Offset(curseur, milieu))
        }
    }
}

/**
 * Ce qui s'affiche derrière la pochette, selon le réglage de l'interface molette.
 *
 * La pochette floutée est agrandie puis adoucie : c'est le procédé qu'emploient les lecteurs
 * vidéo pour meubler les bords d'une image qui ne remplit pas le cadre, et il donne à l'écran la
 * couleur de l'album sans rien lui emprunter d'autre.
 */
@Composable
private fun FondDePochette(pochette: String?, reglages: EtatReglages) {
    val fond = FondLecteur.entries.getOrElse(reglages.fondLecteurRetro) { FondLecteur.DEFAUT }
    when (fond) {
        FondLecteur.DEFAUT -> Unit

        FondLecteur.POCHETTE_FLOUE -> if (pochette != null) {
            Pochette(
                pochette,
                Modifier
                    .fillMaxSize()
                    .scale(1.35f)
                    .blur(22.dp)
                    .alpha(0.45f),
                coins = 0.dp
            )
            // ⚠️ Le voile n'est pas une coquetterie : le fond couvre maintenant l'écran entier,
            // titres et barre de progression compris. Sans lui, une pochette claire les rendrait
            // illisibles — c'était la raison pour laquelle il ne débordait pas de la pochette.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.32f))
            )
        }

        FondLecteur.COULEUR -> {
            val couleur = COULEURS_FOND.getOrElse(reglages.couleurFondRetro) { COULEURS_FOND.first() }
            Box(Modifier.fillMaxSize().background(Color(couleur.argb)))
        }
    }
}

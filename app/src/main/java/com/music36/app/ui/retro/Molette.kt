package com.music36.app.ui.retro

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sign
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.music36.app.data.COULEURS_MOLETTE
import com.music36.app.data.EffetMolette
import com.music36.app.data.StyleMolette
import com.music36.app.ui.LocalReglages
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf

/** Les quatre appuis de l'anneau, plus le bouton central. */
enum class ZoneMolette { MENU, PRECEDENT, SUIVANT, LECTURE, CENTRE }

/**
 * La molette cliquable de la vue Rétro.
 *
 * Deux gestes cohabitent sur la même surface :
 * - **tourner le doigt sur l'anneau** émet des crans (`onCran`, +1 dans le sens des aiguilles) ;
 * - **appuyer** sur un quartier de l'anneau ou sur le centre déclenche l'action correspondante.
 *
 * ⚠️ **Un seul détecteur, écrit à la main.** La version d'origine empilait `detectTapGestures` et
 * `detectDragGestures` sur la même surface : ils se disputaient le pointeur, et la rotation ne
 * partait jamais. Ici une seule boucle suit le doigt du poser au relever et tranche elle-même —
 * si des crans ont été émis, c'était une rotation ; sinon, et si le doigt n'a pas glissé, c'est
 * un appui. Un doigt qui tourne ne valide donc jamais une entrée par mégarde.
 *
 * Écrire le geste à la main a un second avantage : la rotation démarre **dès le premier cran**,
 * sans attendre le seuil de glissement de Compose, ce qui est le toucher de l'appareil d'origine.
 *
 * ⚠️ **`pointerInput(Unit)` fige ses lambdas à la première composition** : le bloc n'est relancé
 * que si la clé change. Passer `onCran`/`onZone` directement dedans revient à commander
 * éternellement le menu du premier affichage — c'était le défaut de la 1.1, où la sélection ne
 * descendait jamais au-delà de la deuxième ligne et où le bouton central rouvrait toujours la
 * même entrée. `rememberUpdatedState` garde la clé stable **et** appelle la version courante.
 *
 * Chaque cran s'accompagne d'un retour haptique (`CLOCK_TICK`) : sans lui, la molette est
 * injouable, on ne sait pas si le geste a été compris. C'est ce petit tic qui faisait les baladeurs à molette.
 */
@Composable
fun Molette(
    enLecture: Boolean,
    onCran: (Int) -> Unit,
    onZone: (ZoneMolette) -> Unit,
    /** Appui maintenu au centre : ouvre le menu d'actions de la ligne sélectionnée. */
    onAppuiLong: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val vue = LocalView.current
    val reglages = LocalReglages.current
    val style = StyleMolette.entries.getOrElse(reglages.styleMolette) { StyleMolette.DEFAUT }
    val effet = EffetMolette.entries.getOrElse(reglages.effetMolette) { EffetMolette.AUCUN }

    /**
     * Les points que le doigt vient de traverser, pour la traînée.
     *
     * ⚠️ **Liste ordinaire, surtout pas observable.** Avec un `mutableStateListOf`, chaque point
     * ajouté — soixante par seconde pendant qu'on tourne — recomposait toute la molette. D'où le
     * retard du doigt sur la roue, et une traînée qui restait figée à l'écran : le fil principal
     * était trop occupé pour faire tourner la boucle qui l'efface.
     *
     * Ils suivent le **doigt**, pas les crans : une traînée qui n'apparaîtrait qu'au
     * franchissement d'un cran donnerait l'impression que le geste n'est pas pris.
     */
    val traces = remember { ArrayList<TraceMolette>(MAX_TRACES) }

    /**
     * Change à chaque image, et n'est lu **que pendant le dessin**.
     *
     * C'est ce qui redessine la traînée sans rien recomposer : lire un état dans le bloc de
     * `Canvas` n'invalide que le dessin.
     */
    val battement = remember { mutableIntStateOf(0) }

    /** Vrai tant qu'il reste une traînée à éteindre. Ne change que deux fois par geste. */
    var trainee by remember { mutableStateOf(false) }

    LaunchedEffect(trainee) {
        while (trainee) {
            withFrameMillis { battement.intValue++ }
            traces.removeAll { trace -> System.currentTimeMillis() - trace.instant > DUREE_TRACE }
            if (traces.isEmpty()) trainee = false
        }
        // Une dernière image après l'extinction : sans elle, les derniers points resteraient
        // dessinés jusqu'au prochain geste — la traînée « bloquée » de la 4.1.
        battement.intValue++
    }

    // ⚠️ Même piège qu'en 1.1 : `pointerInput(Unit)` fige ce qu'il lit à la première
    // composition. Sans cela, changer d'effet dans les réglages ne produisait rien, le bloc
    // de gestes continuant de voir « sans effet ».
    val effetCourant by rememberUpdatedState(effet)
    val cranCourant by rememberUpdatedState(onCran)
    val zoneCourante by rememberUpdatedState(onZone)
    val appuiLongCourant by rememberUpdatedState(onAppuiLong)
    val teinteChoisie = COULEURS_MOLETTE.getOrNull(reglages.couleurMolette)
        ?.takeIf { reglages.couleurMolette > 0 }
        ?.let { Color(it.argb) }
    val couleurAnneau = teinteChoisie ?: MaterialTheme.colorScheme.surfaceContainerHigh
    val couleurBord = if (reglages.lisereLecteur) MaterialTheme.colorScheme.outlineVariant
    else Color.Transparent
    // ⚠️ Le bouton central porte **la couleur de l'anneau**, jamais celle du thème : sinon il
    // virait au clair ou au noir selon le thème pendant que la roue gardait sa teinte, et les
    // deux ne s'accordaient plus. Seuls le relief et le liseré l'en détachent.
    val couleurCentre = couleurAnneau
    val couleurTexte = MaterialTheme.colorScheme.onSurfaceVariant
    val couleurAccent = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val largeur = size.width.toFloat()
                    val hauteur = size.height.toFloat()

                    val pose = awaitFirstDown(requireUnconsumed = false)
                    val surLAnneau = rayonRelatif(pose.position, largeur, hauteur) in RAYON_INTERNE..1f
                    var angle = angleDe(pose.position, largeur, hauteur)
                    var accumule = 0f
                    var crans = 0
                    var glissement = 0f
                    val debutAppui = System.currentTimeMillis()
                    var appuiLongDeclenche = false
                    val auCentre = zoneDe(pose.position, largeur, hauteur) == ZoneMolette.CENTRE

                    while (true) {
                        // ⚠️ **Le doigt immobile n'envoie aucun événement.** Attendre bêtement le
                        // suivant faisait que l'appui long ne se déclenchait qu'au premier
                        // frémissement — d'où les quatre secondes ressenties au lieu de deux. On
                        // borne donc l'attente au temps restant : quand il s'épuise sans que rien
                        // ne bouge, c'est précisément qu'on a maintenu.
                        val evenement = if (!appuiLongDeclenche && auCentre && crans == 0 &&
                            glissement < viewConfiguration.touchSlop
                        ) {
                            val reste = DUREE_APPUI_LONG - (System.currentTimeMillis() - debutAppui)
                            if (reste <= 0L) null
                            else withTimeoutOrNull(reste) { awaitPointerEvent() }
                        } else {
                            awaitPointerEvent()
                        }

                        if (evenement == null) {
                            appuiLongDeclenche = true
                            vue.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            appuiLongCourant()
                            continue
                        }

                        val doigt = evenement.changes.firstOrNull { it.id == pose.id } ?: break

                        if (!doigt.pressed) {
                            // ⚠️ Le relevé qui suit un appui long ne doit **rien** déclencher de
                            // plus : le menu est déjà ouvert, valider par-dessus ouvrirait la
                            // première ligne de ce menu dans la foulée.
                            if (appuiLongDeclenche) break
                            // Relevé sans rotation ni glissement : c'est un appui.
                            if (crans == 0 && glissement < viewConfiguration.touchSlop) {
                                zoneDe(doigt.position, largeur, hauteur)?.let { zone ->
                                    vue.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    zoneCourante(zone)
                                }
                            }
                            break
                        }

                        glissement += doigt.positionChange().getDistance()

                        if (effetCourant != EffetMolette.AUCUN && surLAnneau) {
                            traces += TraceMolette(
                                doigt.position,
                                System.currentTimeMillis(),
                                angleDe(doigt.position, largeur, hauteur)
                            )
                            if (traces.size > MAX_TRACES) traces.removeAt(0)
                            // Une seule recomposition par geste, ici : elle démarre la boucle
                            // qui rendra les images tant que la traînée s'éteint.
                            if (!trainee) trainee = true
                        }


                        // Seul l'anneau fait tourner : un glissement parti du bouton central ou
                        // de l'extérieur ne déplace rien, comme sur l'appareil d'origine.
                        if (surLAnneau) {
                            val actuel = angleDe(doigt.position, largeur, hauteur)
                            var delta = actuel - angle
                            // Le passage par 180° ferait un bond d'un tour complet : on ramène
                            // l'écart dans [-π, π].
                            while (delta > Math.PI) delta -= (2 * Math.PI).toFloat()
                            while (delta < -Math.PI) delta += (2 * Math.PI).toFloat()
                            angle = actuel
                            accumule += delta
                            while (abs(accumule) >= PAS_ANGLE) {
                                val sens = sign(accumule).toInt()
                                accumule -= sens * PAS_ANGLE
                                crans++
                                vue.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                cranCourant(sens)
                            }
                        }

                        // On ne prend le pointeur qu'une fois la rotation avérée : avant cela,
                        // rien n'interdit à un parent de récupérer le geste.
                        if (crans > 0) doigt.consume()
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.matchParentSize()) {
            val rayon = size.minDimension / 2f
            dessinerMolette(style, couleurAnneau, couleurBord, couleurCentre, rayon)

            // ⚠️ Le battement est **passé en argument**, jamais lu dans le vide.
            //
            // En instruction isolée, la lecture n'observait plus rien de fiable : le dessin
            // n'avait alors aucune raison d'être redemandé, et la traînée restait figée sur sa
            // dernière image — quand elle paraissait. Passée en paramètre, la lecture a bien
            // lieu pendant le dessin, ce qui n'invalide que le dessin : aucune recomposition.
            dessinerTrainee(effet, traces, couleurAccent, rayon, battement.intValue)
        }

        Text(
            "MENU",
            color = couleurTexte,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 18.dp)
        )
        Icon(
            Icons.Rounded.SkipPrevious,
            contentDescription = "Précédent",
            tint = couleurTexte,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 16.dp)
                .size(26.dp)
        )
        Icon(
            Icons.Rounded.SkipNext,
            contentDescription = "Suivant",
            tint = couleurTexte,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
                .size(26.dp)
        )
        Icon(
            if (enLecture) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = if (enLecture) "Pause" else "Lecture",
            tint = couleurTexte,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
                .size(26.dp)
        )
    }
}

/** Rayon du point, rapporté au rayon de la molette (1 = le bord). */
private fun rayonRelatif(position: Offset, largeur: Float, hauteur: Float): Float {
    val rayon = minOf(largeur, hauteur) / 2f
    return hypot(position.x - largeur / 2f, position.y - hauteur / 2f) / rayon
}

/** Angle du point, en radians, mesuré depuis le centre. */
private fun angleDe(position: Offset, largeur: Float, hauteur: Float): Float =
    atan2(position.y - hauteur / 2f, position.x - largeur / 2f)

/**
 * Le quartier touché : haut, bas, gauche, droite, ou centre.
 *
 * `null` quand le doigt tombe hors de la molette — les coins du carré qui la contient.
 */
internal fun zoneDe(position: Offset, largeur: Float, hauteur: Float): ZoneMolette? {
    val rayon = rayonRelatif(position, largeur, hauteur)
    if (rayon > 1f) return null
    if (rayon < RAYON_INTERNE) return ZoneMolette.CENTRE

    // atan2 compte les y vers le bas : -π/2 est en haut de l'écran.
    val degres = Math.toDegrees(angleDe(position, largeur, hauteur).toDouble())
    return when {
        degres >= -135 && degres < -45 -> ZoneMolette.MENU
        degres >= -45 && degres < 45 -> ZoneMolette.SUIVANT
        degres >= 45 && degres < 135 -> ZoneMolette.LECTURE
        else -> ZoneMolette.PRECEDENT
    }
}

/** Vingt-quatre crans par tour : assez fin pour viser, assez franc pour se sentir. */
internal const val PAS_ANGLE = (2 * Math.PI / 24).toFloat()

internal const val RAYON_INTERNE = 0.42f

/** Combien de temps il faut maintenir le centre pour ouvrir le menu d'actions. */
internal const val DUREE_APPUI_LONG = 2_000L

/** Un point traversé par le doigt, gardé le temps que la traînée s'éteigne. */
internal data class TraceMolette(val position: Offset, val instant: Long, val angle: Float)

/** Combien de temps une trace met à disparaître. */
internal const val DUREE_TRACE = 550L

/** Au-delà, les plus anciennes sont oubliées : la traînée est courte par nature. */
internal const val MAX_TRACES = 44

/** Mélange deux couleurs, [part] valant la proportion de [vers]. */
internal fun melanger(depuis: Color, vers: Color, part: Float): Color = Color(
    red = depuis.red + (vers.red - depuis.red) * part,
    green = depuis.green + (vers.green - depuis.green) * part,
    blue = depuis.blue + (vers.blue - depuis.blue) * part,
    alpha = depuis.alpha
)

/**
 * L'anneau et son bouton central, selon le style choisi.
 *
 * ⚠️ **Tout est dessiné à l'intérieur du disque.** Les reflets passent par un dégradé dont le
 * centre est décalé, jamais par un second cercle posé plus haut : celui-ci débordait de la roue
 * et laissait une écharpe claire au-dessus d'elle.
 */
private fun DrawScope.dessinerMolette(
    style: StyleMolette,
    anneau: Color,
    bord: Color,
    centre: Color,
    rayon: Float
) {
    val clair = melanger(anneau, Color.White, 0.20f)
    val tresClair = melanger(anneau, Color.White, 0.38f)
    val sombre = melanger(anneau, Color.Black, 0.28f)
    val tresSombre = melanger(anneau, Color.Black, 0.48f)
    val trait = Stroke(width = 2f)
    val haut = center.copy(y = center.y - rayon * 0.45f)
    val basDroite = center.copy(x = center.x + rayon * 0.4f, y = center.y + rayon * 0.4f)

    when (style) {
        StyleMolette.DEFAUT -> drawCircle(
            brush = Brush.verticalGradient(listOf(anneau, anneau.copy(alpha = 0.82f))),
            radius = rayon
        )

        StyleMolette.PLAT -> drawCircle(color = anneau, radius = rayon)

        StyleMolette.MATERIAL -> {
            // L'élévation Material : une ombre diffuse sous le bord, pas un contour.
            drawCircle(color = tresSombre.copy(alpha = 0.55f), radius = rayon)
            drawCircle(color = anneau, radius = rayon * 0.97f)
        }

        StyleMolette.CREUSE -> drawCircle(
            brush = Brush.verticalGradient(listOf(sombre, anneau, clair)),
            radius = rayon
        )

        StyleMolette.BOMBE -> drawCircle(
            brush = Brush.verticalGradient(listOf(clair, anneau, sombre)),
            radius = rayon
        )

        StyleMolette.NEOMORPHIQUE -> {
            // Le relief sans contour : deux lumières opposées, très douces.
            drawCircle(color = anneau, radius = rayon)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(tresClair.copy(alpha = 0.55f), Color.Transparent),
                    center = center.copy(
                        x = center.x - rayon * 0.35f,
                        y = center.y - rayon * 0.35f
                    ),
                    radius = rayon * 1.1f
                ),
                radius = rayon
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(tresSombre.copy(alpha = 0.5f), Color.Transparent),
                    center = basDroite,
                    radius = rayon * 1.1f
                ),
                radius = rayon
            )
        }

        StyleMolette.VERRE -> {
            drawCircle(color = anneau.copy(alpha = 0.55f), radius = rayon)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.26f), Color.Transparent),
                    center = haut,
                    radius = rayon * 0.95f
                ),
                radius = rayon
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.22f),
                radius = rayon,
                style = Stroke(width = 3f)
            )
        }

        StyleMolette.LUSTRE -> {
            drawCircle(
                brush = Brush.verticalGradient(listOf(clair, anneau, tresSombre)),
                radius = rayon
            )
            // L'éclat : vif sur le haut, coupé net avant le milieu.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.40f),
                        Color.White.copy(alpha = 0.06f),
                        Color.Transparent
                    ),
                    center = haut,
                    radius = rayon * 0.72f
                ),
                radius = rayon
            )
        }

        StyleMolette.ARGILE -> {
            drawCircle(color = melanger(anneau, Color.White, 0.10f), radius = rayon)
            // Le bourrelet : un liseré clair épais à l'intérieur du bord, tout autour.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Transparent,
                        tresClair.copy(alpha = 0.75f)
                    ),
                    center = center,
                    radius = rayon
                ),
                radius = rayon
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(tresSombre.copy(alpha = 0.35f), Color.Transparent),
                    center = basDroite,
                    radius = rayon * 0.9f
                ),
                radius = rayon
            )
        }

        StyleMolette.BRUTALISTE -> {
            // L'ombre portée nette, décalée, puis l'aplat et son contour épais.
            drawCircle(
                color = Color.Black.copy(alpha = 0.85f),
                radius = rayon * 0.94f,
                center = center.copy(x = center.x + rayon * 0.05f, y = center.y + rayon * 0.05f)
            )
            drawCircle(color = anneau, radius = rayon * 0.94f)
            drawCircle(
                color = Color.Black,
                radius = rayon * 0.94f,
                style = Stroke(width = rayon * 0.045f)
            )
        }

        StyleMolette.RETRO -> {
            drawCircle(
                brush = Brush.verticalGradient(
                    listOf(melanger(anneau, Color(0xFFE8C48A), 0.30f), sombre)
                ),
                radius = rayon
            )
            // Les sillons : des cercles fins et réguliers, comme un disque usé.
            for (pas in 1..7) {
                drawCircle(
                    color = Color.Black.copy(alpha = 0.07f),
                    radius = rayon * (0.42f + pas * 0.075f),
                    style = Stroke(width = 1.5f)
                )
            }
        }

        StyleMolette.DEGRADE -> drawCircle(
            brush = Brush.verticalGradient(listOf(tresClair, anneau, tresSombre)),
            radius = rayon
        )

        StyleMolette.DUOTONE -> {
            drawCircle(color = clair, radius = rayon)
            // La moitié basse, franche : un dégradé à transition nulle.
            drawCircle(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to Color.Transparent,
                        0.5f to Color.Transparent,
                        0.5f to tresSombre,
                        1.0f to tresSombre
                    )
                ),
                radius = rayon
            )
        }

        StyleMolette.METALLIQUE -> drawCircle(
            brush = Brush.sweepGradient(
                listOf(sombre, tresClair, anneau, tresSombre, clair, anneau, sombre),
                center
            ),
            radius = rayon
        )

        StyleMolette.CERCLE -> drawCircle(
            color = anneau,
            radius = rayon,
            style = Stroke(width = rayon * 0.06f)
        )
    }

    if (style != StyleMolette.NEOMORPHIQUE && style != StyleMolette.BRUTALISTE) {
        drawCircle(color = bord, radius = rayon, style = trait)
    }

    // Le bouton central : même teinte que la roue, distingué par le relief inverse.
    val interieur = rayon * RAYON_INTERNE
    when (style) {
        StyleMolette.CREUSE, StyleMolette.NEOMORPHIQUE -> drawCircle(
            brush = Brush.verticalGradient(listOf(clair, centre)),
            radius = interieur
        )

        StyleMolette.BOMBE, StyleMolette.LUSTRE, StyleMolette.ARGILE -> drawCircle(
            brush = Brush.verticalGradient(listOf(centre, clair)),
            radius = interieur
        )

        StyleMolette.VERRE -> drawCircle(color = centre.copy(alpha = 0.65f), radius = interieur)

        StyleMolette.BRUTALISTE -> {
            drawCircle(color = centre, radius = interieur)
            drawCircle(
                color = Color.Black,
                radius = interieur,
                style = Stroke(width = rayon * 0.035f)
            )
        }

        StyleMolette.CERCLE -> drawCircle(
            color = centre,
            radius = interieur,
            style = Stroke(width = rayon * 0.04f)
        )

        StyleMolette.METALLIQUE -> drawCircle(
            brush = Brush.sweepGradient(listOf(clair, sombre, clair, sombre, clair), center),
            radius = interieur
        )

        else -> drawCircle(color = melanger(centre, Color.Black, 0.12f), radius = interieur)
    }
    if (style != StyleMolette.BRUTALISTE) {
        drawCircle(color = bord, radius = interieur, style = trait)
    }
}

/**
 * La traînée qui suit le doigt, si l'on en a demandé une.
 *
 * ⚠️ [battement] n'est **pas** décoratif : c'est le seul état lu pendant le dessin, et donc la
 * seule chose qui fasse redemander une image tant que la traînée s'éteint. Le paramètre existe
 * pour que cette lecture ait lieu à coup sûr — une lecture en instruction isolée n'offre pas la
 * même garantie.
 */
private fun DrawScope.dessinerTrainee(
    effet: EffetMolette,
    traces: List<TraceMolette>,
    accent: Color,
    rayon: Float,
    @Suppress("UNUSED_PARAMETER") battement: Int
) {
    if (effet == EffetMolette.AUCUN || traces.isEmpty()) return
    val maintenant = System.currentTimeMillis()

    traces.forEach { trace ->
        val age = (maintenant - trace.instant).toFloat() / DUREE_TRACE
        if (age !in 0f..1f) return@forEach
        val reste = 1f - age

        when (effet) {
            EffetMolette.UNIFORME -> drawCircle(
                color = accent.copy(alpha = reste * 0.55f),
                radius = rayon * 0.075f * reste,
                center = trace.position
            )

            EffetMolette.ARC_EN_CIEL -> drawCircle(
                color = teinteArcEnCiel(trace.angle).copy(alpha = reste * 0.7f),
                radius = rayon * 0.08f * reste,
                center = trace.position
            )

            EffetMolette.PAILLETTES -> {
                // Un semis figé par point : tiré du point lui-même, il ne scintille pas au hasard
                // d'une image à l'autre, ce qui serait épuisant à regarder.
                val germe = trace.instant
                repeat(3) { index ->
                    val ecart = rayon * 0.09f * ((germe shr (index * 5) and 7L).toFloat() / 7f)
                    val direction = trace.angle + index * 2.1f
                    drawCircle(
                        color = Color.White.copy(alpha = reste * reste * 0.85f),
                        radius = rayon * 0.012f * reste,
                        center = Offset(
                            trace.position.x + kotlin.math.cos(direction) * ecart,
                            trace.position.y + kotlin.math.sin(direction) * ecart
                        )
                    )
                }
            }

            EffetMolette.ONDE -> drawCircle(
                color = accent.copy(alpha = reste * 0.35f),
                radius = rayon * 0.06f + rayon * 0.14f * age,
                center = trace.position,
                style = Stroke(width = 2f + 3f * reste)
            )

            EffetMolette.AUCUN -> Unit
        }
    }
}

/** Une teinte vive tirée de l'angle : un tour complet fait un tour de roue chromatique. */
private fun teinteArcEnCiel(angle: Float): Color {
    val tour = ((angle / (2f * Math.PI.toFloat())) % 1f + 1f) % 1f
    return Color.hsv(tour * 360f, 0.85f, 1f)
}

package com.music36.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import com.music36.app.data.formaterDuree
import com.music36.app.lecture.SortieAudio
import kotlinx.coroutines.launch

/**
 * Le lecteur plein écran.
 *
 * La ligne de qualité affiche ce que le décodeur reçoit réellement (voir [SortieAudio]) et non
 * les étiquettes du fichier : une pochette peut mentir, le flux décodé non.
 */
@UnstableApi
@Composable
fun EcranLecteur() {
    val actions = LocalActions.current
    val reglages = LocalReglages.current
    val etat = LocalEtatLecture.current
    val portee = rememberCoroutineScope()
    val favoris by actions.depot.favoris.collectAsStateWithLifecycle()
    val sortie by SortieAudio.info.collectAsStateWithLifecycle()
    val materiel by SortieAudio.materiel.collectAsStateWithLifecycle()
    val minuteur by actions.lecteur.minuteur.collectAsStateWithLifecycle()

    var deplacement by remember { mutableFloatStateOf(-1f) }
    var menu by remember { mutableStateOf(false) }
    var dialogueVitesse by remember { mutableStateOf(false) }
    var dialogueMinuteur by remember { mutableStateOf(false) }
    var infos by remember { mutableStateOf(false) }

    // Écran allumé pendant la lecture : la pochette et le temps restent lisibles posés sur un
    // bureau, sans que l'écran s'éteigne au bout de trente secondes.
    val vue = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(reglages.ecranAllume) {
        vue.keepScreenOn = reglages.ecranAllume
        onDispose { vue.keepScreenOn = false }
    }

    val piste = etat.piste
    if (piste == null) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            MessageVide("Aucune lecture en cours", "Choisissez un titre pour commencer.")
        }
        return
    }

    // ⚠️ Cet écran ne vit dans aucun `Scaffold` : il n'hérite donc d'aucune `Surface`, et
    // `LocalContentColor` y vaudrait **noir** — lisible sur le thème clair, invisible sur les
    // thèmes sombres. C'est le défaut corrigé en 1.1 : la couleur de contenu est fournie ici,
    // et chaque texte ou icône notable porte en plus sa teinte explicite.
    CompositionLocalProvider(
        LocalContentColor provides MaterialTheme.colorScheme.onSurface
    ) {
        Box(Modifier.fillMaxSize()) {
            // Fond : la pochette floutée, puis un voile pour garder le texte lisible.
            if (reglages.fondPochette && piste.pochette != null) {
                AsyncImage(
                    model = piste.pochette,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(48.dp)
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.80f),
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                                )
                            )
                        )
                )
            } else {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { Navigateur.retour() }) {
                        Icon(
                            Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "Fermer",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (minuteur != null) {
                        Icon(
                            Icons.Rounded.Timer,
                            contentDescription = "Minuteur actif",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    IconButton(onClick = { Navigateur.aller(Ecran.FileAttente) }) {
                        Icon(
                            Icons.AutoMirrored.Rounded.QueueMusic,
                            contentDescription = "File d'attente",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(
                                Icons.Rounded.Speed,
                                contentDescription = "Options",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        DropdownMenu(menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Vitesse de lecture") },
                                leadingIcon = { Icon(Icons.Rounded.Speed, null) },
                                onClick = { menu = false; dialogueVitesse = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Minuteur de sommeil") },
                                leadingIcon = { Icon(Icons.Rounded.Timer, null) },
                                onClick = { menu = false; dialogueMinuteur = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Égaliseur") },
                                leadingIcon = { Icon(Icons.Rounded.GraphicEq, null) },
                                onClick = { menu = false; Navigateur.aller(Ecran.Egaliseur) }
                            )
                            DropdownMenuItem(
                                text = { Text("Informations") },
                                leadingIcon = { Icon(Icons.Rounded.Info, null) },
                                onClick = { menu = false; infos = true }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Pochette(
                    piste.pochette,
                    Modifier
                        .fillMaxWidth(0.86f)
                        .aspectRatio(1f),
                    coins = 18.dp
                )

                Spacer(Modifier.height(20.dp))
                Text(
                    piste.titre,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    piste.artisteAffiche,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOfNotNull(
                        piste.album.takeIf { it.isNotBlank() },
                        piste.annee.takeIf { it > 0 }?.toString(),
                        piste.genre.takeIf { it.isNotBlank() }
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (reglages.afficherQualite) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (piste.hauteResolution) {
                            EtiquetteHauteResolution()
                            Spacer(Modifier.width(8.dp))
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            shape = CircleShape
                        ) {
                            Text(
                                sortie?.resume()?.takeIf { it.isNotBlank() }
                                    ?: piste.qualite().ifBlank { "Qualité inconnue" },
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                    }
                    // Ce que reçoit le matériel, et ce qu'il sait faire : sans cette ligne,
                    // impossible de savoir si le 24 bits du fichier arrive vraiment au casque.
                    val chaine = listOfNotNull(
                        materiel?.resume(),
                        "sortie 32 bits flottante".takeIf { sortie?.sortieFlottante == true }
                    ).joinToString(" · ")
                    if (chaine.isNotBlank()) {
                        Text(
                            chaine,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp, start = 12.dp, end = 12.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                val duree = etat.duree.coerceAtLeast(1)
                val avancement = if (deplacement >= 0f) deplacement
                else (etat.position.toFloat() / duree).coerceIn(0f, 1f)

                Slider(
                    value = avancement,
                    onValueChange = { deplacement = it },
                    onValueChangeFinished = {
                        actions.lecteur.allerA((deplacement * duree).toLong())
                        deplacement = -1f
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        formaterDuree((avancement * duree).toLong()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        formaterDuree(etat.duree),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { actions.lecteur.basculerAleatoire() }) {
                        Icon(
                            Icons.Rounded.Shuffle,
                            contentDescription = "Lecture aléatoire",
                            tint = if (etat.aleatoire) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { actions.lecteur.precedent() }) {
                        Icon(
                            Icons.Rounded.SkipPrevious,
                            contentDescription = "Précédent",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                        modifier = Modifier.size(72.dp)
                    ) {
                        IconButton(onClick = { actions.lecteur.basculerLecture() }) {
                            Icon(
                                if (etat.enLecture) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (etat.enLecture) "Pause" else "Lecture",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                    IconButton(onClick = { actions.lecteur.suivant() }) {
                        Icon(
                            Icons.Rounded.SkipNext,
                            contentDescription = "Suivant",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    IconButton(onClick = { actions.lecteur.cyclerRepetition() }) {
                        Icon(
                            if (etat.repetition == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne
                            else Icons.Rounded.Repeat,
                            contentDescription = "Répétition",
                            tint = if (etat.repetition == Player.REPEAT_MODE_OFF)
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val favori = piste.uri in favoris
                    IconButton(onClick = { portee.launch { actions.depot.basculerFavori(piste.uri) } }) {
                        Icon(
                            if (favori) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = "Favori",
                            tint = if (favori) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { actions.ajouterAPlaylist(listOf(piste)) }) {
                        Icon(
                            Icons.AutoMirrored.Rounded.PlaylistAdd,
                            contentDescription = "Ajouter à une playlist",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { actions.ouvrirMenuPiste(piste) }) {
                        Icon(
                            Icons.Rounded.Info,
                            contentDescription = "Autres actions",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Text(
                    "Répétition : " + when (etat.repetition) {
                        Player.REPEAT_MODE_ONE -> "ce titre"
                        Player.REPEAT_MODE_ALL -> "toute la sélection"
                        else -> "désactivée"
                    } + if (etat.aleatoire) " · aléatoire" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
            }
        }

    } // fin du CompositionLocalProvider

    if (infos) DialogueInfos(piste) { infos = false }

    if (dialogueVitesse) {
        DialogueVitesse(
            vitesse = reglages.vitesse,
            onChoisir = { actions.lecteur.reglerVitesse(it) },
            onFermer = { dialogueVitesse = false }
        )
    }

    if (dialogueMinuteur) {
        DialogueMinuteur(
            actif = minuteur,
            onMinutes = { actions.lecteur.armerMinuteur(it); dialogueMinuteur = false },
            onFinDePiste = { actions.lecteur.armerMinuteurFinDePiste(); dialogueMinuteur = false },
            onAnnuler = { actions.lecteur.annulerMinuteur(); dialogueMinuteur = false },
            onFermer = { dialogueMinuteur = false }
        )
    }
}

/** Vitesse de lecture, utile pour les podcasts et les livres audio. */
@Composable
fun DialogueVitesse(vitesse: Float, onChoisir: (Float) -> Unit, onFermer: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onFermer,
        title = { Text("Vitesse de lecture") },
        text = {
            Column {
                Text("%.2f×".format(vitesse), style = MaterialTheme.typography.titleMedium)
                Slider(
                    value = vitesse,
                    onValueChange = onChoisir,
                    valueRange = 0.5f..2.5f,
                    steps = 15
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { valeur ->
                        androidx.compose.material3.TextButton(onClick = { onChoisir(valeur) }) {
                            Text("%.2f×".format(valeur))
                        }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onFermer) { Text("Fermer") }
        }
    )
}

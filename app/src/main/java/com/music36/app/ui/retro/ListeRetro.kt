package com.music36.app.ui.retro

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.music36.app.ui.couleurQualite
import androidx.compose.material.icons.rounded.PlaylistRemove
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.ui.focus.focusRequester

/** Le rendu d'un niveau : barre de titre, liste défilante, menu de l'écran d'accueil. */
/** La liste d'un menu, avec sa ligne en surbrillance et sa valeur au bout de chaque ligne. */
@Composable
internal fun MenuRetro(
    titre: String,
    entrees: List<EntreeRetro>,
    selection: Int,
    enLecture: Boolean,
    /** La recherche en cours dans ce menu, `null` quand elle est fermée. */
    recherche: String? = null,
    onRecherche: ((String?) -> Unit)? = null,
    /** Un bouton de plus, posé avant la loupe — le tri d'une playlist. */
    actionSupplementaire: (@Composable () -> Unit)? = null
) {
    Column(Modifier.fillMaxSize()) {
        if (onRecherche == null) {
            BarreTitreRetro(titre, enLecture)
        } else {
            BarreRechercheRetro(titre, enLecture, recherche, onRecherche, actionSupplementaire)
        }

        if (recherche != null && recherche.isNotBlank() && entrees.isEmpty()) {
            Text(
                "Aucun résultat pour « $recherche »",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp).testTag("aucun_resultat")
            )
        } else {
            ListeRetro(entrees, selection)
        }
    }
}

/**
 * La barre de titre qui porte la recherche.
 *
 * ⚠️ **Écrire se fait au clavier, pas à la molette.** Composer « rat*emi » lettre par lettre en
 * tournant serait un supplice ; le clavier sert à taper, la molette reprend la main pour choisir
 * dans les résultats. C'est la seule exception tactile des menus, comme les boutons de l'écran
 * de lecture.
 */
@Composable
internal fun BarreRechercheRetro(
    titre: String,
    enLecture: Boolean,
    recherche: String?,
    onRecherche: (String?) -> Unit,
    actionSupplementaire: (@Composable () -> Unit)?
) {
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val demandeFocus = remember { androidx.compose.ui.focus.FocusRequester() }

    if (recherche == null) {
        BarreTitreRetro(titre, enLecture, actionsDroite = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                actionSupplementaire?.invoke()
                androidx.compose.material3.IconButton(
                    onClick = { onRecherche("") },
                    modifier = Modifier.size(30.dp).testTag("bouton_recherche")
                ) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = "Rechercher dans ce menu",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (enLecture) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = "Lecture en cours",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 6.dp).size(16.dp)
                    )
                }
            }
        })
        return
    }

    // Le clavier s'ouvre de lui-même : appuyer sur la loupe, c'est déjà demander à écrire.
    LaunchedEffect(Unit) { runCatching { demandeFocus.requestFocus() } }

    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(start = 10.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Rounded.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Box(Modifier.weight(1f)) {
            if (recherche.isEmpty()) {
                Text(
                    "Rechercher  (emi*  *emi  rat*emi)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            androidx.compose.foundation.text.BasicTextField(
                value = recherche,
                onValueChange = { onRecherche(it) },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleSmall.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(
                    MaterialTheme.colorScheme.primary
                ),
                // « Terminé » range le clavier sans effacer la recherche : la molette reprend
                // alors la main pour choisir parmi les résultats.
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Search
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSearch = { focus.clearFocus() }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(demandeFocus)
                    .testTag("champ_recherche")
            )
        }
        androidx.compose.material3.IconButton(
            onClick = {
                focus.clearFocus()
                onRecherche(null)
            },
            modifier = Modifier.size(30.dp).testTag("fermer_recherche")
        ) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = "Fermer la recherche",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Le bouton de tri d'une playlist : il annonce le tri en cours, et un appui passe au suivant.
 *
 * ⚠️ Pas de menu derrière : quatre tris se parcourent plus vite d'un appui chacun qu'en ouvrant
 * une liste. Le nom affiché est celui du tri **en cours**, pour qu'on sache où l'on en est.
 */
@Composable
internal fun BoutonTriRetro(tri: com.music36.app.data.TriPlaylist, onTrier: () -> Unit) {
    androidx.compose.material3.TextButton(
        onClick = onTrier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp),
        modifier = Modifier.height(30.dp).testTag("bouton_tri")
    ) {
        Icon(
            Icons.AutoMirrored.Rounded.Sort,
            contentDescription = "Trier : ${tri.libelle}",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            tri.court,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
/** La liste elle-même, sans barre de titre : réutilisée par les menus et par Cover Flow. */
@Composable
internal fun ListeRetro(entrees: List<EntreeRetro>, selection: Int) {
    val liste = rememberLazyListState()
    // Le thème ne dit pas s'il est sombre : c'est la luminance de son fond qui le dit, et c'est
    // elle qui décide de la couleur de rang.
    val sombre = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    // ⚠️ Un cran isolé s'anime ; un défilement rapide **saute**. Animer chaque cran obligeait
    // à mesurer la liste à chaque fois, et sur des milliers de lignes la molette prenait du
    // retard sur le doigt.
    var precedente by remember { mutableIntStateOf(selection) }
    LaunchedEffect(selection, entrees.size) {
        if (selection !in entrees.indices) return@LaunchedEffect
        val saut = kotlin.math.abs(selection - precedente) > 1
        precedente = selection
        runCatching {
            if (saut) liste.scrollToItem(selection) else liste.animateScrollToItem(selection)
        }
    }

    if (entrees.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Vide",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            state = liste,
            modifier = Modifier
                .fillMaxSize()
                .testTag("liste_retro")
        ) {
            itemsIndexed(entrees) { index, entree ->
                val choisie = index == selection
                // La couleur de rang se calcule contre le fond du thème en cours : la même
                // teinte ne peut pas servir sur fond clair et sur fond sombre.
                val couleurRang = entree.rang?.let { couleurQualite(it, sombre) }
                val couleurTexte = when {
                    choisie -> MaterialTheme.colorScheme.onPrimary
                    couleurRang != null -> couleurRang
                    else -> MaterialTheme.colorScheme.onSurface
                }
                val couleurSecondaire = if (choisie) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant

                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            if (choisie) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerLow
                        )
                        .padding(end = 10.dp, top = 7.dp, bottom = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Un liseré à l'entame de la ligne : il garde la qualité visible même sur la
                    // ligne sélectionnée, dont le texte doit rester lisible sur l'accent.
                    Box(
                        Modifier
                            .width(3.dp)
                            .height(28.dp)
                            .background(couleurRang ?: Color.Transparent)
                    )
                    Spacer(Modifier.width(7.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            entree.libelle,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (choisie) FontWeight.SemiBold else FontWeight.Normal,
                            color = couleurTexte,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (entree.detail != null) {
                            Text(
                                entree.detail,
                                style = MaterialTheme.typography.labelSmall,
                                color = couleurSecondaire,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    // Le titre rangé dans aucune playlist se signale ici : une liste n'étant pas
                    // tactile dans cette vue, l'icône informe sans rien attendre du doigt.
                    if (entree.orpheline) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Rounded.PlaylistRemove,
                            contentDescription = "N'appartient à aucune playlist",
                            tint = if (choisie) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    // La valeur vit au bout de la ligne, jamais sous le libellé : c'est là qu'on
                    // la cherche sur un baladeur à molette, et la liste se lit d'un coup d'œil.
                    if (entree.valeur != null) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            entree.valeur,
                            style = MaterialTheme.typography.labelMedium,
                            color = couleurSecondaire,
                            maxLines = 1
                        )
                    }
                    if (entree.fleche) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = couleurSecondaire,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
@Composable
internal fun BarreTitreRetro(
    titre: String,
    enLecture: Boolean,
    actionsDroite: (@Composable () -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(start = 10.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            titre,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (actionsDroite != null) {
            actionsDroite()
        } else if (enLecture) {
            Icon(
                Icons.Rounded.PlayArrow,
                contentDescription = "Lecture en cours",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(16.dp)
            )
        }
    }
}

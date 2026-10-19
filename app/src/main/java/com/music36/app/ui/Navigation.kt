package com.music36.app.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList

/** Les écrans empilables au-dessus de la page principale. */
sealed interface Ecran {
    data object Recherche : Ecran
    data object Egaliseur : Ecran
    data object Lecteur : Ecran
    data object FileAttente : Ecran
    data class Album(val cle: String) : Ecran
    data class Artiste(val cle: String) : Ecran
    data class Genre(val cle: String) : Ecran
    data class Dossier(val chemin: String) : Ecran
    data class ListePlaylist(val id: Long) : Ecran
}

/**
 * Une pile d'écrans, tenue hors de la composition.
 *
 * Un objet global plutôt que `navigation-compose` : les destinations transportent ici des clés
 * arbitraires (chemins de dossiers, noms d'albums), qu'il faudrait sinon encoder dans des URL et
 * décoder à l'arrivée. La pile survit à la rotation sans qu'on ait à rendre les écrans
 * sérialisables.
 */
/**
 * Ce que la page d'accueil doit retrouver en revenant d'un album ou des paramètres : l'onglet
 * ouvert et la position de chaque liste.
 *
 * Ces états vivent hors de la composition parce que l'accueil quitte l'écran dès qu'on empile
 * autre chose ; sans eux, tout retour rouvrirait l'onglet par défaut, en haut de la liste.
 */
object EtatAccueil {
    var page: Int = -1
    private val listes = mutableMapOf<String, androidx.compose.foundation.lazy.LazyListState>()

    fun liste(cle: String): androidx.compose.foundation.lazy.LazyListState =
        listes.getOrPut(cle) { androidx.compose.foundation.lazy.LazyListState() }
}

object Navigateur {
    val pile: SnapshotStateList<Ecran> = mutableStateListOf()

    val courant: Ecran? get() = pile.lastOrNull()

    fun aller(ecran: Ecran) {
        if (pile.lastOrNull() != ecran) pile.add(ecran)
    }

    /** Ouvre le lecteur plein écran sans jamais l'empiler deux fois. */
    fun ouvrirLecteur() {
        pile.remove(Ecran.Lecteur)
        pile.add(Ecran.Lecteur)
    }

    fun retour(): Boolean {
        if (pile.isEmpty()) return false
        pile.removeAt(pile.lastIndex)
        return true
    }

    fun revenirALAccueil() = pile.clear()
}

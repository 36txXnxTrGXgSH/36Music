package com.music36.app.lecture

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.music36.app.data.Depot
import com.music36.app.data.Piste
import com.music36.app.data.typeConteneurDe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** L'état de lecture tel que l'interface le consomme. */
data class EtatLecture(
    val piste: Piste? = null,
    val file: List<Piste> = emptyList(),
    val index: Int = -1,
    val enLecture: Boolean = false,
    val chargement: Boolean = false,
    val position: Long = 0,
    val duree: Long = 0,
    val aleatoire: Boolean = false,
    val repetition: Int = Player.REPEAT_MODE_OFF,
    val vitesse: Float = 1f,
    val connecte: Boolean = false,
    /**
     * Le déplacement demandé à la molette, pas encore appliqué.
     *
     * ⚠️ C'est lui qui rend l'avance rapide fluide : l'écran affiche tout de suite
     * `position + decalage`, alors que le lecteur, lui, ne se déplacera qu'une fois, quand
     * le doigt s'arrêtera.
     */
    val decalageEnAttente: Long = 0,
    val erreur: String? = null
) {
    val actif: Boolean get() = piste != null
}

/**
 * Le lecteur vu depuis l'interface : un `MediaController` branché sur [ServiceLecture].
 *
 * Passer par un contrôleur plutôt que d'appeler ExoPlayer directement, c'est ce qui fait que la
 * notification, l'écran verrouillé, les écouteurs Bluetooth et l'écran de l'application ne
 * peuvent pas diverger : ils commandent tous le même lecteur.
 */
@UnstableApi
class Lecteur(
    private val contexte: Context,
    private val depot: Depot,
    private val portee: CoroutineScope
) {
    private var controleur: MediaController? = null

    /**
     * Les pistes jouées sans passer par la bibliothèque — un fichier ouvert depuis un explorateur.
     * Sans ce répertoire, l'écran ne saurait rien afficher : il ne retrouverait pas l'URI.
     */
    private val horsBibliotheque = mutableMapOf<String, Piste>()

    /**
     * La commande arrivée avant que le service ne réponde.
     *
     * Ouvrir un fichier depuis un explorateur lance l'activité et le contrôleur en même temps :
     * sans cette attente, la lecture demandée serait perdue une fois sur deux.
     */
    private var enAttente: (() -> Unit)? = null

    /**
     * L'utilisateur a-t-il déjà choisi quelque chose ?
     *
     * ⚠️ Dès qu'il lance un morceau, la reprise de la session précédente doit renoncer —
     * **y compris si elle est en train d'attendre** que la bibliothèque finisse de charger.
     */
    private var choixUtilisateur = false

    /**
     * Échecs enchaînés sans qu'un seul morceau n'ait démarré.
     *
     * Sert de garde-fou à la reprise automatique : une file entière d'illisibles ne doit pas
     * faire défiler la bibliothèque à toute vitesse.
     */
    private var echecsDeSuite = 0

    /**
     * La bibliothèque indexée par URI, reconstruite seulement quand elle change.
     *
     * ⚠️ Avec plusieurs milliers de titres, refaire cette carte à chaque événement du lecteur —
     * deux fois par seconde pendant la lecture — coûterait plus que tout le reste de l'écran.
     */
    private var pistesIndexees: List<Piste>? = null
    private var parUri: Map<String, Piste> = emptyMap()

    /** Incrémenté à chaque reconstruction de l'index : sert à dater la file affichée. */
    private var versionIndex = 0

    /** La file telle qu'affichée, recalculée seulement quand elle ou la bibliothèque change. */
    private var chronologieVue: androidx.media3.common.Timeline? = null
    private var versionFileVue = -1
    private var melangeVu = false
    private var indexVu = -1
    private var tailleHistoriqueVue = -1
    private var fileVue: List<Piste> = emptyList()

    /**
     * Les morceaux déjà écoutés depuis le dernier lancement, dans l'ordre où ils l'ont été.
     *
     * ⚠️ Remis à zéro par [jouer], donc **à chaque fois qu'une liste est relancée** — même s'il
     * s'agit de la même. Relancer « Toutes les chansons » commence une écoute, pas la suite de la
     * précédente.
     */
    private var historique: List<Int> = emptyList()

    /**
     * La liste telle que l'utilisateur l'a lancée, dans son ordre d'origine.
     *
     * Sert à remettre la file en ordre quand on éteint l'aléatoire.
     */
    private var listeChoisie: List<Piste> = emptyList()

    /**
     * L'aléatoire, tenu par nous et **pas** par ExoPlayer.
     *
     * ⚠️ Le mélange d'ExoPlayer n'en est pas un : il suit une permutation tirée au moment où la
     * file est posée, et se contente de la parcourir à partir du morceau en cours. Activer
     * l'aléatoire en cours d'écoute jetait donc tout ce qui, dans cette permutation, se trouvait
     * *avant* le morceau courant — sur une bibliothèque de 3 476 titres, il n'en restait parfois
     * que cent. On mélange nous-mêmes ce qu'il reste à lire, ce qui ne perd rien.
     */
    private var aleatoireInterne = false

    /**
     * Les positions réelles, dans la file du lecteur, des titres affichés.
     *
     * ⚠️ En aléatoire, ExoPlayer **ne réordonne pas sa file** : il la parcourt dans un autre
     * ordre. L'écran montrait donc la liste alphabétique pendant que le son suivait le tirage.
     * On affiche désormais l'ordre de lecture, et cette table ramène chaque ligne à la position
     * que le lecteur, lui, connaît.
     */
    private var positionsVues: List<Int> = emptyList()

    private val _etat = MutableStateFlow(EtatLecture())
    val etat: StateFlow<EtatLecture> = _etat.asStateFlow()

    /** Instant (horloge système) auquel la lecture s'arrêtera, `null` si aucun minuteur. */
    private val _minuteur = MutableStateFlow<Long?>(null)
    val minuteur: StateFlow<Long?> = _minuteur.asStateFlow()
    private var tacheMinuteur: Job? = null

    /** Vrai quand le minuteur doit attendre la fin du morceau en cours. */
    private val _minuteurFinDePiste = MutableStateFlow(false)

    init {
        connecter()
        portee.launch {
            while (isActive) {
                val c = controleur
                if (c != null && c.isPlaying) rafraichir()
                delay(500)
            }
        }
    }

    private fun connecter() {
        val jeton = SessionToken(contexte, ComponentName(contexte, ServiceLecture::class.java))
        val futur = MediaController.Builder(contexte, jeton).buildAsync()
        futur.addListener({
            runCatching { futur.get() }
                .onSuccess { c ->
                    controleur = c
                    c.addListener(ecouteur)
                    rafraichir()
                    val differee = enAttente
                    enAttente = null
                    if (differee != null) differee() else reprendreLaSession()
                }
                .onFailure { Log.e(TAG, "Connexion au service impossible", it) }
        }, ContextCompat.getMainExecutor(contexte))
    }

    private val ecouteur = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = rafraichir()

        /**
         * ⚠️ Le silence sans erreur.
         *
         * Quand **aucun décodeur de l'appareil** ne sait lire la piste, ExoPlayer ne lève pas
         * d'exception : il déroule l'horloge sur une piste qu'il n'a pas sélectionnée. Le morceau
         * « se lance » et rien ne sort. C'est le cas de l'ALAC sur beaucoup de téléphones, et
         * c'est ici, et seulement ici, qu'on peut le dire à l'utilisateur.
         */
        override fun onTracksChanged(pistes: androidx.media3.common.Tracks) {
            if (pistes.groups.isEmpty()) return
            val audio = androidx.media3.common.C.TRACK_TYPE_AUDIO
            // ⚠️ Une erreur ne vaut que pour le morceau qui l'a provoquée. Sans cette remise à
            // zéro, un seul fichier illisible affichait son bandeau rouge sur tous les suivants.
            if (pistes.isTypeSupported(audio)) {
                if (_etat.value.erreur != null) _etat.value = _etat.value.copy(erreur = null)
                return
            }
            if (pistes.containsType(audio)) {
                val codec = pistes.groups
                    .firstOrNull { it.type == audio }
                    ?.getTrackFormat(0)
                    ?.sampleMimeType
                val nom = SortieAudio.nomDeCodec(codec).ifBlank { "ce format" }
                _etat.value = _etat.value.copy(
                    erreur = "Aucun décodeur $nom sur cet appareil : Android ne sait pas lire " +
                        "ce fichier. Convertissez-le (en FLAC par exemple)."
                )
                Log.w(TAG, "Piste audio non prise en charge : $codec")
            }
        }

        /** Le morceau change : l'erreur du précédent n'a plus lieu d'être affichée. */
        override fun onMediaItemTransition(element: MediaItem?, raison: Int) {
            if (_etat.value.erreur != null) _etat.value = _etat.value.copy(erreur = null)
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            Log.e(TAG, "Erreur de lecture", error)
            _etat.value = _etat.value.copy(erreur = messageErreur(error))
            reprendreApresEchec()
        }

        /** Un morceau a réellement démarré : la file est saine, on repart de zéro. */
        override fun onIsPlayingChanged(enLecture: Boolean) {
            if (enLecture) echecsDeSuite = 0
        }
    }

    /**
     * Repart sur le morceau suivant après un échec.
     *
     * ⚠️ C'est **le** point qui manquait. Une erreur de lecture laisse ExoPlayer à l'arrêt, en
     * `STATE_IDLE` : la file est intacte à l'écran, mais plus rien ne démarre tant que personne
     * n'appelle `prepare()`. Un seul fichier illisible condamnait donc tout ce qui le suivait —
     * exactement ce que décrivait l'utilisateur.
     *
     * Le compteur évite l'emballement : si rien ne démarre après quelques tentatives, on
     * s'arrête et on laisse le message d'erreur à l'écran plutôt que de dévaler la file.
     */
    private fun reprendreApresEchec() {
        val c = controleur ?: return
        echecsDeSuite++
        when (actionApresEchec(echecsDeSuite, c.hasNextMediaItem())) {
            ActionEchec.SAUTER -> {
                c.seekToNextMediaItem()
                c.prepare()
                c.play()
            }

            // Dernier morceau : on repose le lecteur pour que le bouton « lecture » réponde
            // encore, sans relancer un fichier dont on sait qu'il échoue.
            ActionEchec.REPOSER -> c.prepare()

            ActionEchec.ABANDONNER ->
                Log.w(TAG, "Trop d'échecs enchaînés : la reprise automatique s'arrête")
        }
    }

    /**
     * Traduit l'échec en une phrase utile.
     *
     * Le cas le plus fréquent n'est pas un fichier abîmé mais un format qu'Android ne sait pas
     * décoder — le WMA en tête : aucun téléphone n'embarque ce décodeur.
     */
    private fun messageErreur(erreur: androidx.media3.common.PlaybackException): String {
        val piste = _etat.value.piste
        val fichier = piste?.nomFichier ?: "ce fichier"
        val format = piste?.format?.takeIf { it.isNotBlank() }?.let { "$it — " }.orEmpty()
        return when (erreur.errorCode) {
            androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ->
                "${format}format non pris en charge par Android : $fichier"

            androidx.media3.common.PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
                "Fichier introuvable : $fichier (le dossier a-t-il été déplacé ?)"

            androidx.media3.common.PlaybackException.ERROR_CODE_IO_NO_PERMISSION ->
                "Accès refusé : réautorisez le dossier dans les paramètres"

            else -> "Lecture impossible : $fichier"
        } + " [${erreur.errorCodeName}]"
    }

    /**
     * Tient l'historique à jour au fil des changements de morceau.
     *
     * Revenir en arrière **retire** le morceau de l'historique plutôt que de l'y ajouter une
     * seconde fois : la file doit rester la liste de ce qui a été écouté, pas de ce qui a été
     * survolé.
     */
    private fun suivreLHistorique(courantIndex: Int) {
        if (courantIndex < 0 || courantIndex == indexVu) return
        historique = when {
            indexVu < 0 -> historique
            courantIndex in historique -> historique.takeWhile { it != courantIndex }
            else -> historique + indexVu
        }
    }

    /** La bibliothèque indexée par URI, refaite seulement si elle a changé. */
    private fun indexParUri(): Map<String, Piste> {
        val actuelles = depot.pistes.value
        if (actuelles !== pistesIndexees) {
            pistesIndexees = actuelles
            versionIndex++
            parUri = HashMap<String, Piste>(actuelles.size).apply {
                actuelles.forEach { put(it.uri, it) }
            }
        }
        return parUri
    }

    private fun pisteDe(identifiant: String?): Piste? {
        if (identifiant == null) return null
        return indexParUri()[identifiant] ?: horsBibliotheque[identifiant]
    }

    private fun rafraichir() {
        val c = controleur ?: return
        // ⚠️ La file n'est reconstruite que lorsqu'elle change. Sans cela, une bibliothèque de
        // cinq mille titres était reparcourue deux fois par seconde pour afficher une position.
        //
        // ⚠️ **Et lorsque la bibliothèque change.** Au démarrage, la session est restaurée avant
        // que les pistes ne soient chargées : les URI de la file ne correspondaient alors à rien,
        // et comme la chronologie, elle, ne bougeait plus, la file restait vide pour de bon.
        indexParUri()
        val chronologie = c.currentTimeline
        val melange = c.shuffleModeEnabled
        val courantIndex = c.currentMediaItemIndex
        suivreLHistorique(courantIndex)

        // ⚠️ On ne refait la file que lorsqu'elle peut avoir changé — pas à chaque battement de
        // position. Sur trois mille titres, la reconstruire deux fois par seconde coûterait plus
        // cher que la lecture elle-même.
        if (chronologie !== chronologieVue || versionIndex != versionFileVue ||
            melange != melangeVu || courantIndex != indexVu ||
            historique.size != tailleHistoriqueVue
        ) {
            chronologieVue = chronologie
            versionFileVue = versionIndex
            melangeVu = melange
            indexVu = courantIndex
            tailleHistoriqueVue = historique.size

            // ⚠️ La file part du morceau **en cours**, pas du début du tirage. En aléatoire, la
            // lecture démarre au milieu de l'ordre tiré : afficher cet ordre depuis son début
            // montrait en tête des titres qui ne passeraient jamais.
            val ordre = ordreDeLecture(chronologie, melange)
            val rang = ordre.indexOf(courantIndex)
            val aVenir = if (rang >= 0) ordre.drop(rang) else listOf(courantIndex)
            val paires = (historique + aVenir)
                .filter { it in 0 until c.mediaItemCount }
                .mapNotNull { position ->
                    pisteDe(c.getMediaItemAt(position).mediaId)?.let { position to it }
                }
            positionsVues = paires.map { it.first }
            fileVue = paires.map { it.second }
        }
        val file = fileVue
        val courante = pisteDe(c.currentMediaItem?.mediaId)
        _etat.value = _etat.value.copy(
            piste = courante,
            file = file,
            index = positionsVues.indexOf(courantIndex),
            enLecture = c.isPlaying,
            chargement = c.playbackState == Player.STATE_BUFFERING,
            position = c.currentPosition.coerceAtLeast(0),
            duree = if (c.duration > 0) c.duration else courante?.duree ?: 0,
            aleatoire = aleatoireInterne,
            repetition = c.repeatMode,
            vitesse = c.playbackParameters.speed,
            connecte = true
        )
        if (_minuteurFinDePiste.value && c.playbackState == Player.STATE_ENDED) {
            c.pause()
            annulerMinuteur()
        }
    }

    // ------------------------------------------------------------------ commandes

    /**
     * Remplace la file par [pistes] et démarre à [index].
     *
     * En aléatoire, ExoPlayer tire un ordre complet et le suit jusqu'au bout : aucun titre ne
     * revient tant que la file n'a pas été entièrement jouée. C'est exactement ce qu'on attend du
     * bouton « Lecture aléatoire ».
     */
    fun jouer(pistes: List<Piste>, index: Int = 0, aleatoire: Boolean = false) {
        if (pistes.isEmpty()) return
        choixUtilisateur = true
        echecsDeSuite = 0
        historique = emptyList()
        indexVu = -1
        val c = controleur ?: run {
            enAttente = { jouer(pistes, index, aleatoire) }
            return
        }
        val depart = index.coerceIn(0, pistes.lastIndex)
        val connues = indexParUri()
        pistes.filterNot { it.uri in connues }.forEach { horsBibliotheque[it.uri] = it }

        listeChoisie = pistes
        aleatoireInterne = aleatoire
        // En aléatoire, le morceau choisi ouvre la file et tout le reste suit dans un ordre tiré
        // au sort : la file contient donc **toujours** l'intégralité de la liste.
        val file = if (aleatoire) {
            val premier = pistes[depart]
            listOf(premier) + (pistes - premier).shuffled()
        } else {
            pistes
        }
        val position = if (aleatoire) 0 else depart
        c.shuffleModeEnabled = false
        // ⚠️ On coupe et on vide **avant** de reposer la file.
        //
        // Remplacer une file en pleine lecture laisse ExoPlayer réutiliser sa chaîne de
        // décodage : l'écran affichait le morceau demandé pendant que le haut-parleur finissait
        // le précédent, puis enchaînait sur l'ancienne liste. Repartir d'un lecteur à l'arrêt
        // coûte une fraction de seconde et supprime cette zone grise.
        c.stop()
        c.clearMediaItems()
        c.setMediaItems(file.map(::element), position, repriseDe(file[position]))
        c.prepare()
        c.play()
        _etat.value = _etat.value.copy(erreur = null)
    }

    /**
     * Position de reprise d'une piste longue (émission, mix, livre audio).
     *
     * Reprendre un morceau de trois minutes là où on l'avait laissé serait déroutant ; sur une
     * heure d'enregistrement, repartir de zéro l'est bien davantage.
     */
    private fun repriseDe(piste: Piste): Long {
        if (!depot.etatReglages.value.memoriserPositionLongues) return 0L
        if (piste.duree <= 10 * 60_000L) return 0L
        return depot.statistiques.value[piste.uri]?.reprise?.takeIf { it > 0 } ?: 0L
    }

    /** « Tout lire » : de la première à la dernière, dans l'ordre affiché. */
    fun toutLire(pistes: List<Piste>) = jouer(pistes, 0, aleatoire = false)

    /**
     * « Lecture aléatoire » : démarre sur un titre au hasard, puis enchaîne sans répétition.
     *
     * ExoPlayer tire un ordre complet sur **toute** la file et le suit jusqu'au bout : aucun
     * titre ne revient tant que la bibliothèque n'a pas été parcourue entièrement.
     */
    fun toutMelanger(pistes: List<Piste>) {
        if (pistes.isEmpty()) return
        jouer(pistes, pistes.indices.random(), aleatoire = true)
    }

    fun basculerLecture() {
        val c = controleur ?: return
        if (c.isPlaying) c.pause()
        else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun suivant() = controleur?.let { if (it.hasNextMediaItem()) it.seekToNextMediaItem() else it.seekTo(0, 0) }

    /**
     * Précédent : revient au début du morceau s'il est déjà entamé, sinon passe au précédent.
     * C'est le comportement attendu partout ailleurs.
     */
    fun precedent() {
        val c = controleur ?: return
        if (c.currentPosition > 3_000 || !c.hasPreviousMediaItem()) c.seekTo(0)
        else c.seekToPreviousMediaItem()
    }

    fun allerA(position: Long) {
        controleur?.seekTo(position.coerceAtLeast(0))
        rafraichir()
    }

    /** Le saut demandé mais pas encore appliqué, et la tâche qui l'appliquera. */
    private var sautEnAttente = 0L
    private var tacheSaut: Job? = null

    /**
     * Avance ou recule, **sans rien chercher tant que le doigt tourne**.
     *
     * ⚠️ Un déplacement n'est pas gratuit : la plupart des FLAC n'embarquent pas de table de
     * recherche, et ExoPlayer doit alors localiser la position par dichotomie **dans le fichier**,
     * sur la carte. En chercher une par cran revenait à fouiller plusieurs dizaines de méga-octets
     * dix fois par seconde — d'où la molette, la traînée et la barre qui décrochaient toutes
     * ensemble.
     *
     * On additionne donc les crans, on les montre immédiatement à l'écran, et on ne se déplace
     * qu'une fois : quand plus rien n'arrive pendant [DELAI_SAUT].
     */
    fun avancerDe(millisecondes: Long) {
        val c = controleur ?: return
        val duree = c.duration.coerceAtLeast(0)
        val depart = c.currentPosition.coerceAtLeast(0)
        sautEnAttente = (sautEnAttente + millisecondes).coerceIn(-depart, duree - depart)
        _etat.value = _etat.value.copy(decalageEnAttente = sautEnAttente)

        tacheSaut?.cancel()
        tacheSaut = portee.launch {
            delay(DELAI_SAUT)
            val total = sautEnAttente
            sautEnAttente = 0L
            controleur?.let { it.seekTo((it.currentPosition + total).coerceIn(0, duree)) }
            _etat.value = _etat.value.copy(decalageEnAttente = 0L)
        }
    }

    /**
     * Active ou éteint l'aléatoire **sans rien perdre ni interrompre le son**.
     *
     * ⚠️ On ne touche pas au morceau en cours : seule la portion de file qui le suit est
     * réécrite — mélangée, ou remise dans l'ordre d'origine. C'est ce qui distingue ce geste de
     * celui d'ExoPlayer, qui se contentait de sauter dans une permutation déjà tirée et
     * abandonnait tout ce qui s'y trouvait avant.
     */
    fun basculerAleatoire() {
        val c = controleur ?: return
        aleatoireInterne = !aleatoireInterne
        val depuis = c.currentMediaItemIndex + 1
        val total = c.mediaItemCount
        if (depuis in 0 until total) {
            val restants = (depuis until total).map { c.getMediaItemAt(it) }
            val reordonnes = if (aleatoireInterne) restants.shuffled()
            else remettreDansLOrdre(restants)
            c.replaceMediaItems(depuis, total, reordonnes)
        }
        rafraichir()
    }

    /** Remet des éléments dans l'ordre de la liste que l'utilisateur avait lancée. */
    private fun remettreDansLOrdre(items: List<MediaItem>): List<MediaItem> {
        val rangs = listeChoisie.withIndex().associate { (rang, piste) -> piste.uri to rang }
        return items.sortedBy { rangs[it.mediaId] ?: Int.MAX_VALUE }
    }

    /** Aucune répétition → toute la file → le seul morceau → aucune. */
    fun cyclerRepetition() {
        val c = controleur ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    /** [rang] est une place dans la file **telle qu'affichée**, pas dans celle du lecteur. */
    fun allerALIndex(rang: Int) {
        val c = controleur ?: return
        val position = positionsVues.getOrElse(rang) { rang }
        if (position in 0 until c.mediaItemCount) {
            c.seekTo(position, 0)
            c.play()
        }
    }

    /** Insère juste après le morceau en cours. */
    fun jouerEnsuite(pistes: List<Piste>) {
        choixUtilisateur = true
        val c = controleur ?: return
        if (c.mediaItemCount == 0) return jouer(pistes)
        c.addMediaItems(c.currentMediaItemIndex + 1, pistes.map(::element))
    }

    fun ajouterALaFile(pistes: List<Piste>) {
        choixUtilisateur = true
        val c = controleur ?: return
        if (c.mediaItemCount == 0) return jouer(pistes)
        c.addMediaItems(pistes.map(::element))
    }

    fun retirerDeLaFile(index: Int) {
        val c = controleur ?: return
        if (index in 0 until c.mediaItemCount) c.removeMediaItem(index)
    }

    fun deplacerDansLaFile(de: Int, vers: Int) {
        val c = controleur ?: return
        if (de in 0 until c.mediaItemCount && vers in 0 until c.mediaItemCount) c.moveMediaItem(de, vers)
    }

    fun viderLaFile() {
        historique = emptyList()
        indexVu = -1
        listeChoisie = emptyList()
        aleatoireInterne = false
        controleur?.clearMediaItems()
        _etat.value = EtatLecture(connecte = true)
    }

    fun reglerVitesse(vitesse: Float) {
        depot.modifierReglages { it.copy(vitesse = vitesse) }
    }

    // ------------------------------------------------------------------ minuteur

    fun armerMinuteur(minutes: Int) {
        annulerMinuteur()
        _minuteur.value = System.currentTimeMillis() + minutes * 60_000L
        tacheMinuteur = portee.launch {
            delay(minutes * 60_000L)
            controleur?.pause()
            _minuteur.value = null
        }
    }

    /** Variante « fin du morceau en cours » : la lecture s'arrête au bout de la piste. */
    fun armerMinuteurFinDePiste() {
        annulerMinuteur()
        _minuteurFinDePiste.value = true
        controleur?.repeatMode = Player.REPEAT_MODE_OFF
        val c = controleur ?: return
        val restant = (c.duration - c.currentPosition).coerceAtLeast(0)
        _minuteur.value = System.currentTimeMillis() + restant
        tacheMinuteur = portee.launch {
            delay(restant)
            controleur?.pause()
            _minuteur.value = null
            _minuteurFinDePiste.value = false
        }
    }

    fun annulerMinuteur() {
        tacheMinuteur?.cancel()
        tacheMinuteur = null
        _minuteur.value = null
        _minuteurFinDePiste.value = false
    }

    // ------------------------------------------------------------------ reprise

    /**
     * Reprend la file de la session précédente, à l'arrêt.
     *
     * On attend que la bibliothèque soit chargée : les URI mémorisées ne servent à rien tant
     * qu'on ne peut pas les rattacher à des pistes connues.
     */
    private fun reprendreLaSession() {
        portee.launch {
            val reglages = depot.etatReglages.value
            val c = controleur ?: return@launch
            if (!doitReprendre(reglages.reprendreAuDemarrage, reglages.fileMemorisee,
                    choixUtilisateur, c.mediaItemCount)
            ) {
                return@launch
            }

            val pistes = depot.pistes.first { it.isNotEmpty() }.associateBy { it.uri }

            // ⚠️ **On revérifie après l'attente.** Charger la bibliothèque prend un instant, et
            // l'utilisateur est souvent plus rapide : s'il a lancé un morceau pendant ce temps,
            // reprendre la session lui écraserait sa file sous les doigts — le disque d'hier
            // revenait alors à la place de ce qu'il venait de choisir.
            if (!doitReprendre(reglages.reprendreAuDemarrage, reglages.fileMemorisee,
                    choixUtilisateur, c.mediaItemCount)
            ) {
                return@launch
            }

            val file = reglages.fileMemorisee.split(Depot.SEPARATEUR_FILE)
                .mapNotNull { pistes[it] }
            if (file.isEmpty()) return@launch

            c.setMediaItems(
                file.map(::element),
                reglages.indexMemorise.coerceIn(0, file.lastIndex),
                reglages.positionMemorisee
            )
            c.shuffleModeEnabled = reglages.aleatoireMemorise
            c.repeatMode = reglages.repetitionMemorisee
            c.prepare()
            rafraichir()
        }
    }

    private fun element(piste: Piste): MediaItem = MediaItem.Builder()
        .setMediaId(piste.uri)
        .setUri(Uri.parse(piste.uri))
        // ⚠️ **Toujours** un type, jamais `null`. Sans type, media3 le déduit du nom de fichier :
        // un `.m3u8` le faisait basculer sur HLS, module absent, et la file entière était refusée.
        // Un type inconnu mais explicite garde la source progressive et laisse le reniflage des
        // extracteurs faire son travail.
        .setMimeType(typeConteneurDe(piste.nomFichier) ?: MimeTypes.AUDIO_UNKNOWN)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(piste.titre)
                .setArtist(piste.artisteAffiche)
                .setAlbumTitle(piste.album)
                .setAlbumArtist(piste.artisteAlbum.ifBlank { piste.artisteAffiche })
                .setGenre(piste.genre.takeIf { it.isNotBlank() })
                .setRecordingYear(piste.annee.takeIf { it > 0 })
                .setTrackNumber(piste.numeroPiste.takeIf { it > 0 })
                .setArtworkUri(piste.pochette?.let(Uri::parse))
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .build()
        )
        .build()


    private companion object {
        const val TAG = "36Music/Lecteur"

        /**
         * Le temps de calme après lequel on se déplace vraiment.
         *
         * Assez court pour qu'on ne l'attende pas, assez long pour qu'un tour de molette entier
         * ne provoque qu'une seule recherche dans le fichier.
         */
        const val DELAI_SAUT = 260L
    }
}

/**
 * Faut-il reprendre la file de la session précédente ?
 *
 * Trois refus, et le troisième est celui qui manquait : un morceau **déjà lancé** — par
 * l'utilisateur ou parce que la file n'est pas vide — l'emporte toujours sur le souvenir d'hier.
 */
fun doitReprendre(
    repriseDemandee: Boolean,
    fileMemorisee: String,
    choixUtilisateur: Boolean,
    elementsDansLaFile: Int
): Boolean = repriseDemandee &&
    fileMemorisee.isNotBlank() &&
    !choixUtilisateur &&
    elementsDansLaFile == 0

/** Ce qu'il convient de faire quand un morceau refuse de se lire. */
enum class ActionEchec { SAUTER, REPOSER, ABANDONNER }

/**
 * La décision de reprise après un échec de lecture.
 *
 * Sauter le fichier fautif est presque toujours le bon geste : c'est ce que fait n'importe quel
 * lecteur devant une piste abîmée. Le compteur borne l'enchaînement — devant une file entièrement
 * illisible, dévaler la bibliothèque serait pire que s'arrêter et afficher l'erreur.
 */
fun actionApresEchec(
    echecsDeSuite: Int,
    aUnSuivant: Boolean,
    maximum: Int = 3
): ActionEchec = when {
    echecsDeSuite > maximum -> ActionEchec.ABANDONNER
    aUnSuivant -> ActionEchec.SAUTER
    else -> ActionEchec.REPOSER
}

/**
 * Les positions de la file, dans l'ordre où le lecteur va réellement les jouer.
 *
 * ⚠️ Activer l'aléatoire **ne réordonne pas** la file d'ExoPlayer : il change la façon dont elle
 * est parcourue. Lire la liste de 0 à n-1 donnait donc l'ordre alphabétique alors que le son
 * suivait le tirage — c'est exactement ce que montrait l'écran « file d'attente ».
 */
@UnstableApi
fun ordreDeLecture(chronologie: androidx.media3.common.Timeline, melange: Boolean): List<Int> {
    if (chronologie.isEmpty) return emptyList()
    val ordre = ArrayList<Int>(chronologie.windowCount)
    var position = chronologie.getFirstWindowIndex(melange)
    while (position != androidx.media3.common.C.INDEX_UNSET && ordre.size < chronologie.windowCount) {
        ordre.add(position)
        position = chronologie.getNextWindowIndex(position, Player.REPEAT_MODE_OFF, melange)
    }
    return ordre
}

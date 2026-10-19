package com.music36.app.lecture

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.util.Log
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultRenderersFactory
import com.music36.app.lecture.alac.FabriqueRendus36
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.AdtsExtractor
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.music36.app.MainActivity
import com.music36.app.Music36App
import com.music36.app.R
import com.music36.app.data.EtatReglages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Le service qui détient réellement la lecture.
 *
 * Il survit à l'écran : c'est lui qui porte ExoPlayer et la `MediaSession`, donc la notification,
 * l'écran verrouillé, les boutons du casque et Android Auto. L'interface ne parle jamais
 * directement au lecteur, elle passe par un `MediaController` (voir [Lecteur]).
 */
@UnstableApi
class ServiceLecture : MediaSessionService() {

    private lateinit var lecteur: ExoPlayer
    private var session: MediaSession? = null
    private val portee = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val poignee = Handler(Looper.getMainLooper())

    private lateinit var application36: Music36App
    private var reglages: EtatReglages = EtatReglages()

    /** URI de la piste déjà comptabilisée, pour ne pas compter deux fois la même écoute. */
    private var uriComptee: String? = null
    private var dernierEnregistrement = 0L

    /**
     * La file telle qu'elle a été mémorisée en dernier.
     *
     * Avec plusieurs milliers de titres, reconstruire cette liste toutes les cinq secondes
     * coûterait plus cher que la lecture elle-même. On ne la refait que lorsque la file change.
     */
    private var chronologieMemorisee: androidx.media3.common.Timeline? = null
    private var urisMemorisees: List<String> = emptyList()

    /** Vrai pendant une atténuation commandée (pause ou reprise) : le battement laisse la main. */
    private var fonduManuel = false

    /** La rampe de volume en cours, s'il y en a une, pour pouvoir l'annuler. */
    private var rampeEnCours: Runnable? = null


    override fun onCreate() {
        super.onCreate()
        application36 = application as Music36App
        reglages = application36.depot.etatReglages.value

        lecteur = construireLecteur(reglages)
        lecteur.addListener(ecouteur)
        lecteur.addAnalyticsListener(analyses)

        session = MediaSession.Builder(this, LecteurAvecFondu(lecteur))
            .setSessionActivity(intentionDOuverture())
            .setCallback(rappels)
            .build()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(CANAL)
                .setChannelName(R.string.nom_application)
                .build()
                .apply { setSmallIcon(R.drawable.ic_note) }
        )

        // Les réglages changent pendant que le service tourne : la vitesse, le saut des silences
        // ou l'égaliseur doivent suivre sans qu'on redémarre la lecture.
        application36.depot.etatReglages
            .onEach { appliquer(it) }
            .launchIn(portee)

        // Le branchement d'un DAC USB doit capter la lecture, même quand une enceinte Bluetooth
        // reste appairée. La surveillance elle-même vit dans l'application.
        application36.sortieMaterielle.sortie
            .onEach { appliquerRoutage() }
            .launchIn(portee)

        poignee.post(battement)
    }

    /**
     * Ce que la session accepte des contrôleurs.
     *
     * Sans ce rappel, media3 refuse toute file venue d'un contrôleur qui n'envoie que des
     * identifiants — Android Auto, l'assistant vocal, une montre. L'identifiant est ici l'URI du
     * document, il suffit donc de le remettre en place pour que le morceau soit jouable.
     */
    private val rappels = object : MediaSession.Callback {

        /**
         * ⚠️ **Ce qui lève la limite de taille de la file.**
         *
         * media3 republie la file entière vers la session du système à chaque changement, sans
         * jamais la tronquer, et c'est le contrôleur de notification qui décide si elle l'est :
         * `MediaSessionLegacyStub.isQueueEnabled()` ne regarde que `COMMAND_GET_TIMELINE`. Avec
         * des milliers de titres, cette republication dépasse de très loin ce qu'une transaction
         * accepte et la commande se perd en silence.
         *
         * On retire donc cette commande **à ce seul contrôleur**. L'écran verrouillé, la
         * notification et les boutons du casque continuent de fonctionner : ils lisent le morceau
         * en cours, pas la liste. Notre propre contrôleur, lui, garde la file complète.
         */
        override fun onConnect(
            session: MediaSession,
            controleur: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val commandes = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
            if (!session.isMediaNotificationController(controleur)) {
                return MediaSession.ConnectionResult.accept(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS,
                    commandes
                )
            }
            return MediaSession.ConnectionResult.accept(
                MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS,
                commandes.buildUpon().remove(Player.COMMAND_GET_TIMELINE).build()
            )
        }
        override fun onAddMediaItems(
            session: MediaSession,
            controleur: MediaSession.ControllerInfo,
            elements: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            return Futures.immediateFuture(resoudre(elements).toMutableList())
        }
    }

    /**
     * Choisit la sortie du lecteur.
     *
     * `setPreferredAudioDevice(null)` rend la main au routage automatique d'Android ; un
     * périphérique explicite l'emporte. On ne force que l'USB : détourner un casque filaire ou le
     * Bluetooth du routage système n'apporterait rien et casserait les usages (appel entrant,
     * navigation vocale).
     */
    private fun appliquerRoutage() {
        if (!::lecteur.isInitialized) return
        val surveillance = application36.sortieMaterielle
        val usb = surveillance.peripheriqueActif
            ?.takeIf { reglages.routageUsb && surveillance.sortie.value?.estUsb == true }
        runCatching { lecteur.setPreferredAudioDevice(usb) }
            .onFailure { Log.w(TAG, "Routage audio refusé", it) }
    }

    /**
     * Construit ExoPlayer avec la chaîne audio la plus fidèle possible.
     *
     * - sortie flottante : évite la troncature à 16 bits quand la source est en 24 ou 32 bits ;
     * - `setEnableAudioTrackPlaybackParams` : le changement de vitesse est confié au matériel,
     *   sans rééchantillonnage logiciel supplémentaire ;
     * - `setEnableDecoderFallback` : si le décodeur matériel refuse un fichier exotique, on
     *   retombe sur le décodeur logiciel au lieu d'échouer.
     */
    private fun construireLecteur(reglages: EtatReglages): ExoPlayer {
        // ⚠️ `FabriqueRendus36` et non `DefaultRenderersFactory` : c'est elle qui ajoute le
        // décodeur ALAC embarqué (voir `lecture/alac/`).
        val fabrique = FabriqueRendus36(this)
            .setMediaCodecSelector(selecteurDecodeurs)
            .setEnableAudioFloatOutput(reglages.sortieFlottante)
            .setEnableAudioTrackPlaybackParams(true)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)

        // Recherche autorisée dans les flux à débit constant sans index (MP3 et AAC bruts) :
        // sans cela, la barre de progression de ces fichiers refuse de bouger.
        val extracteurs = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setAdtsExtractorFlags(AdtsExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING)

        return ExoPlayer.Builder(this, fabrique)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this, extracteurs))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setSkipSilenceEnabled(reglages.sauterSilences)
            .build()
    }

    /**
     * Choix des décodeurs.
     *
     * Pour l'**ALAC**, les décodeurs logiciels passent devant : quelques décodeurs de
     * constructeurs acceptent le format puis ne rendent que du silence, alors que le décodeur
     * logiciel d'Android, lui, fait le travail. Pour tout le reste, l'ordre par défaut est le bon
     * — il privilégie le matériel, donc la batterie.
     */
    private val selecteurDecodeurs = MediaCodecSelector { mime, securise, tunnel ->
        val decodeurs = MediaCodecSelector.DEFAULT.getDecoderInfos(mime, securise, tunnel)
        if (mime == MimeTypes.AUDIO_ALAC) {
            decodeurs.sortedBy { if (it.softwareOnly) 0 else 1 }
        } else {
            decodeurs
        }
    }

    /** Ce que la notification ouvre quand on la touche : l'application. */
    private fun intentionDOuverture(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE
    )

    private fun appliquer(nouveaux: EtatReglages) {
        reglages = nouveaux
        lecteur.skipSilenceEnabled = nouveaux.sauterSilences
        lecteur.playbackParameters = PlaybackParameters(
            nouveaux.vitesse,
            if (nouveaux.tonaliteSuitVitesse) nouveaux.vitesse else 1f
        )
        // Le déchargement vers le DSP est incompatible avec les effets audio : l'égaliseur, quand
        // il est allumé, l'emporte.
        val mode = if (nouveaux.dechargementAudio && !nouveaux.egaliseurActif) {
            TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
        } else {
            TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
        }
        lecteur.trackSelectionParameters = lecteur.trackSelectionParameters.buildUpon()
            .setAudioOffloadPreferences(
                TrackSelectionParameters.AudioOffloadPreferences.Builder()
                    .setAudioOffloadMode(mode)
                    .setIsGaplessSupportRequired(true)
                    .build()
            )
            .build()
        application36.egaliseur.attacher(lecteur.audioSessionId, nouveaux)
        appliquerRoutage()
    }

    private val ecouteur = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, raison: Int) {
            uriComptee = null
            if (reglages.fonduMs > 0 && !fonduManuel) lecteur.volume = 0f
            enregistrerSession()
        }

        override fun onIsPlayingChanged(enLecture: Boolean) {
            enregistrerSession()
        }

        override fun onPlaybackStateChanged(etat: Int) {
            if (etat == Player.STATE_ENDED) enregistrerSession()
        }
    }

    private val analyses = object : AnalyticsListener {
        override fun onAudioSessionIdChanged(eventTime: AnalyticsListener.EventTime, session: Int) {
            application36.egaliseur.attacher(session, reglages)
        }

        override fun onAudioDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            nomDecodeur: String,
            instantInitialisation: Long,
            dureeInitialisation: Long
        ) {
            Log.i(TAG, "Décodeur audio : $nomDecodeur")
            SortieAudio.decrireDecodeur(nomDecodeur)
        }

        override fun onAudioInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            evaluation: DecoderReuseEvaluation?
        ) {
            SortieAudio.decrire(format, reglages.sortieFlottante)
            SortieAudio.decrireSource(titreDe(eventTime))
        }

        /** Le moment où le son commence réellement à sortir : c'est celui qui ne ment pas. */
        override fun onAudioPositionAdvancing(
            eventTime: AnalyticsListener.EventTime,
            instantDebutLecture: Long
        ) {
            SortieAudio.decrireSource(titreDe(eventTime))
        }
    }

    /**
     * Le titre de la fenêtre à laquelle se rapporte un événement de la chaîne audio.
     *
     * L'interface lit l'état du `MediaController` ; ceci lit celui du lecteur. Quand les deux
     * affichages diffèrent, on sait enfin lequel des deux a décroché.
     */
    private fun titreDe(evenement: AnalyticsListener.EventTime): String? {
        val chronologie = evenement.timeline
        if (chronologie.isEmpty || evenement.windowIndex >= chronologie.windowCount) return null
        val fenetre = chronologie.getWindow(evenement.windowIndex, Timeline.Window())
        return fenetre.mediaItem.mediaMetadata.title?.toString()
            ?: fenetre.mediaItem.mediaId.substringAfterLast('/').ifBlank { null }
    }

    /**
     * Le battement qui suit la lecture : fondu, position de reprise et comptage d'écoute.
     *
     * Un vrai fondu enchaîné demanderait deux lecteurs qui se recouvrent ; ici l'atténuation est
     * franche — le morceau se lève et s'éteint sur [EtatReglages.fonduMs] — ce qui supprime les
     * attaques brutales sans doubler la consommation.
     */
    private val battement = object : Runnable {
        override fun run() {
            if (::lecteur.isInitialized && lecteur.isPlaying) {
                if (!fonduManuel) appliquerFondu()
                comptabiliser()
            }
            poignee.postDelayed(this, 100)
        }
    }

    private fun appliquerFondu() {
        val fondu = reglages.fonduMs
        if (fondu <= 0) {
            if (lecteur.volume != 1f) lecteur.volume = 1f
            return
        }
        val position = lecteur.currentPosition
        val duree = lecteur.duration
        val restant = if (duree == C.TIME_UNSET) Long.MAX_VALUE else duree - position
        val volume = when {
            position < fondu -> position.toFloat() / fondu
            restant < fondu && lecteur.hasNextMediaItem() -> restant.toFloat() / fondu
            else -> 1f
        }.coerceIn(0f, 1f)
        if (kotlin.math.abs(lecteur.volume - volume) > 0.01f) lecteur.volume = volume
    }

    private fun comptabiliser() {
        val uri = lecteur.currentMediaItem?.mediaId ?: return
        val position = lecteur.currentPosition
        val duree = lecteur.duration

        if (uri != uriComptee && position > SEUIL_ECOUTE) {
            uriComptee = uri
            application36.depot.compterUneLecture(uri)
        }

        val maintenant = System.currentTimeMillis()
        if (maintenant - dernierEnregistrement > INTERVALLE_ENREGISTREMENT) {
            dernierEnregistrement = maintenant
            enregistrerSession()
            if (reglages.memoriserPositionLongues && duree != C.TIME_UNSET && duree > SEUIL_LONGUE) {
                application36.depot.memoriserReprise(uri, position)
            }
        }
    }

    /** Mémorise la file en cours, pour la retrouver au prochain démarrage. */
    private fun enregistrerSession() {
        if (!::lecteur.isInitialized) return
        val chronologie = lecteur.currentTimeline
        if (chronologie !== chronologieMemorisee) {
            chronologieMemorisee = chronologie
            urisMemorisees = (0 until lecteur.mediaItemCount)
                .map { lecteur.getMediaItemAt(it).mediaId }
        }
        val uris = urisMemorisees
        application36.depot.memoriserSession(
            uris = uris,
            index = lecteur.currentMediaItemIndex,
            position = lecteur.currentPosition,
            aleatoire = lecteur.shuffleModeEnabled,
            repetition = lecteur.repeatMode
        )
    }

    /**
     * Enveloppe qui adoucit la pause et la reprise.
     *
     * Toutes les commandes — notification, écran verrouillé, casque, interface — passent par ce
     * `ForwardingPlayer`, donc le fondu s'applique d'où qu'elles viennent. Le lecteur enveloppé
     * est appelé par sa référence directe : Kotlin interdit `super` depuis une lambda, et c'est
     * bien depuis la fin de l'atténuation qu'il faut couper.
     */
    private inner class LecteurAvecFondu(private val interne: ExoPlayer) : ForwardingPlayer(interne) {

        override fun pause() {
            if (!fonduActif()) {
                interne.pause()
                return
            }
            fondre(vers = 0f) {
                interne.pause()
                interne.volume = 1f
            }
        }

        override fun play() {
            if (!fonduActif()) {
                interne.play()
                return
            }
            interne.volume = 0f
            interne.play()
            fondre(vers = 1f) {}
        }

        override fun setPlayWhenReady(playWhenReady: Boolean) {
            if (playWhenReady) play() else pause()
        }

        private fun fonduActif() = reglages.fonduMs > 0 && reglages.fonduPause
    }

    /** Rampe de volume linéaire, par pas de 40 ms, puis [fin]. */
    private fun fondre(vers: Float, fin: () -> Unit) {
        // ⚠️ On annule la rampe précédente. Deux atténuations qui se chevauchent — une pause
        // suivie aussitôt d'une reprise — écrivaient toutes deux dans le volume, et celle qui
        // finissait la dernière imposait sa conclusion : le lecteur restait muet, ou se mettait
        // en pause tout seul. Le défaut survivait au changement de morceau, le volume étant une
        // propriété du lecteur et non de la piste.
        rampeEnCours?.let { poignee.removeCallbacks(it) }
        fonduManuel = true
        val depart = lecteur.volume
        val duree = reglages.fonduMs.coerceAtMost(3_000).toLong()
        val debut = android.os.SystemClock.elapsedRealtime()
        val pas = object : Runnable {
            override fun run() {
                val avancement = ((android.os.SystemClock.elapsedRealtime() - debut).toFloat() /
                    duree).coerceIn(0f, 1f)
                lecteur.volume = depart + (vers - depart) * avancement
                if (avancement < 1f) {
                    poignee.postDelayed(this, 40)
                } else {
                    rampeEnCours = null
                    fonduManuel = false
                    fin()
                }
            }
        }
        rampeEnCours = pas
        poignee.post(pas)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /**
     * L'application est balayée des applications récentes.
     *
     * On ne coupe la lecture que si elle est déjà à l'arrêt : sinon, poser le téléphone et fermer
     * la liste des tâches arrêterait la musique, ce que personne n'attend d'un lecteur.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!lecteur.playWhenReady || lecteur.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        poignee.removeCallbacks(battement)
        rampeEnCours?.let { poignee.removeCallbacks(it) }
        enregistrerSession()
        portee.cancel()
        application36.egaliseur.detacher()
        session?.run {
            player.release()
            release()
        }
        session = null
        SortieAudio.effacer()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "36Music/Service"
        const val CANAL = "36music_lecture"

        /** Au-delà de quinze secondes, on considère que le morceau a été écouté. */
        const val SEUIL_ECOUTE = 15_000L

        /** Les pistes de plus de dix minutes gardent leur position de reprise. */
        const val SEUIL_LONGUE = 10 * 60_000L

        /**
         * Intervalle entre deux sauvegardes de la session.
         *
         * Les réglages vivent dans un `DataStore`, réécrit en entier à chaque modification :
         * avec une file de plusieurs milliers d'URI, une sauvegarde toutes les cinq secondes
         * serait une écriture disque permanente pour une poignée de secondes de précision.
         */
        const val INTERVALLE_ENREGISTREMENT = 30_000L
    }
}

/**
 * Rend jouables les éléments reçus d'un contrôleur.
 *
 * Un contrôleur extérieur — Android Auto, l'assistant, une montre — n'envoie qu'un identifiant.
 * Ici l'identifiant **est** l'URI du document : le remettre en place suffit. Les éléments qui
 * n'ont ni URI ni identifiant sont écartés, faute de quoi la session refuserait toute la file
 * pour un seul élément vide.
 */
@UnstableApi
fun resoudre(elements: List<MediaItem>): List<MediaItem> = elements.mapNotNull { element ->
    when {
        element.localConfiguration != null -> element
        element.mediaId.isNotBlank() -> element.buildUpon().setUri(element.mediaId).build()
        else -> null
    }
}

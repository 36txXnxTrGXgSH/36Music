package com.music36.app.data

import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.File
import java.security.MessageDigest

/** Ce que l'écran affiche pendant qu'on parcourt les dossiers. */
data class EtatAnalyse(
    val enCours: Boolean = false,
    val dossierCourant: String = "",
    val fichiersVus: Int = 0,
    val fichiersLus: Int = 0,
    val total: Int = 0,
    val termineeLe: Long = 0,
    val ajoutees: Int = 0,
    val retirees: Int = 0,
    /** Fichiers gardés malgré des métadonnées illisibles — utile pour comprendre un souci. */
    val illisibles: Int = 0,
    /** Playlists `.m3u` découvertes pendant cette analyse. */
    val playlistsAjoutees: Int = 0,
    /** Playlists dont le fichier avait changé et qu'on a relues. */
    val playlistsMisesAJour: Int = 0,
    /** Playlists retirées parce que leur fichier a disparu de l'appareil. */
    val playlistsRetirees: Int = 0,
    val erreur: String? = null
)

/**
 * Ce qu'une analyse a changé, en une phrase par ligne.
 *
 * ⚠️ « Aucune modification » est une réponse à part entière : sans elle, une analyse qui ne
 * trouve rien ressemble à une analyse qui n'a pas eu lieu — c'est exactement ce qui a été
 * signalé sur la 4.5.
 */
fun resumeAnalyse(etat: EtatAnalyse): String {
    if (etat.erreur != null) return "Analyse interrompue : ${etat.erreur}"
    val lignes = buildList {
        if (etat.ajoutees > 0) add("${etat.ajoutees} titre${pluriel(etat.ajoutees)} ajouté${pluriel(etat.ajoutees)}")
        if (etat.retirees > 0) {
            add("${etat.retirees} titre${pluriel(etat.retirees)} retiré${pluriel(etat.retirees)} (fichiers disparus)")
        }
        if (etat.playlistsAjoutees > 0) {
            add("${etat.playlistsAjoutees} playlist${pluriel(etat.playlistsAjoutees)} importée${pluriel(etat.playlistsAjoutees)}")
        }
        if (etat.playlistsMisesAJour > 0) {
            add("${etat.playlistsMisesAJour} playlist${pluriel(etat.playlistsMisesAJour)} mise${pluriel(etat.playlistsMisesAJour)} à jour")
        }
        if (etat.playlistsRetirees > 0) {
            add("${etat.playlistsRetirees} playlist${pluriel(etat.playlistsRetirees)} retirée${pluriel(etat.playlistsRetirees)} (fichier supprimé)")
        }
        if (etat.illisibles > 0) {
            add("${etat.illisibles} fichier${pluriel(etat.illisibles)} gardé${pluriel(etat.illisibles)} malgré des étiquettes illisibles")
        }
    }
    return if (lignes.isEmpty()) "Aucune modification" else lignes.joinToString("\n")
}

/**
 * Parcourt les dossiers choisis par l'utilisateur et remplit la bibliothèque.
 *
 * Tout passe par SAF (`DocumentsContract`), jamais par `MediaStore` : c'est ce qui permet de
 * n'analyser **que** les dossiers désignés, de fonctionner sur une carte SD, et de ne demander
 * aucune permission de stockage. Rien n'est analysé tant que l'utilisateur n'a pas choisi un
 * premier dossier.
 */
class Analyseur(
    private val contexte: Context,
    private val base: BaseMusique
) {
    private val _etat = MutableStateFlow(EtatAnalyse())
    val etat: StateFlow<EtatAnalyse> = _etat.asStateFlow()

    private val verrou = Mutex()

    /** Compté pendant l'analyse, depuis plusieurs fils : d'où l'entier atomique. */
    private val illisibles = java.util.concurrent.atomic.AtomicInteger(0)

    /** Dossier de cache des pochettes extraites des fichiers. */
    private val dossierPochettes: File by lazy {
        File(contexte.cacheDir, "pochettes").apply { mkdirs() }
    }

    /**
     * Analyse toutes les racines actives.
     *
     * Les fichiers déjà connus dont ni la taille ni la date n'ont bougé ne sont pas relus : une
     * réanalyse d'une grande bibliothèque coûte alors une simple énumération.
     */
    suspend fun analyser(reglages: EtatReglages, complet: Boolean = false) = verrou.withLock {
        // Tout le parcours vit sur le fil d'entrées-sorties : appelée depuis l'écran, cette
        // fonction bloquerait sinon l'interface le temps d'énumérer des milliers de fichiers.
        withContext(Dispatchers.IO) { analyserVraiment(reglages, complet) }
    }

    private suspend fun analyserVraiment(reglages: EtatReglages, complet: Boolean) {
        val debut = System.currentTimeMillis()
        illisibles.set(0)
        _etat.value = EtatAnalyse(enCours = true)
        try {
            val racines = base.racines().toutesMaintenant().filter { it.active }
            if (racines.isEmpty()) {
                _etat.value = EtatAnalyse(termineeLe = System.currentTimeMillis())
                return
            }

            var ajoutees = 0
            var retirees = 0
            val playlistsTrouvees = mutableListOf<FichierTrouve>()

            for (racine in racines) {
                if (!autorisationValide(racine.uri)) {
                    Log.w(TAG, "Autorisation perdue sur ${racine.uri}")
                    continue
                }
                val trouves = mutableListOf<FichierTrouve>()
                val imagesDeDossier = mutableMapOf<String, Uri>()
                parcourir(
                    racineUri = Uri.parse(racine.uri),
                    documentId = DocumentsContract.getTreeDocumentId(Uri.parse(racine.uri)),
                    chemin = racine.nom,
                    ignorerCaches = reglages.ignorerFichiersCaches,
                    audio = trouves,
                    playlists = playlistsTrouvees,
                    images = imagesDeDossier
                )

                val connues = base.pistes().empreintes(racine.uri).associateBy { it.uri }
                val aLire = trouves.filter { f ->
                    val connue = connues[f.uri.toString()]
                    complet || connue == null ||
                        connue.dateFichier != f.dateModif || connue.taille != f.taille
                }
                _etat.value = _etat.value.copy(total = _etat.value.total + aLire.size)

                val nouvelles = lireEnParallele(aLire, racine, connues, reglages, imagesDeDossier)
                if (nouvelles.isNotEmpty()) {
                    nouvelles.chunked(200).forEach { base.pistes().inserer(it) }
                }
                ajoutees += nouvelles.count { connues[it.uri] == null }

                val disparues = connues.keys - trouves.map { it.uri.toString() }.toSet()
                if (disparues.isNotEmpty()) {
                    disparues.chunked(400).forEach { base.pistes().supprimer(it) }
                    retirees += disparues.size
                }
            }

            // ⚠️ Appelé même sans playlist trouvée : c'est aussi là que sont retirées celles
            // dont le fichier a disparu, et zéro fichier trouvé peut vouloir dire qu'on les a
            // toutes supprimées depuis le téléphone.
            val bilanPlaylists = if (reglages.importerM3u) {
                importerPlaylists(playlistsTrouvees, reglages)
            } else {
                BilanPlaylists()
            }

            _etat.value = EtatAnalyse(
                enCours = false,
                termineeLe = System.currentTimeMillis(),
                ajoutees = ajoutees,
                retirees = retirees,
                illisibles = illisibles.get(),
                playlistsAjoutees = bilanPlaylists.ajoutees,
                playlistsMisesAJour = bilanPlaylists.misesAJour,
                playlistsRetirees = bilanPlaylists.retirees,
                fichiersLus = _etat.value.fichiersLus,
                fichiersVus = _etat.value.fichiersVus
            )
            Log.i(TAG, "Analyse terminée en ${System.currentTimeMillis() - debut} ms")
        } catch (e: Exception) {
            Log.e(TAG, "Analyse interrompue", e)
            _etat.value = EtatAnalyse(enCours = false, erreur = e.message ?: "Erreur inconnue")
        }
    }

    /** Un fichier repéré pendant le parcours, avant toute lecture de métadonnées. */
    private data class FichierTrouve(
        val uri: Uri,
        val nom: String,
        val chemin: String,
        val taille: Long,
        val dateModif: Long,
        val mime: String
    )

    private suspend fun parcourir(
        racineUri: Uri,
        documentId: String,
        chemin: String,
        ignorerCaches: Boolean,
        audio: MutableList<FichierTrouve>,
        playlists: MutableList<FichierTrouve>,
        images: MutableMap<String, Uri>,
        profondeur: Int = 0
    ): Unit = withContext(Dispatchers.IO) {
        if (profondeur > PROFONDEUR_MAX) return@withContext
        yield()
        _etat.value = _etat.value.copy(dossierCourant = chemin)

        val enfants = DocumentsContract.buildChildDocumentsUriUsingTree(racineUri, documentId)
        val sousDossiers = mutableListOf<Pair<String, String>>()
        var curseur: Cursor? = null
        try {
            curseur = contexte.contentResolver.query(
                enfants,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED
                ),
                null, null, null
            )
        } catch (e: Exception) {
            Log.w(TAG, "Dossier illisible : $chemin", e)
        }

        curseur?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val nom = c.getString(1) ?: continue
                val mime = c.getString(2) ?: ""
                val taille = if (c.isNull(3)) 0L else c.getLong(3)
                val dateModif = if (c.isNull(4)) 0L else c.getLong(4)

                if (ignorerCaches && nom.startsWith(".")) continue

                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    sousDossiers += id to nom
                    continue
                }

                val uri = DocumentsContract.buildDocumentUriUsingTree(racineUri, id)
                when {
                    estAudio(nom, mime) -> {
                        audio += FichierTrouve(uri, nom, chemin, taille, dateModif, mime)
                        _etat.value = _etat.value.copy(fichiersVus = _etat.value.fichiersVus + 1)
                    }

                    nom.substringAfterLast('.', "").lowercase() in EXTENSIONS_PLAYLIST ->
                        playlists += FichierTrouve(uri, nom, chemin, taille, dateModif, mime)

                    estImage(nom, mime) -> {
                        // Une image nommée « cover », « folder » ou « front » l'emporte ; sinon on
                        // retient la première venue, faute de mieux.
                        val prioritaire = nom.substringBeforeLast('.').lowercase() in NOMS_POCHETTE
                        if (prioritaire || chemin !in images) images[chemin] = uri
                    }
                }
            }
        }

        // Les sous-dossiers sont visités après avoir refermé le curseur : garder plusieurs
        // curseurs SAF ouverts d'un coup finit par épuiser le fournisseur de documents.
        for ((id, nom) in sousDossiers) {
            parcourir(
                racineUri, id, "$chemin/$nom", ignorerCaches, audio, playlists, images,
                profondeur + 1
            )
        }
    }

    private suspend fun lireEnParallele(
        fichiers: List<FichierTrouve>,
        racine: Racine,
        connues: Map<String, Empreinte>,
        reglages: EtatReglages,
        images: Map<String, Uri>
    ): List<Piste> = coroutineScope {
        val resultat = mutableListOf<Piste>()
        // Quatre lectures de front : au-delà, les décodeurs matériels se marchent dessus et le
        // gain disparaît.
        for (paquet in fichiers.chunked(LOT)) {
            val pistes = paquet.map { fichier ->
                async(Dispatchers.IO) {
                    val piste = runCatching {
                        lireMetadonnees(
                            fichier, racine, connues[fichier.uri.toString()], reglages,
                            images[fichier.chemin]
                        )
                    }.getOrElse {
                        // ⚠️ Un fichier dont les étiquettes sont illisibles reste un fichier
                        // audio : l'écarter le ferait **disparaître** de la bibliothèque alors
                        // qu'ExoPlayer le lit peut-être très bien. On le garde, nommé d'après
                        // son fichier, et on compte l'échec pour pouvoir le dire.
                        Log.w(TAG, "Métadonnées illisibles : ${fichier.nom}", it)
                        illisibles.incrementAndGet()
                        sansMetadonnees(fichier, racine, connues[fichier.uri.toString()])
                    }
                    _etat.value = _etat.value.copy(fichiersLus = _etat.value.fichiersLus + 1)
                    piste
                }
            }.awaitAll().filterNotNull()
            resultat += pistes.filter {
                // Une durée inconnue (0) n'est pas une durée courte : ne pas la filtrer.
                reglages.dureeMinimale <= 0 || it.duree == 0L ||
                    it.duree >= reglages.dureeMinimale * 1000L
            }
            yield()
        }
        resultat
    }

    private fun lireMetadonnees(
        fichier: FichierTrouve,
        racine: Racine,
        connue: Empreinte?,
        reglages: EtatReglages,
        imageDuDossier: Uri?
    ): Piste {
        val lecteur = MediaMetadataRetriever()
        var titre = ""
        var artiste = ""
        var album = ""
        var artisteAlbum = ""
        var genre = ""
        var annee = 0
        var numero = 0
        var disque = 0
        var duree = 0L
        var debit = 0
        var echantillonnage = 0
        var bits = 0
        var canaux = 0
        var pochette: String? = null

        try {
            lecteur.setDataSource(contexte, fichier.uri)
            fun cle(k: Int) = runCatching { lecteur.extractMetadata(k) }.getOrNull()?.trim().orEmpty()

            titre = cle(MediaMetadataRetriever.METADATA_KEY_TITLE)
            artiste = cle(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            album = cle(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            artisteAlbum = cle(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
            genre = cle(MediaMetadataRetriever.METADATA_KEY_GENRE)
            annee = analyserAnnee(
                cle(MediaMetadataRetriever.METADATA_KEY_YEAR),
                cle(MediaMetadataRetriever.METADATA_KEY_DATE)
            )
            numero = premierNombre(cle(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER))
            disque = premierNombre(cle(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER))
            duree = cle(MediaMetadataRetriever.METADATA_KEY_DURATION).toLongOrNull() ?: 0L
            debit = (cle(MediaMetadataRetriever.METADATA_KEY_BITRATE).toIntOrNull() ?: 0) / 1000

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                echantillonnage = cle(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE).toIntOrNull() ?: 0
                bits = cle(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE).toIntOrNull() ?: 0
            }

            pochette = extrairePochette(lecteur, fichier, album.ifBlank { fichier.chemin })
                ?: imageDuDossier?.toString()
        } finally {
            runCatching { lecteur.release() }
        }

        val extension = fichier.nom.substringAfterLast('.', "").lowercase()

        // ⚠️ Un `.m4a` peut être de l'AAC **ou** de l'ALAC — deux formats que tout oppose, l'un
        // avec perte, l'autre sans. L'extension ne suffit donc pas : sur ces conteneurs, on ouvre
        // le fichier pour lire le vrai codec, même quand l'analyse approfondie est éteinte.
        // Sans cela, un ALAC s'annonce « AAC », perd son badge Hi-Res et affiche un débit qui ne
        // veut rien dire.
        val conteneurAmbigu = extension in EXTENSIONS_AMBIGUES
        var codec = ""
        if (conteneurAmbigu || (reglages.analyseApprofondie && (echantillonnage == 0 || canaux == 0))) {
            val format = lireFormat(fichier.uri)
            if (echantillonnage == 0) echantillonnage = format.echantillonnage
            if (canaux == 0) canaux = format.canaux
            if (bits == 0) bits = format.bits
            if (debit == 0) debit = format.debit
            codec = nomDuCodec(format.mime)
        }
        // ⚠️ Le conteneur a le dernier mot sur les `.m4a`. `MediaExtractor` échoue sur l'ALAC là
        // où l'appareil n'a pas le décodeur — et il en existe beaucoup : il rend alors « AAC »,
        // avec un débit de fichier sans perte. Le code à quatre lettres de `stsd`, lui, ne dépend
        // d'aucun décodeur installé.
        if (extension in EXTENSIONS_MP4) {
            val reel = Mp4.nomDuCode(codeMp4(fichier.uri))
            if (reel.isNotBlank()) codec = reel
        }
        return Piste(
            uri = fichier.uri.toString(),
            racine = racine.uri,
            dossier = fichier.chemin,
            nomFichier = fichier.nom,
            titre = titre.ifBlank { fichier.nom.substringBeforeLast('.') },
            artiste = artiste,
            album = album.ifBlank { fichier.chemin.substringAfterLast('/') },
            artisteAlbum = artisteAlbum,
            genre = genre,
            annee = annee,
            numeroPiste = numero,
            disque = disque,
            duree = duree,
            taille = fichier.taille,
            dateFichier = fichier.dateModif,
            dateAjout = connue?.dateAjout ?: System.currentTimeMillis(),
            format = codec.ifBlank { FORMATS[extension] ?: extension.uppercase() },
            debit = debit,
            echantillonnage = echantillonnage,
            bits = bits,
            canaux = canaux,
            pochette = pochette
        )
    }

    /**
     * La piste qu'on garde quand les métadonnées refusent de se lire.
     *
     * Tout vient alors du nom du fichier et du dossier : c'est pauvre, mais le morceau reste
     * visible, jouable, et classable dans ses favoris.
     */
    private fun sansMetadonnees(
        fichier: FichierTrouve,
        racine: Racine,
        connue: Empreinte?
    ): Piste {
        val extension = fichier.nom.substringAfterLast('.', "").lowercase()
        return Piste(
            uri = fichier.uri.toString(),
            racine = racine.uri,
            dossier = fichier.chemin,
            nomFichier = fichier.nom,
            titre = fichier.nom.substringBeforeLast('.'),
            artiste = "",
            album = fichier.chemin.substringAfterLast('/'),
            artisteAlbum = "",
            genre = "",
            annee = 0,
            numeroPiste = 0,
            disque = 0,
            duree = 0,
            taille = fichier.taille,
            dateFichier = fichier.dateModif,
            dateAjout = connue?.dateAjout ?: System.currentTimeMillis(),
            format = FORMATS[extension] ?: extension.uppercase(),
            debit = 0,
            echantillonnage = 0,
            bits = 0,
            canaux = 0,
            pochette = null
        )
    }

    private data class FormatAudio(
        val echantillonnage: Int = 0,
        val canaux: Int = 0,
        val bits: Int = 0,
        val debit: Int = 0,
        /** Type MIME de la piste audio : `audio/alac`, `audio/mp4a-latm`… */
        val mime: String = ""
    )

    /**
     * Rend son vrai nom au sans-perte que les versions antérieures ont étiqueté « AAC ».
     *
     * L'analyse est incrémentale : un fichier dont ni la taille ni la date n'ont bougé n'est
     * jamais relu, et une correction de détection ne l'atteindrait donc qu'à la prochaine analyse
     * complète. On ne rouvre ici que les fichiers manifestement mal étiquetés — voir
     * [estUnAacInvraisemblable] — ce qui, sur une bibliothèque ordinaire, n'en fait aucun.
     */
    suspend fun corrigerCodecsMp4() = withContext(Dispatchers.IO) {
        val suspectes = base.pistes().toutesMaintenant()
            .filter { estUnAacInvraisemblable(it.format, it.nomFichier, it.debit) }
        if (suspectes.isEmpty()) return@withContext
        val corrigees = suspectes.mapNotNull { piste ->
            val reel = Mp4.nomDuCode(codeMp4(Uri.parse(piste.uri)))
            if (reel.isBlank() || reel == piste.format) null else piste.copy(format = reel)
        }
        if (corrigees.isNotEmpty()) {
            base.pistes().inserer(corrigees)
            Log.i(TAG, "Codec corrigé pour ${corrigees.size} fichier(s) MP4")
        }
    }

    /** Le code à quatre lettres du flux audio, lu dans le conteneur MP4. */
    private fun codeMp4(uri: Uri): String? = runCatching {
        contexte.contentResolver.openInputStream(uri)?.use { Mp4.codecAudio(it) }
    }.getOrNull()

    private fun lireFormat(uri: Uri): FormatAudio {
        val extracteur = MediaExtractor()
        return try {
            extracteur.setDataSource(contexte, uri, null)
            for (i in 0 until extracteur.trackCount) {
                val format = extracteur.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (!mime.startsWith("audio/")) continue
                FormatAudio(
                    echantillonnage = format.entier(MediaFormat.KEY_SAMPLE_RATE),
                    canaux = format.entier(MediaFormat.KEY_CHANNEL_COUNT),
                    bits = bitsDepuisEncodage(format),
                    debit = format.entier(MediaFormat.KEY_BIT_RATE) / 1000,
                    mime = mime
                ).let { return it }
            }
            FormatAudio()
        } catch (e: Exception) {
            FormatAudio()
        } finally {
            runCatching { extracteur.release() }
        }
    }

    private fun MediaFormat.entier(cle: String): Int =
        if (containsKey(cle)) runCatching { getInteger(cle) }.getOrDefault(0) else 0

    private fun bitsDepuisEncodage(format: MediaFormat): Int {
        if (format.containsKey("bits-per-sample")) {
            return runCatching { format.getInteger("bits-per-sample") }.getOrDefault(0)
        }
        if (!format.containsKey(MediaFormat.KEY_PCM_ENCODING)) return 0
        return when (runCatching { format.getInteger(MediaFormat.KEY_PCM_ENCODING) }.getOrNull()) {
            android.media.AudioFormat.ENCODING_PCM_8BIT -> 8
            android.media.AudioFormat.ENCODING_PCM_16BIT -> 16
            android.media.AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24
            android.media.AudioFormat.ENCODING_PCM_32BIT -> 32
            android.media.AudioFormat.ENCODING_PCM_FLOAT -> 32
            else -> 0
        }
    }

    /**
     * Pochette : d'abord l'image embarquée, sinon un `cover.jpg` posé à côté du fichier.
     *
     * Les images embarquées sont réduites et mises en cache une seule fois par album — sans quoi
     * un album de vingt pistes stockerait vingt fois la même jaquette.
     */
    private fun extrairePochette(
        lecteur: MediaMetadataRetriever,
        fichier: FichierTrouve,
        cleAlbum: String
    ): String? {
        val octets = runCatching { lecteur.embeddedPicture }.getOrNull()
        if (octets != null && octets.isNotEmpty()) {
            val nom = empreinte(fichier.chemin + '|' + cleAlbum + '|' + octets.size) + ".jpg"
            val cible = File(dossierPochettes, nom)
            if (!cible.exists()) {
                runCatching { ecrireVignette(octets, cible) }.onFailure { cible.delete() }
            }
            if (cible.exists()) return Uri.fromFile(cible).toString()
        }
        return null
    }

    private fun ecrireVignette(octets: ByteArray, cible: File) {
        val mesure = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(octets, 0, octets.size, mesure)
        var echelle = 1
        while (mesure.outWidth / echelle > POCHETTE_MAX || mesure.outHeight / echelle > POCHETTE_MAX) {
            echelle *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = echelle }
        val image = BitmapFactory.decodeByteArray(octets, 0, octets.size, options)
            ?: throw IllegalStateException("pochette indécodable")
        cible.outputStream().use { image.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        image.recycle()
    }

    /** Reprend les `.m3u` trouvés dans les dossiers analysés et en fait de vraies playlists. */
    /** Ce que l'import des `.m3u` a changé. */
    private data class BilanPlaylists(
        val ajoutees: Int = 0,
        val misesAJour: Int = 0,
        val retirees: Int = 0
    )

    private suspend fun importerPlaylists(
        fichiers: List<FichierTrouve>,
        reglages: EtatReglages
    ): BilanPlaylists {
        val pistes = base.pistes().toutesMaintenant()
        // ⚠️ Ce que l'application a elle-même exporté n'est pas réimporté : ce sont des copies
        // de playlists déjà présentes, et les relire les ferait toutes apparaître en double.
        val exportes = dossiersExportesDe(reglages).mapNotNull { dossier ->
            runCatching { DocumentsContract.getTreeDocumentId(Uri.parse(dossier)) }.getOrNull()
        }
        var ajoutees = 0
        var misesAJour = 0
        // ⚠️ Reconnues par leur **document**, pas par leur URI : le même fichier vu depuis deux
        // dossiers surveillés imbriqués donne deux URI différentes, et la playlist entrait alors
        // une seconde fois.
        // ⚠️ **Mutable, et tenue à jour au fil de l'import.** Deux dossiers surveillés imbriqués
        // font rencontrer le même fichier deux fois dans une seule analyse : avec une carte figée
        // au départ, la playlist créée à la première rencontre était inconnue à la seconde, et
        // une deuxième ligne naissait pour le même fichier.
        val parFichier = base.playlists().toutesMaintenant()
            .filter { it.source != null }
            .associateByTo(mutableMapOf()) { cleDeFichier(it.source!!) }
        // Un même fichier atteint par deux dossiers surveillés n'est lu qu'une fois.
        for (fichier in fichiers.distinctBy { cleDeFichier(it.uri.toString()) }) {
            if (exportes.any { dossier ->
                    runCatching {
                        contient(dossier, DocumentsContract.getDocumentId(fichier.uri))
                    }.getOrDefault(false)
                }
            ) {
                continue
            }
            runCatching {
                val contenu = contexte.contentResolver.openInputStream(fichier.uri)
                    ?.use { it.readBytes().toString(Charsets.UTF_8) } ?: return@runCatching
                val uris = M3u.resoudre(contenu, pistes)
                if (uris.isEmpty()) return@runCatching
                val source = fichier.uri.toString()
                val empreinte = "${fichier.taille}:${fichier.dateModif}"
                val existante = parFichier[cleDeFichier(source)]

                // ⚠️ **On ne relit le fichier que s'il a changé.** Auparavant, chaque analyse
                // remplaçait le contenu de la playlist par celui du fichier : un titre retiré
                // dans l'application revenait au scan suivant, en silence.
                if (existante != null && existante.empreinte == empreinte) return@runCatching

                val id = existante?.id ?: base.playlists().creer(
                    Playlist(
                        nom = fichier.nom.substringBeforeLast('.'),
                        dateCreation = System.currentTimeMillis(),
                        source = source,
                        empreinte = empreinte
                    )
                )
                if (existante == null) ajoutees++ else misesAJour++
                base.playlists().remplacerContenu(id, uris)
                base.playlists().marquerEmpreinte(id, empreinte)
                // Connue pour la suite de cette analyse, même si elle vient d'être créée.
                base.playlists().parId(id)?.let { parFichier[cleDeFichier(source)] = it }
            }.onFailure { Log.w(TAG, "Playlist ignorée : ${fichier.nom}", it) }
        }
        return BilanPlaylists(ajoutees, misesAJour, retirerLesPlaylistsDisparues(fichiers))
    }

    /**
     * Retire les playlists dont le fichier `.m3u` n'existe plus sur l'appareil.
     *
     * ⚠️ Deux conditions, et la seconde est indispensable : le fichier doit être **absent de
     * l'analyse** et **illisible**. Sans la seconde, une playlist rangée dans un dossier qu'on
     * ne surveille plus — donc jamais parcourue — disparaîtrait de l'application alors que son
     * fichier est toujours là. Les playlists créées dans l'application n'ont pas de source :
     * elles ne sont jamais concernées.
     */
    private suspend fun retirerLesPlaylistsDisparues(trouvees: List<FichierTrouve>): Int {
        val sourcesVues = trouvees.map { it.uri.toString() }.toHashSet()
        var retirees = 0
        base.playlists().toutesMaintenant()
            .filter { it.source != null && it.source !in sourcesVues }
            .forEach { playlist ->
                val source = playlist.source ?: return@forEach
                if (existeEncore(Uri.parse(source))) return@forEach
                base.playlists().viderContenu(playlist.id)
                base.playlists().supprimer(playlist.id)
                retirees++
                Log.i(TAG, "Playlist retirée, fichier disparu : ${playlist.nom}")
            }
        return retirees
    }

    /** Le document existe-t-il encore ? Une requête vide vaut « non ». */
    private fun existeEncore(uri: Uri): Boolean = runCatching {
        contexte.contentResolver.query(
            uri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null
        )?.use { it.moveToFirst() } ?: false
    }.getOrDefault(false)

    private fun autorisationValide(uri: String): Boolean =
        contexte.contentResolver.persistedUriPermissions.any {
            it.uri.toString() == uri && it.isReadPermission
        }

    companion object {
        private const val TAG = "36Music/Analyse"
        private const val PROFONDEUR_MAX = 24
        private const val LOT = 4
        private const val POCHETTE_MAX = 720

        /** Extensions acceptées, au-delà de ce que le type MIME du fournisseur annonce. */
        val EXTENSIONS_AUDIO = setOf(
            "mp3", "m4a", "m4b", "m4r", "mp4", "aac", "flac", "wav", "wave", "ogg", "oga",
            "opus", "aif", "aiff", "aifc", "alac", "ape", "wma", "asf", "wmv", "mkv", "mka",
            "webm", "amr", "awb", "3gp", "3gpp", "dsf", "dff", "wv", "mpc", "ac3", "eac3",
            "tta", "ra", "mid", "midi", "xmf", "ota", "imy", "mp2", "mpga", "caf", "au", "snd"
        )
        val EXTENSIONS_PLAYLIST = setOf("m3u", "m3u8", "pls")

        /** Conteneurs qui peuvent abriter plusieurs codecs : leur extension ne dit rien. */
        /**
         * Au-delà de ce débit, un fichier annoncé « AAC » ne l'est pas.
         *
         * L'AAC plafonne en pratique vers 320 kbit/s et son format n'en autorise pas beaucoup
         * plus ; un `.m4a` à 1 100 kbit/s est du sans-perte qu'un décodeur absent a fait passer
         * pour de l'AAC.
         */
        const val DEBIT_AAC_MAXIMUM = 600

        /** Conteneurs de la famille MP4, dont le vrai codec se lit dans la boîte `stsd`. */
        val EXTENSIONS_MP4 = setOf("m4a", "m4b", "m4r", "mp4", "alac", "m4v", "mov")

        val EXTENSIONS_AMBIGUES = setOf(
            "m4a", "m4b", "m4r", "mp4", "mka", "mkv", "webm", "ogg", "oga", "caf", "asf", "wma"
        )

        /** Le nom courant d'un codec, à partir du type MIME de la piste audio. */
        fun nomDuCodec(mime: String): String = when {
            mime.isBlank() -> ""
            mime == "audio/alac" -> "ALAC"
            mime == "audio/mp4a-latm" || mime == "audio/aac" -> "AAC"
            mime == "audio/flac" -> "FLAC"
            mime == "audio/mpeg" || mime == "audio/mpeg-L2" -> "MP3"
            mime == "audio/opus" -> "Opus"
            mime == "audio/vorbis" -> "Vorbis"
            mime == "audio/raw" || mime.startsWith("audio/pcm") -> "PCM"
            mime == "audio/ac3" -> "AC3"
            mime == "audio/eac3" -> "E-AC3"
            mime == "audio/amr-nb" -> "AMR"
            mime == "audio/amr-wb" -> "AMR-WB"
            mime == "audio/x-ms-wma" -> "WMA"
            else -> mime.substringAfter('/').uppercase()
        }
        val EXTENSIONS_IMAGE = setOf("jpg", "jpeg", "png", "webp", "bmp")

        /** Noms de fichiers que les logiciels de gravure posent à côté des pistes. */
        val NOMS_POCHETTE = setOf(
            "cover", "folder", "front", "albumart", "album", "pochette", "artwork", "thumb"
        )

        /** Nom lisible du format, à partir de l'extension. */
        val FORMATS = mapOf(
            "mp3" to "MP3", "m4a" to "AAC", "m4b" to "AAC", "mp4" to "AAC", "aac" to "AAC",
            "flac" to "FLAC", "wav" to "WAV", "wave" to "WAV", "ogg" to "Vorbis",
            "oga" to "Vorbis", "opus" to "Opus", "aif" to "AIFF", "aiff" to "AIFF",
            "alac" to "ALAC", "ape" to "APE", "wma" to "WMA", "wv" to "WavPack",
            "dsf" to "DSD", "dff" to "DSD", "mka" to "Matroska", "mkv" to "Matroska",
            "webm" to "WebM", "ac3" to "AC3", "eac3" to "E-AC3", "amr" to "AMR",
            "mid" to "MIDI", "midi" to "MIDI", "mpc" to "Musepack", "tta" to "TTA"
        )

        /**
         * Types MIME des listes de lecture.
         *
         * ⚠️ `audio/x-mpegurl` **commence par `audio/`**. C'est par là qu'un `.m3u8` entrait dans
         * la bibliothèque : il ne portait aucune musique, mais son nom suffisait à faire basculer
         * ExoPlayer sur HLS, dont le module n'est pas embarqué. Comme le lecteur fabrique toutes
         * ses sources dès la pose de la file, ce seul élément la faisait refuser **entière** —
         * et « Toutes les chansons » était la seule vue qui n'écartait rien.
         */
        val MIMES_PLAYLIST = setOf(
            "audio/x-mpegurl", "audio/mpegurl", "application/x-mpegurl",
            "application/vnd.apple.mpegurl", "audio/x-scpls", "application/pls+xml"
        )

        fun estAudio(nom: String, mime: String): Boolean {
            val extension = nom.substringAfterLast('.', "").lowercase()
            // Les listes de lecture se reconnaissent avant tout le reste, sans quoi leur type
            // MIME les ferait passer pour de l'audio.
            if (extension in EXTENSIONS_PLAYLIST || mime.lowercase() in MIMES_PLAYLIST) return false
            if (extension in EXTENSIONS_AUDIO) return true
            return mime.startsWith("audio/") || mime == "application/ogg" ||
                mime == "application/x-ogg"
        }

        fun estImage(nom: String, mime: String): Boolean =
            nom.substringAfterLast('.', "").lowercase() in EXTENSIONS_IMAGE ||
                mime.startsWith("image/")

        fun analyserAnnee(vararg valeurs: String): Int {
            for (valeur in valeurs) {
                val trouve = Regex("(19|20)\\d{2}").find(valeur)?.value?.toIntOrNull()
                if (trouve != null) return trouve
            }
            return 0
        }

        /** « 3/12 » comme « 3 » donnent 3 ; tout le reste donne 0. */
        fun premierNombre(valeur: String): Int =
            Regex("\\d+").find(valeur)?.value?.toIntOrNull() ?: 0

        fun empreinte(texte: String): String =
            MessageDigest.getInstance("MD5").digest(texte.toByteArray())
                .joinToString("") { "%02x".format(it) }
    }
}

/**
 * Ce fichier annoncé « AAC » peut-il vraiment l'être ?
 *
 * Sert de filtre à [Analyseur.corrigerCodecsMp4] : on ne rouvre que les fichiers dont l'étiquette
 * est contredite par leur propre débit, et jamais toute la bibliothèque.
 */
fun estUnAacInvraisemblable(format: String, nomFichier: String, debit: Int): Boolean =
    format.equals("AAC", ignoreCase = true) &&
        nomFichier.substringAfterLast('.', "").lowercase() in Analyseur.EXTENSIONS_MP4 &&
        debit > Analyseur.DEBIT_AAC_MAXIMUM

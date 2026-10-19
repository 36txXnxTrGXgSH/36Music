package com.music36.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.Flow
import com.music36.app.data.contient
import com.music36.app.data.avecDossierExporte

/**
 * Le point d'entrée unique sur la bibliothèque : tout l'écran lit ces flux, personne n'interroge
 * la base directement.
 *
 * Les regroupements (artistes, albums, genres, dossiers) sont recalculés en mémoire depuis la
 * liste complète des pistes plutôt que par des requêtes SQL dédiées. Pour quelques milliers de
 * titres c'est instantané, et cela garantit que les sept onglets voient exactement la même chose.
 */
class Depot(
    private val contexte: Context,
    val base: BaseMusique,
    val reglages: Reglages,
    private val portee: CoroutineScope
) {
    val analyseur = Analyseur(contexte, base)

    init {
        purgerLesListesDeLecture()
        // Elle ouvre des fichiers : elle n'a rien à faire sur le fil qui dessine.
        portee.launch(Dispatchers.IO) { analyseur.corrigerCodecsMp4() }
    }

    /**
     * Retire de la bibliothèque les listes de lecture qu'une version antérieure y avait rangées.
     *
     * Un `.m3u8` annonce `audio/x-mpegurl` : il passait pour de l'audio. Il ne portait aucune
     * musique, mais son nom suffisait à faire basculer ExoPlayer sur HLS — module absent — et,
     * comme le lecteur fabrique toutes ses sources dès la pose de la file, la file entière était
     * refusée. Le scanner ne les accepte plus ; celles déjà en base partent d'elles-mêmes, sans
     * imposer une réanalyse complète de la bibliothèque.
     */
    private fun purgerLesListesDeLecture() {
        // ⚠️ Hors du fil principal : la portée de l'application est sur `Main.immediate`, et
        // filtrer plusieurs milliers de pistes au démarrage y gelait l'affichage.
        portee.launch(Dispatchers.Default) {
            val indesirables = base.pistes().toutesMaintenant()
                .filter {
                    it.nomFichier.substringAfterLast('.', "").lowercase() in
                        Analyseur.EXTENSIONS_PLAYLIST
                }
                .map { it.uri }
            if (indesirables.isNotEmpty()) base.pistes().supprimer(indesirables)
        }
    }

    /**
     * Un flux de base qui ne renverse pas la portée de l'application quand il échoue.
     *
     * ⚠️ Room lève quand sa base se referme sous lui. En production cela n'arrive pas — la base
     * vit aussi longtemps que le processus — mais l'exception partait alors dans la portée
     * partagée par toute l'application, et rien ne garantit qu'un flux ait plus de droit qu'un
     * autre d'emporter les seize autres avec lui. On journalise et on s'arrête, sans bruit.
     */
    private fun <T> Flow<T>.sansFaireTomberLApplication(quoi: String): Flow<T> =
        catch { erreur -> Log.w("36Music/Depot", "Flux « $quoi » interrompu", erreur) }

    val etatReglages: StateFlow<EtatReglages> =
        reglages.flux.stateIn(portee, SharingStarted.Eagerly, EtatReglages())

    val pistes: StateFlow<List<Piste>> =
        base.pistes().toutes().sansFaireTomberLApplication("pistes")
            .stateIn(portee, SharingStarted.Eagerly, emptyList())

    val racines: StateFlow<List<Racine>> =
        base.racines().toutes().sansFaireTomberLApplication("racines")
            .stateIn(portee, SharingStarted.Eagerly, emptyList())

    val favoris: StateFlow<Set<String>> = base.favoris().uris()
        .map { it.toSet() }
        .stateIn(portee, SharingStarted.Eagerly, emptySet())

    val statistiques: StateFlow<Map<String, Statistique>> = base.statistiques().toutes()
        .map { liste -> liste.associateBy { it.uri } }
        .stateIn(portee, SharingStarted.Eagerly, emptyMap())

    // Les regroupements sont calculés hors du fil principal : sur une bibliothèque de plusieurs
    // milliers de titres, les faire au fil de l'affichage se verrait.
    val playlists: StateFlow<List<PlaylistGarnie>> = combine(
        base.playlists().toutes(),
        base.playlists().tousLesLiens(),
        base.pistes().toutes()
    ) { playlists, liens, pistes ->
        val parUri = pistes.associateBy { it.uri }
        val parPlaylist = liens.groupBy { it.playlistId }
        playlists.map { playlist ->
            PlaylistGarnie(
                playlist = playlist,
                pistes = parPlaylist[playlist.id].orEmpty()
                    .sortedBy { it.position }
                    .mapNotNull { parUri[it.uri] }
            )
        }
    }.flowOn(Dispatchers.Default).sansFaireTomberLApplication("playlists")
        .stateIn(portee, SharingStarted.Eagerly, emptyList())

    val pistesFavorites: StateFlow<List<Piste>> =
        combine(pistes, favoris) { toutes, cles -> toutes.filter { it.uri in cles } }
            .flowOn(Dispatchers.Default)
            .stateIn(portee, SharingStarted.Eagerly, emptyList())

    val artistes: StateFlow<List<Collection>> = pistes.map { it.enArtistes() }
        .flowOn(Dispatchers.Default)
        .stateIn(portee, SharingStarted.Eagerly, emptyList())

    val albums: StateFlow<List<Collection>> = pistes.map { it.enAlbums() }
        .flowOn(Dispatchers.Default)
        .stateIn(portee, SharingStarted.Eagerly, emptyList())

    val genres: StateFlow<List<Collection>> = pistes.map { it.enGenres() }
        .flowOn(Dispatchers.Default)
        .stateIn(portee, SharingStarted.Eagerly, emptyList())

    /** Un dossier par chemin réellement porteur de musique, racines comprises. */
    val dossiers: StateFlow<List<Collection>> = pistes.map { liste ->
        liste.groupBy { it.dossier }
            .map { (chemin, pistes) ->
                Collection(
                    cle = chemin,
                    nom = chemin.substringAfterLast('/'),
                    sousTitre = chemin,
                    pistes = pistes.trier(Tri.PISTE, true)
                )
            }
            .sortedBy { it.sousTitre.lowercase() }
    }.flowOn(Dispatchers.Default).stateIn(portee, SharingStarted.Eagerly, emptyList())

    /**
     * Les dossiers de premier niveau : ceux que l'utilisateur a désignés.
     *
     * ⚠️ **Les racines de même nom sont réunies en une seule entrée.** Un dossier `Music` sur la
     * mémoire interne et un autre sur la carte SD portent le même nom, et la navigation les
     * confond déjà : elle filtre sur le chemin (`Music/…`), identique de part et d'autre. Les
     * laisser séparés donnait deux lignes indiscernables **et de même clé** — ce que `LazyColumn`
     * refuse, en faisant tomber l'application.
     */
    val dossiersRacines: StateFlow<List<Collection>> =
        combine(pistes, racines) { liste, racines -> regrouperRacines(racines, liste) }.flowOn(Dispatchers.Default).stateIn(portee, SharingStarted.Eagerly, emptyList())

    /**
     * Les titres présents dans **au moins une** playlist.
     *
     * Sert à repérer, d'un coup d'œil dans les listes, ceux qui n'appartiennent à aucune —
     * ceux qu'on a oubliés de ranger.
     */
    val urisEnPlaylist: StateFlow<Set<String>> =
        base.playlists().tousLesLiens()
            .map { liens -> liens.mapTo(HashSet()) { it.uri } }
            .flowOn(Dispatchers.Default)
            .stateIn(portee, SharingStarted.Eagerly, emptySet())

    /**
     * Retire [uri] des playlists désignées, sans toucher au fichier.
     *
     * Le morceau reste dans la bibliothèque et sur la carte : seule son appartenance disparaît.
     */
    suspend fun retirerDesPlaylists(uri: String, ids: List<Long>) = withContext(Dispatchers.IO) {
        ids.forEach { id ->
            val restantes = base.playlists().liens(id).sortedBy { it.position }
                .map { it.uri }
                .filterNot { it == uri }
            base.playlists().remplacerContenu(id, restantes)
            repercuterDansLeFichier(id)
        }
    }

    /**
     * Réécrit le `.m3u` d'origine après une modification faite dans l'application.
     *
     * ⚠️ Sans cela, le fichier et la playlist divergent : le titre retiré ici reste là-bas, et
     * reviendrait le jour où le fichier changerait pour une autre raison. L'empreinte est remise
     * à jour dans la foulée, sinon l'analyse suivante prendrait notre propre écriture pour une
     * modification extérieure et réimporterait par-dessus.
     */
    suspend fun repercuterDansLeFichier(id: Long): ResultatReecriture {
        if (!etatReglages.value.repercuterM3u) return ResultatReecriture.DESACTIVE
        val playlist = base.playlists().parId(id) ?: return ResultatReecriture.SANS_FICHIER
        val source = playlist.source ?: return ResultatReecriture.SANS_FICHIER
        return runCatching {
            val uri = Uri.parse(source)
            val parUri = base.pistes().toutesMaintenant().associateBy { it.uri }
            val pistes = base.playlists().liens(id).sortedBy { it.position }
                .mapNotNull { parUri[it.uri] }

            // ⚠️ Les chemins sont écrits **relativement au dossier du fichier** : un `.m3u`
            // désigne ses pistes depuis sa propre place, et non depuis la racine surveillée.
            val dossier = DocumentsContract.getDocumentId(uri).substringBeforeLast('/')
            val contenu = M3u.ecrire(playlist.nom, pistes) { piste ->
                cheminRelatif(dossier, identifiantDe(piste.uri) ?: piste.nomFichier)
            }

            // « wt » tronque : sans cela, une playlist raccourcie garderait la fin de l'ancien
            // contenu derrière la nouvelle.
            val ecrit = contexte.contentResolver.openOutputStream(uri, "wt")?.use { flux ->
                flux.write(contenu.toByteArray(Charsets.UTF_8))
                true
            } ?: false
            if (!ecrit) return@runCatching ResultatReecriture.ECHEC

            // Le fichier prend le nom qui dit ce qu'il contient : du M3U en UTF-8.
            val renomme = convertirEnM3u8(uri)
            if (renomme != null) {
                base.playlists().marquerSource(id, renomme.toString(), empreinteDe(renomme))
            } else {
                base.playlists().marquerEmpreinte(id, empreinteDe(uri))
            }
            ResultatReecriture.ECRIT
        }.getOrElse { erreur ->
            Log.w("36Music/Depot", "Réécriture du .m3u refusée : ${playlist.nom}", erreur)
            // Un refus de permission et un fichier disparu ne se corrigent pas de la même
            // façon : autant les distinguer pour pouvoir le dire à l'utilisateur.
            if (erreur is SecurityException || racinesEnLectureSeule().isNotEmpty()) {
                ResultatReecriture.LECTURE_SEULE
            } else {
                ResultatReecriture.ECHEC
            }
        }
    }

    /**
     * Renomme un `.m3u` en `.m3u8` après réécriture, et rend sa nouvelle URI.
     *
     * ⚠️ Rend `null` quand il n'y a rien à faire **ou** quand le renommage échoue : l'appelant
     * garde alors l'ancienne URI, qui reste valable. Perdre le lien vers le fichier serait pire
     * que de garder un nom imparfait.
     */
    private fun convertirEnM3u8(uri: Uri): Uri? {
        val identifiant = identifiantDe(uri.toString()) ?: return null
        val nouveau = nomEnM3u8(identifiant.substringAfterLast('/')) ?: return null

        // ⚠️ Un fichier de ce nom existe déjà : **ne pas renommer**. Le fournisseur d'Android
        // rend alors le document existant, et deux playlists se retrouvent sur le même fichier
        // — c'est exactement ce qui a produit les doublons du 28/09/2026. On garde le `.m3u`,
        // que le ménage des doublons n'a pas à réparer.
        val voisin = identifiant.substringBeforeLast('/') + "/" + nouveau
        if (fichierExiste(DocumentsContract.buildDocumentUri(uri.authority, voisin))) {
            Log.i("36Music/Depot", "Renommage évité, « $nouveau » existe déjà")
            return null
        }

        return runCatching {
            DocumentsContract.renameDocument(contexte.contentResolver, uri, nouveau)
        }.getOrElse {
            Log.w("36Music/Depot", "Renommage en .m3u8 refusé : $nouveau", it)
            null
        }
    }

    /** L'identifiant de document d'un URI SAF, celui qui porte `volume:chemin`. */
    private fun identifiantDe(uri: String): String? = runCatching {
        DocumentsContract.getDocumentId(Uri.parse(uri))
    }.getOrNull()

    /** Taille et date du fichier, telles que l'analyse les lira. */
    private fun empreinteDe(uri: Uri): String? = runCatching {
        contexte.contentResolver.query(
            uri,
            arrayOf(
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            ),
            null, null, null
        )?.use { curseur ->
            if (curseur.moveToFirst()) "${curseur.getLong(0)}:${curseur.getLong(1)}" else null
        }
    }.getOrNull()

    /**
     * Efface **réellement** les fichiers de la carte, puis les retire de la bibliothèque.
     *
     * ⚠️ Irréversible : ni corbeille, ni annulation. L'appelant doit avoir demandé confirmation.
     * Les URI que le fournisseur refuse d'effacer — carte retirée, fichier déjà disparu, dossier
     * en lecture seule — sont comptées à part et **restent** dans la bibliothèque : les faire
     * disparaître de l'écran alors qu'elles sont toujours là serait mentir.
     */
    /**
     * Les dossiers surveillés qu'Android ne nous laisse qu'en lecture.
     *
     * Rien ne peut y être effacé, déplacé ni réécrit. Les écrans s'en servent pour **dire
     * pourquoi** plutôt que de laisser croire à une panne.
     */
    fun racinesEnLectureSeule(): List<Racine> = racines.value.filterNot { ecritureAutorisee(it.uri) }

    /** L'écriture est-elle autorisée dans ce dossier surveillé ? */
    fun ecritureAutorisee(uriRacine: String): Boolean =
        contexte.contentResolver.persistedUriPermissions.any {
            it.uri.toString() == uriRacine && it.isWritePermission
        }

    suspend fun effacerFichiers(uris: List<String>): ResultatEffacement = withContext(Dispatchers.IO) {
        val effacees = mutableListOf<String>()
        var echecs = 0
        uris.forEach { uri ->
            val parti = runCatching {
                DocumentsContract.deleteDocument(contexte.contentResolver, Uri.parse(uri))
            }.getOrElse {
                Log.w("36Music/Depot", "Effacement refusé : $uri", it)
                false
            }
            if (parti) effacees += uri else echecs++
        }
        if (effacees.isNotEmpty()) base.pistes().supprimer(effacees)
        ResultatEffacement(
            effaces = effacees.size,
            echecs = echecs,
            // Si rien n'est parti et qu'un dossier n'est autorisé qu'en lecture, c'est presque
            // toujours la vraie raison : autant la nommer.
            lectureSeule = echecs > 0 && racinesEnLectureSeule().isNotEmpty()
        )
    }

    /**
     * Déplace des fichiers vers [destination], un dossier choisi par l'utilisateur.
     *
     * ⚠️ Android n'autorise le déplacement qu'**au sein d'un même support** : d'une carte SD vers
     * la mémoire interne, le fournisseur refuse. Et `moveDocument` réclame le dossier d'origine,
     * qu'aucune API ne donne — on le reconstruit depuis l'identifiant du document, dont le dernier
     * segment est le nom du fichier. Les refus sont comptés, jamais masqués.
     */
    suspend fun deplacerFichiers(pistes: List<Piste>, destination: Uri): ResultatDeplacement =
        withContext(Dispatchers.IO) {
            var deplaces = 0
            var echecs = 0
            pistes.forEach { piste ->
                val fait = runCatching {
                    val source = Uri.parse(piste.uri)
                    val parent = DocumentsContract.buildDocumentUriUsingTree(
                        Uri.parse(piste.racine),
                        DocumentsContract.getDocumentId(source).substringBeforeLast('/')
                    )
                    DocumentsContract.moveDocument(
                        contexte.contentResolver, source, parent, destination
                    ) != null
                }.getOrElse {
                    Log.w("36Music/Depot", "Déplacement refusé : ${piste.nomFichier}", it)
                    false
                }
                if (fait) deplaces++ else echecs++
            }
            if (deplaces > 0) analyser()
            ResultatDeplacement(
                deplaces = deplaces,
                echecs = echecs,
                lectureSeule = echecs > 0 && racinesEnLectureSeule().isNotEmpty()
            )
        }

    /**
     * Écrit les playlists choisies dans [destination], au format demandé.
     *
     * Le dossier vient du sélecteur d'Android : l'application n'écrit jamais ailleurs que là où
     * l'utilisateur l'a autorisée. Un fichier de même nom est **remplacé** — réexporter après
     * avoir modifié une playlist est le geste le plus courant, et accumuler des « (2) » serait
     * plus pénible qu'utile.
     */
    suspend fun exporterPlaylists(
        ids: List<Long>,
        destination: Uri,
        format: FormatExport
    ): ResultatExport = withContext(Dispatchers.IO) {
        val garnies = playlists.value.filter { it.playlist.id in ids.toHashSet() }
        var ecrites = 0
        var echecs = 0
        garnies.forEach { garnie ->
            val nom = nomFichierExport(garnie.playlist.nom, format)
            val fait = runCatching {
                val parent = DocumentsContract.buildDocumentUriUsingTree(
                    destination, DocumentsContract.getTreeDocumentId(destination)
                )
                supprimerSiPresent(parent, nom)
                val fichier = DocumentsContract.createDocument(
                    contexte.contentResolver, parent, format.mime, nom
                ) ?: return@runCatching false
                contexte.contentResolver.openOutputStream(fichier, "wt")?.use { flux ->
                    flux.write(
                        contenuExport(garnie.playlist.nom, garnie.pistes, format)
                            .toByteArray(charset(format.codage))
                    )
                }
                true
            }.getOrElse {
                Log.w("36Music/Depot", "Export refusé : $nom", it)
                false
            }
            if (fait) ecrites++ else echecs++
        }
        // ⚠️ Retenu pour que l'analyse **n'importe pas** ce qu'on vient d'écrire : sinon chaque
        // playlist exportée dans un dossier surveillé revient en double à l'analyse suivante.
        // Un dossier surveillé pour lui-même n'entre pas dans la liste : l'utilisateur a
        // explicitement demandé qu'on le lise.
        if (ecrites > 0 && racines.value.none { it.uri == destination.toString() }) {
            reglages.modifier { it.avecDossierExporte(destination.toString()) }
            oublierLesPlaylistsDe(destination.toString())
        }
        ResultatExport(ecrites = ecrites, echecs = echecs, dossier = destination.toString())
    }

    /**
     * Retire de la bibliothèque les playlists **déjà importées** depuis un dossier d'export.
     *
     * Elles n'auraient jamais dû y entrer : ce sont nos propres copies. Les fichiers, eux, ne
     * sont pas touchés — c'est l'export de l'utilisateur, il en fait ce qu'il veut.
     */
    private suspend fun oublierLesPlaylistsDe(dossier: String) {
        val idDossier = runCatching {
            DocumentsContract.getTreeDocumentId(Uri.parse(dossier))
        }.getOrNull() ?: return
        base.playlists().toutesMaintenant()
            .filter { playlist ->
                val source = playlist.source ?: return@filter false
                runCatching {
                    contient(idDossier, DocumentsContract.getDocumentId(Uri.parse(source)))
                }.getOrDefault(false)
            }
            .forEach { playlist ->
                base.playlists().viderContenu(playlist.id)
                base.playlists().supprimer(playlist.id)
                Log.i("36Music/Depot", "Playlist d'export oubliée : ${playlist.nom}")
            }
    }

    /** Efface un homonyme déjà présent, pour que l'export remplace au lieu de s'empiler. */
    private fun supprimerSiPresent(parent: Uri, nom: String) {
        val enfants = DocumentsContract.buildChildDocumentsUriUsingTree(
            parent, DocumentsContract.getDocumentId(parent)
        )
        runCatching {
            contexte.contentResolver.query(
                enfants,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME
                ),
                null, null, null
            )?.use { curseur ->
                while (curseur.moveToNext()) {
                    if (curseur.getString(1) == nom) {
                        DocumentsContract.deleteDocument(
                            contexte.contentResolver,
                            DocumentsContract.buildDocumentUriUsingTree(parent, curseur.getString(0))
                        )
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ dossiers, en arbre

    /** Les sous-dossiers immédiats de [chemin], avec leur contenu récursif. */
    fun sousDossiers(chemin: String): List<Collection> = pistes.value.sousDossiersDe(chemin)

    /** Les pistes posées directement dans [chemin] (sans descendre dans les sous-dossiers). */
    fun pistesDuDossier(chemin: String): List<Piste> =
        pistes.value.filter { it.dossier == chemin }

    /** Toutes les pistes de [chemin] et de ce qu'il contient — ce que lit « Tout lire ». */
    fun pistesRecursives(chemin: String): List<Piste> =
        pistes.value.filter { it.dossier == chemin || it.dossier.startsWith("$chemin/") }

    // ------------------------------------------------------------------ racines

    /**
     * Retient un dossier choisi par l'utilisateur.
     *
     * La permission est rendue persistante immédiatement : sans cela, l'URI redevient illisible
     * au prochain démarrage et la bibliothèque se viderait toute seule.
     *
     * ⚠️ **En lecture *et* en écriture.** Jusqu'à la 4.5 seule la lecture était conservée : le
     * sélecteur accorde bien les deux, mais ce qu'on ne retient pas est perdu. Android refusait
     * donc tout effacement (« le fichier est toujours là »), tout déplacement et toute
     * réécriture d'un `.m3u`, sans qu'aucun écran ne puisse dire pourquoi. Un dossier ajouté
     * avant cette version reste en lecture seule tant qu'il n'a pas été choisi à nouveau :
     * une permission persistante ne se complète pas après coup.
     */
    suspend fun ajouterRacine(uri: Uri): ResultatRacine = withContext(Dispatchers.IO) {
        runCatching {
            contexte.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }

        val choisi = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
        val existantes = base.racines().toutesMaintenant()

        // ⚠️ Déjà surveillé : on ne fait que **reprendre la permission**, qui vient peut-être
        // de gagner le droit d'écrire. Ré-insérer ne changerait rien d'autre.
        if (existantes.any { it.uri == uri.toString() }) {
            return@withContext if (ecritureAutorisee(uri.toString())) {
                ResultatRacine.ECRITURE_AUTORISEE
            } else {
                ResultatRacine.DEJA_SURVEILLE
            }
        }

        // ⚠️ Un sous-dossier d'un dossier déjà surveillé n'est **pas** ajouté : sa musique est
        // déjà dans la bibliothèque, et l'ajouter la compterait deux fois.
        val parent = choisi?.let { id ->
            existantes.firstOrNull { racine ->
                runCatching {
                    contient(DocumentsContract.getTreeDocumentId(Uri.parse(racine.uri)), id)
                }.getOrDefault(false)
            }
        }
        if (parent != null) return@withContext ResultatRacine.DEJA_COUVERT(parent.nom)

        ajouterVraimentLaRacine(uri)
        ResultatRacine.AJOUTE
    }

    private suspend fun ajouterVraimentLaRacine(uri: Uri) {
        val nom = DocumentFile.fromTreeUri(contexte, uri)?.name
            ?: DocumentsContract.getTreeDocumentId(uri).substringAfterLast('/')
        base.racines().ajouter(
            Racine(uri = uri.toString(), nom = nom, dateAjout = System.currentTimeMillis())
        )
    }

    /**
     * Les dossiers surveillés qui sont **contenus dans un autre**, avec le nom de celui-ci.
     *
     * Une bibliothèque qui compte deux fois la même musique n'est le signe d'aucune panne : c'est
     * un dossier de trop, et il faut pouvoir le désigner pour le retirer.
     */
    fun racinesEnDouble(): Map<String, String> {
        val toutes = racines.value
        val ids = toutes.associateWith { racine ->
            runCatching {
                DocumentsContract.getTreeDocumentId(Uri.parse(racine.uri))
            }.getOrNull()
        }
        return buildMap {
            toutes.forEach { enfant ->
                val idEnfant = ids[enfant] ?: return@forEach
                val parent = toutes.firstOrNull { autre ->
                    autre.uri != enfant.uri && ids[autre]?.let { contient(it, idEnfant) } == true
                }
                if (parent != null) put(enfant.uri, parent.nom)
            }
        }
    }

    suspend fun supprimerRacine(racine: Racine) = withContext(Dispatchers.IO) {
        base.pistes().viderRacine(racine.uri)
        base.racines().supprimer(racine)
        runCatching {
            contexte.contentResolver.releasePersistableUriPermission(
                Uri.parse(racine.uri), Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    /** Relance une analyse ; [complet] relit les métadonnées même des fichiers inchangés. */
    fun analyser(complet: Boolean = false) {
        portee.launch {
            analyseur.analyser(etatReglages.value, complet)
            // ⚠️ Après chaque analyse, et pas seulement à la demande : c'est le seul moment où
            // l'on sait ce qui existe encore sur l'appareil.
            nettoyerLesPlaylists()
        }
    }

    /**
     * Fait que la liste des playlists corresponde aux fichiers présents sur l'appareil.
     *
     * Deux ménages, dans cet ordre, et l'ordre compte :
     *
     * 1. **Un fichier, une playlist.** Deux lignes ont pu se retrouver sur le même fichier — le
     *    28/09/2026, renommer `X.m3u` en `X.m3u8` alors qu'un `X.m3u8` existait déjà (une copie
     *    exportée) a fait tomber les deux sur le même document. On garde la plus fournie, et
     *    **on réécrit son fichier avant** de retirer l'autre : le fichier repart ainsi avec les
     *    données de l'application, jamais avec celles d'une ligne qu'on s'apprête à jeter.
     * 2. **Plus de playlist sans fichier.** Ce qui n'existe plus sur l'appareil ne doit plus
     *    figurer dans la bibliothèque. Les playlists créées dans l'application n'ont pas de
     *    fichier : elles ne sont jamais concernées.
     */
    suspend fun nettoyerLesPlaylists(): ResultatDoublons = withContext(Dispatchers.IO) {
        val toutes = base.playlists().toutesMaintenant()
        val liens = base.playlists().tousLesLiensMaintenant().groupBy { it.playlistId }
        val menage = deciderLeMenage(
            playlists = toutes,
            nombreDePistes = { id -> liens[id]?.size ?: 0 },
            fichierExiste = { source -> fichierExiste(Uri.parse(source)) },
            cleDuFichier = ::cleDeFichier
        )

        // ⚠️ **D'abord écrire, ensuite retirer.** Le fichier repart avec les données de
        // l'application ; le faire après le retrait risquerait d'y écrire une ligne qu'on vient
        // de jeter.
        var reecrites = 0
        menage.aReecrire.forEach { id ->
            if (repercuterDansLeFichier(id) == ResultatReecriture.ECRIT) reecrites++
        }

        val parId = toutes.associateBy { it.id }
        (menage.doublons + menage.disparues).forEach { id ->
            base.playlists().viderContenu(id)
            base.playlists().supprimer(id)
            Log.i("36Music/Depot", "Playlist retirée du ménage : ${parId[id]?.nom}")
        }

        ResultatDoublons(
            reecrites = reecrites,
            retirees = menage.doublons.size,
            disparues = menage.disparues.size
        )
    }

    /** Le document existe-t-il encore ? Une requête vide vaut « non ». */
    private fun fichierExiste(uri: Uri): Boolean = runCatching {
        contexte.contentResolver.query(
            uri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null
        )?.use { it.moveToFirst() } ?: false
    }.getOrDefault(false)

    // ------------------------------------------------------------------ favoris

    suspend fun basculerFavori(uri: String): Boolean = withContext(Dispatchers.IO) {
        if (base.favoris().estFavori(uri)) {
            base.favoris().retirer(uri)
            false
        } else {
            base.favoris().ajouter(Favori(uri, System.currentTimeMillis()))
            true
        }
    }

    fun estFavori(uri: String?): Boolean = uri != null && uri in favoris.value

    /**
     * Efface tous les compteurs d'écoute.
     *
     * ⚠️ Les positions de reprise partent avec : elles vivent dans la même table, et garder la
     * reprise d'un morceau dont on vient d'effacer l'historique n'aurait pas de sens.
     */
    suspend fun reinitialiserStatistiques() = withContext(Dispatchers.IO) {
        base.statistiques().toutEffacer()
    }

    // ------------------------------------------------------------------ playlists

    suspend fun creerPlaylist(nom: String, uris: List<String> = emptyList()): Long =
        withContext(Dispatchers.IO) {
            val id = base.playlists().creer(
                Playlist(nom = nom, dateCreation = System.currentTimeMillis())
            )
            val uniques = uris.distinct()
            if (uniques.isNotEmpty()) base.playlists().ajouterAuBout(id, uniques)
            id
        }

    /**
     * Ajoute à une playlist les titres qui n'y sont pas déjà.
     *
     * ⚠️ On ne se contente pas d'ignorer les doublons en silence : le résultat dit combien de
     * titres sont entrés et combien étaient déjà là, pour que l'écran puisse l'annoncer. Ajouter
     * deux fois le même morceau n'a aucun sens dans une playlist, mais ne rien dire laisserait
     * croire que l'ajout a échoué.
     */
    suspend fun ajouterAPlaylist(id: Long, uris: List<String>): ResultatAjout =
        withContext(Dispatchers.IO) {
            val demandees = uris.distinct()
            val presentes = base.playlists().liens(id).mapTo(HashSet()) { it.uri }
            val nouvelles = demandees.filterNot { it in presentes }
            // ⚠️ Le sort du fichier d'origine **remonte avec le résultat** : quand la
            // réécriture échoue, l'écran doit pouvoir le dire. Elle échouait en silence
            // jusqu'à la 4.7, et le fichier restait dans son état d'avant sans un mot.
            val reecriture = if (nouvelles.isEmpty()) {
                ResultatReecriture.SANS_FICHIER
            } else {
                base.playlists().ajouterAuBout(id, nouvelles)
                repercuterDansLeFichier(id)
            }
            ResultatAjout(
                ajoutees = nouvelles.size,
                deja = demandees.size - nouvelles.size,
                reecriture = reecriture
            )
        }

    /**
     * Retire **une seule** apparition d'un titre d'une playlist.
     *
     * ⚠️ Une playlist peut porter deux fois le même morceau. Retirer par l'URI les emporterait
     * tous ; on désigne donc l'apparition par son rang parmi celles de ce titre (`occurrence`,
     * à partir de zéro, dans l'ordre du fichier). Ce rang ne dépend pas du tri affiché, ni des
     * titres disparus de la bibliothèque — c'est ce qui le rend sûr.
     */
    suspend fun retirerOccurrence(
        id: Long,
        uri: String,
        occurrence: Int
    ): ResultatReecriture = withContext(Dispatchers.IO) {
        val liens = base.playlists().liens(id).sortedBy { it.position }
        val visee = indexDeLApparition(liens.map { it.uri }, uri, occurrence)
            ?: return@withContext ResultatReecriture.ECHEC
        base.playlists().remplacerContenu(
            id,
            liens.filterIndexed { index, _ -> index != visee }.map { it.uri }
        )
        repercuterDansLeFichier(id)
    }

    suspend fun retirerDePlaylist(id: Long, position: Int) = withContext(Dispatchers.IO) {
        val restantes = base.playlists().liens(id).sortedBy { it.position }
            .filterIndexed { index, _ -> index != position }
            .map { it.uri }
        base.playlists().remplacerContenu(id, restantes)
        repercuterDansLeFichier(id)
    }

    suspend fun deplacerDansPlaylist(id: Long, de: Int, vers: Int) = withContext(Dispatchers.IO) {
        val uris = base.playlists().liens(id).sortedBy { it.position }.map { it.uri }.toMutableList()
        if (de !in uris.indices || vers !in uris.indices) return@withContext
        uris.add(vers, uris.removeAt(de))
        base.playlists().remplacerContenu(id, uris)
        repercuterDansLeFichier(id)
    }

    suspend fun renommerPlaylist(id: Long, nom: String) = withContext(Dispatchers.IO) {
        base.playlists().renommer(id, nom)
    }

    suspend fun supprimerPlaylist(id: Long) = withContext(Dispatchers.IO) {
        base.playlists().viderContenu(id)
        base.playlists().supprimer(id)
    }

    /**
     * Crée une playlist **dans un fichier** choisi par l'utilisateur, et y range des titres.
     *
     * Le nom vient du fichier : le sélecteur d'Android demande déjà le dossier et le nom, et
     * les redemander à la molette obligerait à écrire lettre par lettre.
     *
     * ⚠️ La permission est rendue persistante : sans cela, la playlist ne pourrait plus être
     * réécrite au prochain démarrage, et le fichier divergerait en silence.
     */
    suspend fun creerPlaylistDansFichier(destination: Uri, uris: List<String>): String =
        withContext(Dispatchers.IO) {
            runCatching {
                contexte.contentResolver.takePersistableUriPermission(
                    destination,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            val nom = (DocumentFile.fromSingleUri(contexte, destination)?.name
                ?: destination.lastPathSegment.orEmpty().substringAfterLast('/'))
                .substringBeforeLast('.')
                .ifBlank { "Playlist" }
            val id = base.playlists().creer(
                Playlist(
                    nom = nom,
                    dateCreation = System.currentTimeMillis(),
                    source = destination.toString()
                )
            )
            val uniques = uris.distinct()
            if (uniques.isNotEmpty()) base.playlists().ajouterAuBout(id, uniques)
            repercuterDansLeFichier(id)
            nom
        }

    /**
     * Écrit les playlists dans le cache de l'application et rend des URI partageables.
     *
     * ⚠️ Passe par `FileProvider` : une autre application n'a le droit de lire nos fichiers que
     * si nous lui en donnons l'autorisation, URI par URI. Le dossier est vidé à chaque partage,
     * pour ne pas accumuler des copies dont personne ne veut plus.
     */
    suspend fun preparerPartage(ids: List<Long>, format: FormatExport): List<Uri> =
        withContext(Dispatchers.IO) {
            val dossier = java.io.File(contexte.cacheDir, "partage")
            dossier.listFiles()?.forEach { it.delete() }
            dossier.mkdirs()
            playlists.value.filter { it.playlist.id in ids.toHashSet() }.mapNotNull { garnie ->
                runCatching {
                    val fichier = java.io.File(
                        dossier, nomFichierExport(garnie.playlist.nom, format)
                    )
                    fichier.writeText(
                        contenuExport(garnie.playlist.nom, garnie.pistes, format),
                        charset(format.codage)
                    )
                    FileProvider.getUriForFile(
                        contexte, "${contexte.packageName}.partage", fichier
                    )
                }.onFailure {
                    Log.w("36Music/Depot", "Partage impossible : ${garnie.playlist.nom}", it)
                }.getOrNull()
            }
        }

    /** Écrit une playlist dans le fichier `.m3u` choisi par l'utilisateur. */
    suspend fun exporterPlaylist(garnie: PlaylistGarnie, destination: Uri) =
        withContext(Dispatchers.IO) {
            contexte.contentResolver.openOutputStream(destination)?.use { flux ->
                flux.write(M3u.ecrire(garnie.playlist.nom, garnie.pistes).toByteArray())
            }
        }

    /** Lit un `.m3u` choisi par l'utilisateur et en fait une playlist. */
    suspend fun importerPlaylist(source: Uri, nom: String) = withContext(Dispatchers.IO) {
        val contenu = contexte.contentResolver.openInputStream(source)
            ?.use { it.readBytes().toString(Charsets.UTF_8) } ?: return@withContext
        val uris = M3u.resoudre(contenu, base.pistes().toutesMaintenant())
        if (uris.isNotEmpty()) creerPlaylist(nom, uris)
    }

    // ------------------------------------------------------------------ statistiques

    fun compterUneLecture(uri: String) {
        portee.launch(Dispatchers.IO) {
            base.statistiques().compterUneLecture(uri, System.currentTimeMillis())
        }
    }

    fun memoriserReprise(uri: String, position: Long) {
        portee.launch(Dispatchers.IO) {
            base.statistiques().enregistrerReprise(uri, position)
        }
    }

    /** Les pistes les plus jouées, pour la recherche et le classement de la vue molette. */
    fun recemmentJouees(limite: Int = 30): List<Piste> {
        val stats = statistiques.value
        return pistes.value
            .filter { (stats[it.uri]?.derniereLecture ?: 0) > 0 }
            .sortedByDescending { stats[it.uri]?.derniereLecture ?: 0 }
            .take(limite)
    }

    // ------------------------------------------------------------------ réglages

    fun modifierReglages(bloc: (EtatReglages) -> EtatReglages) {
        portee.launch { reglages.modifier(bloc) }
    }

    /** Mémoire de la file en cours, relue au démarrage suivant. */
    fun memoriserSession(uris: List<String>, index: Int, position: Long, aleatoire: Boolean, repetition: Int) {
        portee.launch {
            reglages.modifier {
                it.copy(
                    fileMemorisee = uris.joinToString(SEPARATEUR_FILE),
                    indexMemorise = index,
                    positionMemorisee = position,
                    aleatoireMemorise = aleatoire,
                    repetitionMemorisee = repetition
                )
            }
        }
    }

    companion object {
        const val SEPARATEUR_FILE = "\n"
    }
}

/** Recherche insensible à la casse et aux accents sur le titre, l'artiste, l'album et le fichier. */
fun List<Piste>.rechercher(requete: String): List<Piste> {
    val cle = normaliser(requete)
    if (cle.isBlank()) return emptyList()
    val mots = cle.split(' ').filter { it.isNotBlank() }
    return filter { piste ->
        val champ = normaliser(
            "${piste.titre} ${piste.artiste} ${piste.artisteAlbum} ${piste.album} " +
                "${piste.genre} ${piste.nomFichier}"
        )
        mots.all { champ.contains(it) }
    }
}

/**
 * Les accents, une fois pour toutes.
 *
 * ⚠️ Compilée **une seule fois** : construite à chaque appel, cette expression était recompilée
 * pour chaque nom comparé — des milliers de fois par cran de molette pendant une recherche.
 */
private val ACCENTS = Regex("\\p{InCombiningDiacriticalMarks}+")

fun normaliser(texte: String): String =
    java.text.Normalizer.normalize(texte.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(ACCENTS, "")

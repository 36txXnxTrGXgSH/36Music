package com.music36.app

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import com.music36.app.lecture.InfoSortieMaterielle
import com.music36.app.lecture.SortieAudio
import com.music36.app.lecture.TypeSortie
import org.junit.After
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import com.music36.app.data.EffetMolette
import com.music36.app.data.EtatReglages
import com.music36.app.data.Favori
import com.music36.app.data.Piste
import com.music36.app.data.Racine
import com.music36.app.data.StyleBarre
import com.music36.app.data.StyleInterface
import com.music36.app.data.StyleMolette
import com.music36.app.data.ThemeApplication
import com.music36.app.data.ThemeCouleur
import com.music36.app.lecture.EtatLecture
import com.music36.app.ui.ActionsUi
import com.music36.app.ui.LocalActions
import com.music36.app.ui.LocalEtatLecture
import com.music36.app.ui.LocalReglages
import com.music36.app.ui.ThemeMusique
import com.music36.app.ui.retro.EcranRetro
import com.music36.app.ui.retro.NiveauRetro
import kotlinx.coroutines.runBlocking
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Les captures d'écran de la page de présentation, dessinées depuis le code.
 *
 * ⚠️ **Hors de la suite ordinaire** : elles ne tournent que si `MUSIC36_CAPTURES` désigne le
 * dossier de sortie. Elles ne vérifient rien ; elles fabriquent des images, sur une bibliothèque
 * **entièrement inventée** — artistes, titres et pochettes. Une page publique ne doit montrer la
 * bibliothèque de personne, ni la musique de quelqu'un d'autre.
 *
 * ```
 * MUSIC36_CAPTURES=/chemin/sortie ./gradlew testDebugUnitTest --tests "*CapturesPresentationTest"
 * ```
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi")
@UnstableApi
class CapturesPresentationTest {

    @get:Rule
    val regle = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val sortie = File(System.getenv("MUSIC36_CAPTURES") ?: "build/captures-presentation")
    private val app get() = ApplicationProvider.getApplicationContext<Music36App>()
    private lateinit var pistes: List<Piste>

    @Before
    fun preparer() {
        Assume.assumeTrue("captures non demandées", System.getenv("MUSIC36_CAPTURES") != null)
        org.robolectric.Shadows.shadowOf(
            ApplicationProvider.getApplicationContext<android.app.Application>()
        ).declareActionUnbindable("androidx.media3.session.MediaSessionService")
        sortie.mkdirs()
        // ⚠️ Des images ordinaires, pas « matérielles » : le rendu de test ne sait pas dessiner
        // une image confiée à la carte graphique, et les pochettes restaient grises. Le chargement
        // se fait aussi sur place, pour qu'il soit fini quand la capture est prise.
        coil.Coil.setImageLoader(
            coil.ImageLoader.Builder(app)
                .allowHardware(false)
                .interceptorDispatcher(kotlinx.coroutines.Dispatchers.Unconfined)
                .fetcherDispatcher(kotlinx.coroutines.Dispatchers.Unconfined)
                .decoderDispatcher(kotlinx.coroutines.Dispatchers.Unconfined)
                .build()
        )
        pistes = bibliotheque()
        runBlocking {
            app.base.racines().ajouter(Racine(RACINE, "Musique", 1L))
            app.base.pistes().inserer(pistes)
            app.depot.creerPlaylist("Soirée d'été", pistes.filter { it.genre != "Classique" }.map { it.uri })
            app.depot.creerPlaylist("Concentration", pistes.filter { it.genre in setOf("Classique", "Ambient") }.map { it.uri })
            app.depot.creerPlaylist("Route du soir", pistes.shuffled(kotlin.random.Random(7)).take(9).map { it.uri })
            pistes.take(4).forEach { app.base.favoris().ajouter(Favori(it.uri, 1L)) }
            // Les quatre rangs de qualité côte à côte, pour la capture des couleurs.
            app.depot.creerPlaylist("Un peu de tout", listOf(
                "Moteur", "Signal", "Valse des phares", "Papier glacé", "Lumière d'août",
                "Atlas", "Néons d'hiver", "Ligne blanche", "Hors cadre", "Haute mer"
            ).map { titre -> pistes.first { it.titre == titre }.uri })
        }
        attendre { app.depot.pistes.value.size == pistes.size && app.depot.playlists.value.size == 4 }
    }

    // ------------------------------------------------------------------ la bibliothèque inventée

    private data class Album(
        val artiste: String,
        val titre: String,
        val genre: String,
        val annee: Int,
        val qualite: Triple<String, Int, Int>,
        val couleurs: Pair<Int, Int>,
        val morceaux: List<String>
    )

    private val albums = listOf(
        Album("Les Lucioles", "Nuits de juillet", "Pop", 2023, Triple("FLAC", 96_000, 24),
            0xFFFF7A59.toInt() to 0xFF6A2C91.toInt(),
            listOf("Lumière d'août", "Les toits de la ville", "Danser sous la pluie", "Minuit pile")),
        Album("Nora Vale", "Atlas", "Électro", 2024, Triple("FLAC", 44_100, 16),
            0xFF1FD1C1.toInt() to 0xFF173A8F.toInt(),
            listOf("Signal", "Atlas", "Haute mer", "Néon froid", "Dernier train")),
        Album("Orchestre de la Baie", "Marées", "Classique", 2019, Triple("FLAC", 192_000, 24),
            0xFFE8C872.toInt() to 0xFF5A3A12.toInt(),
            listOf("Prélude en ré", "Marée montante", "Valse des phares", "Nocturne pour cordes")),
        Album("Kito", "Hors cadre", "Hip-hop", 2022, Triple("ALAC", 48_000, 24),
            0xFFFF4D6D.toInt() to 0xFF2B2D42.toInt(),
            listOf("Hors cadre", "Béton tendre", "Rue des Lilas", "Encore un tour")),
        Album("Mira & Sol", "Ambres", "Ambient", 2021, Triple("FLAC", 48_000, 24),
            0xFF9BE15D.toInt() to 0xFF00A39C.toInt(),
            listOf("Ambre", "Brume légère", "Forêt intérieure")),
        Album("Velours", "Rétroviseur", "Rock", 2020, Triple("MP3", 44_100, 16),
            0xFFFFB347.toInt() to 0xFFC0392B.toInt(),
            listOf("Rétroviseur", "Moteur", "Ligne blanche", "Fin de partie")),
        Album("Céleste Moreau", "Éclats", "Jazz", 2018, Triple("FLAC", 88_200, 24),
            0xFF5DA9E9.toInt() to 0xFF1B1B3A.toInt(),
            listOf("Éclats", "Bleu nuit", "Ça tourne", "Été indien")),
        // Le rang « compressé » : de l'AAC, sans profondeur de bits (un format avec perte n'en a pas).
        Album("Iris Calder", "Papier glacé", "Pop", 2017, Triple("M4A", 44_100, 0),
            0xFFB8C6DB.toInt() to 0xFF3E4A61.toInt(),
            listOf("Papier glacé", "Néons d'hiver", "Lettre à Lou"))
    )

    private fun bibliotheque(): List<Piste> {
        val dossierPochettes = File(sortie, "pochettes-fictives").apply { mkdirs() }
        return albums.flatMapIndexed { a, album ->
            val pochette = File(dossierPochettes, "album$a.png").also { dessinerPochette(it, album.couleurs) }
            album.morceaux.mapIndexed { n, titre ->
                val (format, frequence, bits) = album.qualite
                Piste(
                    uri = "$RACINE/document/${album.artiste}-${n}",
                    racine = RACINE,
                    dossier = "Musique/${album.artiste}/${album.titre}",
                    nomFichier = "${n + 1} $titre.${format.lowercase()}",
                    titre = titre,
                    artiste = album.artiste,
                    album = album.titre,
                    artisteAlbum = album.artiste,
                    genre = album.genre,
                    annee = album.annee,
                    numeroPiste = n + 1,
                    disque = 1,
                    duree = 150_000L + ((a * 7 + n * 13) % 9) * 21_000L,
                    taille = 30_000_000,
                    dateFichier = 1L,
                    dateAjout = 1_700_000_000_000L + (a * 10 + n) * 86_400_000L,
                    format = format,
                    debit = when (format) {
                        "MP3" -> 320
                        "M4A" -> 256
                        else -> 1_400 + a * 120
                    },
                    echantillonnage = frequence,
                    bits = bits,
                    canaux = 2,
                    // `toURI()` et non « file:// » + chemin : sous Windows, « file://C:/… » fait
                    // de « C: » un nom d'hôte, et l'image n'est jamais trouvée.
                    pochette = pochette.toURI().toString()
                )
            }
        }
    }

    /** Une pochette abstraite : un dégradé et un disque de lumière, rien qui imite une vraie. */
    private fun dessinerPochette(fichier: File, couleurs: Pair<Int, Int>) {
        val cote = 600
        val image = Bitmap.createBitmap(cote, cote, Bitmap.Config.ARGB_8888)
        val toile = Canvas(image)
        toile.drawRect(0f, 0f, cote.toFloat(), cote.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, cote.toFloat(), cote.toFloat(),
                couleurs.first, couleurs.second, Shader.TileMode.CLAMP)
        })
        toile.drawCircle(cote * 0.68f, cote * 0.34f, cote * 0.30f, Paint().apply {
            isAntiAlias = true
            shader = RadialGradient(cote * 0.68f, cote * 0.34f, cote * 0.30f,
                0x88FFFFFF.toInt(), 0x00FFFFFF, Shader.TileMode.CLAMP)
        })
        fichier.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    // ------------------------------------------------------------------ l'outillage

    private fun attendre(condition: () -> Boolean) {
        repeat(100) {
            if (condition()) return
            Thread.sleep(50)
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
        }
    }

    private fun afficher(
        reglages: EtatReglages = EtatReglages(style = StyleInterface.RETRO),
        theme: ThemeCouleur = ThemeCouleur.OCEAN,
        teinte: Float = 195f,
        lecture: EtatLecture = enLecture(),
        contenu: @Composable () -> Unit
    ) {
        regle.setContent {
            ThemeMusique(ThemeApplication.SOMBRE, dynamique = false, themeCouleur = theme, teinte = teinte) {
                CompositionLocalProvider(
                    LocalActions provides ActionsUi(app, {}, {}, {}, {}),
                    LocalReglages provides reglages,
                    LocalEtatLecture provides lecture
                ) { contenu() }
            }
        }
        regle.waitForIdle()
    }

    @After
    fun oublierLaSortie() {
        SortieAudio.decrireMateriel(null)
        SortieAudio.decrireDecodeur(null)
        SortieAudio.effacer()
    }

    private fun enLecture(titre: String = "Signal"): EtatLecture {
        val piste = pistes.first { it.titre == titre }
        return EtatLecture(
            piste = piste,
            file = pistes,
            index = pistes.indexOf(piste),
            enLecture = true,
            position = 92_000,
            duree = piste.duree,
            repetition = Player.REPEAT_MODE_OFF,
            connecte = true
        )
    }

    /**
     * Dessine la fenêtre dans une image.
     *
     * ⚠️ Pas `captureToImage()` : il attend qu'un vrai écran redessine la fenêtre, ce qui n'arrive
     * jamais hors d'un téléphone. On demande à la vue de se dessiner elle-même dans une image —
     * après avoir laissé aux pochettes le temps de se charger.
     */
    private fun enregistrer(nom: String) {
        repeat(20) {
            Thread.sleep(60)
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            regle.waitForIdle()
        }
        val vue = regle.activity.window.decorView
        val image = Bitmap.createBitmap(vue.width, vue.height, Bitmap.Config.ARGB_8888)
        regle.runOnUiThread { vue.draw(Canvas(image)) }
        File(sortie, "$nom.png").outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    // ------------------------------------------------------------------ les écrans

    @Test
    fun `01 accueil`() {
        afficher { EcranRetro(NiveauRetro.Racine) }
        enregistrer("01-accueil")
    }

    @Test
    fun `02 lecture en cours`() {
        afficher(EtatReglages(style = StyleInterface.RETRO, fondLecteurRetro = 1)) {
            EcranRetro(NiveauRetro.Lecture)
        }
        enregistrer("02-lecture")
    }

    @Test
    fun `03 toutes les chansons`() {
        afficher { EcranRetro(NiveauRetro.Sons) }
        enregistrer("03-chansons")
    }

    @Test
    fun `04 recherche a jokers`() {
        afficher { EcranRetro(NiveauRetro.Sons) }
        regle.onNodeWithTag("bouton_recherche").performClick()
        regle.onNodeWithTag("champ_recherche").performTextInput("*nuit*")
        enregistrer("04-recherche")
    }

    @Test
    fun `05 playlist triee`() {
        val id = app.depot.playlists.value.first { it.playlist.nom == "Soirée d'été" }.playlist.id
        afficher { EcranRetro(NiveauRetro.Playlist(id)) }
        enregistrer("05-playlist")
    }

    @Test
    fun `06 cover flow`() {
        afficher { EcranRetro(NiveauRetro.CoverFlow) }
        enregistrer("06-coverflow")
    }

    @Test
    fun `07 barre de couleurs`() {
        afficher(theme = ThemeCouleur.RETRO_80, teinte = 315f) {
            EcranRetro(NiveauRetro.TeintePaletteRetro(ThemeCouleur.RETRO_80))
        }
        enregistrer("07-couleurs")
    }

    @Test
    fun `08 molette personnalisee`() {
        afficher(
            EtatReglages(
                style = StyleInterface.RETRO,
                styleMolette = StyleMolette.METALLIQUE.ordinal,
                effetMolette = EffetMolette.ARC_EN_CIEL.ordinal,
                styleBarre = StyleBarre.ONDULE.ordinal,
                fondLecteurRetro = 1
            ),
            theme = ThemeCouleur.LUXE,
            teinte = 45f
        ) { EcranRetro(NiveauRetro.Lecture) }
        enregistrer("08-personnalisation")
    }

    @Test
    fun `09 parametres`() {
        afficher { EcranRetro(NiveauRetro.ParametresApp) }
        enregistrer("09-parametres")
    }

    @Test
    fun `10 egaliseur et son`() {
        afficher { EcranRetro(NiveauRetro.ParamSon) }
        enregistrer("10-son")
    }

    @Test
    fun `11 couleurs de qualite`() {
        val id = app.depot.playlists.value.first { it.playlist.nom == "Un peu de tout" }.playlist.id
        afficher { EcranRetro(NiveauRetro.Playlist(id)) }
        enregistrer("11-couleurs-qualite")
    }

    @Test
    fun `12 fiche du fichier`() {
        SortieAudio.decrireDecodeur("c2.android.flac.decoder")
        afficher(lecture = enLecture("Lumière d'août")) { EcranRetro(NiveauRetro.InfosPiste) }
        enregistrer("12-fiche-qualite")
    }

    @Test
    fun `13 sortie vers un DAC USB`() {
        // Ce que publient le service et la surveillance du matériel pendant une vraie lecture :
        // un FLAC 24 bits / 192 kHz décodé tel quel, vers un DAC USB-C qui monte à 384 kHz.
        SortieAudio.decrire(
            Format.Builder()
                .setSampleMimeType(MimeTypes.AUDIO_FLAC)
                .setSampleRate(192_000)
                .setChannelCount(2)
                .setPcmEncoding(C.ENCODING_PCM_24BIT)
                .build(),
            sortieFlottante = true
        )
        SortieAudio.decrireMateriel(
            InfoSortieMaterielle(
                type = TypeSortie.USB,
                nom = "DAC USB-C",
                frequences = listOf(44_100, 48_000, 96_000, 192_000, 384_000),
                profondeurs = listOf(16, 24, 32),
                canaux = 2
            )
        )
        afficher(
            EtatReglages(style = StyleInterface.RETRO, fondLecteurRetro = 1),
            lecture = enLecture("Valse des phares")
        ) { EcranRetro(NiveauRetro.Lecture) }
        enregistrer("13-sortie-dac")
    }

    private companion object {
        const val RACINE = "content://com.android.externalstorage.documents/tree/0000-0000%3AMusique"
    }
}

package com.music36.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import com.music36.app.data.EtatReglages
import com.music36.app.data.M3u
import com.music36.app.data.Piste
import com.music36.app.data.ResultatAjout
import com.music36.app.data.ResultatDoublons
import com.music36.app.data.ResultatReecriture
import com.music36.app.data.ThemeApplication
import com.music36.app.data.ResultatRacine
import com.music36.app.data.cheminRelatif
import com.music36.app.data.cleDeFichier
import com.music36.app.data.deciderLeMenage
import com.music36.app.data.avecDossierExporte
import com.music36.app.data.contient
import com.music36.app.data.dossiersExportesDe
import com.music36.app.data.nomEnM3u8
import com.music36.app.data.messageRacine
import com.music36.app.data.messageAjout
import com.music36.app.data.messageDoublons
import com.music36.app.data.messageReecriture
import com.music36.app.data.messageReecritureEnLot
import com.music36.app.lecture.EtatLecture
import com.music36.app.ui.ActionsUi
import com.music36.app.ui.LocalActions
import com.music36.app.ui.LocalEtatLecture
import com.music36.app.ui.LocalReglages
import com.music36.app.ui.ThemeMusique
import com.music36.app.ui.retro.EcranRetro
import com.music36.app.ui.retro.NiveauRetro
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * La réécriture du `.m3u` d'origine : les chemins qu'elle écrit, et ce qu'elle annonce.
 *
 * ⚠️ Deux défauts signalés par l'utilisateur tiennent ici. Le fichier n'était pas mis à jour —
 * faute du droit d'écrire — et **personne ne le disait** : l'échec était avalé par un
 * `runCatching`. Un résultat qui ne remonte nulle part est un résultat qu'on ne corrige jamais.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@UnstableApi
class ReecriturePlaylistTest {

    @get:Rule
    val regle = createComposeRule()

    @Before
    fun couperLeService() {
        org.robolectric.Shadows.shadowOf(
            ApplicationProvider.getApplicationContext<android.app.Application>()
        ).declareActionUnbindable("androidx.media3.session.MediaSessionService")
    }

    // ------------------------------------------------------------------ les chemins écrits

    @Test
    fun `un chemin s'ecrit depuis le dossier de la playlist`() {
        // ⚠️ Le cas qui comptait : la playlist est dans un sous-dossier, la musique ailleurs.
        // Écrire « Music/FR/x.flac » depuis « Music/Playlists » fait chercher
        // « Music/Playlists/Music/FR/x.flac » — c'est-à-dire rien.
        assertEquals(
            "../FR/Casseurs.flac",
            cheminRelatif("EAB4-6BE0:Music/Playlists", "EAB4-6BE0:Music/FR/Casseurs.flac")
        )
        // Même dossier : le nom suffit.
        assertEquals(
            "Casseurs.flac",
            cheminRelatif("EAB4-6BE0:Music/Playlists", "EAB4-6BE0:Music/Playlists/Casseurs.flac")
        )
        // La playlist est au-dessus de la musique.
        assertEquals(
            "FR/Rap/Casseurs.flac",
            cheminRelatif("EAB4-6BE0:Music", "EAB4-6BE0:Music/FR/Rap/Casseurs.flac")
        )
        // Deux dossiers de remontée : la playlist est deux crans plus bas que la musique.
        assertEquals(
            "../../Autres/x.mp3",
            cheminRelatif("EAB4-6BE0:Music/Listes/2026", "EAB4-6BE0:Music/Autres/x.mp3")
        )
    }

    @Test
    fun `un autre support s'ecrit depuis la racine du volume`() {
        // Aucun chemin relatif n'a de sens d'une carte SD vers la mémoire interne.
        assertEquals(
            "/Music/x.flac",
            cheminRelatif("EAB4-6BE0:Music/Playlists", "primary:Music/x.flac")
        )
    }

    @Test
    fun `le fichier ecrit se relit par l'application elle-meme`() {
        // ⚠️ Le vrai garde-fou : ce qu'on écrit doit se relire. Sans cela, la prochaine analyse
        // importerait une playlist vide par-dessus celle de l'utilisateur.
        val piste = piste("Music/FR", "Casseurs.flac")
        val contenu = M3u.ecrire("Ma liste", listOf(piste)) {
            cheminRelatif("EAB4-6BE0:Music/Playlists", "EAB4-6BE0:Music/FR/Casseurs.flac")
        }
        assertTrue(contenu, contenu.startsWith("#EXTM3U"))
        assertTrue(contenu, contenu.contains("#EXTINF:"))
        assertTrue(contenu, contenu.contains("../FR/Casseurs.flac"))
        assertEquals(listOf(piste.uri), M3u.resoudre(contenu, listOf(piste)))
    }

    @Test
    fun `un fichier mal forme ressort normalise`() {
        // Le fichier d'origine peut n'être qu'une liste de chemins, avec des antislashs et sans
        // en-tête : ce qu'on réécrit est toujours du #EXTM3U complet.
        val piste = piste("Music/FR", "Casseurs.flac")
        val avant = "Music\\FR\\Casseurs.flac\r\nune ligne qui ne désigne rien\r\n"
        val relues = M3u.resoudre(avant, listOf(piste))
        assertEquals(listOf(piste.uri), relues)

        val apres = M3u.ecrire("Ma liste", listOf(piste))
        assertTrue(apres, apres.startsWith("#EXTM3U"))
        assertFalse("plus d'antislash dans le fichier réécrit", apres.contains("\\"))
    }

    // ------------------------------------------------------------------ ce qui est annoncé

    @Test
    fun `un ajout dit quand le fichier n'a pas suivi`() {
        val message = messageAjout(
            ResultatAjout(ajoutees = 1, deja = 0, reecriture = ResultatReecriture.LECTURE_SEULE),
            "Rap FR"
        )
        assertTrue(message, message.contains("Ajouté à « Rap FR »"))
        assertTrue(message, message.contains("lecture seule"))

        // Quand tout va bien, le message reste celui d'avant : pas de bruit inutile.
        assertEquals(
            "Ajouté à « Rap FR »",
            messageAjout(ResultatAjout(1, 0, ResultatReecriture.ECRIT), "Rap FR")
        )
        // Et quand rien n'a été ajouté, il n'y avait rien à réécrire : aucun avertissement.
        assertEquals(
            "Déjà dans « Rap FR »",
            messageAjout(ResultatAjout(0, 1, ResultatReecriture.LECTURE_SEULE), "Rap FR")
        )
    }

    @Test
    fun `chaque issue de reecriture a sa phrase`() {
        assertEquals("Fichier .m3u réécrit", messageReecriture(ResultatReecriture.ECRIT))
        assertTrue(
            messageReecriture(ResultatReecriture.LECTURE_SEULE).contains("Autoriser l'écriture")
        )
        assertTrue(
            messageReecriture(ResultatReecriture.DESACTIVE).contains("Répercuter")
        )
        ResultatReecriture.entries.forEach {
            assertTrue("$it sans phrase", messageReecriture(it).isNotBlank())
        }
    }

    @Test
    fun `le lot annonce ce qui est passe et ce qui a ete refuse`() {
        assertEquals(
            "Aucune playlist ne vient d'un fichier",
            messageReecritureEnLot(ecrits = 0, refuses = 0, sansFichier = 3)
        )
        assertTrue(
            messageReecritureEnLot(ecrits = 12, refuses = 0, sansFichier = 0)
                .contains("12 fichiers .m3u réécrits")
        )
        assertTrue(
            messageReecritureEnLot(ecrits = 0, refuses = 4, sansFichier = 0)
                .contains("lecture seule")
        )
        assertTrue(
            messageReecritureEnLot(ecrits = 2, refuses = 1, sansFichier = 0)
                .contains("2 réécrits, 1 refusé")
        )
    }

    // ------------------------------------------------------------------ dossiers imbriqués

    @Test
    fun `un sous-dossier d'un dossier surveille est reconnu comme tel`() {
        // ⚠️ Le 28/09/2026, choisir « Music/Playlists » alors que « Music » était déjà surveillé
        // a fait passer la bibliothèque de 14 à 27 playlists : tout y comptait deux fois.
        assertTrue(contient("EAB4-6BE0:Music", "EAB4-6BE0:Music/Playlists"))
        assertTrue("un dossier se contient lui-même", contient("EAB4-6BE0:Music", "EAB4-6BE0:Music"))
        assertFalse(contient("EAB4-6BE0:Music/Playlists", "EAB4-6BE0:Music"))
        // Comparaison par segments, jamais sur le texte : sinon « Music2 » passerait pour un
        // enfant de « Music ».
        assertFalse(contient("EAB4-6BE0:Music", "EAB4-6BE0:Music2"))
        assertFalse(contient("EAB4-6BE0:Music", "EAB4-6BE0:Musique"))
        // Deux supports différents ne se contiennent jamais.
        assertFalse(contient("primary:Music", "EAB4-6BE0:Music/Playlists"))
    }

    @Test
    fun `chaque issue du choix d'un dossier a sa phrase`() {
        assertTrue(messageRacine(ResultatRacine.AJOUTE).isNotBlank())
        assertTrue(
            messageRacine(ResultatRacine.ECRITURE_AUTORISEE).contains("Écriture autorisée")
        )
        val couvert = messageRacine(ResultatRacine.DEJA_COUVERT("Music"))
        assertTrue(couvert, couvert.contains("Music"))
        assertTrue(couvert, couvert.contains("n'a pas été ajouté"))
    }

    // ------------------------------------------------------------------ exports et .m3u8

    @Test
    fun `une playlist mise a jour prend le nom m3u8`() {
        // ⚠️ 36Music écrit toujours en UTF-8 ; un fichier « .m3u » est censé ne pas l'être, et
        // les accents y ressortent en charabia chez les autres lecteurs.
        assertEquals("Rap FR.m3u8", nomEnM3u8("Rap FR.m3u"))
        assertEquals("Rap FR.M3U8", nomEnM3u8("Rap FR.M3U")?.replace(".m3u8", ".M3U8"))
        // Déjà au bon nom, ou d'une autre nature : on n'y touche pas.
        assertNull(nomEnM3u8("Rap FR.m3u8"))
        assertNull(nomEnM3u8("Rap FR.pls"))
        assertNull(nomEnM3u8("Rap FR"))
    }

    @Test
    fun `un dossier d'export se retient et se relit`() {
        val avant = EtatReglages()
        assertTrue(dossiersExportesDe(avant).isEmpty())

        val apres = avant.avecDossierExporte("content://tree/EAB4-6BE0%3AMusic%2FExport")
        assertEquals(1, dossiersExportesDe(apres).size)
        // ⚠️ Sans dédoublonnage, exporter dix fois au même endroit allongerait la liste
        // indéfiniment et ralentirait chaque analyse.
        val encore = apres.avecDossierExporte("content://tree/EAB4-6BE0%3AMusic%2FExport")
        assertEquals(1, dossiersExportesDe(encore).size)

        val deux = encore.avecDossierExporte("content://tree/primary%3ADownload")
        assertEquals(2, dossiersExportesDe(deux).size)
    }

    @Test
    fun `un fichier exporte est reconnu comme etant dans le dossier d'export`() {
        // C'est ce test qui dit pourquoi les doublons disparaissent : le fichier écrit par
        // l'export tombe dans le dossier retenu, donc l'analyse ne le réimporte pas.
        assertTrue(
            contient("EAB4-6BE0:Music/Export", "EAB4-6BE0:Music/Export/BEST ALL Genre.m3u8")
        )
        assertFalse(
            contient("EAB4-6BE0:Music/Export", "EAB4-6BE0:Music/Playlists/BEST ALL Genre.m3u")
        )
    }

    // ------------------------------------------------------------------ les doublons

    @Test
    fun `deux playlists sur un meme fichier n'en font plus qu'une`() {
        // ⚠️ Le cas réel du 28/09/2026, relevé dans la base du téléphone : vingt-huit lignes
        // pour quatorze fichiers, deux par fichier, avec des contenus différents.
        val playlists = (1..14).flatMap { n ->
            val fichier = "content://tree/EAB4-6BE0%3AMusic/document/liste$n.m3u8"
            listOf(
                playlist(id = n.toLong(), nom = "Liste $n", source = fichier),
                playlist(id = (n + 100).toLong(), nom = "Liste $n", source = fichier)
            )
        }
        // L'ancienne ligne porte moins de titres que celle de l'application.
        val pistes = playlists.associate { it.id to if (it.id > 100) 236 else 234 }

        val menage = deciderLeMenage(playlists, { pistes[it] ?: 0 }, { true })

        assertEquals("une playlist gardée par fichier", 14, menage.aReecrire.size)
        assertEquals("une ligne retirée par fichier", 14, menage.doublons.size)
        assertTrue("rien ne disparaît : les fichiers sont là", menage.disparues.isEmpty())
        // La plus fournie est gardée, et c'est son fichier qu'on réécrit.
        assertTrue(menage.aReecrire.all { it > 100 })
        assertTrue(menage.doublons.all { it <= 100 })
    }

    @Test
    fun `le meme fichier vu par deux dossiers surveilles ne fait qu'une playlist`() {
        // ⚠️ Le cas réel du 28/09/2026, relevé dans la base du téléphone : la même playlist
        // atteinte par « tree/…:Music » et par « tree/…:Music%2FPlaylists ». Les URI diffèrent,
        // le document est le même — et c'est le document qui compte.
        val parLaRacine = "content://com.android.externalstorage.documents/tree/" +
            "EAB4-6BE0%3AMusic/document/EAB4-6BE0%3AMusic%2FPlaylists%2FListe.m3u8"
        val parLeSousDossier = "content://com.android.externalstorage.documents/tree/" +
            "EAB4-6BE0%3AMusic%2FPlaylists/document/EAB4-6BE0%3AMusic%2FPlaylists%2FListe.m3u8"
        assertEquals(cleDeFichier(parLaRacine), cleDeFichier(parLeSousDossier))

        val menage = deciderLeMenage(
            listOf(playlist(3, "Liste", parLaRacine), playlist(17, "Liste", parLeSousDossier)),
            { id -> if (id == 3L) 236 else 234 },
            { true },
            ::cleDeFichier
        )
        assertEquals("la plus fournie est gardée", listOf(3L), menage.aReecrire)
        assertEquals(listOf(17L), menage.doublons)
    }

    @Test
    fun `une playlist sans fichier sur l'appareil ne figure plus dans la liste`() {
        val presente = playlist(1, "Ici", "content://tree/x/document/ici.m3u8")
        val partie = playlist(2, "Partie", "content://tree/x/document/partie.m3u8")
        // ⚠️ Une playlist créée dans l'application n'a pas de fichier : elle ne doit jamais
        // être emportée par ce ménage.
        val maison = playlist(3, "À moi", null)

        val menage = deciderLeMenage(
            listOf(presente, partie, maison),
            { 10 },
            { source -> source.endsWith("ici.m3u8") }
        )

        assertEquals(listOf(2L), menage.disparues)
        assertTrue(menage.doublons.isEmpty())
    }

    @Test
    fun `a contenu egal, la ligne la plus recente l'emporte`() {
        val fichier = "content://tree/x/document/liste.m3u8"
        val menage = deciderLeMenage(
            listOf(playlist(1, "Liste", fichier), playlist(9, "Liste", fichier)),
            { 100 },
            { true }
        )
        assertEquals(listOf(9L), menage.aReecrire)
        assertEquals(listOf(1L), menage.doublons)
    }


    @Test
    fun `le menage dit ce qu'il a fait`() {
        assertEquals("Aucun doublon", messageDoublons(ResultatDoublons(0, 0, 0)))
        val message = messageDoublons(ResultatDoublons(reecrites = 14, retirees = 14, disparues = 2))
        assertTrue(message, message.contains("14 fichiers mis à jour"))
        assertTrue(message, message.contains("14 doublons retirés"))
        assertTrue(message, message.contains("2 playlists sans fichier"))
        // ⚠️ L'ordre des phrases n'est pas cosmétique : la mise à jour des fichiers vient
        // **avant** le retrait, comme le fait le ménage lui-même.
        assertTrue(message.indexOf("mis à jour") < message.indexOf("retiré"))
    }

    // ------------------------------------------------------------------ l'écran

    @Test
    fun `les playlists proposent de reecrire tous les fichiers`() {
        val app = ApplicationProvider.getApplicationContext<Music36App>()
        regle.setContent {
            ThemeMusique(ThemeApplication.SOMBRE, dynamique = false) {
                CompositionLocalProvider(
                    LocalActions provides ActionsUi(app, {}, {}, {}, {}),
                    LocalReglages provides EtatReglages(),
                    LocalEtatLecture provides EtatLecture()
                ) {
                    EcranRetro(NiveauRetro.ParamPlaylists)
                }
            }
        }
        regle.onNodeWithTag("liste_retro")
            .performScrollToNode(hasText("Réécrire tous les fichiers .m3u"))
        regle.onNodeWithText("Réécrire tous les fichiers .m3u").assertIsDisplayed()
    }

    private fun playlist(id: Long, nom: String, source: String?) = com.music36.app.data.Playlist(
        id = id,
        nom = nom,
        dateCreation = 1L,
        source = source
    )

    private fun piste(dossier: String, nomFichier: String) = Piste(
        uri = "content://tree/EAB4-6BE0%3AMusic/document/EAB4-6BE0%3A$dossier%2F$nomFichier",
        racine = "content://tree/EAB4-6BE0%3AMusic",
        dossier = dossier,
        nomFichier = nomFichier,
        titre = nomFichier.substringBeforeLast('.'),
        artiste = "Casseurs Flowters",
        album = "Orelsan et Gringe",
        artisteAlbum = "Casseurs Flowters",
        genre = "Rap",
        annee = 2013,
        numeroPiste = 6,
        disque = 1,
        duree = 404_000,
        taille = 10,
        dateFichier = 1,
        dateAjout = 1,
        format = "FLAC",
        debit = 900,
        echantillonnage = 44_100,
        bits = 16,
        canaux = 2
    )
}

package com.music36.app

import android.net.Uri
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.music36.app.lecture.alac.FabriqueRendus36
import com.music36.app.lecture.ordreDeLecture
import com.music36.app.lecture.resoudre
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Le trajet réel d'une commande : interface → `MediaController` → `MediaSession` → ExoPlayer.
 *
 * L'utilisateur voyait la bonne file à l'écran et entendait l'ancienne : le contrôleur applique la
 * nouvelle liste chez lui *avant* que la session ne l'accepte, donc l'écran ne prouve rien. Ce
 * test regarde le seul état qui compte, celui du lecteur du service.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@UnstableApi
class SessionAllerRetourTest {

    private lateinit var lecteur: ExoPlayer
    private lateinit var session: MediaSession
    private var controleur: MediaController? = null

    private fun contexte() = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun element(uri: String) = MediaItem.Builder()
        .setMediaId(uri)
        .setUri(Uri.parse(uri))
        .build()

    private fun tourner() = shadowOf(Looper.getMainLooper()).idle()

    @Before
    fun ouvrir() {
        // ⚠️ Le câblage du service, pas un lecteur nu : c'est la fabrique qui glisse le moteur
        // ALAC dans la liste, et c'est un `ForwardingPlayer` que la session reçoit.
        lecteur = ExoPlayer.Builder(contexte(), FabriqueRendus36(contexte())).build()
        session = MediaSession.Builder(contexte(), EnveloppeFondu(lecteur)).setId("test").build()
        val futur = MediaController.Builder(contexte(), session.token).buildAsync()
        repeat(20) { tourner() }
        controleur = futur.get()
        repeat(20) { tourner() }
    }

    @After
    fun fermer() {
        controleur?.release()
        session.release()
        lecteur.release()
        tourner()
    }

    /** Copie de `LecteurAvecFondu` : mêmes redirections, même surface. */
    private class EnveloppeFondu(private val interne: ExoPlayer) : ForwardingPlayer(interne) {
        override fun pause() = interne.pause()
        override fun play() = interne.play()
        override fun setPlayWhenReady(playWhenReady: Boolean) {
            if (playWhenReady) play() else pause()
        }
    }

    private fun urisDuLecteur() =
        (0 until lecteur.mediaItemCount).map { lecteur.getMediaItemAt(it).mediaId }

    @Test
    fun `la session recoit la file envoyee par le controleur`() {
        val c = controleur!!
        c.setMediaItems(listOf(element("content://a/1"), element("content://a/2")), 0, 0)
        repeat(20) { tourner() }

        assertEquals(listOf("content://a/1", "content://a/2"), urisDuLecteur())
    }

    @Test
    fun `remplacer la file remplace bien celle du lecteur`() {
        val c = controleur!!
        c.setMediaItems(listOf(element("content://genre/1"), element("content://genre/2")), 0, 0)
        repeat(20) { tourner() }

        c.setMediaItems(listOf(element("content://tous/1"), element("content://tous/2")), 1, 0)
        repeat(20) { tourner() }

        assertEquals(listOf("content://tous/1", "content://tous/2"), urisDuLecteur())
        assertEquals(1, lecteur.currentMediaItemIndex)
    }

    /** Exactement la séquence de `Lecteur.jouer` : mélange, file, préparation, lecture. */
    private fun jouer(uris: List<String>, index: Int, aleatoire: Boolean) {
        val c = controleur!!
        c.shuffleModeEnabled = aleatoire
        c.stop()
        c.clearMediaItems()
        c.setMediaItems(uris.map(::element), index, 0)
        c.prepare()
        c.play()
        repeat(30) { tourner() }
    }

    @Test
    fun `changer de categorie en pleine lecture emmene le lecteur sur le titre choisi`() {
        val genre = (1..4).map { "content://rnb/$it" }
        val toutes = (1..40).map { "content://tous/$it" }

        jouer(genre, 0, false)
        assertEquals("content://rnb/1", lecteur.currentMediaItem?.mediaId)

        // L'utilisateur quitte le genre et choisit le 18ᵉ titre de « Toutes les chansons ».
        jouer(toutes, 17, false)

        assertEquals(40, lecteur.mediaItemCount)
        assertEquals("content://tous/18", lecteur.currentMediaItem?.mediaId)
        assertEquals(17, lecteur.currentMediaItemIndex)
    }

    @Test
    fun `l'URI survit a l'aller-retour de serialisation`() {
        val c = controleur!!
        c.setMediaItems(listOf(element("content://a/1")), 0, 0)
        repeat(20) { tourner() }

        assertEquals(
            "content://a/1",
            lecteur.getMediaItemAt(0).localConfiguration?.uri?.toString()
        )
    }

    @Test
    fun `un controleur exterieur qui n'envoie qu'un identifiant obtient un element jouable`() {
        val recus = listOf(
            MediaItem.Builder().setMediaId("content://doc/12").build(),
            element("content://doc/13"),
            MediaItem.Builder().build()
        )

        val resolus = resoudre(recus)

        assertEquals(2, resolus.size)
        assertEquals("content://doc/12", resolus[0].localConfiguration?.uri?.toString())
        assertEquals("content://doc/13", resolus[1].localConfiguration?.uri?.toString())
    }

    @Test
    fun `une file de la taille d'une bibliotheque arrive jusqu'au lecteur`() {
        val c = controleur!!
        val grande = (1..1200).map { "content://biblio/$it" }

        c.setMediaItems(grande.map(::element), 600, 0)
        repeat(40) { tourner() }

        assertEquals(1200, lecteur.mediaItemCount)
        assertEquals("content://biblio/601", lecteur.currentMediaItem?.mediaId)
    }

    @Test
    fun `en aleatoire la file annoncee suit le tirage, pas l'ordre alphabetique`() {
        val c = controleur!!
        val titres = (1..40).map { "content://tous/$it" }
        c.setMediaItems(titres.map(::element), 0, 0)
        c.shuffleModeEnabled = true
        repeat(30) { tourner() }

        val ordre = ordreDeLecture(lecteur.currentTimeline, melange = true)

        // Toute la file est parcourue, une fois chaque titre — c'est la promesse de « aléatoire
        // sans répétition » — mais dans un ordre qui n'est pas celui de la liste.
        assertEquals(40, ordre.size)
        assertEquals((0 until 40).toSet(), ordre.toSet())
        assertNotEquals((0 until 40).toList(), ordre)
    }

    @Test
    fun `sans aleatoire la file annoncee est celle de la liste`() {
        val c = controleur!!
        c.setMediaItems((1..12).map { element("content://tous/$it") }, 0, 0)
        c.shuffleModeEnabled = false
        repeat(30) { tourner() }

        assertEquals(
            (0 until 12).toList(),
            ordreDeLecture(lecteur.currentTimeline, melange = false)
        )
    }

    @Test
    fun `une file vide ne fait pas tourner le parcours a vide`() {
        assertEquals(emptyList<Int>(), ordreDeLecture(lecteur.currentTimeline, melange = true))
    }

    @Test
    fun `l'ordre annonce est bien celui que le lecteur suit ensuite`() {
        // ⚠️ Le vrai contrôle : on ne compare pas deux calculs, on fait **avancer** le lecteur et
        // on regarde où il va. C'est la seule façon de savoir si la file annoncée dit vrai.
        val c = controleur!!
        c.setMediaItems((1..20).map { element("content://t/$it") }, 0, 0)
        c.shuffleModeEnabled = true
        repeat(30) { tourner() }
        // On se place au début de l'ordre tiré : le reste de la marche est alors garanti.
        val ordreInitial = ordreDeLecture(lecteur.currentTimeline, melange = true)
        c.seekTo(ordreInitial.first(), 0)
        repeat(20) { tourner() }

        val annonce = ordreDeLecture(lecteur.currentTimeline, melange = true)
        val depart = lecteur.currentMediaItemIndex
        val rang = annonce.indexOf(depart)

        // ⚠️ Le tirage est aléatoire : la lecture peut démarrer près de la fin de l'ordre. On ne
        // marche donc que sur ce qui reste, sinon le test échoue une fois sur quatre — et un test
        // qui échoue au hasard ne protège plus rien.
        val pas = minOf(6, annonce.size - rang - 1)
        val suivis = mutableListOf(depart)
        repeat(pas) {
            lecteur.seekToNextMediaItem()
            repeat(5) { tourner() }
            suivis += lecteur.currentMediaItemIndex
        }

        assertEquals("le tirage doit laisser de quoi avancer", 6, pas)
        assertEquals(
            "la file annoncée doit dire ce que le lecteur va jouer",
            annonce.subList(rang, rang + suivis.size),
            suivis
        )
    }
}

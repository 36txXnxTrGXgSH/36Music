package com.music36.app

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import com.music36.app.data.Analyseur
import com.music36.app.data.typeConteneurDe
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Un `.m3u8` égaré dans la file.
 *
 * Le scanner acceptait les listes de lecture — le fournisseur SAF leur donne le type
 * `audio/x-mpegurl`, qui passe le filtre « ça commence par audio/ ». Elles n'apparaissaient que
 * dans « Toutes les chansons », la seule vue qui n'écarte rien.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@UnstableApi
class ListeDeLectureDansLaFileTest {

    private lateinit var lecteur: ExoPlayer

    @Before
    fun ouvrir() {
        lecteur = ExoPlayer.Builder(
            ApplicationProvider.getApplicationContext<android.app.Application>()
        ).build()
    }

    @After
    fun fermer() = lecteur.release()

    /** Copie de `Lecteur.element` : c'est le `setMimeType` qui décide de tout. */
    private fun element(nom: String) = MediaItem.Builder()
        .setMediaId("content://test/$nom")
        .setUri(Uri.parse("content://test/document/primary%3AMusic%2F$nom"))
        .setMimeType(typeConteneurDe(nom))
        .build()

    @Test
    fun `une seule liste de lecture fait echouer toute la file`() {
        val file = listOf(element("a.flac"), element("BEST ALL Genre.m3u8"), element("b.flac"))

        val echec = runCatching { lecteur.setMediaItems(file) }.exceptionOrNull()

        // ⚠️ ExoPlayer construit **toutes** les sources dès la pose de la file : un seul élément
        // qu'il ne sait pas fabriquer emporte les deux autres avec lui.
        assertEquals(0, lecteur.mediaItemCount)
        checkNotNull(echec) { "la file aurait dû être refusée" }
        println("échec = ${echec::class.java.name} : ${echec.message}")
    }

    @Test
    fun `sans la liste de lecture la meme file passe`() {
        lecteur.setMediaItems(listOf(element("a.flac"), element("b.flac")))
        assertEquals(2, lecteur.mediaItemCount)
    }

    /** Le correctif : un type explicite, donc jamais de déduction sur le nom de fichier. */
    private fun elementCorrige(nom: String) = MediaItem.Builder()
        .setMediaId("content://test/$nom")
        .setUri(Uri.parse("content://test/document/primary%3AMusic%2F$nom"))
        .setMimeType(typeConteneurDe(nom) ?: androidx.media3.common.MimeTypes.AUDIO_UNKNOWN)
        .build()

    @Test
    fun `avec un type explicite une liste de lecture n'emporte plus la file`() {
        lecteur.setMediaItems(
            listOf(
                elementCorrige("a.flac"),
                elementCorrige("BEST ALL Genre.m3u8"),
                elementCorrige("b.flac")
            )
        )
        assertEquals(3, lecteur.mediaItemCount)
    }

    @Test
    fun `le scanner n'accepte plus les listes de lecture`() {
        // Le type que le fournisseur SAF annonce vraiment pour un `.m3u8`.
        assertEquals(false, Analyseur.estAudio("BEST ALL Genre.m3u8", "audio/x-mpegurl"))
        assertEquals(false, Analyseur.estAudio("mix.m3u", "audio/mpegurl"))
        assertEquals(false, Analyseur.estAudio("radio.pls", "audio/x-scpls"))
        // Un fichier sans extension mais annoncé comme liste de lecture est écarté aussi.
        assertEquals(false, Analyseur.estAudio("liste", "application/vnd.apple.mpegurl"))
        // Et l'audio reste de l'audio.
        assertEquals(true, Analyseur.estAudio("titre.flac", "audio/flac"))
        assertEquals(true, Analyseur.estAudio("titre.m4a", "audio/mp4"))
    }
}

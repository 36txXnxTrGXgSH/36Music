package com.music36.app

import androidx.compose.ui.graphics.Color
import com.music36.app.data.PaletteApplication
import com.music36.app.data.Piste
import com.music36.app.data.RangQualite
import com.music36.app.ui.couleurQualite
import com.music36.app.ui.enNoirAbsolu
import com.music36.app.ui.paletteDe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La couleur qui dit la qualité d'un fichier, dans la vue molette.
 *
 * Une couleur d'accent est vite jolie et illisible : celles-ci sont donc construites à une
 * luminance visée, comme le reste du thème, et mesurées ici contre le fond réel des lignes dans
 * les dix-huit palettes et les trois modes. Le seuil est celui du WCAG pour un texte courant.
 */
class QualiteCouleurTest {



    private fun piste(format: String, bits: Int = 16, echantillonnage: Int = 44_100) = Piste(
        uri = "content://test/1",
        racine = "content://test",
        dossier = "Musique",
        nomFichier = "titre.$format",
        titre = "Titre",
        artiste = "Artiste",
        album = "Album",
        artisteAlbum = "Artiste",
        genre = "",
        annee = 2013,
        numeroPiste = 1,
        disque = 1,
        duree = 200_000,
        taille = 10,
        dateFichier = 1,
        dateAjout = 1,
        format = format,
        debit = 320,
        echantillonnage = echantillonnage,
        bits = bits,
        canaux = 2
    )

    @Test
    fun `chaque format tombe dans le rang attendu`() {
        assertEquals(RangQualite.SANS_PERTE, piste("FLAC").rangQualite)
        assertEquals(RangQualite.SANS_PERTE, piste("ALAC").rangQualite)
        assertEquals(RangQualite.SANS_PERTE, piste("WAV").rangQualite)
        assertEquals(RangQualite.COMPRESSE_EVOLUE, piste("M4A").rangQualite)
        assertEquals(RangQualite.COMPRESSE_EVOLUE, piste("AAC").rangQualite)
        assertEquals(RangQualite.COMPRESSE_EVOLUE, piste("OPUS").rangQualite)
        assertEquals(RangQualite.ORDINAIRE, piste("MP3").rangQualite)
        assertEquals(RangQualite.ORDINAIRE, piste("WMA").rangQualite)
    }

    @Test
    fun `le sans-perte au-dela du CD monte en haute resolution`() {
        assertEquals(RangQualite.HAUTE_RESOLUTION, piste("FLAC", bits = 24).rangQualite)
        assertEquals(
            RangQualite.HAUTE_RESOLUTION,
            piste("FLAC", echantillonnage = 96_000).rangQualite
        )
        // ⚠️ Un MP3 à 320 kbit/s reste de la compression avec perte, quoi qu'annonce l'en-tête.
        assertEquals(RangQualite.ORDINAIRE, piste("MP3", bits = 24).rangQualite)
    }

    @Test
    fun `le rang ordinaire garde la couleur de texte habituelle`() {
        assertNull(couleurQualite(RangQualite.ORDINAIRE, sombre = false))
        assertNull(couleurQualite(RangQualite.ORDINAIRE, sombre = true))
    }

    @Test
    fun `chaque couleur de rang reste lisible sur les dix-huit palettes`() {
        val rangs = RangQualite.entries.filter { it != RangQualite.ORDINAIRE }
        PaletteApplication.entries.forEach { palette ->
            listOf(
                "clair" to paletteDe(palette, sombre = false),
                "sombre" to paletteDe(palette, sombre = true),
                "AMOLED" to enNoirAbsolu(paletteDe(palette, sombre = true))
            ).forEach { (mode, couleurs) ->
                val sombre = mode != "clair"
                rangs.forEach { rang ->
                    val couleur = couleurQualite(rang, sombre)!!
                    val mesure = Contraste.entre(couleur, couleurs.surfaceContainerLow)
                    assertTrue(
                        "${palette.libelle} $mode — ${rang.libelle} : contraste %.2f".format(mesure),
                        mesure >= 4.5
                    )
                }
            }
        }
    }

    @Test
    fun `les rangs se distinguent les uns des autres`() {
        listOf(false, true).forEach { sombre ->
            val couleurs = RangQualite.entries
                .filter { it != RangQualite.ORDINAIRE }
                .map { couleurQualite(it, sombre)!! }
            couleurs.indices.forEach { i ->
                (i + 1 until couleurs.size).forEach { j ->
                    assertNotEquals("sombre=$sombre", couleurs[i], couleurs[j])
                }
            }
        }
    }
}

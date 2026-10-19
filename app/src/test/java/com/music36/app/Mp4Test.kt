package com.music36.app

import com.music36.app.data.Mp4
import com.music36.app.data.estUnAacInvraisemblable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * La lecture du vrai codec dans un conteneur MP4.
 *
 * `MediaExtractor` rend « AAC » pour un ALAC sur les appareils dépourvus du décodeur : le fichier
 * s'annonçait alors sans perte à 1 100 kbit/s, ce qui n'existe pas. Le conteneur, lui, le dit.
 */
class Mp4Test {

    private fun boite(type: String, contenu: ByteArray): ByteArray {
        val taille = contenu.size + 8
        return byteArrayOf(
            (taille ushr 24).toByte(), (taille ushr 16).toByte(),
            (taille ushr 8).toByte(), taille.toByte()
        ) + type.toByteArray(Charsets.US_ASCII) + contenu
    }

    private fun stsd(code: String): ByteArray {
        val entree = byteArrayOf(0, 0, 0, 8) + code.toByteArray(Charsets.US_ASCII)
        return boite("stsd", byteArrayOf(0, 0, 0, 0, 0, 0, 0, 1) + entree)
    }

    /** Un fichier plausible : un gros `mdat` d'abord, puis l'arborescence du `moov`. */
    private fun fichier(code: String, tailleMdat: Int = 4096): ByteArray =
        boite("ftyp", "M4A isom".toByteArray(Charsets.US_ASCII)) +
            boite("mdat", ByteArray(tailleMdat)) +
            boite("moov", boite("trak", boite("mdia", boite("minf", boite("stbl", stsd(code))))))

    private fun codec(octets: ByteArray) = Mp4.codecAudio(ByteArrayInputStream(octets))

    @Test
    fun `un m4a sans perte se declare alac`() {
        assertEquals("alac", codec(fichier("alac")))
        assertEquals("ALAC", Mp4.nomDuCode(codec(fichier("alac"))))
    }

    @Test
    fun `un m4a avec perte se declare mp4a`() {
        assertEquals("AAC", Mp4.nomDuCode(codec(fichier("mp4a"))))
    }

    @Test
    fun `le gros mdat est saute, pas lu`() {
        // Trois méga-octets d'audio ne doivent pas empêcher d'atteindre le `moov` qui suit.
        assertEquals("ALAC", Mp4.nomDuCode(codec(fichier("alac", tailleMdat = 3_000_000))))
    }

    @Test
    fun `un fichier qui n'est pas du MP4 ne rend rien`() {
        assertNull(codec("fLaC\u0000\u0000\u0000\u0022".toByteArray(Charsets.US_ASCII)))
        assertNull(codec(ByteArray(0)))
        assertEquals("", Mp4.nomDuCode(null))
    }

    @Test
    fun `une boite tronquee ne fait pas tourner l'analyse a vide`() {
        // Taille annoncée bien plus grande que ce qui reste : on renonce au lieu d'insister.
        val tronque = byteArrayOf(0, 0x0F, 0, 0) + "moov".toByteArray(Charsets.US_ASCII)
        assertNull(codec(tronque))
    }

    @Test
    fun `on ne rouvre que les fichiers dont l'etiquette est contredite par leur debit`() {
        // Le cas réel : un ALAC pris pour de l'AAC, à un débit que l'AAC n'atteint pas.
        assertTrue(estUnAacInvraisemblable("AAC", "04 Avec Tout Mon Amour.m4a", 1108))
        // Un vrai AAC, qu'il ne faut surtout pas rouvrir à chaque démarrage.
        assertFalse(estUnAacInvraisemblable("AAC", "titre.m4a", 256))
        // Déjà corrigé.
        assertFalse(estUnAacInvraisemblable("ALAC", "titre.m4a", 1108))
        // Hors de la famille MP4 : le débit élevé y est normal.
        assertFalse(estUnAacInvraisemblable("AAC", "titre.aac", 1108))
        assertFalse(estUnAacInvraisemblable("FLAC", "titre.flac", 900))
    }
}

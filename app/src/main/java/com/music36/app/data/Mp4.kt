package com.music36.app.data

import java.io.InputStream

/**
 * Le vrai codec d'un conteneur MP4, lu dans le fichier lui-même.
 *
 * ⚠️ Un `.m4a` peut abriter de l'AAC **ou** de l'ALAC — l'un avec perte, l'autre sans. Android
 * devrait le dire, mais `MediaExtractor` échoue sur l'ALAC des appareils dépourvus du décodeur
 * correspondant : il retombe alors sur l'extension, et un fichier sans perte s'annonce « AAC »
 * avec un débit de 1 100 kbit/s qui n'a aucun sens.
 *
 * Le conteneur, lui, ne ment pas : le code à quatre lettres de la première entrée `stsd` nomme le
 * codec. On descend donc l'arborescence `moov > trak > mdia > minf > stbl > stsd` et on le lit.
 */
object Mp4 {

    /** Les boîtes dans lesquelles il faut descendre pour atteindre `stsd`. */
    private val CONTENEURS = setOf("moov", "trak", "mdia", "minf", "stbl")

    /**
     * Au-delà, on renonce : un fichier mal formé ne doit pas faire tourner l'analyse à vide.
     * Les `moov` dépassent rarement quelques centaines de kilo-octets, `mdat` étant sauté d'un
     * bond et non lu.
     */
    private const val BOITES_MAXIMUM = 4_096

    /** Le code à quatre lettres du premier flux audio, ou `null` si le fichier n'en dit rien. */
    fun codecAudio(flux: InputStream): String? = parcourir(flux, Long.MAX_VALUE, Compteur())

    private class Compteur {
        var boites = 0
    }

    private fun parcourir(flux: InputStream, disponible: Long, compteur: Compteur): String? {
        var reste = disponible
        while (reste >= 8) {
            if (++compteur.boites > BOITES_MAXIMUM) return null
            val entete = lire(flux, 8) ?: return null
            var taille = entier(entete, 0).toLong() and 0xFFFFFFFFL
            val type = String(entete, 4, 4, Charsets.US_ASCII)
            var enTete = 8L
            if (taille == 1L) {
                val grande = lire(flux, 8) ?: return null
                taille = 0
                for (i in 0 until 8) taille = (taille shl 8) or (grande[i].toLong() and 0xFF)
                enTete = 16L
            } else if (taille == 0L) {
                // La boîte court jusqu'à la fin du fichier.
                taille = reste
            }
            val contenu = taille - enTete
            if (contenu < 0) return null

            when {
                type in CONTENEURS -> parcourir(flux, contenu, compteur)?.let { return it }
                type == "stsd" -> return codecDeStsd(flux, contenu)
                else -> if (!sauter(flux, contenu)) return null
            }
            reste -= taille
        }
        return null
    }

    /** `stsd` : version et drapeaux, nombre d'entrées, puis la première entrée — taille et code. */
    private fun codecDeStsd(flux: InputStream, taille: Long): String? {
        if (taille < 16) return null
        val entete = lire(flux, 8) ?: return null
        if (entier(entete, 4) <= 0) return null
        val entree = lire(flux, 8) ?: return null
        return String(entree, 4, 4, Charsets.US_ASCII)
    }

    private fun lire(flux: InputStream, n: Int): ByteArray? {
        val tampon = ByteArray(n)
        var lus = 0
        while (lus < n) {
            val v = flux.read(tampon, lus, n - lus)
            if (v <= 0) return null
            lus += v
        }
        return tampon
    }

    /** `skip` peut rendre moins que demandé : on insiste, sinon on décale tout le reste. */
    private fun sauter(flux: InputStream, n: Long): Boolean {
        var reste = n
        while (reste > 0) {
            val saute = flux.skip(reste)
            if (saute > 0) {
                reste -= saute
                continue
            }
            if (flux.read() < 0) return false
            reste--
        }
        return true
    }

    private fun entier(o: ByteArray, i: Int): Int =
        ((o[i].toInt() and 0xFF) shl 24) or ((o[i + 1].toInt() and 0xFF) shl 16) or
            ((o[i + 2].toInt() and 0xFF) shl 8) or (o[i + 3].toInt() and 0xFF)

    /** Le nom courant d'un codec à partir de son code à quatre lettres. */
    fun nomDuCode(code: String?): String = when (code) {
        null -> ""
        "alac" -> "ALAC"
        "mp4a" -> "AAC"
        "fLaC" -> "FLAC"
        "Opus" -> "Opus"
        "ac-3" -> "AC3"
        "ec-3" -> "E-AC3"
        "lpcm", "sowt", "twos", "in24", "in32" -> "PCM"
        ".mp3" -> "MP3"
        else -> ""
    }
}

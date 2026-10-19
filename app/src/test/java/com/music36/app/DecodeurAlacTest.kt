package com.music36.app

import com.music36.app.lecture.alac.ConfigAlac
import com.music36.app.lecture.alac.DecodeurAlac
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Le décodeur ALAC, mis à l'épreuve sur un vrai fichier.
 *
 * ⚠️ **Comment on sait qu'un décodeur sans perte est juste sans avoir la référence.**
 *
 * Un décodage faux ne donne pas « un peu de bruit » : le prédicteur diverge dès le premier bit
 * mal lu et rend du bruit blanc saturé. Trois mesures le distinguent d'un signal musical, et
 * aucune ne peut être trompée par hasard :
 *
 * 1. **l'autocorrélation à un échantillon** — la musique échantillonnée à 44,1 kHz est très
 *    corrélée d'un échantillon au suivant (> 0,8) ; le bruit est à zéro ;
 * 2. **la continuité aux frontières de trames** — chaque trame ALAC se décode indépendamment ;
 *    si l'une était fausse, le raccord avec la suivante ferait un saut brutal ;
 * 3. **l'absence de saturation permanente** et une énergie qui varie dans le temps.
 *
 * Le test s'abstient si le fichier d'exemple n'est pas sur le poste. Son chemin vient de la
 * variable d'environnement `MUSIC36_EXEMPLE_ALAC` : un fichier ALAC `.m4a` quelconque convient.
 *
 * ⚠️ Jamais de chemin écrit en dur, ni de fichier d'exemple dans le dépôt : ce serait de la
 * musique sous droits, et le chemin d'un poste n'a de sens pour personne d'autre.
 */
class DecodeurAlacTest {

    private val exemple = File(System.getenv("MUSIC36_EXEMPLE_ALAC") ?: "")

    @Test
    fun `le cookie ALAC se lit avec ou sans son entete`() {
        // Cookie complet (28 octets, en-tête version/drapeaux compris) du fichier d'exemple.
        val complet = hexVers(
            "00000000000010000010280a0e0200ff00003c530010d7000000ac44"
        )
        val config = ConfigAlac.lire(complet)!!
        assertEquals(4096, config.frameLength)
        assertEquals(16, config.bitDepth)
        assertEquals(40, config.pb)
        assertEquals(10, config.mb)
        assertEquals(14, config.kb)
        assertEquals(2, config.canaux)
        assertEquals(44_100, config.echantillonnage)

        // Même cookie amputé de son en-tête : media3 transmet l'une ou l'autre forme.
        val nu = complet.copyOfRange(4, complet.size)
        val configNue = ConfigAlac.lire(nu)!!
        assertEquals(config.frameLength, configNue.frameLength)
        assertEquals(config.echantillonnage, configNue.echantillonnage)
    }

    @Test
    fun `un cookie invalide est refuse plutot que devine`() {
        assertEquals(null, ConfigAlac.lire(ByteArray(4)))
        assertEquals(null, ConfigAlac.lire(hexVers("00000000000000000010280a0e0200ff00003c530010d7000000ac44")))
    }

    @Test
    fun `le decodage d'un vrai fichier rend de la musique, pas du bruit`() {
        Assume.assumeTrue("Fichier d'exemple absent de ce poste", exemple.exists())

        val donnees = exemple.readBytes()
        val piste = LecteurMp4(donnees).lire()
        val config = ConfigAlac.lire(piste.cookie)
            ?: throw AssertionError("cookie ALAC illisible")
        assertEquals(2, config.canaux)

        val decodeur = DecodeurAlac(config)
        val sortie = ShortArray(config.frameLength * config.canaux)
        val signal = ShortArray(TRAMES_TESTEES * config.frameLength * config.canaux)
        val frontieres = mutableListOf<Int>()
        var ecrits = 0

        val trames = minOf(TRAMES_TESTEES, piste.tailles.size)
        for (index in 0 until trames) {
            val debut = piste.positions[index]
            val taille = piste.tailles[index]
            val trame = donnees.copyOfRange(debut, debut + taille)

            val echantillons = decodeur.decoder(trame, trame.size, sortie)
            assertEquals("trame $index", config.frameLength, echantillons)

            if (index > 0) frontieres += ecrits
            System.arraycopy(sortie, 0, signal, ecrits, echantillons * config.canaux)
            ecrits += echantillons * config.canaux
        }

        val gauche = IntArray(ecrits / 2) { signal[it * 2].toInt() }

        // 1. Le signal ressemble-t-il à de la musique ?
        val correlation = autocorrelation(gauche)
        assertTrue(
            "Autocorrélation ${"%.3f".format(correlation)} : le décodage rend du bruit, pas un signal",
            correlation > 0.8
        )

        // 2. Les trames se raccordent-elles ?
        val sautMoyen = frontieres.map {
            abs(signal[it].toInt() - signal[it - 2].toInt())
        }.average()
        val sautCourant = (2 until gauche.size).map {
            abs(gauche[it] - gauche[it - 1])
        }.average()
        assertTrue(
            "Raccord de trames à $sautMoyen contre $sautCourant en moyenne : une trame est fausse",
            sautMoyen < sautCourant * 12 + 64
        )

        // 3. Amplitude vivante, sans saturation permanente.
        val satures = gauche.count { abs(it) >= 32_700 }
        assertTrue("Signal saturé sur $satures échantillons", satures < gauche.size / 100)
        val efficace = sqrt(gauche.sumOf { it.toDouble() * it } / gauche.size)
        assertTrue("Signal quasi muet (efficace = $efficace)", efficace > 100)
        assertTrue("Signal anormalement fort (efficace = $efficace)", efficace < 30_000)
    }

    @Test
    fun `le fichier entier se decode sans accroc`() {
        Assume.assumeTrue("Fichier d'exemple absent de ce poste", exemple.exists())

        val donnees = exemple.readBytes()
        val piste = LecteurMp4(donnees).lire()
        val config = ConfigAlac.lire(piste.cookie)!!
        val decodeur = DecodeurAlac(config)
        val sortie = ShortArray(config.frameLength * config.canaux)

        // ⚠️ Toutes les trames, pas un échantillon : une seule trame fautive s'entendrait comme
        // un craquement, et ne se verrait pas sur les vingt premières secondes.
        var total = 0L
        var maximum = 0
        val extrait = java.io.ByteArrayOutputStream()
        val secondes = 30
        val limiteExtrait = config.echantillonnage * secondes * config.canaux * 2

        for (index in piste.tailles.indices) {
            val debut = piste.positions[index]
            val trame = donnees.copyOfRange(debut, debut + piste.tailles[index])
            val echantillons = try {
                decodeur.decoder(trame, trame.size, sortie)
            } catch (erreur: Exception) {
                throw AssertionError("Trame $index sur ${piste.tailles.size} : ${erreur.message}")
            }
            total += echantillons
            for (i in 0 until echantillons * config.canaux) {
                val valeur = sortie[i].toInt()
                if (abs(valeur) > maximum) maximum = abs(valeur)
                if (extrait.size() < limiteExtrait) {
                    extrait.write(valeur and 0xFF)
                    extrait.write((valeur shr 8) and 0xFF)
                }
            }
        }

        // La durée annoncée par le conteneur : 9 619 680 échantillons à 44,1 kHz (3 min 38 s).
        assertEquals(9_619_680L, total)
        assertTrue("Amplitude maximale de $maximum : décodage suspect", maximum in 1000..32_767)

        // Un extrait audible, déposé dans le dossier de construction : les mesures disent que
        // c'est juste, l'oreille le confirme en dix secondes.
        val destination = File("build/extrait-alac.wav")
        destination.parentFile?.mkdirs()
        destination.writeBytes(enteteWav(extrait.size(), config) + extrait.toByteArray())
    }

    /** En-tête WAV minimal, pour rendre l'extrait écoutable. */
    private fun enteteWav(octets: Int, config: ConfigAlac): ByteArray {
        val tampon = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        tampon.put("RIFF".toByteArray())
        tampon.putInt(36 + octets)
        tampon.put("WAVEfmt ".toByteArray())
        tampon.putInt(16)
        tampon.putShort(1)
        tampon.putShort(config.canaux.toShort())
        tampon.putInt(config.echantillonnage)
        tampon.putInt(config.echantillonnage * config.canaux * 2)
        tampon.putShort((config.canaux * 2).toShort())
        tampon.putShort(16)
        tampon.put("data".toByteArray())
        tampon.putInt(octets)
        return tampon.array()
    }

    private fun autocorrelation(signal: IntArray): Double {
        var produit = 0.0
        var energie = 0.0
        for (i in 1 until signal.size) {
            produit += signal[i].toDouble() * signal[i - 1]
            energie += signal[i].toDouble() * signal[i]
        }
        return if (energie == 0.0) 0.0 else produit / energie
    }

    private fun hexVers(hex: String) = ByteArray(hex.length / 2) {
        hex.substring(it * 2, it * 2 + 2).toInt(16).toByte()
    }

    private companion object {
        /** Une vingtaine de secondes : assez pour juger, assez court pour un test unitaire. */
        const val TRAMES_TESTEES = 200
    }
}

/**
 * Lecteur MP4 minimal, réservé aux tests : de quoi retrouver le cookie ALAC et découper les trames.
 *
 * L'application, elle, laisse ce travail à l'extracteur de media3 ; ce lecteur n'existe que pour
 * éprouver le décodeur sur un fichier réel, hors de tout Android.
 */
internal class LecteurMp4(private val donnees: ByteArray) {

    class Piste(val cookie: ByteArray, val positions: IntArray, val tailles: IntArray)

    private var cookie: ByteArray? = null
    private var tailles = IntArray(0)
    private var tailleFixe = 0
    private var nombreEchantillons = 0
    private val morceaux = mutableListOf<Int>()
    private val liaisons = mutableListOf<Triple<Int, Int, Int>>() // premierMorceau, parMorceau, id

    fun lire(): Piste {
        parcourir(0, donnees.size)
        val cookieLu = cookie ?: throw AssertionError("aucune configuration ALAC trouvée")

        val positions = IntArray(nombreEchantillons)
        val taillesFinales = IntArray(nombreEchantillons)
        var echantillon = 0

        for ((indexLiaison, liaison) in liaisons.withIndex()) {
            val (premier, parMorceau, _) = liaison
            val dernier = if (indexLiaison + 1 < liaisons.size) {
                liaisons[indexLiaison + 1].first - 1
            } else {
                morceaux.size
            }
            for (morceau in premier..dernier) {
                if (morceau - 1 !in morceaux.indices) continue
                var position = morceaux[morceau - 1]
                repeat(parMorceau) {
                    if (echantillon < nombreEchantillons) {
                        val taille = if (tailleFixe != 0) tailleFixe else tailles[echantillon]
                        positions[echantillon] = position
                        taillesFinales[echantillon] = taille
                        position += taille
                        echantillon++
                    }
                }
            }
        }
        return Piste(cookieLu, positions.copyOf(echantillon), taillesFinales.copyOf(echantillon))
    }

    private fun entier32(position: Int): Int =
        ((donnees[position].toInt() and 0xFF) shl 24) or
            ((donnees[position + 1].toInt() and 0xFF) shl 16) or
            ((donnees[position + 2].toInt() and 0xFF) shl 8) or
            (donnees[position + 3].toInt() and 0xFF)

    private fun nom(position: Int) = String(donnees, position, 4, Charsets.ISO_8859_1)

    private fun parcourir(debut: Int, fin: Int) {
        var position = debut
        while (position + 8 <= fin) {
            var taille = entier32(position)
            val nom = nom(position + 4)
            var enTete = 8
            if (taille == 1) {
                enTete = 16
                taille = entier32(position + 12) // partie basse, suffisante ici
            } else if (taille == 0) {
                taille = fin - position
            }
            if (taille < enTete) return
            val corps = position + enTete
            val finBoite = position + taille

            when (nom) {
                "moov", "trak", "mdia", "minf", "stbl" -> parcourir(corps, finBoite)
                "stsd" -> parcourir(corps + 8, finBoite)
                "alac" -> {
                    // La boîte de configuration fait 36 octets ; l'entrée de format en fait plus.
                    if (taille <= 64 && cookie == null) {
                        cookie = donnees.copyOfRange(corps, finBoite)
                    } else {
                        parcourir(corps + 28, finBoite)
                    }
                }

                "stsz" -> {
                    tailleFixe = entier32(corps + 4)
                    nombreEchantillons = entier32(corps + 8)
                    if (tailleFixe == 0) {
                        tailles = IntArray(nombreEchantillons) { entier32(corps + 12 + it * 4) }
                    }
                }

                "stco" -> {
                    val nombre = entier32(corps + 4)
                    for (i in 0 until nombre) morceaux += entier32(corps + 8 + i * 4)
                }

                "stsc" -> {
                    val nombre = entier32(corps + 4)
                    for (i in 0 until nombre) {
                        val base = corps + 8 + i * 12
                        liaisons += Triple(entier32(base), entier32(base + 4), entier32(base + 8))
                    }
                }
            }
            position = finBoite
        }
    }
}

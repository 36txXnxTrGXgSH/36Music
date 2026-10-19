package com.music36.app.lecture.alac

/**
 * Décodeur ALAC (Apple Lossless), écrit en Kotlin pur.
 *
 * ## Pourquoi ce fichier existe
 *
 * Beaucoup de téléphones Android n'embarquent **aucun** décodeur `audio/alac`. Le conteneur `.m4a`
 * est lu, la piste est trouvée, et rien ne sort — ExoPlayer ne considère même pas cela comme une
 * erreur (voir `docs/SORTIE-AUDIO.md`). La seule façon de lire ces fichiers partout est de décoder
 * l'ALAC nous-mêmes.
 *
 * L'algorithme suit le décodeur de référence publié par Apple (Apache 2.0, 2011) : décodage
 * entropique de Rice adaptatif, puis prédicteur linéaire à coefficients adaptatifs, puis démixage
 * stéréo. Aucune de ces trois étapes n'est approximative — l'ALAC est **sans perte**, le résultat
 * doit être identique au fichier d'origine, bit pour bit. C'est d'ailleurs ainsi qu'on le teste :
 * en comparant à la sortie d'un autre décodeur, échantillon par échantillon.
 *
 * ## Ce qui n'est pas couvert
 *
 * Les profondeurs 16 et 24 bits (la totalité des fichiers courants). Le 20 et le 32 bits lèvent
 * une exception explicite plutôt que de rendre du bruit.
 */
class DecodeurAlac(val config: ConfigAlac) {

    /** Erreurs de décodage : trame illisible, format non pris en charge. */
    class ErreurAlac(message: String) : Exception(message)

    private val predicteur = IntArray(config.frameLength)
    private val melangeU = IntArray(config.frameLength)
    private val melangeV = IntArray(config.frameLength)
    private val decalages = IntArray(config.frameLength * 2)
    private val coefficientsU = ShortArray(32)
    private val coefficientsV = ShortArray(32)

    /**
     * Décode une trame ALAC vers du PCM 16 bits entrelacé.
     *
     * @return le nombre d'échantillons **par canal** écrits dans [sortie].
     */
    fun decoder(trame: ByteArray, longueur: Int, sortie: ShortArray): Int {
        val bits = LecteurBits(trame, longueur)
        var echantillons = 0

        while (true) {
            val etiquette = bits.lire(3)
            when (etiquette) {
                ELEMENT_MONO, ELEMENT_BASSES -> {
                    echantillons = decoderMono(bits, sortie)
                    return echantillons
                }

                ELEMENT_STEREO -> {
                    echantillons = decoderStereo(bits, sortie)
                    return echantillons
                }

                ELEMENT_REMPLISSAGE -> {
                    // Élément de remplissage : une taille, puis des octets à ignorer.
                    var taille = bits.lire(4)
                    if (taille == 15) taille += bits.lire(8) - 1
                    bits.avancer(taille * 8)
                }

                ELEMENT_FIN -> return echantillons

                else -> throw ErreurAlac("Élément ALAC inattendu : $etiquette")
            }
        }
    }

    // ------------------------------------------------------------------ éléments

    private fun decoderMono(bits: LecteurBits, sortie: ShortArray): Int {
        bits.lire(4) // identifiant de l'élément
        bits.lire(12) // réservé
        val entete = bits.lire(4)
        val tramePartielle = entete ushr 3
        val octetsDecales = (entete ushr 1) and 0x03
        val echappement = entete and 0x01
        var bitsCanal = config.bitDepth - octetsDecales * 8

        var nombre = config.frameLength
        if (tramePartielle != 0) nombre = (bits.lire(16) shl 16) or bits.lire(16)
        verifierTaille(nombre)

        if (echappement == 0) {
            bits.lire(8) // bits de mixage, inutilisés en mono
            bits.lire(8) // résidu de mixage
            val modeEtDecalage = bits.lire(8)
            val mode = modeEtDecalage ushr 4
            val decalageDen = modeEtDecalage and 0x0F
            val facteurEtNombre = bits.lire(8)
            val facteurPb = facteurEtNombre ushr 5
            val actifs = facteurEtNombre and 0x1F
            for (i in 0 until actifs) coefficientsU[i] = bits.lire(16).toShort()

            val positionDecalages = bits.position
            if (octetsDecales != 0) bits.avancer(octetsDecales * 8 * nombre)

            decompresser(bits, predicteur, nombre, bitsCanal, facteurPb)
            if (mode == 0) {
                predire(predicteur, melangeU, nombre, coefficientsU, actifs, bitsCanal, decalageDen)
            } else {
                predire(predicteur, predicteur, nombre, null, 31, bitsCanal, 0)
                predire(predicteur, melangeU, nombre, coefficientsU, actifs, bitsCanal, decalageDen)
            }
            if (octetsDecales != 0) lireDecalages(bits, positionDecalages, octetsDecales, nombre, 1)
        } else {
            bitsCanal = config.bitDepth
            val decalage = 32 - bitsCanal
            for (i in 0 until nombre) {
                melangeU[i] = (bits.lire(bitsCanal) shl decalage) shr decalage
            }
        }

        ecrireMono(melangeU, sortie, nombre, octetsDecales)
        return nombre
    }

    private fun decoderStereo(bits: LecteurBits, sortie: ShortArray): Int {
        bits.lire(4) // identifiant de l'élément
        bits.lire(12) // réservé
        val entete = bits.lire(4)
        val tramePartielle = entete ushr 3
        val octetsDecales = (entete ushr 1) and 0x03
        val echappement = entete and 0x01

        // ⚠️ Un bit de plus qu'en mono : le démixage produit une différence entre canaux, qui
        // demande une amplitude double. L'oublier donne un décodage qui « marche » mais saturé.
        var bitsCanal = config.bitDepth - octetsDecales * 8 + 1

        var nombre = config.frameLength
        if (tramePartielle != 0) nombre = (bits.lire(16) shl 16) or bits.lire(16)
        verifierTaille(nombre)

        var bitsMelange = 0
        var residuMelange = 0

        if (echappement == 0) {
            bitsMelange = bits.lire(8)
            residuMelange = bits.lire(8).toByte().toInt() // signé
            val modeU = bits.lire(8)
            val modeUHaut = modeU ushr 4
            val decalageU = modeU and 0x0F
            val facteurU = bits.lire(8)
            val pbU = facteurU ushr 5
            val actifsU = facteurU and 0x1F
            for (i in 0 until actifsU) coefficientsU[i] = bits.lire(16).toShort()

            val modeV = bits.lire(8)
            val modeVHaut = modeV ushr 4
            val decalageV = modeV and 0x0F
            val facteurV = bits.lire(8)
            val pbV = facteurV ushr 5
            val actifsV = facteurV and 0x1F
            for (i in 0 until actifsV) coefficientsV[i] = bits.lire(16).toShort()

            val positionDecalages = bits.position
            if (octetsDecales != 0) bits.avancer(octetsDecales * 8 * 2 * nombre)

            decompresser(bits, predicteur, nombre, bitsCanal, pbU)
            if (modeUHaut == 0) {
                predire(predicteur, melangeU, nombre, coefficientsU, actifsU, bitsCanal, decalageU)
            } else {
                predire(predicteur, predicteur, nombre, null, 31, bitsCanal, 0)
                predire(predicteur, melangeU, nombre, coefficientsU, actifsU, bitsCanal, decalageU)
            }

            decompresser(bits, predicteur, nombre, bitsCanal, pbV)
            if (modeVHaut == 0) {
                predire(predicteur, melangeV, nombre, coefficientsV, actifsV, bitsCanal, decalageV)
            } else {
                predire(predicteur, predicteur, nombre, null, 31, bitsCanal, 0)
                predire(predicteur, melangeV, nombre, coefficientsV, actifsV, bitsCanal, decalageV)
            }

            if (octetsDecales != 0) lireDecalages(bits, positionDecalages, octetsDecales, nombre, 2)
        } else {
            bitsCanal = config.bitDepth
            val decalage = 32 - bitsCanal
            for (i in 0 until nombre) {
                melangeU[i] = (bits.lire(bitsCanal) shl decalage) shr decalage
                melangeV[i] = (bits.lire(bitsCanal) shl decalage) shr decalage
            }
        }

        demixer(sortie, nombre, bitsMelange, residuMelange, octetsDecales)
        return nombre
    }

    private fun verifierTaille(nombre: Int) {
        if (nombre <= 0 || nombre > config.frameLength) {
            throw ErreurAlac("Trame de $nombre échantillons, attendu au plus ${config.frameLength}")
        }
    }

    /** Les bits de poids faible mis de côté avant la compression (fichiers 24 bits surtout). */
    private fun lireDecalages(
        bits: LecteurBits,
        position: Int,
        octetsDecales: Int,
        nombre: Int,
        canaux: Int
    ) {
        val retenu = bits.position
        bits.position = position
        val largeur = octetsDecales * 8
        for (i in 0 until nombre * canaux) decalages[i] = bits.lire(largeur)
        bits.position = retenu
    }

    // ------------------------------------------------------------------ sortie

    private fun ecrireMono(source: IntArray, sortie: ShortArray, nombre: Int, octetsDecales: Int) {
        when (config.bitDepth) {
            16 -> for (i in 0 until nombre) sortie[i] = source[i].toShort()
            24 -> {
                val largeur = octetsDecales * 8
                for (i in 0 until nombre) {
                    var valeur = source[i]
                    if (largeur != 0) valeur = (valeur shl largeur) or decalages[i]
                    sortie[i] = (valeur shr 8).toShort()
                }
            }

            else -> throw ErreurAlac("Profondeur ${config.bitDepth} bits non prise en charge")
        }
    }

    /**
     * Démixage stéréo.
     *
     * ALAC ne code pas gauche et droite mais une moyenne et une différence pondérées ; sans cette
     * étape, on obtient deux canaux cohérents mais faux.
     */
    private fun demixer(
        sortie: ShortArray,
        nombre: Int,
        bitsMelange: Int,
        residuMelange: Int,
        octetsDecales: Int
    ) {
        when (config.bitDepth) {
            16 -> {
                if (residuMelange != 0) {
                    for (i in 0 until nombre) {
                        val v = melangeV[i]
                        val gauche = melangeU[i] + v - ((residuMelange * v) shr bitsMelange)
                        sortie[i * 2] = gauche.toShort()
                        sortie[i * 2 + 1] = (gauche - v).toShort()
                    }
                } else {
                    for (i in 0 until nombre) {
                        sortie[i * 2] = melangeU[i].toShort()
                        sortie[i * 2 + 1] = melangeV[i].toShort()
                    }
                }
            }

            24 -> {
                val largeur = octetsDecales * 8
                for (i in 0 until nombre) {
                    var gauche: Int
                    var droite: Int
                    if (residuMelange != 0) {
                        val v = melangeV[i]
                        gauche = melangeU[i] + v - ((residuMelange * v) shr bitsMelange)
                        droite = gauche - v
                    } else {
                        gauche = melangeU[i]
                        droite = melangeV[i]
                    }
                    if (largeur != 0) {
                        gauche = (gauche shl largeur) or decalages[i * 2]
                        droite = (droite shl largeur) or decalages[i * 2 + 1]
                    }
                    // ⚠️ Sortie 16 bits : les huit bits de poids faible d'un fichier 24 bits sont
                    // perdus ici. C'est le compromis assumé de cette version — tout vaut mieux
                    // qu'un silence — et le seul endroit à reprendre pour du 24 bits de bout en bout.
                    sortie[i * 2] = (gauche shr 8).toShort()
                    sortie[i * 2 + 1] = (droite shr 8).toShort()
                }
            }

            else -> throw ErreurAlac("Profondeur ${config.bitDepth} bits non prise en charge")
        }
    }

    // ------------------------------------------------------------------ décodage entropique

    /**
     * Décodage de Rice adaptatif.
     *
     * Le paramètre du code suit la moyenne glissante des valeurs déjà lues : plus le signal est
     * calme, plus le code est court. Les silences sont codés en bloc (« run » de zéros), d'où le
     * second mode.
     */
    private fun decompresser(
        bits: LecteurBits,
        sortie: IntArray,
        nombre: Int,
        bitsCanal: Int,
        facteurPb: Int
    ) {
        val pb = (config.pb * facteurPb) / 4
        val kb = config.kb
        val wb = (1 shl kb) - 1
        var mb = config.mb
        var mode = 0
        var i = 0

        while (i < nombre) {
            // ⚠️ L'historique est tenu **multiplié par 512** (QB) : c'est sa division qui donne
            // la moyenne des valeurs récentes, donc le paramètre du code. Oublier ce décalage
            // rend un décodeur qui lit trop de bits, se désynchronise dès le deuxième échantillon
            // et ne produit plus que du bruit — sans jamais échouer franchement.
            var k = lg3a(mb ushr DECALAGE_QB)
            if (k > kb) k = kb
            val m = (1 shl k) - 1

            val n = riceLire32(bits, m, k, bitsCanal)

            val decode = n + mode
            // Le bit de poids faible porte le signe : 0 → positif, 1 → négatif.
            val signe = ((decode and 1) * -2) + 1
            sortie[i] = ((decode + 1) shr 1) * signe
            i++

            mb = pb * (n + mode) + mb - ((pb * mb) shr DECALAGE_QB)
            if (n > SEUIL_MOYENNE) mb = SEUIL_MOYENNE
            mode = 0

            if ((mb shl DECALAGE_MULT) < QB && i < nombre) {
                mode = 1
                k = leading(mb) - DECALAGE_BIT + ((mb + DECALAGE_OFFSET) shr DECALAGE_DEN)
                val mz = ((1 shl k) - 1) and wb

                val zeros = riceLire16(bits, mz, k)
                if (zeros > 0) {
                    if (i + zeros > nombre) throw ErreurAlac("Suite de zéros hors trame")
                    java.util.Arrays.fill(sortie, i, i + zeros, 0)
                    i += zeros
                }
                if (zeros >= 65535) mode = 0
                mb = 0
            }
        }
    }

    private fun riceLire32(bits: LecteurBits, m: Int, k: Int, maxBits: Int): Int {
        val flux = bits.regarder32()
        var resultat = leading(flux.inv())

        if (resultat >= PREFIXE_MAX) {
            bits.avancer(PREFIXE_MAX + maxBits)
            return (flux shl PREFIXE_MAX) ushr (32 - maxBits)
        }
        bits.avancer(resultat + 1)
        if (k != 1) {
            val valeur = (flux shl (resultat + 1)) ushr (32 - k)
            bits.avancer(k - 1)
            resultat *= m
            if (valeur > 1) {
                resultat += valeur - 1
                bits.avancer(1)
            }
        }
        return resultat
    }

    private fun riceLire16(bits: LecteurBits, m: Int, k: Int): Int {
        val flux = bits.regarder32()
        var resultat = leading(flux.inv())

        if (resultat >= PREFIXE_MAX) {
            bits.avancer(PREFIXE_MAX + 16)
            return (flux shl PREFIXE_MAX) ushr 16
        }
        bits.avancer(resultat + 1)
        if (k != 1) {
            val valeur = (flux shl (resultat + 1)) ushr (32 - k)
            bits.avancer(k - 1)
            resultat *= m
            if (valeur > 1) {
                resultat += valeur - 1
                bits.avancer(1)
            }
        }
        return resultat
    }

    // ------------------------------------------------------------------ prédicteur

    /**
     * Prédicteur linéaire à coefficients adaptatifs.
     *
     * Chaque échantillon est prédit à partir des précédents ; seule l'erreur de prédiction a été
     * codée. Les coefficients se corrigent **au fil du décodage**, exactement comme à l'encodage :
     * c'est ce qui rend l'ALAC sans perte, et ce qui interdit la moindre approximation ici.
     */
    private fun predire(
        entree: IntArray,
        sortie: IntArray,
        nombre: Int,
        coefficients: ShortArray?,
        actifs: Int,
        bitsCanal: Int,
        decalageDen: Int
    ) {
        val decalageCanal = 32 - bitsCanal
        sortie[0] = entree[0]

        if (actifs == 0) {
            if (nombre > 1 && entree !== sortie) {
                System.arraycopy(entree, 1, sortie, 1, nombre - 1)
            }
            return
        }

        if (actifs == 31) {
            // Mode « décalage » : simple somme cumulée.
            var precedent = sortie[0]
            for (i in 1 until nombre) {
                val valeur = entree[i] + precedent
                precedent = (valeur shl decalageCanal) shr decalageCanal
                sortie[i] = precedent
            }
            return
        }

        val coefs = coefficients ?: return
        val demiDen = 1 shl (decalageDen - 1)

        for (i in 1..actifs) {
            val valeur = entree[i] + sortie[i - 1]
            sortie[i] = (valeur shl decalageCanal) shr decalageCanal
        }

        val debut = actifs + 1
        for (i in debut until nombre) {
            var somme = 0
            val sommet = sortie[i - debut]
            val base = i - 1
            for (j in 0 until actifs) {
                somme += coefs[j] * (sortie[base - j] - sommet)
            }

            var erreur = entree[i]
            var reste = erreur
            val signe = signeDe(erreur)
            erreur += sommet + ((somme + demiDen) shr decalageDen)
            sortie[i] = (erreur shl decalageCanal) shr decalageCanal

            // Correction des coefficients : on ne touche que ceux qui ont poussé dans le mauvais
            // sens, et on s'arrête dès que l'erreur est résorbée.
            if (signe > 0) {
                for (j in actifs - 1 downTo 0) {
                    val ecart = sommet - sortie[base - j]
                    val s = signeDe(ecart)
                    coefs[j] = (coefs[j] - s).toShort()
                    reste -= (actifs - j) * ((s * ecart) shr decalageDen)
                    if (reste <= 0) break
                }
            } else if (signe < 0) {
                for (j in actifs - 1 downTo 0) {
                    val ecart = sommet - sortie[base - j]
                    val s = signeDe(ecart)
                    coefs[j] = (coefs[j] + s).toShort()
                    reste -= (actifs - j) * ((-s * ecart) shr decalageDen)
                    if (reste >= 0) break
                }
            }
        }
    }

    private companion object {
        const val ELEMENT_MONO = 0
        const val ELEMENT_STEREO = 1
        const val ELEMENT_BASSES = 3
        const val ELEMENT_REMPLISSAGE = 6
        const val ELEMENT_FIN = 7

        const val DECALAGE_QB = 9
        const val QB = 1 shl DECALAGE_QB
        const val DECALAGE_MULT = 2
        const val DECALAGE_DEN = DECALAGE_QB - DECALAGE_MULT - 1
        const val DECALAGE_OFFSET = 1 shl (DECALAGE_DEN - 2)
        const val DECALAGE_BIT = 24
        const val PREFIXE_MAX = 9
        const val SEUIL_MOYENNE = 0xFFFF

        fun leading(valeur: Int): Int = Integer.numberOfLeadingZeros(valeur)

        fun lg3a(valeur: Int): Int = 31 - leading(valeur + 3)

        fun signeDe(valeur: Int): Int = (valeur shr 31) or ((-valeur) ushr 31)
    }
}

/**
 * Le « magic cookie » ALAC : les quelques champs sans lesquels aucune trame n'est décodable.
 *
 * Il est rangé dans la boîte `alac` du conteneur MP4 et transmis par l'extracteur.
 */
data class ConfigAlac(
    val frameLength: Int,
    val bitDepth: Int,
    val pb: Int,
    val mb: Int,
    val kb: Int,
    val canaux: Int,
    val maxRun: Int,
    val maxFrameBytes: Int,
    val debitMoyen: Int,
    val echantillonnage: Int
) {
    companion object {
        /**
         * Lit le cookie, avec ou sans l'en-tête de quatre octets (version et drapeaux).
         *
         * Les deux formes circulent : media3 transmet tantôt la boîte complète, tantôt sa seule
         * charge utile. Accepter les deux évite un décodeur qui marche ici et pas là.
         */
        fun lire(cookie: ByteArray): ConfigAlac? {
            val debut = when {
                cookie.size >= 28 -> 4
                cookie.size >= 24 -> 0
                else -> return null
            }
            fun octet(i: Int) = cookie[debut + i].toInt() and 0xFF
            fun entier16(i: Int) = (octet(i) shl 8) or octet(i + 1)
            fun entier32(i: Int) =
                (octet(i) shl 24) or (octet(i + 1) shl 16) or (octet(i + 2) shl 8) or octet(i + 3)

            val frameLength = entier32(0)
            val bitDepth = octet(5)
            if (frameLength <= 0 || frameLength > 1 shl 20) return null
            if (bitDepth !in intArrayOf(16, 20, 24, 32)) return null

            return ConfigAlac(
                frameLength = frameLength,
                bitDepth = bitDepth,
                pb = octet(6),
                mb = octet(7),
                kb = octet(8),
                canaux = octet(9),
                maxRun = entier16(10),
                maxFrameBytes = entier32(12),
                debitMoyen = entier32(16),
                echantillonnage = entier32(20)
            )
        }
    }
}

/**
 * Lecture bit à bit, à la manière du décodeur de référence.
 *
 * `regarder32` rend toujours 32 bits sans consommer : les codes de Rice se lisent en regardant
 * devant soi, puis en avançant du nombre de bits réellement utilisés.
 */
internal class LecteurBits(private val donnees: ByteArray, private val longueur: Int) {

    /** Position courante, **en bits**. */
    var position: Int = 0

    fun regarder32(): Int {
        val octet = position ushr 3
        val decalage = position and 7
        var valeur = 0L
        for (i in 0 until 5) {
            val indice = octet + i
            val b = if (indice < longueur) (donnees[indice].toLong() and 0xFF) else 0L
            valeur = (valeur shl 8) or b
        }
        return ((valeur ushr (8 - decalage)) and 0xFFFFFFFFL).toInt()
    }

    fun lire(nombre: Int): Int {
        if (nombre == 0) return 0
        val valeur = regarder32()
        position += nombre
        return if (nombre == 32) valeur else valeur ushr (32 - nombre)
    }

    fun avancer(nombre: Int) {
        position += nombre
    }
}

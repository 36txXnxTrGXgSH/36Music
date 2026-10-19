package com.music36.app

import com.music36.app.lecture.doitReprendre
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La reprise de la session précédente ne doit jamais passer devant un choix de l'utilisateur.
 *
 * ⚠️ Le défaut de la 2.0 : la reprise attend que la bibliothèque soit chargée, et l'utilisateur
 * est souvent plus rapide. Sans ce garde-fou, le disque d'hier — un genre, un dossier — revenait
 * écraser la file qu'il venait de lancer, quelques secondes après l'avoir choisie.
 */
class RepriseSessionTest {

    @Test
    fun `au demarrage, la session precedente est reprise`() {
        assertTrue(
            doitReprendre(
                repriseDemandee = true,
                fileMemorisee = "content://a\ncontent://b",
                choixUtilisateur = false,
                elementsDansLaFile = 0
            )
        )
    }

    @Test
    fun `un morceau lance par l'utilisateur annule la reprise`() {
        assertFalse(
            doitReprendre(
                repriseDemandee = true,
                fileMemorisee = "content://a",
                choixUtilisateur = true,
                elementsDansLaFile = 0
            )
        )
    }

    @Test
    fun `une file deja remplie annule la reprise`() {
        assertFalse(
            doitReprendre(
                repriseDemandee = true,
                fileMemorisee = "content://a",
                choixUtilisateur = false,
                elementsDansLaFile = 12
            )
        )
    }

    @Test
    fun `sans souvenir ou sans reglage, il n'y a rien a reprendre`() {
        assertFalse(doitReprendre(true, "", false, 0))
        assertFalse(doitReprendre(false, "content://a", false, 0))
    }
}

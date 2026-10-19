package com.music36.app

import androidx.compose.ui.geometry.Offset
import com.music36.app.ui.retro.PAS_ANGLE
import com.music36.app.ui.retro.ZoneMolette
import com.music36.app.ui.retro.zoneDe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** La géométrie de la molette : quel quartier répond à quel appui. */
class MoletteTest {

    private val taille = 300f
    private val centre = taille / 2f

    private fun zone(x: Float, y: Float) = zoneDe(Offset(x, y), taille, taille)

    @Test
    fun `les quatre quartiers repondent au bon endroit`() {
        assertEquals(ZoneMolette.MENU, zone(centre, 10f))
        assertEquals(ZoneMolette.LECTURE, zone(centre, taille - 10f))
        assertEquals(ZoneMolette.PRECEDENT, zone(10f, centre))
        assertEquals(ZoneMolette.SUIVANT, zone(taille - 10f, centre))
    }

    @Test
    fun `le bouton central est distinct de l'anneau`() {
        assertEquals(ZoneMolette.CENTRE, zone(centre, centre))
        assertEquals(ZoneMolette.CENTRE, zone(centre, centre - 30f))
        // Juste au-delà du disque central, on retombe sur l'anneau.
        assertEquals(ZoneMolette.MENU, zone(centre, centre - 70f))
    }

    @Test
    fun `un appui dans les coins du carre ne declenche rien`() {
        assertNull(zone(0f, 0f))
        assertNull(zone(taille, taille))
    }

    @Test
    fun `un tour complet fait vingt-quatre crans`() {
        val crans = (2 * Math.PI / PAS_ANGLE)
        assertTrue("Un tour doit donner 24 crans, obtenu $crans", Math.abs(crans - 24.0) < 0.001)
    }
}

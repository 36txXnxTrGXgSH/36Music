package com.music36.app

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.music36.app.ui.retro.Molette
import com.music36.app.ui.retro.ZoneMolette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * La molette elle-même : un vrai doigt qui tourne produit-il des crans ?
 *
 * Le composant est monté seul, sans l'écran de la vue Rétro : quand la molette ne répond plus, ce test dit
 * tout de suite si le défaut vient du geste ou du menu qui l'écoute.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MoletteGestesTest {

    @get:Rule
    val regle = createComposeRule()

    private var crans = 0
    private val zones = mutableListOf<ZoneMolette>()

    private fun monter() {
        regle.setContent {
            Molette(
                enLecture = false,
                onCran = { crans += it },
                onZone = { zones += it },
                modifier = Modifier
                    .testTag("molette")
                    .size(300.dp)
            )
        }
    }

    /** Glisse le doigt sur l'anneau, de [depuis] à [vers] degrés (0 = à droite, sens horaire). */
    private fun tourner(depuis: Float, vers: Float) {
        regle.onNodeWithTag("molette").performTouchInput {
            val rayon = (width.coerceAtMost(height) / 2f) * 0.72f
            swipe(
                curve = { t ->
                    val avancement = (t / 400f).coerceIn(0f, 1f)
                    val angle = Math.toRadians(
                        (depuis + (vers - depuis) * avancement).toDouble()
                    )
                    Offset(
                        center.x + rayon * kotlin.math.cos(angle).toFloat(),
                        center.y + rayon * kotlin.math.sin(angle).toFloat()
                    )
                },
                durationMillis = 400
            )
        }
        regle.waitForIdle()
    }

    @Test
    fun `un quart de tour horaire emet six crans positifs`() {
        monter()
        tourner(depuis = -90f, vers = 0f)
        assertEquals("Un quart de tour vaut six crans de 15°", 6, crans)
    }

    @Test
    fun `tourner en sens inverse rend des crans negatifs`() {
        monter()
        tourner(depuis = 0f, vers = -90f)
        assertTrue("Attendu des crans négatifs, obtenu $crans", crans <= -5)
    }

    @Test
    fun `une rotation ne declenche aucun appui`() {
        monter()
        tourner(depuis = -90f, vers = 0f)
        assertTrue("Une rotation ne doit pas valider : $zones", zones.isEmpty())
    }

    @Test
    fun `un appui au centre ne fait pas tourner`() {
        monter()
        regle.onNodeWithTag("molette").performTouchInput { click(center) }
        regle.waitForIdle()
        assertEquals(listOf(ZoneMolette.CENTRE), zones)
        assertEquals(0, crans)
    }

    @Test
    fun `un glissement parti du bouton central ne fait pas tourner`() {
        monter()
        regle.onNodeWithTag("molette").performTouchInput {
            swipe(center, Offset(center.x + width * 0.3f, center.y), durationMillis = 300)
        }
        regle.waitForIdle()
        assertEquals(0, crans)
    }
}

package com.music36.app

import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import com.music36.app.data.StyleInterface
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Une installation neuve s'ouvre sur la vue Rétro, même quand Android a restauré une sauvegarde
 * où l'interface moderne avait été choisie ; ensuite, le choix de chacun est respecté.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@UnstableApi
class PremiereOuvertureTest {

    private val app = ApplicationProvider.getApplicationContext<Music36App>()
    private val temoin = File(app.noBackupFilesDir, Music36App.TEMOIN_PREMIERE_OUVERTURE)

    private fun style() = runBlocking { app.reglages.flux.first().style }

    private fun choisir(style: StyleInterface) = runBlocking {
        app.reglages.modifier { it.copy(style = style) }
    }

    @Test
    fun `la premiere ouverture laisse un temoin et la vue Retro`() {
        assertTrue("le témoin doit être posé dès le démarrage", temoin.exists())
        assertEquals(StyleInterface.RETRO, style())
    }

    @Test
    fun `une sauvegarde restauree en interface moderne ne l'emporte pas`() {
        // Ce que fait Android à la réinstallation : les réglages reviennent, pas le témoin.
        choisir(StyleInterface.MODERNE)
        temoin.delete()
        app.imposerLaVueRetroALaPremiereOuverture()
        assertEquals(StyleInterface.RETRO, style())
        assertTrue(temoin.exists())
    }

    @Test
    fun `ensuite, le choix de l'interface moderne est garde`() {
        choisir(StyleInterface.MODERNE)
        app.imposerLaVueRetroALaPremiereOuverture()
        assertEquals(StyleInterface.MODERNE, style())
    }
}

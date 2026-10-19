package com.music36.app

import androidx.media3.common.util.UnstableApi
import com.music36.app.lecture.ActionEchec
import com.music36.app.lecture.actionApresEchec
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Ce qui arrive à la file quand un fichier refuse de se lire.
 *
 * Une erreur laisse ExoPlayer en `STATE_IDLE` : la file reste affichée, mais plus rien ne démarre
 * tant que personne ne rappelle `prepare()`. Sans la reprise, un seul fichier abîmé condamnait
 * donc tous ceux qui le suivaient.
 */
@UnstableApi
class RepriseApresEchecTest {

    @Test
    fun `un fichier illisible en milieu de file passe au suivant`() {
        assertEquals(ActionEchec.SAUTER, actionApresEchec(echecsDeSuite = 1, aUnSuivant = true))
    }

    @Test
    fun `le dernier morceau repose le lecteur au lieu de s'acharner`() {
        assertEquals(ActionEchec.REPOSER, actionApresEchec(echecsDeSuite = 1, aUnSuivant = false))
    }

    @Test
    fun `une file entierement illisible ne fait pas devaler la bibliotheque`() {
        assertEquals(ActionEchec.SAUTER, actionApresEchec(3, aUnSuivant = true))
        assertEquals(ActionEchec.ABANDONNER, actionApresEchec(4, aUnSuivant = true))
        assertEquals(ActionEchec.ABANDONNER, actionApresEchec(9, aUnSuivant = false))
    }
}

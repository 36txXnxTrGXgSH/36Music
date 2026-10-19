package com.music36.app

import com.music36.app.data.ResultatAjout
import com.music36.app.data.messageAjout
import com.music36.app.data.messageAppartenance
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Ce que l'application dit après un ajout à une playlist, et quand on lui demande où est rangé
 * un titre.
 *
 * ⚠️ Le cas qui compte est le doublon. Ajouter deux fois le même morceau à une playlist n'a pas
 * de sens — mais l'ignorer **en silence** laisse croire que l'ajout a échoué, et l'utilisateur
 * recommence.
 */
class PlaylistMessagesTest {

    @Test
    fun `un titre deja present est annonce, pas ignore`() {
        assertEquals(
            "Déjà dans « Route »",
            messageAjout(ResultatAjout(ajoutees = 0, deja = 1), "Route")
        )
    }

    @Test
    fun `un ajout simple reste discret`() {
        assertEquals(
            "Ajouté à « Route »",
            messageAjout(ResultatAjout(ajoutees = 1, deja = 0), "Route")
        )
    }

    @Test
    fun `un lot dit ce qui est entre et ce qui etait deja la`() {
        assertEquals(
            "12 titres ajoutés à « Route »",
            messageAjout(ResultatAjout(ajoutees = 12, deja = 0), "Route")
        )
        assertEquals(
            "Ces 4 titres sont déjà dans « Route »",
            messageAjout(ResultatAjout(ajoutees = 0, deja = 4), "Route")
        )
        assertEquals(
            "3 ajoutés à « Route », 1 était déjà là",
            messageAjout(ResultatAjout(ajoutees = 3, deja = 1), "Route")
        )
        assertEquals(
            "1 ajouté à « Route », 5 étaient déjà là",
            messageAjout(ResultatAjout(ajoutees = 1, deja = 5), "Route")
        )
    }

    @Test
    fun `l'icone repond aussi quand le titre est bien range`() {
        // Sans cette réponse, appuyer sur l'icône d'un titre déjà rangé ne donnerait rien.
        assertEquals(
            "Ce titre n'appartient à aucune playlist",
            messageAppartenance(emptyList())
        )
        assertEquals("Dans « Route »", messageAppartenance(listOf("Route")))
        assertEquals(
            "Dans « Route », « Soir »",
            messageAppartenance(listOf("Route", "Soir"))
        )
    }

    @Test
    fun `au-dela de trois playlists, on compte au lieu de tout enumerer`() {
        assertEquals(
            "Dans 5 playlists : « A », « B »…",
            messageAppartenance(listOf("A", "B", "C", "D", "E"))
        )
    }
}

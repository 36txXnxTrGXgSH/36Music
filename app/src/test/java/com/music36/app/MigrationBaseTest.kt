package com.music36.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.music36.app.data.BaseMusique
import com.music36.app.data.MIGRATIONS
import com.music36.app.data.Playlist
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Les migrations de la base.
 *
 * ⚠️ L'application employait `fallbackToDestructiveMigration` : la moindre montée de version
 * effaçait **playlists, favoris et compteurs d'écoute** — les seules données qu'aucune réanalyse
 * ne reconstruit. Le filet est désormais ici : une version sans sa migration fait échouer les
 * tests, pas la bibliothèque de l'utilisateur.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationBaseTest {

    private lateinit var base: BaseMusique

    @Before
    fun ouvrir() {
        base = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BaseMusique::class.java
        ).addMigrations(*MIGRATIONS).allowMainThreadQueries().build()
    }

    @After
    fun fermer() = base.close()

    @Test
    fun `chaque version a sa migration`() {
        // La version N exige N-1 migrations : 1 vers 2, 2 vers 3, et ainsi de suite.
        val version = base.openHelper.readableDatabase.version
        assertEquals(
            "version $version : il manque une migration dans MIGRATIONS",
            version - 1, MIGRATIONS.size
        )
    }

    @Test
    fun `les migrations s'enchainent sans trou`() {
        MIGRATIONS.sortedBy { it.startVersion }.forEachIndexed { index, migration ->
            assertEquals("migration ${index + 1} mal chaînée", index + 1, migration.startVersion)
            assertEquals("migration ${index + 1} mal chaînée", index + 2, migration.endVersion)
        }
    }

    @Test
    fun `l'empreinte d'une playlist se conserve`() = runBlocking {
        // C'est elle qui empêche l'analyse d'écraser une playlist modifiée dans l'application.
        val id = base.playlists().creer(
            Playlist(nom = "Route", dateCreation = 1, source = "content://x", empreinte = "10:20")
        )
        assertEquals("10:20", base.playlists().parId(id)?.empreinte)

        base.playlists().marquerEmpreinte(id, null)
        assertNull(base.playlists().parId(id)?.empreinte)
    }
}

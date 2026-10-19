package com.music36.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Dao
interface PisteDao {
    @Query("SELECT * FROM piste")
    fun toutes(): Flow<List<Piste>>

    @Query("SELECT * FROM piste")
    suspend fun toutesMaintenant(): List<Piste>

    @Query("SELECT uri, dateFichier, taille, dateAjout FROM piste WHERE racine = :racine")
    suspend fun empreintes(racine: String): List<Empreinte>

    @Query("SELECT * FROM piste WHERE uri = :uri")
    suspend fun parUri(uri: String): Piste?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserer(pistes: List<Piste>)

    @Query("DELETE FROM piste WHERE uri IN (:uris)")
    suspend fun supprimer(uris: List<String>)

    @Query("DELETE FROM piste WHERE racine = :racine")
    suspend fun viderRacine(racine: String)

    @Query("SELECT COUNT(*) FROM piste")
    suspend fun compte(): Int
}

/** Ce qu'il faut pour décider si un fichier déjà connu doit être relu. */
data class Empreinte(
    val uri: String,
    val dateFichier: Long,
    val taille: Long,
    val dateAjout: Long
)

@Dao
interface RacineDao {
    @Query("SELECT * FROM racine ORDER BY dateAjout")
    fun toutes(): Flow<List<Racine>>

    @Query("SELECT * FROM racine ORDER BY dateAjout")
    suspend fun toutesMaintenant(): List<Racine>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun ajouter(racine: Racine)

    @Delete
    suspend fun supprimer(racine: Racine)
}

@Dao
interface FavoriDao {
    @Query("SELECT uri FROM favori")
    fun uris(): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM favori WHERE uri = :uri)")
    suspend fun estFavori(uri: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun ajouter(favori: Favori)

    @Query("DELETE FROM favori WHERE uri = :uri")
    suspend fun retirer(uri: String)
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlist ORDER BY nom COLLATE NOCASE")
    fun toutes(): Flow<List<Playlist>>

    @Query("SELECT * FROM playlist ORDER BY nom COLLATE NOCASE")
    suspend fun toutesMaintenant(): List<Playlist>

    @Query("SELECT * FROM playlist_piste ORDER BY playlistId, position")
    fun tousLesLiens(): Flow<List<PlaylistPiste>>

    /** Les liens à l'instant présent : le ménage des doublons compte, il n'observe pas. */
    @Query("SELECT * FROM playlist_piste")
    suspend fun tousLesLiensMaintenant(): List<PlaylistPiste>

    @Query("SELECT * FROM playlist_piste WHERE playlistId = :id ORDER BY position")
    suspend fun liens(id: Long): List<PlaylistPiste>

    @Query("SELECT * FROM playlist WHERE source = :source LIMIT 1")
    suspend fun parSource(source: String): Playlist?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun creer(playlist: Playlist): Long

    @Query("UPDATE playlist SET nom = :nom WHERE id = :id")
    suspend fun renommer(id: Long, nom: String)

    @Query("UPDATE playlist SET empreinte = :empreinte WHERE id = :id")
    suspend fun marquerEmpreinte(id: Long, empreinte: String?)

    /** Le fichier d'origine a changé de nom : la playlist doit suivre, sinon elle le perd. */
    @Query("UPDATE playlist SET source = :source, empreinte = :empreinte WHERE id = :id")
    suspend fun marquerSource(id: Long, source: String, empreinte: String?)

    @Query("SELECT * FROM playlist WHERE id = :id")
    suspend fun parId(id: Long): Playlist?

    @Query("DELETE FROM playlist WHERE id = :id")
    suspend fun supprimer(id: Long)

    @Query("DELETE FROM playlist_piste WHERE playlistId = :id")
    suspend fun viderContenu(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insererLiens(liens: List<PlaylistPiste>)

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_piste WHERE playlistId = :id")
    suspend fun dernierePosition(id: Long): Int

    /** Réécrit le contenu d'une playlist d'un bloc : les positions restent ainsi continues. */
    @Transaction
    suspend fun remplacerContenu(id: Long, uris: List<String>) {
        viderContenu(id)
        insererLiens(uris.mapIndexed { index, uri -> PlaylistPiste(id, uri, index) })
    }

    @Transaction
    suspend fun ajouterAuBout(id: Long, uris: List<String>) {
        var position = dernierePosition(id) + 1
        insererLiens(uris.map { PlaylistPiste(id, it, position++) })
    }
}

@Dao
interface StatistiqueDao {
    @Query("DELETE FROM statistique")
    suspend fun toutEffacer()

    @Query("SELECT * FROM statistique")
    fun toutes(): Flow<List<Statistique>>

    @Query("SELECT * FROM statistique WHERE uri = :uri")
    suspend fun parUri(uri: String): Statistique?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enregistrer(statistique: Statistique)

    @Transaction
    suspend fun compterUneLecture(uri: String, quand: Long) {
        val ancienne = parUri(uri)
        enregistrer(
            Statistique(
                uri = uri,
                lectures = (ancienne?.lectures ?: 0) + 1,
                derniereLecture = quand,
                reprise = 0
            )
        )
    }

    @Transaction
    suspend fun enregistrerReprise(uri: String, position: Long) {
        val ancienne = parUri(uri)
        enregistrer(
            Statistique(
                uri = uri,
                lectures = ancienne?.lectures ?: 0,
                derniereLecture = ancienne?.derniereLecture ?: 0,
                reprise = position
            )
        )
    }
}

@Database(
    entities = [Piste::class, Racine::class, Favori::class, Playlist::class,
        PlaylistPiste::class, Statistique::class],
    version = 2,
    exportSchema = false
)
abstract class BaseMusique : RoomDatabase() {
    abstract fun pistes(): PisteDao
    abstract fun racines(): RacineDao
    abstract fun favoris(): FavoriDao
    abstract fun playlists(): PlaylistDao
    abstract fun statistiques(): StatistiqueDao

    companion object {
        /**
         * Ouvre la base. L'unicité est assurée par [com.music36.app.Music36App], qui la garde en
         * `lazy` : pas de singleton statique ici, sinon deux exécutions de test se partageraient
         * une base déjà fermée.
         */
        fun ouvrir(contexte: Context): BaseMusique = Room.databaseBuilder(
            contexte.applicationContext,
            BaseMusique::class.java,
            "36music.db"
        ).addMigrations(*MIGRATIONS).build()
    }
}

/**
 * Les migrations de la base, dans l'ordre.
 *
 * ⚠️ Il n'y a **plus** de `fallbackToDestructiveMigration` : elle effaçait sans prévenir les
 * playlists, les favoris et les compteurs d'écoute — les seules données que l'application ne sait
 * pas reconstruire en réanalysant. Toute montée de version ajoute donc sa migration ici, et
 * `BaseMigrationTest` refuse de compiler si l'on oublie.
 */
val MIGRATIONS: Array<Migration> = arrayOf(
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE playlist ADD COLUMN empreinte TEXT")
        }
    }
)

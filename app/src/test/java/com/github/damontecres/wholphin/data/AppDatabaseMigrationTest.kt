package com.github.damontecres.wholphin.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.github.damontecres.wholphin.data.model.ActivationFlag
import com.github.damontecres.wholphin.data.model.SeriesTrackChoice
import com.github.damontecres.wholphin.data.model.SeriesTrackChoiceType
import com.github.damontecres.wholphin.data.model.TrackChoiceParentType
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.util.UUID

/** Real Room upgrades over exported schema fixtures and Robolectric native SQLite; no live data. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [30])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class AppDatabaseMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val opened = mutableListOf<AppDatabase>()
    private val names = mutableListOf<String>()
    private val serverId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val seriesId = UUID.fromString("00000000-0000-0000-0000-000000000003")
    private val itemId = UUID.fromString("00000000-0000-0000-0000-000000000004")
    private val seasonId = UUID.fromString("00000000-0000-0000-0000-000000000005")

    private fun UUID.key() = toString().replace("-", "")

    @After
    fun close() {
        opened.forEach(AppDatabase::close)
        names.forEach(context::deleteDatabase)
    }

    private fun open(name: String): AppDatabase =
        Room
            .databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(Migrations.Migrate2to3)
            .allowMainThreadQueries()
            .build()
            .also {
                opened.add(it)
                it.openHelper.writableDatabase
            }

    private fun fixture(
        version: Int,
        seed: (SupportSQLiteDatabase) -> Unit = {},
    ): String {
        val name = "stage-c-$version-${names.size}"
        names.add(name)
        // MigrationTestHelper loads instrumentation APK assets, which are not available to JVM
        // tests. Load the same exported Room fixtures from test resources, then open the result
        // through the real AppDatabase builder so Room runs and validates its actual migrations.
        val schema =
            javaClass.classLoader!!
                .getResourceAsStream("${AppDatabase::class.java.name}/$version.json")!!
                .bufferedReader()
                .use {
                    Json
                        .parseToJsonElement(it.readText())
                        .jsonObject
                        .getValue("database")
                        .jsonObject
                }
        val config =
            SupportSQLiteOpenHelper.Configuration
                .builder(context)
                .name(name)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(version) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            for (entity in schema.getValue("entities").jsonArray) {
                                val fields = entity.jsonObject
                                val table = fields.getValue("tableName").jsonPrimitive.content
                                db.execSQL(
                                    fields
                                        .getValue("createSql")
                                        .jsonPrimitive.content
                                        .replace("\${TABLE_NAME}", table),
                                )
                                for (index in fields["indices"]?.jsonArray.orEmpty()) {
                                    db.execSQL(
                                        index.jsonObject
                                            .getValue("createSql")
                                            .jsonPrimitive.content
                                            .replace("\${TABLE_NAME}", table),
                                    )
                                }
                            }
                            schema.getValue("setupQueries").jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
                        }

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) = error("Fixture creation only")
                    },
                ).build()
        FrameworkSQLiteOpenHelperFactory().create(config).use { seed(it.writableDatabase) }
        return name
    }

    private fun seedIdentity(
        db: SupportSQLiteDatabase,
        version: Int = 36,
    ) {
        val server = if (version == 2) serverId.toString() else serverId.key()
        val user = if (version == 2) userId.toString() else userId.key()
        db.execSQL("INSERT INTO servers (id, name, url) VALUES (?, 'fixture', 'http://jellyfin.invalid')", arrayOf(server))
        db.execSQL(
            "INSERT INTO users (id, name, serverId, accessToken) VALUES (?, 'viewer', ?, 'synthetic-token')",
            arrayOf(user, server),
        )
    }

    private fun seedPlayback(db: SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT INTO ItemPlayback (rowId, userId, itemId, sourceId, audioIndex, subtitleIndex) VALUES (9, 1, ?, ?, 2, -2)",
            arrayOf(itemId.key(), seasonId.key()),
        )
        db.execSQL("INSERT INTO ItemTrackModification VALUES (1, ?, 3, 1500)", arrayOf(itemId.key()))
        db.execSQL(
            "INSERT INTO PlaybackLanguageChoice VALUES (1, ?, ?, 'jpn', 'eng', 0)",
            arrayOf(seriesId.key(), itemId.key()),
        )
        db.execSQL("INSERT INTO RememberedTab VALUES (1, 'library', 2)")
        db.execSQL("INSERT INTO NavDrawerPinnedItem VALUES (1, 'library', 'MOVIE', 1)")
        db.execSQL("INSERT INTO LibraryDisplayInfo VALUES (1, 'library', 'SortName', 'Ascending', '{}', NULL)")
        db.execSQL("INSERT INTO playback_effects VALUES (1, 'item', 'VIDEO', 0, 1, 2, 3, 4, 5, 6, 7, 8)")
        db.execSQL("INSERT INTO seerr_servers VALUES (1, 'http://seerr.invalid', 'fixture', '1.0.42')")
        db.execSQL("INSERT INTO seerr_users VALUES (1, 1, 'JELLYFIN', 'viewer', NULL, 'synthetic-credential')")
    }

    private fun rows(
        db: SupportSQLiteDatabase,
        query: String,
    ): List<List<String?>> =
        db.query(query).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add((0 until cursor.columnCount).map { if (cursor.isNull(it)) null else cursor.getString(it) })
                }
            }
        }

    private fun assertHealthy(db: SupportSQLiteDatabase) {
        assertEquals(38, db.version)
        assertEquals(listOf(listOf("ok")), rows(db, "PRAGMA integrity_check"))
        assertTrue(rows(db, "PRAGMA foreign_key_check").isEmpty())
    }

    @Test
    fun `fresh install creates both integrity and track persistence`() {
        val name = "stage-c-fresh".also(names::add)
        val db = open(name).openHelper.writableDatabase
        assertHealthy(db)
        val tables = rows(db, "SELECT name FROM sqlite_master WHERE type='table'").flatten()
        assertTrue("season_integrity_expectations" in tables)
        assertTrue("SeriesTrackChoice" in tables)
        assertTrue("PlaybackLanguageChoice" in tables)
    }

    @Test
    fun `latest released schema preserves every existing table and converts choices`() =
        runTest {
            val before = mutableMapOf<String, List<List<String?>>>()
            val name =
                fixture(36) { db ->
                    seedIdentity(db)
                    seedPlayback(db)
                    db.execSQL(
                        "UPDATE users SET pin='3210', requireLogin=1, uiLanguage='ro', preferredAudioLanguage='jpn', preferredSubtitleLanguage='eng', subtitleMode='SMART'",
                    )
                    db.execSQL(
                        "INSERT INTO season_integrity_expectations VALUES (1, ?, 2, 123, '1,2,4', 1700000000000)",
                        arrayOf(seriesId.key()),
                    )
                    rows(
                        db,
                        "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name != 'room_master_table'",
                    ).flatten()
                        .filterNotNull()
                        .forEach { before[it] = rows(db, "SELECT * FROM `$it`") }
                }
            val room = open(name)
            val db = room.openHelper.writableDatabase
            assertHealthy(db)
            before.forEach { (table, values) ->
                val expected = if (table == "users") values.map { it + "UNSET" } else values
                assertEquals(table, expected, rows(db, "SELECT * FROM `$table`"))
            }
            val expected = room.seasonIntegrityDao().getSeries(1, seriesId).single()
            assertEquals(setOf(1, 2, 4), expected.expectedEpisodeNumbers)
            assertEquals(1700000000000L, expected.lastUpdatedEpochMillis)
            val playback = room.itemPlaybackDao().getItem(1, itemId)!!
            assertEquals(9L, playback.rowId)
            assertEquals(2, playback.audioIndex)
            assertEquals(-2, playback.subtitleIndex)
            assertEquals(1500L, room.itemPlaybackDao().getTrackModifications(1, itemId, 3)!!.delayMs)
            val choices = room.seriesTrackChoiceDao().getAll(1)
            assertEquals(2, choices.size)
            assertEquals("jpn", choices.single { it.type == SeriesTrackChoiceType.AUDIO }.language)
            assertEquals("eng", choices.single { it.type == SeriesTrackChoiceType.SUBTITLE }.language)
            assertTrue(choices.all { it.parentType == TrackChoiceParentType.DEPRECATED && it.itemId == itemId })
        }

    @Test
    fun `every supported historical schema upgrades without losing server or user identity`() {
        for (version in listOf(2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 20, 30, 31, 32, 33, 34, 35, 36, 37)) {
            val name = fixture(version) { seedIdentity(it, version) }
            val db = open(name).openHelper.writableDatabase
            assertHealthy(db)
            assertEquals(listOf(listOf("UNSET")), rows(db, "SELECT c_homeSettingsSource FROM users"))
            assertEquals(
                listOf(listOf(serverId.key(), "fixture", "http://jellyfin.invalid")),
                rows(db, "SELECT id, name, url FROM servers"),
            )
            assertEquals(
                listOf(listOf("1", userId.key(), serverId.key(), "synthetic-token")),
                rows(db, "SELECT rowId, id, serverId, accessToken FROM users"),
            )
        }
    }

    @Test
    fun `schema 37 adds only Home source while preserving integrity tracks credentials and preferences`() =
        runTest {
            val name =
                fixture(37) { db ->
                    seedIdentity(db)
                    seedPlayback(db)
                    db.execSQL("UPDATE users SET pin='3210', requireLogin=1, uiLanguage='ro'")
                    db.execSQL(
                        "INSERT INTO season_integrity_expectations VALUES (1, ?, 2, 123, '1,2,4', 1700000000000)",
                        arrayOf(seriesId.key()),
                    )
                    db.execSQL(
                        "INSERT INTO SeriesTrackChoice (userId, parentId, type, parentType, language, activation, trackFlags) VALUES (1, ?, 'AUDIO', 'SERIES', 'jpn', 'ACTIVATED', 0)",
                        arrayOf(seriesId.key()),
                    )
                }
            val room = open(name)
            val db = room.openHelper.writableDatabase
            assertHealthy(db)
            assertEquals(
                listOf(listOf("UNSET", "3210", "1", "ro", "synthetic-token")),
                rows(db, "SELECT c_homeSettingsSource, pin, requireLogin, uiLanguage, accessToken FROM users"),
            )
            assertEquals(
                setOf(1, 2, 4),
                room
                    .seasonIntegrityDao()
                    .getSeries(1, seriesId)
                    .single()
                    .expectedEpisodeNumbers,
            )
            assertEquals(
                "jpn",
                room
                    .seriesTrackChoiceDao()
                    .getAll(1)
                    .single()
                    .language,
            )
            assertEquals(listOf(listOf("synthetic-credential")), rows(db, "SELECT credential FROM seerr_users"))
            db.execSQL("UPDATE users SET c_homeSettingsSource='LOCAL' WHERE rowId=1")
            assertEquals(listOf(listOf("LOCAL")), rows(db, "SELECT c_homeSettingsSource FROM users"))
        }

    @Test
    fun `schema 35 creates integrity cache and migrates legacy playback choices`() =
        runTest {
            val name =
                fixture(35) {
                    seedIdentity(it)
                    seedPlayback(it)
                }
            val db = open(name)
            assertHealthy(db.openHelper.writableDatabase)
            assertTrue(db.seasonIntegrityDao().getSeries(1, seriesId).isEmpty())
            assertEquals(2, db.seriesTrackChoiceDao().count())
            assertEquals(1, db.itemPlaybackDao().getItems(1).size)
        }

    @Test
    fun `nullable legacy settings preserve disabled subtitles without inventing choices`() =
        runTest {
            val name =
                fixture(36) { db ->
                    seedIdentity(db)
                    db.execSQL("INSERT INTO PlaybackLanguageChoice VALUES (1, ?, NULL, NULL, NULL, 1)", arrayOf(seriesId.key()))
                    db.execSQL("INSERT INTO PlaybackLanguageChoice VALUES (1, ?, NULL, NULL, NULL, NULL)", arrayOf(seasonId.key()))
                    db.execSQL("INSERT INTO PlaybackLanguageChoice VALUES (1, ?, NULL, '', '   ', NULL)", arrayOf(itemId.key()))
                }
            val room = open(name)
            val choice = room.seriesTrackChoiceDao().getAll(1).single()
            assertEquals(SeriesTrackChoiceType.SUBTITLE, choice.type)
            assertEquals(ActivationFlag.DISABLED, choice.activation)
            assertEquals(null, choice.language)
            assertEquals(null, choice.itemId)
            assertHealthy(room.openHelper.writableDatabase)
        }

    @Test
    fun `conversion repeats safely and never overwrites a new selection`() =
        runTest {
            val name =
                fixture(36) {
                    seedIdentity(it)
                    seedPlayback(it)
                }
            val room = open(name)
            val dao = room.seriesTrackChoiceDao()
            val newer =
                dao.getAll(1).first { it.type == SeriesTrackChoiceType.SUBTITLE }.copy(
                    activation = ActivationFlag.ONLY_FORCED,
                    language = "fra",
                    parentType = TrackChoiceParentType.SERIES,
                )
            dao.save(newer)
            repeat(2) { Migrations.Migrate36to37().onPostMigrate(room.openHelper.writableDatabase) }
            assertEquals(2, dao.count())
            assertEquals(newer, dao.getBySeriesId(1, seriesId, SeriesTrackChoiceType.SUBTITLE).single())
            assertEquals(1, rows(room.openHelper.writableDatabase, "SELECT * FROM PlaybackLanguageChoice").size)
        }

    @Test
    fun `new DAO round trips metadata and prioritizes seasons within the owning user`() =
        runTest {
            val name =
                fixture(36) { db ->
                    seedIdentity(db)
                    db.execSQL("INSERT INTO users (rowId, id, serverId) VALUES (2, ?, ?)", arrayOf(UUID.randomUUID().key(), serverId.key()))
                }
            val room = open(name)
            val dao = room.seriesTrackChoiceDao()
            val series = SeriesTrackChoice(1, seriesId, SeriesTrackChoiceType.AUDIO, TrackChoiceParentType.SERIES, language = "eng")
            val season =
                SeriesTrackChoice(
                    1,
                    seasonId,
                    SeriesTrackChoiceType.AUDIO,
                    TrackChoiceParentType.SEASON,
                    itemId,
                    "jpn",
                    ActivationFlag.ACTIVATED,
                    9,
                    "aac",
                    1,
                    "Main",
                    6,
                )
            dao.save(listOf(series, season, series.copy(userId = 2, language = "spa")))
            assertEquals(listOf(season, series), dao.get(1, seasonId, seriesId, SeriesTrackChoiceType.AUDIO))
            assertEquals("spa", dao.getBySeriesId(2, seriesId, SeriesTrackChoiceType.AUDIO).single().language)
            assertTrue(dao.getBySeriesId(1, seriesId, SeriesTrackChoiceType.SUBTITLE).isEmpty())
            dao.delete(listOf(season))
            assertEquals(listOf(series), dao.get(1, seasonId, seriesId, SeriesTrackChoiceType.AUDIO))
            assertFalse(dao.getAll(2).isEmpty())
            room.openHelper.writableDatabase.execSQL("DELETE FROM users WHERE rowId=1")
            assertTrue(dao.getAll(1).isEmpty())
            assertEquals(1, dao.getAll(2).size)
            assertHealthy(room.openHelper.writableDatabase)
        }
}

package pl.brulion.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase
import pl.brulion.data.sync.SyncSection

@Database(
    entities = [
        AccountEntity::class,
        ProfileEntity::class,
        GradeEntity::class,
        AnnouncementEntity::class,
        AttendanceEntity::class,
        EventEntity::class,
        NoteEntity::class,
        LuckyNumberEntity::class,
        TimetableLessonEntity::class,
        MessageEntity::class,
        MessageBodyEntity::class,
        MessageAttachmentEntity::class,
        FeedEntryEntity::class,
        SectionSyncEntity::class,
        HiddenAnnouncementEntity::class,
        CalendarLinkEntity::class,
    ],
    version = 11,
    autoMigrations = [
        AutoMigration(from = 1, to = 2, spec = BrulionDatabase.DropSynergiaLogin::class),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7, spec = BrulionDatabase.SectionSyncs::class),
        AutoMigration(from = 7, to = 8),
        AutoMigration(from = 8, to = 9),
        AutoMigration(from = 9, to = 10),
        AutoMigration(from = 10, to = 11),
    ],
)
@TypeConverters(Converters::class)
abstract class BrulionDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun profileDao(): ProfileDao
    abstract fun gradeDao(): GradeDao
    abstract fun announcementDao(): AnnouncementDao
    abstract fun attendanceDao(): AttendanceDao
    abstract fun eventDao(): EventDao
    abstract fun noteDao(): NoteDao
    abstract fun luckyNumberDao(): LuckyNumberDao
    abstract fun timetableDao(): TimetableDao
    abstract fun messageDao(): MessageDao
    abstract fun feedDao(): FeedDao
    abstract fun sectionSyncDao(): SectionSyncDao
    abstract fun hiddenAnnouncementDao(): HiddenAnnouncementDao
    abstract fun calendarLinkDao(): CalendarLinkDao

    /** Direct Synergia login was dropped: Librus rejects the password grant (unsupported_grant_type). */
    @DeleteColumn(tableName = "accounts", columnName = "type")
    @DeleteColumn(tableName = "accounts", columnName = "login")
    @DeleteColumn(tableName = "accounts", columnName = "password")
    @DeleteColumn(tableName = "profiles", columnName = "apiRefreshToken")
    class DropSynergiaLogin : AutoMigrationSpec

    /** Profiles synced before sections were tracked count every section as synced then. */
    class SectionSyncs : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            for (section in SyncSection.entries) {
                db.execSQL(
                    "INSERT OR IGNORE INTO section_syncs (profileId, section, lastOkAt) " +
                        "SELECT id, ?, lastSyncAt FROM profiles WHERE lastSyncAt > 0",
                    arrayOf(section.name),
                )
            }
        }
    }

    companion object {
        fun create(context: Context): BrulionDatabase =
            Room.databaseBuilder(context, BrulionDatabase::class.java, "brulion.db").build()
    }
}

package com.jlptstudy.app.data.study

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// 릴리스에서 fallbackToDestructiveMigration을 쓰지 않는다. 스키마를 바꾸면 Migration과 테스트를 함께 낸다.
@Database(
    entities = [CardStateEntity::class, ReviewLogEntity::class, UserSettingsEntity::class, QuizAttemptEntity::class, QuizAnswerEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class StudyDatabase : RoomDatabase() {
    abstract fun studyDao(): StudyDao

    companion object {
        const val FILE_NAME = "study.db"

        // 기본 onCorruption은 DB 파일을 지우고 빈 DB로 다시 연다. 기록을 잃지 않도록 아무것도 하지 않아 열기 실패(RecordDbFailed)로 간다.
        private object NoDeleteOnCorruptionFactory : SupportSQLiteOpenHelper.Factory {
            override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
                val inner = configuration.callback
                val callback = object : SupportSQLiteOpenHelper.Callback(inner.version) {
                    override fun onConfigure(db: SupportSQLiteDatabase) = inner.onConfigure(db)
                    override fun onCreate(db: SupportSQLiteDatabase) = inner.onCreate(db)
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                        inner.onUpgrade(db, oldVersion, newVersion)
                    override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                        inner.onDowngrade(db, oldVersion, newVersion)
                    override fun onOpen(db: SupportSQLiteDatabase) = inner.onOpen(db)
                    override fun onCorruption(db: SupportSQLiteDatabase) = Unit
                }
                return FrameworkSQLiteOpenHelperFactory().create(
                    SupportSQLiteOpenHelper.Configuration.builder(configuration.context)
                        .name(configuration.name).callback(callback)
                        .noBackupDirectory(configuration.useNoBackupDirectory)
                        .allowDataLossOnRecovery(configuration.allowDataLossOnRecovery).build(),
                )
            }
        }

        suspend fun open(context: Context): StudyDatabase = withContext(Dispatchers.IO) {
            val db = Room.databaseBuilder(context.applicationContext, StudyDatabase::class.java, FILE_NAME)
                .addMigrations(MIGRATION_1_2).openHelperFactory(NoDeleteOnCorruptionFactory).build()
            try {
                db.openHelper.writableDatabase
            } catch (e: Throwable) {
                db.close()
                throw e
            }
            db
        }
    }
}

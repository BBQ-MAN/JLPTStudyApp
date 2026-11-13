package com.jlptstudy.app.data.content

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Database(
    entities = [
        ContentMetaEntity::class, VocabEntity::class, VocabMeaningEntity::class,
        KanjiEntity::class, VocabKanjiEntity::class, RetiredEntity::class,
        KanjiStrokeEntity::class, GrammarEntity::class, GrammarExampleEntity::class,
        QuestionEntity::class, ExamTemplateEntity::class, ListeningEntity::class,
        JmFormEntity::class, VocabConflictEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class ContentDatabase : RoomDatabase() {
    abstract fun contentDao(): ContentDao

    companion object {
        const val ASSET_PATH = "content/content.db"
        const val HASH_ASSET_PATH = "content/content.db.sha256"
        private const val FILE_PREFIX = "content_"

        /** 에셋 해시 앞 12자로 파일 이름을 정해 연다(4.5). 에셋이 없거나 손상되면 예외. */
        suspend fun open(context: Context): ContentDatabase = withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            val hash = appContext.assets.open(HASH_ASSET_PATH).bufferedReader().use { it.readText() }.trim().take(12)
            require(hash.length == 12) { "content.db.sha256 is too short" }
            val name = "$FILE_PREFIX$hash.db"
            val db = Room.databaseBuilder(appContext, ContentDatabase::class.java, name)
                .createFromAsset(ASSET_PATH)
                .build()
            try {
                db.openHelper.writableDatabase // 여기서 실제로 복사·열기·스키마 확인
            } catch (e: Throwable) {
                db.close()
                throw e
            }
            deleteStale(appContext, keepName = name)
            db
        }

        /** 열기에 성공한 뒤 다른 `content_*.db`를 지운다. 학습 기록 DB(`study.db`)는 건드리지 않는다. */
        fun deleteStale(context: Context, keepName: String) {
            context.databaseList()
                .filter { it.startsWith(FILE_PREFIX) && it.endsWith(".db") && it != keepName }
                .forEach { context.deleteDatabase(it) }
        }
    }
}

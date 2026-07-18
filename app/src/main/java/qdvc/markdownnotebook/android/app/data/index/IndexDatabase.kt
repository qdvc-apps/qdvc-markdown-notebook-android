package qdvc.markdownnotebook.android.app.data.index

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The on-device note index. This database lives in the app's private storage
 * and is a disposable cache — if it is ever lost or its schema changes, it is
 * rebuilt from a live workspace scan, so destructive migration is safe here.
 */
@Database(
    entities = [IndexedNoteEntity::class, NoteFtsEntity::class, IndexMetaEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class IndexDatabase : RoomDatabase() {
    abstract fun indexDao(): IndexDao

    companion object {
        @Volatile
        private var instance: IndexDatabase? = null

        fun get(context: Context): IndexDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    IndexDatabase::class.java,
                    "note_index.db",
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}

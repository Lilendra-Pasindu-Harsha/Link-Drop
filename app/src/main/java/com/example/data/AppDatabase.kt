package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.model.TransferDirection
import com.example.model.TransferMode
import com.example.model.TransferRecord
import com.example.model.TransferStatus

class Converters {
    @TypeConverter
    fun toDirection(value: String): TransferDirection = TransferDirection.valueOf(value)

    @TypeConverter
    fun fromDirection(direction: TransferDirection): String = direction.name

    @TypeConverter
    fun toStatus(value: String): TransferStatus = TransferStatus.valueOf(value)

    @TypeConverter
    fun fromStatus(status: TransferStatus): String = status.name

    @TypeConverter
    fun toMode(value: String): TransferMode = TransferMode.valueOf(value)

    @TypeConverter
    fun fromMode(mode: TransferMode): String = mode.name
}

@Database(entities = [TransferRecord::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transferDao(): TransferDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "linkdrop_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

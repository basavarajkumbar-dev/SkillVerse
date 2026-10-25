package com.skillverse.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [
        ChallengeRefEntity::class,
        AttemptEntity::class,
        DailyAssignmentEntity::class,
        UserProgressEntity::class,
        SkillMasteryEntity::class,
        XpTransactionEntity::class,
        AchievementEntity::class,
        RevisionEntity::class,
    ],
    version = SkillVerseDatabase.VERSION,
    exportSchema = true,
)
abstract class SkillVerseDatabase : RoomDatabase() {
    abstract fun challengeRefDao(): ChallengeRefDao
    abstract fun attemptDao(): AttemptDao
    abstract fun assignmentDao(): AssignmentDao
    abstract fun progressDao(): ProgressDao
    abstract fun skillMasteryDao(): SkillMasteryDao
    abstract fun xpDao(): XpDao
    abstract fun achievementDao(): AchievementDao
    abstract fun revisionDao(): RevisionDao

    companion object {
        const val VERSION = 1
        const val NAME = "skillverse.db"

        /**
         * Schema migrations. Add one Migration per version bump (e.g. MIGRATION_1_2) and list it here.
         * Exported schemas in app/schemas let MigrationTestHelper verify each step. Destructive
         * fallback is deliberately NOT enabled for upgrades, so learner progress is never silently wiped.
         */
        val MIGRATIONS: Array<Migration> = emptyArray()

        fun build(context: Context): SkillVerseDatabase =
            Room.databaseBuilder(context.applicationContext, SkillVerseDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
    }
}

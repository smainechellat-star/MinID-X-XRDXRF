package com.xrdxrf.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

object AssetDbUtils {
    private const val TAG = "AssetDbUtils"
    private const val PREFS_NAME = "xrd_db_meta"
    private const val INDEX_VERSION = 1

    @JvmStatic
    fun openReadonlyDb(context: Context, assetName: String, dbName: String): SQLiteDatabase {
        try {
            Log.d(TAG, "Opening database: asset='$assetName', db='$dbName'")

            val dbFile = copyAssetToDatabasePath(context, assetName, dbName)
            Log.d(TAG, "Database file path: ${dbFile.path}")
            Log.d(TAG, "File exists: ${dbFile.exists()}, size: ${dbFile.length()} bytes")

            // ✅ Check file permissions
            if (!dbFile.canRead()) {
                Log.e(TAG, "Database file is not readable! Attempting to fix permissions...")
                dbFile.setReadable(true, false)
            }

            // ✅ Open database with error handling
            return SQLiteDatabase.openDatabase(
                dbFile.path,
                null,
                SQLiteDatabase.OPEN_READONLY
            ).also {
                Log.d(TAG, "✓ Database opened successfully")
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to open database: ${e.message}", e)
            throw RuntimeException("Database error: ${e.message}", e)
        }
    }

    @JvmStatic
    fun ensureXrdIndexes(context: Context, dbName: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = "index_v${INDEX_VERSION}_$dbName"
        if (prefs.getBoolean(key, false)) {
            return
        }

        val dbFile = context.getDatabasePath(dbName)
        if (!dbFile.exists()) {
            Log.w(TAG, "Skipping index setup because database file does not exist: ${dbFile.path}")
            return
        }

        var db: SQLiteDatabase? = null
        try {
            dbFile.setWritable(true, true)
            db = SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READWRITE)

            db.beginTransaction()
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_minerals_substance_name ON minerals(substance_name)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_minerals_chemical_formula ON minerals(chemical_formula)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_minerals_crystal_system ON minerals(crystal_system)")
            for (i in 1..20) {
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_minerals_d$i ON minerals(d$i)")
            }
            db.execSQL("ANALYZE")
            db.execSQL("PRAGMA optimize")
            db.setTransactionSuccessful()

            prefs.edit().putBoolean(key, true).apply()
            Log.d(TAG, "XRD indexes ensured for $dbName")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create XRD indexes for $dbName: ${e.message}", e)
        } finally {
            try {
                db?.endTransaction()
            } catch (_: Exception) {
            }
            try {
                db?.close()
            } catch (_: Exception) {
            }
        }
    }

    @JvmStatic
    fun openReadonlyDbFromZip(context: Context, zipAssetName: String, dbNameInZip: String, dbName: String): SQLiteDatabase {
        try {
            Log.d(TAG, "Extracting DB from ZIP: zip='$zipAssetName', entry='$dbNameInZip', db='$dbName'")

            val dbFile = extractDbFromZip(context, zipAssetName, dbNameInZip, dbName)
            return SQLiteDatabase.openDatabase(
                dbFile.path,
                null,
                SQLiteDatabase.OPEN_READONLY
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to open database from ZIP: ${e.message}", e)
            throw RuntimeException("Database ZIP error: ${e.message}", e)
        }
    }

    @JvmStatic
    fun copyAssetToDatabasePath(context: Context, assetName: String, dbName: String): File {
        val dbFile = context.getDatabasePath(dbName)
        Log.d(TAG, "Target database path: ${dbFile.path}")

        if (!dbFile.exists()) {
            Log.d(TAG, "Database not found, copying from assets...")

            try {
                // Ensure parent directory exists
                dbFile.parentFile?.mkdirs()
                Log.d(TAG, "Created parent directory: ${dbFile.parentFile?.path}")

                // Open asset and copy
                context.assets.open(assetName).use { input ->
                    FileOutputStream(dbFile).use { output ->
                        val bytesCopied = input.copyTo(output)
                        Log.d(TAG, "✓ Copied $bytesCopied bytes from assets/$assetName")
                    }
                }

                // ✅ Set proper file permissions (critical for Android 10+)
                dbFile.setReadable(true, false)
                dbFile.setWritable(true, true)

                Log.d(TAG, "File permissions: readable=${dbFile.canRead()}, writable=${dbFile.canWrite()}")

            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to copy asset '$assetName': ${e.message}", e)

                // Clean up partial file if it exists
                if (dbFile.exists()) {
                    dbFile.delete()
                    Log.d(TAG, "Deleted partial database file")
                }

                throw e
            }
        } else {
            Log.d(TAG, "Database already exists, skipping copy")
        }

        return dbFile
    }

    @JvmStatic
    fun extractDbFromZip(context: Context, zipAssetName: String, dbNameInZip: String, dbName: String): File {
        val dbFile = context.getDatabasePath(dbName)

        if (!dbFile.exists()) {
            try {
                dbFile.parentFile?.mkdirs()

                context.assets.open(zipAssetName).use { input ->
                    ZipInputStream(input).use { zipInput ->
                        var entry = zipInput.nextEntry
                        var found = false

                        while (entry != null) {
                            if (entry.name == dbNameInZip) {
                                Log.d(TAG, "Found DB entry in ZIP: $dbNameInZip")
                                FileOutputStream(dbFile).use { output ->
                                    val bytesCopied = zipInput.copyTo(output)
                                    Log.d(TAG, "✓ Extracted $bytesCopied bytes")
                                }
                                found = true
                                break
                            }
                            entry = zipInput.nextEntry
                        }

                        if (!found) {
                            throw RuntimeException("Database file '$dbNameInZip' not found in ZIP '$zipAssetName'")
                        }
                    }
                }

                dbFile.setReadable(true, false)
                dbFile.setWritable(true, true)

            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to extract DB from ZIP: ${e.message}", e)
                if (dbFile.exists()) dbFile.delete()
                throw e
            }
        }

        return dbFile
    }

    @JvmStatic
    fun copyAssetToFilesDir(context: Context, assetName: String, destFileName: String): File {
        val outFile = File(context.filesDir, destFileName)

        if (!outFile.exists()) {
            try {
                context.assets.open(assetName).use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output)
                    }
                }
                outFile.setReadable(true, false)
                Log.d(TAG, "✓ Copied asset to files dir: $destFileName")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to copy to files dir: ${e.message}", e)
                if (outFile.exists()) outFile.delete()
                throw e
            }
        }

        return outFile
    }
}

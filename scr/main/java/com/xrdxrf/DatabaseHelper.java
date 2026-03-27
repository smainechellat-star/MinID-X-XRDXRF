package com.xrdxrf.app;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import com.github.junrar.Archive;
import com.github.junrar.rarfile.FileHeader;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static String DB_NAME = "DATA2.db";
    private static String DB_ZIP_NAME = "minerals.zip";
    private Context context;
    private SQLiteDatabase db;

    public DatabaseHelper(Context context) {
        super(context, DB_NAME, null, 1);
        this.context = context;
        try {
            createDataBase();
            openDataBase();
            Log.d("DB_HELPER", "Database successfully opened and is ready.");
            Log.d("DB_HELPER", "DB Path: " + context.getDatabasePath(DB_NAME).getPath());
            Log.d("DB_HELPER", "DB Size: " + context.getDatabasePath(DB_NAME).length() / 1024 / 1024 + " MB");
            Log.d("DB_HELPER", "Exists: " + context.getDatabasePath(DB_NAME).exists());
        } catch (Exception e) {
            Log.e("DB_HELPER", "Error creating/opening database", e);
        }
    }

    public void createDataBase() throws IOException {
        boolean dbExist = checkDataBase();

        if (!dbExist) {
            Log.d("DB_HELPER", "Database not found. Copying from assets...");
            copyDataBase();
        }
    }

    private boolean checkDataBase() {
        File dbFile = context.getDatabasePath(DB_NAME);
        return dbFile.exists();
    }

    private void copyDataBase() throws IOException {
        if (isRarArchive()) {
            extractRarDatabase();
        } else {
            extractZipDatabase();
        }
    }

    private boolean isRarArchive() {
        try {
            InputStream headerStream = new BufferedInputStream(context.getAssets().open(DB_ZIP_NAME));
            byte[] header = new byte[8];
            int read = headerStream.read(header);
            headerStream.close();
            if (read >= 7) {
                return header[0] == 'R' && header[1] == 'a' && header[2] == 'r' && header[3] == '!';
            }
        } catch (Exception e) {
            Log.e("DB_HELPER", "Archive detection failed", e);
        }
        return false;
    }

    private void extractZipDatabase() throws IOException {
        InputStream myInput = context.getAssets().open(DB_ZIP_NAME);
        ZipInputStream zipInputStream = new ZipInputStream(myInput);

        ZipEntry zipEntry;
        while ((zipEntry = zipInputStream.getNextEntry()) != null) {
            if (zipEntry.getName().equals(DB_NAME) || zipEntry.getName().endsWith(".db")) {
                String outFileName = context.getDatabasePath(DB_NAME).getPath();
                OutputStream myOutput = new FileOutputStream(outFileName);

                byte[] buffer = new byte[1024 * 10];
                int length;

                Log.d("DB_HELPER", "Extracting database... this may take a few seconds.");

                while ((length = zipInputStream.read(buffer)) > 0) {
                    myOutput.write(buffer, 0, length);
                }

                myOutput.flush();
                myOutput.close();
                zipInputStream.closeEntry();
                Log.d("DB_HELPER", "Extraction complete.");
                break;
            }
        }
        zipInputStream.close();
    }

    private void extractRarDatabase() throws IOException {
        File tempRar = new File(context.getCacheDir(), DB_ZIP_NAME);
        InputStream assetInput = context.getAssets().open(DB_ZIP_NAME);
        OutputStream tempOutput = new FileOutputStream(tempRar);
        byte[] buffer = new byte[1024 * 10];
        int length;
        while ((length = assetInput.read(buffer)) > 0) {
            tempOutput.write(buffer, 0, length);
        }
        tempOutput.flush();
        tempOutput.close();
        assetInput.close();

        try {
            Archive archive = new Archive(tempRar);
            FileHeader fileHeader;
            boolean extracted = false;
            while ((fileHeader = archive.nextFileHeader()) != null) {
                if (fileHeader.isDirectory()) {
                    continue;
                }
                String fileName = fileHeader.getFileNameString();
                if (fileName.endsWith(".db")) {
                    String outFileName = context.getDatabasePath(DB_NAME).getPath();
                    OutputStream myOutput = new FileOutputStream(outFileName);
                    Log.d("DB_HELPER", "Extracting database from RAR... this may take a few seconds.");
                    archive.extractFile(fileHeader, myOutput);
                    myOutput.flush();
                    myOutput.close();
                    extracted = true;
                    Log.d("DB_HELPER", "RAR extraction complete.");
                    break;
                }
            }
            archive.close();

            if (!extracted) {
                throw new IOException("No .db file found in RAR archive.");
            }
        } catch (Exception e) {
            Log.e("DB_HELPER", "Error extracting RAR database", e);
            throw new IOException("RAR extraction failed: " + e.getMessage(), e);
        } finally {
            if (tempRar.exists()) {
                tempRar.delete();
            }
        }
    }

    public void openDataBase() throws IOException {
        db = this.getWritableDatabase();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        try {
            createTables(db);
        } catch (Exception e) {
            Log.e("DB_HELPER", "Error creating tables in onCreate", e);
        }
    }

    private void createTables(SQLiteDatabase db) {
        try {
            db.execSQL("CREATE TABLE IF NOT EXISTS minerals (" +
                    "id INTEGER PRIMARY KEY," +
                    "name TEXT NOT NULL," +
                    "crystal_system TEXT," +
                    "chemical_formula TEXT," +
                    "d1 REAL," +
                    "d2 REAL," +
                    "d3 REAL" +
                    ");");

            db.execSQL("CREATE TABLE IF NOT EXISTS d_spacing (" +
                    "id INTEGER PRIMARY KEY," +
                    "mineral_id INTEGER NOT NULL," +
                    "d_spacing REAL," +
                    "intensity INTEGER," +
                    "FOREIGN KEY(mineral_id) REFERENCES minerals(id)" +
                    ");");

            Log.d("DB_HELPER", "Tables created successfully");
        } catch (Exception e) {
            Log.e("DB_HELPER", "Error creating tables", e);
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Handle upgrades if needed in future versions
    }

    // Helper method to get data (You will use this in the next step)
    public Cursor getMineralData(String mineralName, double d1Ref, double d2Ref, double tolerance) {
        double d1Min = d1Ref - tolerance;
        double d1Max = d1Ref + tolerance;
        double d2Min = d2Ref - tolerance;
        double d2Max = d2Ref + tolerance;
        String query = "SELECT * FROM minerals WHERE substance_name LIKE ? " +
            "AND d1 BETWEEN ? AND ? " +
            "AND d2 BETWEEN ? AND ?";
        String nameLike = "%" + mineralName + "%";
        return db.rawQuery(query, new String[]{nameLike, String.valueOf(d1Min), String.valueOf(d1Max), String.valueOf(d2Min), String.valueOf(d2Max)});
    }

    public Cursor getSubstanceData(String input) {
        SQLiteDatabase readDb = this.getReadableDatabase();
        String query = "SELECT * FROM minerals WHERE substance_name LIKE ? OR chemical_formula LIKE ?";
        String likeString = "%" + input + "%";
        Log.d("DB_HELPER", "Searching for: " + input);
        Cursor cursor = readDb.rawQuery(query, new String[]{likeString, likeString});
        Log.d("DB_HELPER", "Found " + cursor.getCount() + " results");
        return cursor;
    }
}

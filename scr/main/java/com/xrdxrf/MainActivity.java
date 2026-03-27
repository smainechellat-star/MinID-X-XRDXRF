package com.xrdxrf.app;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import com.xrdxrf.app.AssetDbUtils;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnXrdIdentification = findViewById(R.id.btnXrdIdentification);
        Button btnXrfEnergyLookup = findViewById(R.id.btnXrfEnergyLookup);
        Button btnSettings = findViewById(R.id.btnSettings);

        btnXrdIdentification.setOnClickListener(v ->
                startActivity(new Intent(this, XrdSelectionActivity.class)));

        btnXrfEnergyLookup.setOnClickListener(v ->
                startActivity(new Intent(this, XrfActivity.class)));

        btnSettings.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        extractDatabaseFromZipIfNeeded("minerals.zip", "minerals_offline.db", "minerals_offline.db");
        checkDatabaseHealth();
    }

    private void checkDatabaseHealth() {
        String dbName = "minerals_offline.db"; // Your DB file name
        String dbPath = getDatabasePath(dbName).getPath();
        Log.d("DB_DEBUG", "Expected Path: " + dbPath);
        File dbFile = new File(dbPath);
        // 1. Check if file exists
        if (dbFile.exists()) {
            Log.d("DB_DEBUG", "✅ Database file EXISTS");
            Log.d("DB_DEBUG", "✅ File Size: " + dbFile.length() + " bytes");
        } else {
            Log.e("DB_DEBUG", "❌ Database file NOT FOUND at path");
            Log.e("DB_DEBUG", "⚠️ You must copy from assets to internal storage first!");
            return;
        }
        // 2. Try to open and query
        try {
            SQLiteDatabase db = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READONLY);
            Log.d("DB_DEBUG", "✅ Database OPENED successfully");
            // 3. Check table exists
            Cursor cursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='minerals'", null);
            if (cursor.moveToFirst()) {
                Log.d("DB_DEBUG", "✅ Table 'minerals' EXISTS");
            } else {
                Log.e("DB_DEBUG", "❌ Table 'minerals' NOT FOUND");
            }
            cursor.close();
            // 4. Check row count
            Cursor countCursor = db.rawQuery("SELECT COUNT(*) FROM minerals", null);
            if (countCursor.moveToFirst()) {
                int count = countCursor.getInt(0);
                Log.d("DB_DEBUG", "✅ Total Rows: " + count);
            }
            countCursor.close();
            // 5. Check column names (Critical for your previous error)
            Cursor colCursor = db.rawQuery("SELECT * FROM minerals LIMIT 1", null);
            String[] columns = colCursor.getColumnNames();
            Log.d("DB_DEBUG", "✅ Column Names: " + TextUtils.join(", ", columns));
            colCursor.close();
            db.close();
        } catch (Exception e) {
            Log.e("DB_DEBUG", "❌ Error opening database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void copyDatabaseIfNeeded(String dbName) {
        String dbPath = getDatabasePath(dbName).getPath();
        File dbFile = new File(dbPath);
        if (!dbFile.exists() || dbFile.length() == 0) {
            try {
                InputStream is = getAssets().open(dbName);
                FileOutputStream fos = new FileOutputStream(dbPath);
                byte[] buffer = new byte[4096];
                int length;
                while ((length = is.read(buffer)) > 0) {
                    fos.write(buffer, 0, length);
                }
                fos.flush();
                fos.close();
                is.close();
                Log.d("DB_DEBUG", "✅ Database copied from assets to internal storage.");
            } catch (Exception e) {
                Log.e("DB_DEBUG", "❌ Failed to copy database: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            Log.d("DB_DEBUG", "Database already exists in internal storage.");
        }
    }

    private void extractDatabaseFromZipIfNeeded(String zipAssetName, String dbNameInZip, String dbName) {
        try {
            AssetDbUtils.extractDbFromZip(this, zipAssetName, dbNameInZip, dbName);
            Log.d("DB_DEBUG", "✅ Database extracted from ZIP if needed.");
        } catch (Exception e) {
            Log.e("DB_DEBUG", "❌ Failed to extract database from ZIP: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

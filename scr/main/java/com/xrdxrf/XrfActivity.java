package com.xrdxrf.app;

import android.os.Bundle;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

public class XrfActivity extends AppCompatActivity {

    private static final String XRF_DB_NAME = "xrf.db";

    private SQLiteDatabase xrfDb;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_xrf);

        Button btnSearchElement = findViewById(R.id.btnSearchElement);
        EditText etElementSymbol = findViewById(R.id.etElementSymbol);
        TextView tvEnergyData = findViewById(R.id.tvEnergyData);
        TextView tvTimeToResult = findViewById(R.id.tvTimeToResult);
        TextView tvXrfSources = findViewById(R.id.tvXrfSources);

        tvXrfSources.setText("XRF Data: " + XRF_DB_NAME);

        initializeXrfDatabase();

        btnSearchElement.setOnClickListener(v -> {
            String input = etElementSymbol.getText().toString().trim();
            if (input.isEmpty()) {
                Toast.makeText(this, "Please enter an element symbol or name", Toast.LENGTH_SHORT).show();
                return;
            }

            long startTime = System.currentTimeMillis();

            AsyncTask.execute(() -> {
                try {
                    String result = lookupElement(input);
                    long elapsed = System.currentTimeMillis() - startTime;
                    runOnUiThread(() -> {
                        tvEnergyData.setText(result);
                        tvTimeToResult.setText(String.format(Locale.US, "Time to First Result: %.3f second", elapsed / 1000.0));
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        tvEnergyData.setText("");
                        tvTimeToResult.setText("Time to First Result: ---");
                        Toast.makeText(this, "XRF error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }
            });
        });
    }

    private void initializeXrfDatabase() {
        try {
            // Copy database from assets directly
            xrfDb = AssetDbUtils.openReadonlyDb(this, XRF_DB_NAME, XRF_DB_NAME);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to initialize XRF database: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String lookupElement(String input) {
        if (xrfDb == null) {
            return "XRF database not available.";
        }

        String query;
        String[] args;
        if (input.matches("\\d+")) {
            query = "SELECT * FROM xrf_elements WHERE atomic_number = ? LIMIT 1";
            args = new String[]{input};
        } else {
            query = "SELECT * FROM xrf_elements WHERE symbol = ? COLLATE NOCASE OR name = ? COLLATE NOCASE LIMIT 1";
            args = new String[]{input, input};
        }

        Cursor cursor = xrfDb.rawQuery(query, args);
        try {
            if (!cursor.moveToFirst()) {
                return "No XRF data found for: " + input;
            }

            String symbol = cursor.getString(cursor.getColumnIndexOrThrow("symbol"));
            String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
            int atomicNumber = cursor.getInt(cursor.getColumnIndexOrThrow("atomic_number"));

            StringBuilder sb = new StringBuilder();
            sb.append("Element: ").append(name).append(" (").append(symbol).append(")\n");
            sb.append("Atomic Number: ").append(atomicNumber).append("\n");
            sb.append("─────────────────────────────────────────\n\n");

            // Special cases for certain elements
            if (atomicNumber == 1) {
                sb.append("⚠ XRF not applicable: Hydrogen has no inner electronic shells capable of producing characteristic X-ray emission.\n");
                return sb.toString().trim();
            } else if (atomicNumber == 2) {
                sb.append("⚠ XRF not applicable: Helium lacks higher-energy electronic shells required for characteristic X-ray fluorescence.\n");
                return sb.toString().trim();
            } else if (atomicNumber == 3) {
                sb.append("⚠ XRF theoretically possible but experimentally impractical: Lithium Kα emission (~54 eV) is strongly absorbed by air, sample matrix, and detector window.\n");
                return sb.toString().trim();
            } else if (atomicNumber >= 104 && atomicNumber <= 118) {
                sb.append("⚠ No experimental XRF data available: Superheavy elements are artificially produced, extremely short-lived, and not available in macroscopic quantities required for X-ray fluorescence measurements.\n");
                return sb.toString().trim();
            }

            // K-series (6 lines)
            sb.append("K-series (6 lines)\n");
            appendEnergy(sb, "K-edge (keV)", cursor, "k_edge");
            appendEnergy(sb, "Kβ2 (keV)", cursor, "kb2");
            appendEnergy(sb, "Kβ1 (keV)", cursor, "kb1");
            appendEnergy(sb, "Kβ3 (keV)", cursor, "kb3");
            appendEnergy(sb, "Kα1 (keV)", cursor, "ka1");
            appendEnergy(sb, "Kα2 (keV)", cursor, "ka2");
            sb.append("\n");

            // L-series (12 lines)
            sb.append("L-series (12 lines)\n");
            appendEnergy(sb, "LI-edge (keV)", cursor, "li_edge");
            appendEnergy(sb, "Lγ3 (keV)", cursor, "lg3");
            appendEnergy(sb, "Lβ3 (keV)", cursor, "lb3");
            appendEnergy(sb, "Lβ4 (keV)", cursor, "lb4");
            appendEnergy(sb, "LII-edge (keV)", cursor, "lii_edge");
            appendEnergy(sb, "Lγ1 (keV)", cursor, "lg1");
            appendEnergy(sb, "Lβ1 (keV)", cursor, "lb1");
            appendEnergy(sb, "LIII-edge (keV)", cursor, "liii_edge");
            appendEnergy(sb, "Lβ2 (keV)", cursor, "lb2");
            appendEnergy(sb, "Lα1 (keV)", cursor, "la1");
            appendEnergy(sb, "Lα2 (keV)", cursor, "la2");
            appendEnergy(sb, "LI (keV)", cursor, "li");
            sb.append("\n");

            // M-series (8 lines)
            sb.append("M-series (8 lines)\n");
            appendEnergy(sb, "MII-NIV", cursor, "mii_niv");
            appendEnergy(sb, "MIII-edge (keV)", cursor, "miii_edge");
            appendEnergy(sb, "Mγ (keV)", cursor, "mg");
            appendEnergy(sb, "MIV-edge (keV)", cursor, "miv_edge");
            appendEnergy(sb, "Mβ (keV)", cursor, "mb");
            appendEnergy(sb, "MV-edge (keV)", cursor, "mv_edge");
            appendEnergy(sb, "Mα1 (keV)", cursor, "ma1");
            appendEnergy(sb, "Mα2 (keV)", cursor, "ma2");

            return sb.toString().trim();
        } finally {
            cursor.close();
        }
    }

    private void appendEnergy(StringBuilder sb, String label, Cursor cursor, String column) {
        int idx = cursor.getColumnIndex(column);
        if (idx >= 0 && !cursor.isNull(idx)) {
            double value = cursor.getDouble(idx);
            sb.append(label).append("\t").append(String.format(Locale.US, "%.4f", value)).append("\n");
        } else {
            sb.append(label).append("\t—\n");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (xrfDb != null && xrfDb.isOpen()) {
            xrfDb.close();
        }
    }
}


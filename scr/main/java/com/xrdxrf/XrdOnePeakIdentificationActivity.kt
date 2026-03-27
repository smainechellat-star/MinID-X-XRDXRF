package com.xrdxrf.app

import android.app.ProgressDialog
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.round

class XrdOnePeakIdentificationActivity : AppCompatActivity() {

    private lateinit var spinnerCategory: Spinner
    private lateinit var etPeakValue: EditText
    private lateinit var btnSearch: Button

    private var codDb: SQLiteDatabase? = null
    private var progressDialog: ProgressDialog? = null
    private val backgroundExecutor = Executors.newSingleThreadExecutor()
    private var isDbReady = false

    companion object {
        private const val HANAWALT_TOLERANCE = 0.02
        private const val TAG = "XrdOnePeak"
        private const val CANDIDATE_LIMIT = 5000
        private const val MAX_RENDERED_RESULTS = 150
    }

    private val threeDecimalPattern = Regex("^\\d+(\\.\\d{3})$")
    private val categories = arrayOf("All", "Minerals", "Compounds", "Organic", "Inorganic")
    private var selectedCategory = "All"
    private var currentResults: List<Map<String, Any>> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_xrd_one_peak_identification)

        spinnerCategory = findViewById<Spinner>(R.id.spinnerCategory)
        etPeakValue = findViewById<EditText>(R.id.etPeakValue)
        btnSearch = findViewById<Button>(R.id.btnSearch)

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCategory.adapter = adapter

        spinnerCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: android.view.View, pos: Int, id: Long) {
                selectedCategory = categories[pos]
                android.util.Log.d(TAG, "Category selected: $selectedCategory")
            }
            override fun onNothingSelected(parent: AdapterView<*>) {
                selectedCategory = "All"
            }
        }

        btnSearch.isEnabled = false
        initializeDatabaseAsync()
        btnSearch.setOnClickListener { searchByOnePeak() }
    }

    private fun initializeDatabaseAsync() {
        backgroundExecutor.execute {
            try {
                AssetDbUtils.copyAssetToDatabasePath(this, "cod_xrd_final.db", "cod_xrd_final.db")
                AssetDbUtils.ensureXrdIndexes(this, "cod_xrd_final.db")
                codDb = AssetDbUtils.openReadonlyDb(this, "cod_xrd_final.db", "cod_xrd_final.db")
                android.util.Log.d(TAG, "✅ Database opened successfully")
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    isDbReady = codDb != null
                    btnSearch.isEnabled = isDbReady
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "❌ Database init failed: ${e.message}", e)
                codDb = null
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    isDbReady = false
                    btnSearch.isEnabled = false
                    Toast.makeText(this, "Database error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun searchByOnePeak() {
        if (!isDbReady) {
            Toast.makeText(this, "Database is preparing, please wait...", Toast.LENGTH_SHORT).show()
            return
        }

        val raw = etPeakValue.text.toString().trim()
        if (raw.isBlank()) {
            Toast.makeText(this, "Enter d-spacing value", Toast.LENGTH_SHORT).show()
            return
        }

        val cleaned = raw.replace(',', '.').replace(';', '.').trim()

        if (!threeDecimalPattern.matches(cleaned)) {
            etPeakValue.error = "Use 3 decimals (e.g., 3.342)"
            Toast.makeText(this, "Use 3 decimals", Toast.LENGTH_SHORT).show()
            return
        }

        val peakValue = parseDValueDebug(etPeakValue, cleaned) ?: return

        progressDialog = ProgressDialog(this).apply {
            setTitle("Searching (Hanawalt ±0.02 Å)")
            setMessage("Finding matches in category: $selectedCategory...")
            setCancelable(false)
            show()
        }

        backgroundExecutor.execute {
            try {
                val db = codDb
                if (db == null) {
                    runOnUiThread {
                        progressDialog?.dismiss()
                        Toast.makeText(this@XrdOnePeakIdentificationActivity, "Database unavailable", Toast.LENGTH_SHORT).show()
                    }
                    return@execute
                }

                val startTime = System.currentTimeMillis()
                val results = searchByPeakValue(db, peakValue)
                val elapsedMs = System.currentTimeMillis() - startTime

                runOnUiThread {
                    try {
                        progressDialog?.dismiss()
                        if (results.isNotEmpty()) {
                            findViewById<TextView>(R.id.tvTimeToResult).text =
                                String.format(Locale.US, "Time: %.3f sec", elapsedMs / 1000.0)
                            currentResults = results
                            displayAllResults()
                            
                            // Show success message with count and category
                            Toast.makeText(
                                this@XrdOnePeakIdentificationActivity,
                                "Found ${results.size} match(es) in category: $selectedCategory",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            findViewById<TextView>(R.id.tvTimeToResult).text = "Time: ---"
                            Toast.makeText(
                                this@XrdOnePeakIdentificationActivity,
                                "No matches in category: $selectedCategory for d=${String.format("%.3f", peakValue)}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "UI Update Error", e)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "❌ Search error: ${e.message}", e)
                runOnUiThread {
                    progressDialog?.dismiss()
                    Toast.makeText(this@XrdOnePeakIdentificationActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun searchByPeakValue(db: SQLiteDatabase, peakValue: Double): List<Map<String, Any>> {
        val tolerance = HANAWALT_TOLERANCE
        val roundedInput = round(peakValue * 1000.0) / 1000.0
        val matches = mutableListOf<Map<String, Any>>()

        android.util.Log.d(TAG, "🔍 Searching for d=$roundedInput, category=$selectedCategory")

        val candidateQuery = buildCandidateQuery(roundedInput, tolerance)
        
        try {
            db.rawQuery(candidateQuery.first, candidateQuery.second)?.use { cursor ->
                // Get column indices once
                val nameCol = cursor.getColumnIndex("substance_name")
                val formulaCol = cursor.getColumnIndex("chemical_formula")
                val systemCol = cursor.getColumnIndex("crystal_system")
                
                // Get indices for all d and i columns
                val dCols = IntArray(20)
                val iCols = IntArray(20)
                for (i in 0 until 20) {
                    dCols[i] = cursor.getColumnIndex("d${i + 1}")
                    iCols[i] = cursor.getColumnIndex("i${i + 1}")
                }
                
                android.util.Log.d(TAG, "📊 Scanning ${cursor.count} rows...")
                var scannedCount = 0
                var filteredByCategory = 0

                while (cursor.moveToNext()) {
                    scannedCount++
                    try {
                        val substanceName = if (nameCol >= 0) cursor.getString(nameCol)?.trim().orEmpty() else ""
                        val formula = if (formulaCol >= 0) cursor.getString(formulaCol)?.trim() ?: "" else ""
                        val crystalSystem = if (systemCol >= 0) cursor.getString(systemCol) ?: "" else ""

                        if (substanceName.isBlank() || substanceName.equals("None", ignoreCase = true)) continue

                        // 🔴 CRITICAL: Apply category filtering FIRST
                        if (!matchesCategoryByFormula(formula)) {
                            filteredByCategory++
                            continue
                        }

                        // Check all peaks for match
                        var bestIntensity = 0.0
                        val matchedDPeaks = mutableListOf<Double>()
                        val allPeaks = mutableListOf<Pair<Double, Double>>()
                        
                        for (i in 0 until 20) {
                            if (dCols[i] >= 0 && iCols[i] >= 0 && 
                                !cursor.isNull(dCols[i]) && !cursor.isNull(iCols[i])) {
                                
                                val dVal = cursor.getDouble(dCols[i])
                                val iVal = cursor.getDouble(iCols[i])
                                
                                if (dVal > 0 && iVal >= 0) {
                                    allPeaks.add(Pair(dVal, iVal))
                                    
                                    val roundedD = round(dVal * 1000.0) / 1000.0
                                    if (abs(roundedD - roundedInput) <= tolerance) {
                                        matchedDPeaks.add(roundedD)
                                        if (iVal > bestIntensity) {
                                            bestIntensity = iVal
                                        }
                                    }
                                }
                            }
                        }

                        // If match found, add to results
                        if (matchedDPeaks.isNotEmpty()) {
                            android.util.Log.d(TAG, "✅ Match #${matches.size + 1}: $substanceName (category: $selectedCategory)")
                            
                            matches.add(mapOf(
                                "substance_name" to substanceName,
                                "formula" to normalizeFormula(formula),
                                "system" to crystalSystem,
                                "peaks" to allPeaks,
                                "matched_d" to matchedDPeaks.first(),
                                "matched_intensity" to bestIntensity,
                                "category" to selectedCategory
                            ))
                        }
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "❌ Row ${scannedCount} processing failed: ${e.message}")
                        continue
                    }
                }
                android.util.Log.d(TAG, "📊 Scanned: $scannedCount, Filtered out by category: $filteredByCategory, Matches: ${matches.size}")
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ Query execution failed: ${e.message}", e)
        }

        // Sort by intensity (highest first)
        return matches.sortedByDescending { it["matched_intensity"] as? Double ?: 0.0 }
    }

    private fun buildCandidateQuery(peakValue: Double, tolerance: Double): Pair<String, Array<String>> {
        val min = peakValue - tolerance
        val max = peakValue + tolerance
        val parts = mutableListOf<String>()
        val args = mutableListOf<String>()
        for (col in 1..20) {
            parts += "SELECT rowid AS rid FROM minerals WHERE d$col BETWEEN ? AND ?"
            args += String.format(Locale.US, "%.6f", min)
            args += String.format(Locale.US, "%.6f", max)
        }
        val union = parts.joinToString(" UNION ALL ")
        val sql = """
            WITH candidates AS (
                $union
            ), distinct_candidates AS (
                SELECT DISTINCT rid FROM candidates LIMIT $CANDIDATE_LIMIT
            )
            SELECT m.substance_name, m.chemical_formula, m.crystal_system,
                   m.d1, m.i1, m.d2, m.i2, m.d3, m.i3, m.d4, m.i4, m.d5, m.i5,
                   m.d6, m.i6, m.d7, m.i7, m.d8, m.i8, m.d9, m.i9, m.d10, m.i10,
                   m.d11, m.i11, m.d12, m.i12, m.d13, m.i13, m.d14, m.i14, m.d15, m.i15,
                   m.d16, m.i16, m.d17, m.i17, m.d18, m.i18, m.d19, m.i19, m.d20, m.i20
            FROM minerals m
            JOIN distinct_candidates dc ON dc.rid = m.rowid
            WHERE m.substance_name IS NOT NULL AND m.substance_name != ''
        """.trimIndent()

        return Pair(sql, args.toTypedArray())
    }

    private fun matchesCategoryByFormula(formula: String): Boolean {
        // If "All" is selected, include everything
        if (selectedCategory == "All") return true
        
        val result = when (selectedCategory) {
            "Minerals" -> {
                // Minerals are inorganic, not large organic molecules
                !isLargeOrganicMolecule(formula) && !isOrganicFormula(formula)
            }
            "Compounds" -> {
                // Compounds can be either organic or inorganic, but not large minerals?
                // For now, include everything except those filtered by other categories
                true
            }
            "Organic" -> {
                isOrganicFormula(formula)
            }
            "Inorganic" -> {
                !isOrganicFormula(formula) && !isLargeOrganicMolecule(formula)
            }
            else -> true
        }
        
        if (!result) {
            android.util.Log.d(TAG, "Filtered out by category $selectedCategory: formula=$formula")
        }
        return result
    }

    private fun isLargeOrganicMolecule(formula: String): Boolean {
        if (formula.isBlank()) return false
        val cleanFormula = formula.replace("\\s".toRegex(), "")
        val atoms = countTotalAtoms(cleanFormula)
        return atoms > 100
    }

    private fun countTotalAtoms(formula: String): Int {
        var total = 0
        var i = 0
        while (i < formula.length) {
            val ch = formula[i]
            if (!ch.isLetter()) return 0
            if (!ch.isUpperCase()) return 0
            var j = i + 1
            while (j < formula.length && formula[j].isLetter() && formula[j].isLowerCase()) j++
            var num = 0
            var k = j
            while (k < formula.length && formula[k].isDigit()) {
                num = num * 10 + (formula[k] - '0')
                k++
            }
            if (num == 0) num = 1
            total += num
            i = k
        }
        return total
    }

    private fun isOrganicFormula(formula: String): Boolean {
        if (formula.isBlank()) return false
        // Must contain Carbon
        if (!formula.contains("C", ignoreCase = true)) return false
        
        // Check for metal carbides or carbonates that should be considered inorganic
        val metalCarbides = listOf("CaC", "FeC", "SiC", "WC", "TiC")
        val carbonates = listOf("CO3", "HCO3")
        
        for (mc in metalCarbides) {
            if (formula.contains(mc, ignoreCase = true)) return false
        }
        
        for (carb in carbonates) {
            if (formula.contains(carb, ignoreCase = true)) return false
        }
        
        // Check for metal-carbon bonds that indicate inorganic
        val metalSymbols = listOf("Na", "K", "Ca", "Mg", "Fe", "Al", "Si", "Ba", "Sr", "Li", "Be", "B")
        for (metal in metalSymbols) {
            if (formula.contains(metal) && formula.contains("C")) {
                // If it contains metal and C but no H, likely inorganic
                if (!formula.contains("H")) return false
            }
        }
        
        return true
    }

    private fun normalizeFormula(formula: String?): String {
        val raw = formula?.trim().orEmpty()
        if (raw.isBlank()) return ""
        val elements = mutableListOf<Pair<String, Int>>()
        var i = 0
        while (i < raw.length) {
            val ch = raw[i]
            if (!ch.isLetter() || !ch.isUpperCase()) return raw
            var symbol = ch.toString()
            var j = i + 1
            while (j < raw.length && raw[j].isLetter() && raw[j].isLowerCase()) {
                symbol += raw[j]
                j++
            }
            var num = 0
            var k = j
            while (k < raw.length && raw[k].isDigit()) {
                num = num * 10 + (raw[k] - '0')
                k++
            }
            if (num == 0) num = 1
            elements.add(symbol to num)
            i = k
        }
        if (elements.isEmpty()) return raw
        val gcd = elements.map { it.second }.reduce { a, b -> gcd(a, b) }
        return buildString {
            elements.forEach { (s, c) ->
                append(s)
                if (c / gcd > 1) append(c / gcd)
            }
        }
    }

    private fun gcd(a: Int, b: Int): Int {
        var x = a
        var y = b
        while (y != 0) {
            val t = x % y
            x = y
            y = t
        }
        return x
    }

    private fun parseDValueDebug(editText: EditText, raw: String): Double? {
        val value = raw.toDoubleOrNull() ?: return null
        val rounded = round(value * 1000.0) / 1000.0
        val formatted = String.format("%.3f", rounded)
        if (raw != formatted) {
            runOnUiThread {
                editText.setText(formatted)
                editText.setSelection(formatted.length)
            }
        }
        return rounded
    }

    private fun displayAllResults() {
        val container = findViewById<LinearLayout>(R.id.resultsContainer)
        container.removeAllViews()
        container.visibility = android.view.View.VISIBLE

        val displayCount = minOf(currentResults.size, MAX_RENDERED_RESULTS)

        container.addView(TextView(this).apply {
            text = if (currentResults.size > displayCount) {
                "Found ${currentResults.size} match(es) in category: $selectedCategory. Showing first $displayCount."
            } else {
                "Found ${currentResults.size} match(es) in category: $selectedCategory"
            }
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 8, 0, 16)
            setTextColor(android.graphics.Color.parseColor("#FF1976D2"))
        })

        currentResults.take(displayCount).forEachIndexed { idx, r ->
            val name = (r["substance_name"] as? String)?.trim().orEmpty()
            val formula = (r["formula"] as? String)?.trim().orEmpty()
            val system = (r["system"] as? String)?.trim().orEmpty()
            val matchedD = (r["matched_d"] as? Double) ?: 0.0
            val intensity = (r["matched_intensity"] as? Double) ?: 0.0

            val cardView = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 12, 16, 12)
                setBackgroundResource(android.R.drawable.list_selector_background)
            }
            
            cardView.addView(TextView(this).apply {
                text = "${idx + 1}. ${name.ifBlank { "Unknown" }}"
                textSize = 16f
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
            
            cardView.addView(TextView(this).apply {
                text = "   Formula: ${formula.ifBlank { "---" }}"
                textSize = 14f
            })
            
            cardView.addView(TextView(this).apply {
                text = "   Matched: d=${String.format("%.3f", matchedD)} Å (I=${String.format("%.1f", intensity)}%)"
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
            
            if (system.isNotBlank()) {
                cardView.addView(TextView(this).apply {
                    text = "   System: $system"
                    textSize = 14f
                })
            }
            
            container.addView(cardView)

            if (idx < displayCount - 1) {
                container.addView(android.view.View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        1
                    ).apply {
                        setMargins(16, 0, 16, 0)
                    }
                    setBackgroundColor(android.graphics.Color.parseColor("#FFCCCCCC"))
                })
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        backgroundExecutor.shutdown()
        codDb?.let {
            if (it.isOpen) it.close()
        }
    }
}
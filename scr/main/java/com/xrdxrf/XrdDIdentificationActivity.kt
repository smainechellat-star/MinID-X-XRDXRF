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

class XrdDIdentificationActivity : AppCompatActivity() {

    private lateinit var spinnerCategory: Spinner
    private lateinit var etD1: EditText
    private lateinit var etD2: EditText
    private lateinit var etD3: EditText
    private lateinit var etD4: EditText
    private lateinit var etD5: EditText
    private lateinit var btnSearch: Button
    
    private var codDb: SQLiteDatabase? = null
    private var progressDialog: ProgressDialog? = null
    private val backgroundExecutor = Executors.newSingleThreadExecutor()
    private var isDbReady = false
    
    private val threeDecimalPattern = Regex("^\\d+(\\.\\d{3})$")
    private var lastSearchCount = 0

    private val categories = arrayOf("All", "Minerals", "Compounds", "Organic", "Inorganic")
    private var selectedCategory = "All"
    
    private var currentResults: List<Map<String, Any>> = emptyList()
    private var currentResultIndex = 0

    companion object {
        private const val HANAWALT_TOLERANCE = 0.02
        private const val TAG = "XrdDIdentification"
        private const val CANDIDATE_LIMIT = 4000
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_xrd_d_identification)

        spinnerCategory = findViewById(R.id.spinnerCategory)
        etD1 = findViewById(R.id.etD1)
        etD2 = findViewById(R.id.etD2)
        etD3 = findViewById(R.id.etD3)
        etD4 = findViewById(R.id.etD4)
        etD5 = findViewById(R.id.etD5)
        btnSearch = findViewById(R.id.btnSearch)
        
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
        btnSearch.setOnClickListener { searchByDSpacing() }
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

    private fun searchByDSpacing() {
        if (!isDbReady) {
            Toast.makeText(this, "Database is preparing, please wait...", Toast.LENGTH_SHORT).show()
            return
        }

        val inputs = listOf(
            "d1" to etD1, "d2" to etD2, "d3" to etD3, "d4" to etD4, "d5" to etD5
        )

        val dValues = mutableListOf<Double>()
        for ((label, editText) in inputs) {
            val raw = editText.text.toString().trim()
            if (raw.isBlank()) continue
            if (!threeDecimalPattern.matches(raw)) {
                editText.error = "Use 3 decimals (e.g., 3.342)"
                Toast.makeText(this, "$label must use 3 decimals (e.g., 3.342)", Toast.LENGTH_SHORT).show()
                return
            }
            val parsed = parseDValue(editText, raw) ?: return
            dValues.add(parsed)
        }
        if (dValues.size < 3) {
            Toast.makeText(this, "Please enter at least 3 d-spacing values", Toast.LENGTH_SHORT).show()
            return
        }
        lastSearchCount = dValues.size

        val startTime = System.currentTimeMillis()

        progressDialog = ProgressDialog(this).apply {
            setTitle("Searching")
            setMessage("Matching d-spacing values in category: $selectedCategory...")
            setCancelable(false)
            show()
        }

        backgroundExecutor.execute {
            try {
                val db = codDb
                if (db == null) {
                    runOnUiThread {
                        progressDialog?.dismiss()
                        Toast.makeText(this@XrdDIdentificationActivity, "Database unavailable", Toast.LENGTH_SHORT).show()
                    }
                    return@execute
                }

                val results = searchPatterns(db, dValues)
                runOnUiThread {
                    val elapsedMs = System.currentTimeMillis() - startTime
                    progressDialog?.dismiss()
                    if (results.isNotEmpty()) {
                        findViewById<TextView>(R.id.tvTimeToResult).text = 
                            String.format(Locale.US, "Time to First Result: %.3f seconds", elapsedMs / 1000.0)
                        currentResults = results
                        currentResultIndex = 0
                        displayResultAtIndex(0)
                        
                        Toast.makeText(
                            this@XrdDIdentificationActivity, 
                            "Found ${results.size} match(es) in category: $selectedCategory", 
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        val tvTimeToResult = findViewById<TextView>(R.id.tvTimeToResult)
                        tvTimeToResult.text = "Time to First Result: ---"
                        Toast.makeText(
                            this@XrdDIdentificationActivity, 
                            "No matches in category: $selectedCategory for d-spacings: ${dValues.joinToString { String.format("%.3f", it) }}", 
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressDialog?.dismiss()
                    Toast.makeText(this@XrdDIdentificationActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun searchPatterns(db: SQLiteDatabase, dValues: List<Double>): List<Map<String, Any>> {
        val tolerance = HANAWALT_TOLERANCE
        val matches = mutableListOf<Map<String, Any>>()
        
        val roundedInputs = dValues.map { round(it * 1000.0) / 1000.0 }
        android.util.Log.d(TAG, "🔍 Searching for d-spacings: $roundedInputs in category: $selectedCategory")

        val candidateQuery = buildCandidateQuery(dValues, tolerance)
        
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
                
                android.util.Log.d(TAG, "📊 Scanning database (${cursor.count} rows)...")
                var scannedCount = 0
                var filteredByCategory = 0

                while (cursor.moveToNext()) {
                    scannedCount++
                    try {
                        val name = if (nameCol >= 0) cursor.getString(nameCol)?.trim().orEmpty() else ""
                        val formula = if (formulaCol >= 0) cursor.getString(formulaCol) ?: "" else ""
                        val system = if (systemCol >= 0) cursor.getString(systemCol) ?: "" else ""

                        if (name.isBlank() || name.equals("None", ignoreCase = true)) continue

                        // 🔴 CRITICAL: Apply category filtering FIRST
                        if (!matchesCategoryByFormula(formula)) {
                            filteredByCategory++
                            continue
                        }

                        // Collect all valid peaks
                        val allPeaks = mutableListOf<Pair<Double, Double>>()
                        for (i in 0 until 20) {
                            if (dCols[i] >= 0 && iCols[i] >= 0 && 
                                !cursor.isNull(dCols[i]) && !cursor.isNull(iCols[i])) {
                                
                                val dVal = cursor.getDouble(dCols[i])
                                val iVal = cursor.getDouble(iCols[i])
                                if (dVal > 0 && iVal >= 0) {
                                    allPeaks.add(Pair(dVal, iVal))
                                }
                            }
                        }

                        // Score matching peaks
                        var score = 0
                        val matchedPeaks = mutableListOf<Double>()
                        
                        for (inputD in roundedInputs) {
                            for ((dbD, _) in allPeaks) {
                                val roundedDbD = round(dbD * 1000.0) / 1000.0
                                if (abs(roundedDbD - inputD) <= tolerance) {
                                    score++
                                    matchedPeaks.add(roundedDbD)
                                    break
                                }
                            }
                        }

                        // Add if at least 2 peaks match
                        if (score >= 2) {
                            android.util.Log.d(TAG, "✅ Match #${matches.size + 1}: $name (score=$score/$dValues.size, category: $selectedCategory)")
                            matches.add(mapOf(
                                "substance_name" to name,
                                "formula" to normalizeFormula(formula),
                                "system" to system,
                                "score" to score,
                                "percentage" to (score.toDouble() / dValues.size * 100),
                                "peaks" to allPeaks,
                                "matched_peaks" to matchedPeaks,
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

        // Sort by score (highest first)
        return matches.sortedWith(compareByDescending<Map<String, Any>> { it["score"] as Int }
            .thenBy { it["substance_name"] as String })
    }

    private fun buildCandidateQuery(dValues: List<Double>, tolerance: Double): Pair<String, Array<String>> {
        val parts = mutableListOf<String>()
        val args = mutableListOf<String>()

        for (d in dValues) {
            val min = d - tolerance
            val max = d + tolerance
            for (col in 1..20) {
                parts += "SELECT rowid AS rid FROM minerals WHERE d$col BETWEEN ? AND ?"
                args += String.format(Locale.US, "%.6f", min)
                args += String.format(Locale.US, "%.6f", max)
            }
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
                // Compounds can be either organic or inorganic
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
        val totalAtoms = countTotalAtoms(formula)
        return totalAtoms > 100
    }

    private fun countTotalAtoms(formula: String): Int {
        var total = 0
        var i = 0
        while (i < formula.length) {
            if (!formula[i].isLetter() || !formula[i].isUpperCase()) return Int.MAX_VALUE
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
        if (!formula.contains("C")) return false
        
        // Check for carbonates (inorganic)
        if (formula.contains("CO3") || formula.contains("HCO3")) return false
        
        // Check for metal carbides (inorganic)
        val metalCarbides = listOf("CaC", "FeC", "SiC", "WC", "TiC")
        for (mc in metalCarbides) {
            if (formula.contains(mc)) return false
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
        
        val gcdValue = elements.map { it.second }.reduce { a, b -> gcd(a, b) }
        val reduced = elements.map { (sym, count) -> sym to (count / gcdValue) }

        return buildString {
            reduced.forEach { (sym, count) ->
                append(sym)
                if (count > 1) append(count)
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

    private fun parseDValue(editText: EditText, raw: String): Double? {
        val value = raw.toDoubleOrNull() ?: return null
        val rounded = round(value * 1000.0) / 1000.0
        val formatted = String.format("%.3f", rounded)
        if (raw != formatted) {
            editText.setText(formatted)
            editText.setSelection(formatted.length)
        }
        return rounded
    }
    
    private fun displayResultAtIndex(index: Int) {
        if (index < 0 || index >= currentResults.size) return
        
        currentResultIndex = index
        val best = currentResults[index]
        val mineralName = (best["substance_name"] as? String)?.trim().orEmpty()
        val matchedPeaks = (best["matched_peaks"] as? List<Double>) ?: emptyList()
        
        val resultsContainer = findViewById<LinearLayout>(R.id.resultsContainer)
        resultsContainer.removeAllViews()
        resultsContainer.visibility = android.view.View.VISIBLE
        
        // Navigation header
        val navView = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 12)
        }
        
        navView.addView(Button(this).apply {
            text = "← Prev"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            isEnabled = index > 0
            setOnClickListener { displayResultAtIndex(currentResultIndex - 1) }
        })
        
        navView.addView(TextView(this).apply {
            text = "${index + 1} / ${currentResults.size}"
            gravity = android.view.Gravity.CENTER
            setPadding(16, 0, 16, 0)
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        
        navView.addView(Button(this).apply {
            text = "Next →"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            isEnabled = index < currentResults.size - 1
            setOnClickListener { displayResultAtIndex(currentResultIndex + 1) }
        })
        resultsContainer.addView(navView)
        
        // Content
        resultsContainer.addView(TextView(this).apply {
            text = "Match ${index + 1} of ${currentResults.size} (Category: $selectedCategory)"
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 8, 0, 16)
        })
        
        resultsContainer.addView(TextView(this).apply {
            val displayName = if (mineralName.isNotBlank()) mineralName else "---"
            text = "Mineral/Compound: $displayName"
            textSize = 14f
            setPadding(0, 4, 0, 4)
        })
        
        resultsContainer.addView(TextView(this).apply {
            val rawFormula = best["formula"] as? String
            val displayFormula = normalizeFormula(rawFormula)
            text = "Chemical Formula: ${if (displayFormula.isNotBlank()) displayFormula else "---"}"
            textSize = 14f
            setPadding(0, 4, 0, 4)
        })
        
        resultsContainer.addView(TextView(this).apply {
            val crystalSystemCode = best["system"] as? String ?: ""
            text = "Crystal System: ${if (crystalSystemCode.isNotBlank()) crystalSystemCode else "---"}"
            textSize = 14f
            setPadding(0, 4, 0, 4)
        })
        
        resultsContainer.addView(TextView(this).apply {
            val totalInputs = if (lastSearchCount > 0) lastSearchCount else 3
            text = "Match Quality: ${(best["percentage"] as Double).toInt()}% (${best["score"]} out of $totalInputs d-spacings)"
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 8, 0, 4)
        })
        
        if (matchedPeaks.isNotEmpty()) {
            resultsContainer.addView(TextView(this).apply {
                text = "Matched d-spacings: ${matchedPeaks.joinToString { String.format("%.3f", it) }} Å"
                textSize = 14f
                setPadding(0, 4, 0, 8)
            })
        }
        
        resultsContainer.addView(TextView(this).apply {
            text = "Top Diffraction Peaks:"
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 12, 0, 8)
        })
        
        val allPeaks = best["peaks"] as? List<Pair<Double, Double>> ?: emptyList()
        if (allPeaks.isNotEmpty()) {
            allPeaks.take(20).forEachIndexed { idx, (d, intensity) ->
                resultsContainer.addView(TextView(this).apply {
                    val matched = matchedPeaks.any { abs(it - round(d * 1000.0) / 1000.0) <= HANAWALT_TOLERANCE }
                    val prefix = if (matched) "✓ " else "  "
                    text = "$prefix${idx + 1}. d = ${String.format("%.3f", d)} Å, I = ${String.format("%.1f", intensity)}"
                    textSize = 12f
                    setPadding(24, 2, 0, 2)
                    if (matched) {
                        setTextColor(android.graphics.Color.parseColor("#FF4CAF50"))
                    }
                })
            }
        } else {
            resultsContainer.addView(TextView(this).apply {
                text = "No peak data available"
                textSize = 12f
                setPadding(24, 2, 0, 2)
            })
        }
        
        Toast.makeText(this, "Showing result ${index + 1} of ${currentResults.size}", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        backgroundExecutor.shutdown()
        codDb?.let {
            if (it.isOpen) it.close()
        }
    }
}
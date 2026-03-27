package com.xrdxrf.app

import android.app.ProgressDialog
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs

class XrdMIdentificationActivity : AppCompatActivity() {
    // Uses cod_xrd_final.db from assets as the mineral database

    private lateinit var spinnerCategory: Spinner
    private lateinit var actvMineralName: AutoCompleteTextView
    private lateinit var btnSearchMineral: Button

    private var codDb: SQLiteDatabase? = null
    private var progressDialog: ProgressDialog? = null
    private val backgroundExecutor = Executors.newSingleThreadExecutor()
    private var idColumnName: String? = null
    private var isDbReady = false
    private var searchableColumns: List<String> = emptyList()
    private var tableColumns: Set<String> = emptySet()

    private data class MineralResult(
        val mineralName: String?,
        val compoundName: String?,
        val formula: String?
    )

    // Category options based on database structure
    private val categories = arrayOf("All", "Minerals", "Compounds", "Organic", "Inorganic")
    private var selectedCategory = "All"

    companion object {
        private const val MAX_SEARCH_RESULTS = 80
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_xrd_m_identification)

        spinnerCategory = findViewById(R.id.spinnerCategory)
        actvMineralName = findViewById(R.id.actvMineralName)
        btnSearchMineral = findViewById(R.id.btnSearchMineral)

        // Setup category spinner
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCategory.adapter = adapter

        spinnerCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: android.view.View, pos: Int, id: Long) {
                selectedCategory = categories[pos]
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                selectedCategory = "All"
            }
        }

        btnSearchMineral.isEnabled = false

        // Initialize database on background thread to avoid ANR on screen open.
        initializeDatabaseAsync()

        // Setup autocomplete
        setupAutoComplete()

        // Search button
        btnSearchMineral.setOnClickListener {
            try {
                val mineralName = actvMineralName.text.toString().trim()
                if (mineralName.isNotEmpty()) {
                    if (!isDbReady) {
                        Toast.makeText(this, "Database is preparing, please wait...", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    searchMineral(mineralName)
                } else {
                    Toast.makeText(this, "Please enter a mineral name", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("SEARCH_ERROR", "Search button click error", e)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupAutoComplete() {
        backgroundExecutor.execute {
            try {
                val minerals = loadCommonSubstances()
                runOnUiThread {
                    try {
                        val acAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, minerals)
                        actvMineralName.setAdapter(acAdapter)
                    } catch (e: Exception) {
                        android.util.Log.e("AUTOCOMPLETE_UI_ERROR", "Failed to set adapter", e)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("AUTOCOMPLETE_ERROR", "Failed to load common substances", e)
                // Fallback to loading from database
                try {
                    val minerals = getMineralSuggestions()
                    runOnUiThread {
                        try {
                            val acAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, minerals)
                            actvMineralName.setAdapter(acAdapter)
                        } catch (e: Exception) {
                            android.util.Log.e("AUTOCOMPLETE_UI_ERROR", "Failed to set adapter (fallback)", e)
                        }
                    }
                } catch (e2: Exception) {
                    android.util.Log.e("AUTOCOMPLETE_FALLBACK_ERROR", "Fallback also failed", e2)
                }
            }
        }
    }

    private fun loadCommonSubstances(): List<String> {
        val suggestions = mutableListOf<String>()
        try {
            val jsonString = assets.open("common_substances.json").bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                suggestions.add(jsonArray.getString(i))
            }
        } catch (e: Exception) {
            android.util.Log.e("COMMON_SUBSTANCES_ERROR", "Failed to load common_substances.json", e)
        }
        return suggestions
    }

    private fun getMineralSuggestions(): List<String> {
        val suggestions = mutableListOf<String>()

        val db = codDb ?: return suggestions

        val query = """
            SELECT DISTINCT substance_name
            FROM minerals
            WHERE substance_name IS NOT NULL AND substance_name != ''
            ORDER BY substance_name
            LIMIT 1000
        """.trimIndent()

        try {
            db.rawQuery(query, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    try {
                        val name = cursor.getString(0)
                        if (!name.isNullOrBlank()) {
                            suggestions.add(name)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("CURSOR_READ_ERROR", "Error reading cursor row", e)
                        continue
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SUGGESTIONS_QUERY_ERROR", "Failed to execute suggestions query", e)
        }

        return suggestions
    }

    private fun initializeDatabaseAsync() {
        val dbName = "cod_xrd_final.db"
        backgroundExecutor.execute {
            try {
                val assetList = assets.list("")
                val fileExists = assetList?.contains(dbName) == true
                android.util.Log.d("DB_DEBUG", "File '$dbName' in assets: $fileExists")
                if (!fileExists) {
                    android.util.Log.e("DB_DEBUG", "Available assets: ${assetList?.joinToString(", ")}")
                }

                android.util.Log.d("DB_DEBUG", "Attempting to open: $dbName")
                AssetDbUtils.copyAssetToDatabasePath(this, dbName, dbName)
                AssetDbUtils.ensureXrdIndexes(this, dbName)
                codDb = AssetDbUtils.openReadonlyDb(this, dbName, dbName)

                tableColumns = resolveTableColumns(codDb)
                idColumnName = resolveIdColumn(codDb)
                searchableColumns = resolveSearchableColumns(codDb)
                android.util.Log.d("DB_DEBUG", "Resolved ID column: ${idColumnName ?: "<none>"}")
                android.util.Log.d("DB_DEBUG", "Searchable columns: ${searchableColumns.joinToString()}")
                android.util.Log.d("DB_DEBUG", "✓ Database opened successfully: ${codDb?.path}")

                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    isDbReady = codDb != null
                    btnSearchMineral.isEnabled = isDbReady
                }
            } catch (e: Exception) {
                android.util.Log.e("DB_ERROR", "Failed to open database", e)
                codDb = null
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    isDbReady = false
                    btnSearchMineral.isEnabled = false
                    Toast.makeText(
                        this,
                        "Database error: ${e.message}\\nSee Logcat for details",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun searchMineral(mineralName: String) {
        val startTime = System.currentTimeMillis()

        progressDialog = ProgressDialog(this).apply {
            setTitle("Searching")
            setMessage("Finding minerals...")
            setCancelable(false)
            show()
        }

        backgroundExecutor.execute {
            try {
                val result = findMineralResult(mineralName)

                // ✅ FIX: Wrap UI updates in try-catch to prevent crash on display error
                runOnUiThread {
                    try {
                        val elapsedMs = System.currentTimeMillis() - startTime
                        progressDialog?.dismiss()
                        
                        if (isFinishing) return@runOnUiThread

                        if (result != null) {
                            displayResult(result, elapsedMs)
                        } else {
                            val tvTimeToResult = findViewById<TextView>(R.id.tvTimeToResult)
                            tvTimeToResult?.text = "Time to First Result: ---"
                            Toast.makeText(this, "No results found for category: $selectedCategory", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("UI_UPDATE_ERROR", "Error updating UI", e)
                        progressDialog?.dismiss()
                        Toast.makeText(this, "Display Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("SEARCH_ASYNC_ERROR", "Async search failed", e)
                runOnUiThread {
                    progressDialog?.dismiss()
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun findMineralResult(mineralName: String): MineralResult? {
        val db = codDb ?: return null

        val searchPattern = "%$mineralName%"
        val compactPattern = "%${mineralName.replace(" ", "")}%"
        val candidates = mutableListOf<MineralResult>()
        val idColumn = idColumnName

        val whereParts = mutableListOf<String>()
        val args = mutableListOf<String>()
        val hasSubstanceName = tableColumns.contains("substance_name")
        val hasFormula = tableColumns.contains("chemical_formula")
        val hasCrystalSystem = tableColumns.contains("crystal_system")
        val hasC2MineralName = tableColumns.contains("c2mineralname")
        val hasC1CompoundName = tableColumns.contains("c1compoundname")

        // Primary fast path for known columns
        if (hasSubstanceName) {
            whereParts += "(substance_name LIKE ? COLLATE NOCASE)"
            args += searchPattern
            whereParts += "(REPLACE(substance_name, ' ', '') LIKE ? COLLATE NOCASE)"
            args += compactPattern
        }
        if (hasFormula) {
            whereParts += "(chemical_formula LIKE ? COLLATE NOCASE)"
            args += searchPattern
        }
        if (hasC2MineralName) {
            whereParts += "(c2mineralname LIKE ? COLLATE NOCASE)"
            args += searchPattern
        }
        if (hasC1CompoundName) {
            whereParts += "(c1compoundname LIKE ? COLLATE NOCASE)"
            args += searchPattern
        }

        if (!idColumn.isNullOrBlank()) {
            whereParts += "(CAST($idColumn AS TEXT) = ?)"
            args += mineralName
            whereParts += "(CAST($idColumn AS TEXT) LIKE ?)"
            args += searchPattern
        }

        // Fallback for schema variants (e.g., c2mineralname / c1compoundname / other id columns)
        for (col in searchableColumns) {
            if (col == "substance_name" || col == "chemical_formula" || col == idColumn) continue
            whereParts += "(CAST($col AS TEXT) LIKE ? COLLATE NOCASE)"
            args += searchPattern
        }

        if (whereParts.isEmpty()) return null

        val displayNameExpr = when {
            hasSubstanceName && hasC2MineralName && hasC1CompoundName -> "COALESCE(NULLIF(substance_name, ''), NULLIF(c2mineralname, ''), NULLIF(c1compoundname, ''))"
            hasSubstanceName && hasC2MineralName -> "COALESCE(NULLIF(substance_name, ''), NULLIF(c2mineralname, ''))"
            hasSubstanceName && hasC1CompoundName -> "COALESCE(NULLIF(substance_name, ''), NULLIF(c1compoundname, ''))"
            hasSubstanceName -> "substance_name"
            hasC2MineralName && hasC1CompoundName -> "COALESCE(NULLIF(c2mineralname, ''), NULLIF(c1compoundname, ''))"
            hasC2MineralName -> "c2mineralname"
            hasC1CompoundName -> "c1compoundname"
            else -> "''"
        }
        val formulaExpr = if (hasFormula) "chemical_formula" else "NULL"
        val crystalExpr = if (hasCrystalSystem) "crystal_system" else "NULL"

        val baseQuery = """
            SELECT
                $displayNameExpr AS display_name,
                $formulaExpr AS chemical_formula,
                $crystalExpr AS crystal_system
            FROM minerals
            WHERE ${whereParts.joinToString(" OR ")}
            LIMIT $MAX_SEARCH_RESULTS
        """.trimIndent()

        try {
            db.rawQuery(baseQuery, args.toTypedArray())?.use { cursor ->
                while (cursor.moveToNext()) {
                    try {
                        val name = cursor.getString(0)
                        val formula = cursor.getString(1)
                        // val crystalSystem = cursor.getString(2) // Not needed here
                        if (!name.isNullOrBlank() && matchesCategoryByFormula(formula)) {
                            candidates.add(MineralResult(name, name, formula))
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("CANDIDATE_READ_ERROR", "Error reading candidate row", e)
                        continue
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("FIND_MINERAL_QUERY_ERROR", "Failed to execute findMineralResult query", e)
            return null
        }

        return selectBestResult(mineralName, candidates)
    }

    private fun resolveSearchableColumns(db: SQLiteDatabase?): List<String> {
        if (db == null) return emptyList()
        val cols = mutableListOf<String>()
        try {
            for (c in resolveTableColumns(db)) {
                val looksSearchable = c.contains("name") || c.contains("formula") || c.endsWith("id") || c.contains("id_")
                if (looksSearchable) cols.add(c)
            }
        } catch (e: Exception) {
            android.util.Log.e("DB_DEBUG", "Failed resolving searchable columns", e)
        }
        return cols.distinct()
    }

    private fun resolveTableColumns(db: SQLiteDatabase?): Set<String> {
        if (db == null) return emptySet()
        val columns = mutableSetOf<String>()
        try {
            db.rawQuery("PRAGMA table_info(minerals)", null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (nameIdx >= 0) {
                        columns.add(cursor.getString(nameIdx).lowercase(Locale.US))
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("DB_DEBUG", "Failed to inspect minerals schema", e)
        }
        return columns
    }

    private fun selectBestResult(input: String, candidates: List<MineralResult>): MineralResult? {
        if (candidates.isEmpty()) return null

        fun scoreName(name: String?): Int {
            val n = name?.trim().orEmpty()
            if (n.isBlank()) return 0
            return when {
                n.equals(input, ignoreCase = true) -> 100
                n.startsWith(input, ignoreCase = true) -> 90
                n.contains(input, ignoreCase = true) -> 80
                else -> 0
            }
        }

        val scored = candidates.map { candidate ->
            val score = maxOf(scoreName(candidate.mineralName), scoreName(candidate.compoundName))
            val normalized = normalizeFormula(candidate.formula)
            val reducedAtoms = totalAtoms(normalized)
            val formulaLength = normalized.length
            Triple(candidate, score, Pair(reducedAtoms, formulaLength))
        }

        val bestScore = scored.maxOf { it.second }
        val bestCandidates = scored.filter { it.second == bestScore }

        return bestCandidates.minByOrNull { (it.third as Pair<Int, Int>).first }?.first ?: candidates.first()
    }

    private fun resolveIdColumn(db: SQLiteDatabase?): String? {
        if (db == null) return null
        val knownNames = listOf("cod_id", "mineral_id", "id", "entry_id", "codid")
        val columns = mutableSetOf<String>()
        try {
            db.rawQuery("PRAGMA table_info(minerals)", null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (nameIdx >= 0) {
                        columns.add(cursor.getString(nameIdx).lowercase(Locale.US))
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("DB_DEBUG", "Failed to inspect minerals schema", e)
            return null
        }

        for (candidate in knownNames) {
            if (columns.contains(candidate)) return candidate
        }

        return columns.firstOrNull { it.endsWith("id") }
    }

    private fun normalizeFormula(formula: String?): String {
        val raw = formula?.trim().orEmpty()
        if (raw.isBlank()) return ""

        val elements = mutableListOf<Pair<String, Int>>()
        var i = 0
        while (i < raw.length) {
            val ch = raw[i]
            if (!ch.isLetter() || !ch.isUpperCase()) {
                return raw
            }

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

    private fun totalAtoms(formula: String): Int {
        if (formula.isBlank()) return Int.MAX_VALUE
        var total = 0
        var i = 0
        while (i < formula.length) {
            val ch = formula[i]
            if (!ch.isLetter() || !ch.isUpperCase()) return Int.MAX_VALUE
            var j = i + 1
            while (j < formula.length && formula[j].isLetter() && formula[j].isLowerCase()) {
                j++
            }
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

    private fun matchesCategoryByFormula(formula: String): Boolean {
        return when (selectedCategory) {
            "Minerals" -> {
                val isLargeOrganic = isLargeOrganicMolecule(formula)
                !isLargeOrganic
            }
            "Compounds" -> true
            "Organic" -> isOrganicFormula(formula)
            "Inorganic" -> !isOrganicFormula(formula)
            else -> true
        }
    }

    private fun isLargeOrganicMolecule(formula: String): Boolean {
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
        if (!formula.contains("C")) return false
        val forbidden = listOf("Ca", "Cd", "Ce", "Cl", "Co", "Cr", "Cs", "Cu")
        if (forbidden.any { formula.contains(it) }) return false
        return true
    }

    private fun displayResult(result: MineralResult, elapsedMs: Long) {
        val mineralName = result.mineralName
        val compoundName = result.compoundName
        val formula = result.formula
        val lookupName = if (!mineralName.isNullOrBlank()) mineralName else compoundName

        var crystalSystem = ""
        val crystalSystems = mutableListOf<Pair<String, String>>()
        val allPeaks = Array(20) { Pair("/", "/") }

        val db = codDb

        if (!lookupName.isNullOrBlank() && db != null) {
            // ✅ FIX: Wrap crystal system queries in try-catch to handle missing tables/data
            try {
                db.rawQuery(
                    """SELECT DISTINCT crystal_system_name, space_group 
                       FROM crystal_systems 
                       WHERE mineral_name = ? COLLATE NOCASE
                       ORDER BY crystal_system_name, space_group""",
                    arrayOf(lookupName)
                )?.use { csCursor ->
                    while (csCursor.moveToNext()) {
                        try {
                            val systemName = csCursor.getString(0) ?: ""
                            val spaceGroup = csCursor.getString(1) ?: ""
                            if (systemName.isNotBlank()) {
                                crystalSystems.add(Pair(systemName, spaceGroup))
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("CRYSTAL_CURSOR_ERROR", "Error reading crystal system cursor", e)
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("CRYSTAL_QUERY_ERROR", "Failed to query crystal_systems (exact)", e)
            }

            if (crystalSystems.isEmpty()) {
                try {
                    db.rawQuery(
                        """SELECT DISTINCT crystal_system_name, space_group 
                           FROM crystal_systems 
                           WHERE (mineral_name LIKE ? OR compound_name LIKE ? OR chemical_formula LIKE ?) COLLATE NOCASE
                           ORDER BY crystal_system_name, space_group""",
                        arrayOf("%$lookupName%", "%$lookupName%", "%${formula ?: ""}%")
                    )?.use { csCursor ->
                        while (csCursor.moveToNext()) {
                            try {
                                val systemName = csCursor.getString(0) ?: ""
                                val spaceGroup = csCursor.getString(1) ?: ""
                                if (systemName.isNotBlank()) {
                                    crystalSystems.add(Pair(systemName, spaceGroup))
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("CRYSTAL_CURSOR_ERROR", "Error reading cursor (partial)", e)
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("CRYSTAL_QUERY_ERROR", "Failed to query crystal_systems (partial)", e)
                }
            }

            // Get peaks
            var peaksFound = 0
            var found = false

            // ✅ FIX: Wrap peak query in try-catch
            try {
                db.rawQuery(
                    "SELECT crystal_system, d1, i1, d2, i2, d3, i3, d4, i4, d5, i5, d6, i6, d7, i7, d8, i8, d9, i9, d10, i10, d11, i11, d12, i12, d13, i13, d14, i14, d15, i15, d16, i16, d17, i17, d18, i18, d19, i19, d20, i20 FROM minerals WHERE substance_name = ? COLLATE NOCASE LIMIT 1",
                    arrayOf(lookupName)
                )?.use { codCursor ->
                    if (codCursor.moveToFirst()) {
                        found = true
                        try {
                            crystalSystem = codCursor.getString(0) ?: ""
                        } catch (e: Exception) {
                            android.util.Log.e("PEAK_READ_ERROR", "Error reading crystal_system", e)
                            crystalSystem = ""
                        }
                        for (i in 0 until 20) {
                            try {
                                val dIndex = 1 + i * 2
                                val iIndex = 2 + i * 2
                                if (!codCursor.isNull(dIndex) && !codCursor.isNull(iIndex)) {
                                    val d = codCursor.getDouble(dIndex)
                                    val intensity = codCursor.getDouble(iIndex)
                                    if (d > 0 && intensity >= 0 && intensity <= 1000) {
                                        allPeaks[peaksFound] = Pair(String.format("%.3f", d), String.format("%.1f", intensity))
                                        peaksFound++
                                    }
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("PEAK_READ_ERROR", "Error reading peak $i", e)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("PEAKS_QUERY_ERROR", "Failed to query peaks by substance_name", e)
            }

            if (!found && !formula.isNullOrBlank()) {
                try {
                    db.rawQuery(
                        "SELECT crystal_system, d1, i1, d2, i2, d3, i3, d4, i4, d5, i5, d6, i6, d7, i7, d8, i8, d9, i9, d10, i10, d11, i11, d12, i12, d13, i13, d14, i14, d15, i15, d16, i16, d17, i17, d18, i18, d19, i19, d20, i20 FROM minerals WHERE chemical_formula = ? COLLATE NOCASE LIMIT 1",
                        arrayOf(formula)
                    )?.use { codCursor ->
                        if (codCursor.moveToFirst()) {
                            try {
                                crystalSystem = codCursor.getString(0) ?: ""
                            } catch (e: Exception) {
                                android.util.Log.e("PEAK_READ_ERROR", "Error reading crystal_system (formula)", e)
                                crystalSystem = ""
                            }
                            for (i in 0 until 20) {
                                try {
                                    val dIndex = 1 + i * 2
                                    val iIndex = 2 + i * 2
                                    if (!codCursor.isNull(dIndex) && !codCursor.isNull(iIndex)) {
                                        val d = codCursor.getDouble(dIndex)
                                        val intensity = codCursor.getDouble(iIndex)
                                        if (d > 0 && intensity >= 0 && intensity <= 1000) {
                                            allPeaks[peaksFound] = Pair(String.format("%.3f", d), String.format("%.1f", intensity))
                                            peaksFound++
                                        }
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("PEAK_READ_ERROR", "Error reading peak $i (formula)", e)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("PEAKS_QUERY_ERROR", "Failed to query peaks by chemical_formula", e)
                }
            }
        } else if (lookupName.isNullOrBlank()) {
            android.util.Log.w("DISPLAY_RESULT", "lookupName is null or blank")
        } else if (db == null) {
            android.util.Log.w("DISPLAY_RESULT", "Database is null - showing limited results")
        }

        // ✅ FIX: Safe findViewById calls
        val tvCrystalSystemResult = findViewById<TextView>(R.id.tvCrystalSystemResult)
        val tvFormulaResult = findViewById<TextView>(R.id.tvFormulaResult)
        val llDSpacingContainer = findViewById<LinearLayout>(R.id.llDSpacingContainer)
        val tvTimeToResult = findViewById<TextView>(R.id.tvTimeToResult)

        val crystalSystemDisplay = when {
            crystalSystems.isNotEmpty() -> {
                crystalSystems.joinToString(" | ") { (systemName, spaceGroup) ->
                    if (spaceGroup.isNotBlank()) {
                        "$systemName ($spaceGroup)"
                    } else {
                        systemName
                    }
                }
            }
            crystalSystem.isNotBlank() -> crystalSystem
            else -> "---"
        }
        tvCrystalSystemResult?.text = crystalSystemDisplay

        val normalizedFormula = normalizeFormula(formula)
        tvFormulaResult?.text = if (normalizedFormula.isNotBlank()) normalizedFormula else "---"

        llDSpacingContainer?.removeAllViews()
        allPeaks.forEachIndexed { index, (d, intensity) ->
            val item = TextView(this).apply {
                text = "${index + 1}. d=$d Å | I=$intensity%"
                textSize = 12f
                setPadding(0, 2, 0, 2)
            }
            llDSpacingContainer?.addView(item)
        }

        tvTimeToResult?.text = String.format(Locale.US, "Time to First Result: %.3f second", elapsedMs / 1000.0)

        Toast.makeText(this, "Found: $lookupName", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        backgroundExecutor.shutdown()
        codDb?.let {
            if (it.isOpen) {
                it.close()
            }
        }
    }
}
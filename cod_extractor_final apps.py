# ================= SUPPRESS WARNINGS FIRST =================
import warnings
warnings.filterwarnings("ignore", category=Warning)

# ================= IMPORTS =================
import os
import sqlite3
import json
import math
from pymatgen.core import Structure
from pymatgen.analysis.diffraction.xrd import XRDCalculator

# ================= CONFIGURATION =================
CIF_ROOT = r"D:\Project XRD XRF\COD SQL Database\cod-cifs-mysql\cif"
OUTPUT_DB = "cod_xrd_final.db"
OUTPUT_JSON = "cod_xrd_final.json"
MAX_PEAKS = 20

# ================= UTILITY FUNCTIONS =================
def classify_category(formula, name):
    if not formula:
        return "UNKNOWN"
    f_upper = formula.upper()
    # Organic: C+H but not carbonate/cyanide
    if "C" in f_upper and "H" in f_upper:
        if not any(x in f_upper for x in ["CO3", "CN", "C2O4"]):
            return "ORGANIC"
    # Mineral: known names
    if name and any(mineral in name.lower() for mineral in [
        "quartz", "calcite", "feldspar", "mica", "gypsum",
        "halite", "dolomite", "hematite", "magnetite", "kaolinite"
    ]):
        return "MINERAL"
    return "INORGANIC"

def extract_cif_metadata(cif_path):
    metadata = {'name': None, 'crystal_system': None}
    try:
        with open(cif_path, 'r', encoding='utf-8', errors='ignore') as f:
            for line in f:
                line = line.strip()
                if '_chemical_name_mineral' in line and metadata['name'] is None:
                    parts = line.split(None, 1)
                    if len(parts) > 1:
                        metadata['name'] = parts[1].strip("'\"")
                elif '_chemical_name_common' in line and metadata['name'] is None:
                    parts = line.split(None, 1)
                    if len(parts) > 1:
                        metadata['name'] = parts[1].strip("'\"")
                elif '_symmetry_space_group_crystal_system' in line:
                    parts = line.split(None, 1)
                    if len(parts) > 1:
                        metadata['crystal_system'] = parts[1].strip("'\"").lower()
    except Exception:
        pass
    return metadata

def calculate_xrd_peaks_pymatgen(cif_path, max_peaks=20):
    """Generate top N XRD peaks using pymatgen (Cu-Kα radiation)"""
    try:
        structure = Structure.from_file(cif_path)
        
        # Skip empty or tiny structures
        if structure.num_sites == 0 or structure.num_sites > 500:
            return None
        
        # Skip organics with implicit H (common in COD)
        comp_str = str(structure.composition)
        if "H" in comp_str and structure.num_sites < 20:
            return None
        
        # Generate XRD pattern (5°–90° 2θ, Cu-Kα = 1.5406 Å)
        calculator = XRDCalculator(wavelength="CuKa")
        pattern = calculator.get_pattern(structure, two_theta_range=(5, 90))
        
        if len(pattern.x) == 0:
            return None
        
        # Convert 2θ → d-spacing via Bragg's law
        peaks = []
        for i in range(len(pattern.x)):
            two_theta = pattern.x[i]
            intensity = pattern.y[i]
            theta_rad = math.radians(two_theta / 2)
            d = 1.5406 / (2 * math.sin(theta_rad))
            peaks.append((round(d, 3), round(intensity, 2)))
        
        # Sort by intensity (descending) — Hanawalt method
        peaks.sort(key=lambda x: x[1], reverse=True)
        return peaks[:max_peaks]
    
    except Exception:
        return None

# ================= MAIN PROCESSOR =================
def main():
    print("=" * 60)
    print("COD EXTRACTOR — FINAL (PYMATGEN + CLEAN OUTPUT)")
    print("=" * 60)
    
    # Collect CIF files
    cif_files = []
    for root, _, files in os.walk(CIF_ROOT):
        for file in files:
            if file.endswith('.cif'):
                cif_files.append(os.path.join(root, file))
    
    total_files = len(cif_files)
    print(f"Found {total_files:,} CIF files")
    
    # Initialize database
    conn = sqlite3.connect(OUTPUT_DB)
    cursor = conn.cursor()
    cols = [
        "cod_id TEXT PRIMARY KEY",
        "substance_name TEXT",
        "category TEXT",
        "crystal_system TEXT",
        "chemical_formula TEXT"
    ]
    for i in range(1, MAX_PEAKS + 1):
        cols.extend([f"d{i} REAL", f"i{i} REAL"])
    cursor.execute(f"CREATE TABLE IF NOT EXISTS minerals ({', '.join(cols)})")
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_name ON minerals(substance_name)")
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_d1 ON minerals(d1)")
    conn.commit()
    conn.close()
    
    # Process files
    processed = 0
    failed = 0
    all_json = []
    
    print(f"\nProcessing {total_files:,} files...")
    for i, cif_path in enumerate(cif_files):
        if i % 500 == 0:
            progress = (i / total_files) * 100
            print(f"\rProgress: {progress:.1f}% ({i:,}/{total_files:,})", end="", flush=True)
        
        filename = os.path.basename(cif_path)
        cod_id = filename.replace('.cif', '')
        metadata = extract_cif_metadata(cif_path)
        peaks = calculate_xrd_peaks_pymatgen(cif_path, MAX_PEAKS)
        
        if peaks is None:
            failed += 1
            continue
        
        # Get chemical formula
        try:
            structure = Structure.from_file(cif_path)
            formula = structure.composition.reduced_formula
        except Exception:
            formula = "UNKNOWN"
        
        category = classify_category(formula, metadata['name'])
        
        # Build DB record
        record = [cod_id, metadata['name'], category, metadata['crystal_system'], formula]
        for j in range(MAX_PEAKS):
            if j < len(peaks):
                record.extend([peaks[j][0], peaks[j][1]])
            else:
                record.extend([None, None])
        
        # Save to database
        try:
            conn = sqlite3.connect(OUTPUT_DB)
            placeholders = ", ".join(["?"] * len(record))
            conn.execute(f"INSERT OR REPLACE INTO minerals VALUES ({placeholders})", record)
            conn.commit()
            conn.close()
            
            # JSON backup (optional)
            json_entry = {
                'cod_id': cod_id,
                'substance_name': metadata['name'],
                'category': category,
                'crystal_system': metadata['crystal_system'],
                'chemical_formula': formula,
                'peaks': [{'d': d, 'i': i} for d, i in peaks]
            }
            all_json.append(json_entry)
            processed += 1
        except Exception:
            failed += 1
    
    # Save JSON backup
    with open(OUTPUT_JSON, 'w', encoding='utf-8') as f:
        json.dump(all_json, f, indent=2, ensure_ascii=False)
    
    print("\n" + "=" * 60)
    print("EXTRACTION COMPLETE")
    print("=" * 60)
    print(f"Total files:            {total_files:,}")
    print(f"Successfully processed: {processed:,}")
    print(f"Failed:                 {failed:,}")
    print(f"Success rate:           {(processed/total_files*100):.1f}%")
    print(f"Database:               {OUTPUT_DB}")
    print(f"JSON backup:            {OUTPUT_JSON}")
    print("=" * 60)

if __name__ == "__main__":
    main()
# ================= SUPPRESS WARNINGS FIRST =================
import warnings
warnings.filterwarnings("ignore", category=Warning)

# ================= IMPORTS =================
import os
import sqlite3
import math
import re
import time
import multiprocessing as mp
from collections import defaultdict
import numpy as np
from pymatgen.core import Structure
from pymatgen.analysis.diffraction.xrd import XRDCalculator

# ================= CONFIGURATION =================
# 🔧 OPTIMIZED SETTINGS FOR RYZEN 7 / 16GB RAM
CIF_ROOT = r"D:\Project XRD XRF\COD SQL Database\cod-cifs-mysql\cif"
OUTPUT_DB = "cod_xrd_final.db"
MAX_PEAKS = 20

# Process Management
# Use all logical cores (Ryzen 7 usually has 16 logical threads)
NUM_PROCESSES = mp.cpu_count() 

# Memory & Speed Tuning
# Smaller chunks prevent RAM spikes on 16GB machines
CHUNK_SIZE = 250  
# Batch inserts (Higher = Faster DB writes, but higher RAM usage)
DB_COMMIT_BATCH_SIZE = 1000 

# Performance targets
TARGET_SPEED = 120  # files/minute (Ryzen 7 can easily handle this)

# ================= COMPREHENSIVE MINERAL GROUPS (FULLY PRESERVED) =================
# [Keeping your exact MINERAL_GROUPS dictionary here - same as your code]
MINERAL_GROUPS = {
    'sulfide': [
        'cinnabar', 'pyrite', 'chalcopyrite', 'galena', 'sphalerite', 'pyrrhotite',
        'arsenopyrite', 'bornite', 'chalcocite', 'covellite', 'molybdenite',
        'stibnite', 'realgar', 'orpiment', 'argentite', 'acanthite',
        'pentlandite', 'millerite', 'cobaltite', 'sperrylite', 'cinnabarite'
    ],
    'sulfosalt': [
        'tetrahedrite', 'tennantite', 'enargite', 'bournonite', 'jamesonite',
        'boulangerite', 'proustite', 'pyrargyrite', 'stephanite', 'polybasite'
    ],
    'oxide': [
        'hematite', 'magnetite', 'ilmenite', 'rutile', 'anatase', 'brookite',
        'corundum', 'cassiterite', 'chromite', 'spinel', 'magnetite',
        'goethite', 'limonite', 'bauxite', 'pyrolusite', 'psilomelane',
        'columbite', 'tantalite', 'wolframite', 'scheelite', 'uraninite'
    ],
    'hydroxide': [
        'brucite', 'gibbsite', 'diaspore', 'boehmite', 'manganite',
        'romanechite', 'todorokite'
    ],
    'halide': [
        'halite', 'fluorite', 'sylvite', 'carnallite', 'cryolite',
        'atacamite', 'chlorargyrite', 'iodargyrite'
    ],
    'carbonate': [
        'calcite', 'aragonite', 'dolomite', 'magnesite', 'siderite',
        'rhodochrosite', 'smithsonite', 'cerussite', 'malachite',
        'azurite', 'strontianite', 'witherite', 'thermonatrite',
        'trona', 'natron', 'hydrozincite', 'aurichalcite'
    ],
    'nitrate': [
        'nitratine', 'niter', 'nitrobarite', 'gwihabaite'
    ],
    'borate': [
        'borax', 'kernite', 'colemanite', 'ulexite', 'boracite',
        'szaibelyite', 'indigirite', 'inyoite'
    ],
    'sulfate': [
        'gypsum', 'anhydrite', 'barite', 'celestine', 'anglesite',
        'alunite', 'jarosite', 'chalcanthite', 'melanterite',
        'epsomite', 'mirabilite', 'thenardite', 'glauberite', 'polyhalite',
        'kieserite', 'langbeinite', 'schoenite'
    ],
    'chromate': [
        'crocoite', 'phoenicochroite', 'vauquelinite', 'wulfenite'
    ],
    'phosphate': [
        'apatite', 'monazite', 'xenotime', 'vivianite', 'turquoise',
        'variscite', 'strengite', 'childrenite', 'amblygonite',
        'triphylite', 'lithiophilite', 'heterosite', 'purpurite',
        'wavellite', 'crandallite', 'gorceixite', 'goyazite'
    ],
    'arsenate': [
        'erythrite', 'annabergite', 'scorodite', 'olivenite',
        'adamite', 'legrandite', 'austinite', 'conichalcite'
    ],
    'vanadate': [
        'vanadinite', 'descloizite', 'mottramite', 'carnotite',
        'tyuyamunite', 'volborthite'
    ],
    'tungstate': [
        'wolframite', 'scheelite', 'stolzite', 'raspite',
        'ferberite', 'hubnerite', 'sanmartinite'
    ],
    'molybdate': [
        'wulfenite', 'powellite', 'ilsemannite', 'lindgrenite'
    ],
    'nesosilicate': [
        'olivine', 'forsterite', 'fayalite', 'tephroite', 'knebelite',
        'garnet', 'almandine', 'pyrope', 'spessartine', 'grossular',
        'andradite', 'uvarovite', 'zircon', 'thorite', 'sphene',
        'topaz', 'staurolite', 'chloritoid', 'sillimanite', 'andalusite',
        'kyanite', 'mullite', 'phenakite', 'willemite'
    ],
    'sorosilicate': [
        'epidote', 'zoisite', 'clinozoisite', 'pumpellyite',
        'vesuvianite', 'hemimorphite', 'lawsonite', 'ilvaite'
    ],
    'cyclosilicate': [
        'beryl', 'cordierite', 'tourmaline', 'schorl', 'elbaite',
        'dravite', 'uvite', 'dioptase', 'pezzottaite', 'sugilite'
    ],
    'pyroxene': [
        'enstatite', 'bronzite', 'hypersthene', 'diopside', 'hedenbergite',
        'augite', 'pigeonite', 'jadeite', 'aegirine', 'spodumene',
        'wollastonite', 'rhodonite', 'pectolite'
    ],
    'amphibole': [
        'tremolite', 'actinolite', 'hornblende', 'gedrite', 'anthophyllite',
        'riebeckite', 'glaucophane', 'arfvedsonite', 'cummingtonite',
        'grunerite', 'richterite', 'kaersutite'
    ],
    'phyllosilicate': [
        'mica', 'muscovite', 'biotite', 'phlogopite', 'lepidolite',
        'clay', 'kaolinite', 'halloysite', 'dickite', 'nacrite',
        'serpentine', 'chrysotile', 'antigorite', 'lizardite',
        'talc', 'pyrophyllite', 'chlorite', 'clinochlore', 'chamosite',
        'vermiculite', 'smectite', 'montmorillonite', 'beidellite',
        'nontronite', 'saponite', 'sepiolite', 'palygorskite'
    ],
    'silica': [
        'quartz', 'low quartz', 'high quartz', 'alpha quartz', 'beta quartz',
        'cristobalite', 'tridymite', 'coesite', 'stishovite', 'moganite',
        'chalcedony', 'chert', 'flint', 'jasper', 'agate', 'onyx', 'opal'
    ],
    'feldspar': [
        'orthoclase', 'microcline', 'sanidine', 'adularia', 'albite',
        'oligoclase', 'andesine', 'labradorite', 'bytownite', 'anorthite',
        'plagioclase', 'celsian', 'hyalophane', 'anorthoclase'
    ],
    'feldspathoid': [
        'nepheline', 'kalsilite', 'leucite', 'sodalite', 'nosean',
        'hauyne', 'cancrinite', 'vishnevite', 'pollucite'
    ],
    'zeolite': [
        'analcime', 'chabazite', 'clinoptilolite', 'heulandite',
        'laumontite', 'mordenite', 'natrolite', 'phillipsite',
        'stilbite', 'thomsonite', 'scapolite', 'marialite', 'meionite'
    ],
    'native_element': [
        'gold', 'silver', 'copper', 'platinum', 'palladium', 'iridium',
        'iron', 'nickel', 'arsenic', 'antimony', 'bismuth', 'sulfur',
        'diamond', 'graphite', 'carbon'
    ],
    'organic_mineral': [
        'whewellite', 'weddellite', 'mellite', 'earlandite',
        'abelsonite', 'karpatite', 'evenkite', 'fichtelite',
        'hartite', 'pingguite'
    ]
}

# Flatten for quick lookup
ALL_MINERAL_NAMES = set()
for group, minerals in MINERAL_GROUPS.items():
    ALL_MINERAL_NAMES.update(minerals)

# ================= CONSTANTS =================
CU_WAVELENGTH = 1.54184  # Å (Cu Kα weighted average)
ENCODINGS = ['utf-8', 'latin-1', 'cp1252', 'iso-8859-1', 'ascii', 'cp850']

# ================= OPTIMIZED LOOKUP STRUCTURES =================
# Pre-compile regex patterns once
NAME_PATTERNS = [
    (re.compile(r'^\s*_chemical_name_mineral\s+([^\n]+)', re.IGNORECASE), 10),
    (re.compile(r'^\s*_mineral_name\s+([^\n]+)', re.IGNORECASE), 9),
    (re.compile(r'^\s*_chemical_name_common\s+([^\n]+)', re.IGNORECASE), 8),
    (re.compile(r'^\s*_chemical_name_systematic\s+([^\n]+)', re.IGNORECASE), 7),
    (re.compile(r'^\s*_chemical_name_structure_type\s+([^\n]+)', re.IGNORECASE), 6),
    (re.compile(r'^\s*_chemical_name\s+([^\n]+)', re.IGNORECASE), 5),
    (re.compile(r'^\s*_chemical_formula_structural\s+([^\n]+)', re.IGNORECASE), 4),
    (re.compile(r'^\s*_chemical_formula_analytical\s+([^\n]+)', re.IGNORECASE), 3),
    (re.compile(r'^\s*_chemical_formula_sum\s+([^\n]+)', re.IGNORECASE), 2),
]

# ================= ENHANCED CRYSTAL SYSTEM EXTRACTION =================
# Comprehensive list of crystal system patterns
CRYSTAL_PATTERNS = [
    # Primary crystal system tags
    (re.compile(r'^\s*_symmetry_cell_setting\s+([^\n]+)', re.IGNORECASE), 10),
    (re.compile(r'^\s*_symmetry_space_group_crystal_system\s+([^\n]+)', re.IGNORECASE), 9),
    (re.compile(r'^\s*_space_group_crystal_system\s+([^\n]+)', re.IGNORECASE), 9),
    (re.compile(r'^\s*_symmetry_crystal_system\s+([^\n]+)', re.IGNORECASE), 9),
    (re.compile(r'^\s*_cell_crystal_system\s+([^\n]+)', re.IGNORECASE), 8),
    
    # Secondary tags that might contain crystal system
    (re.compile(r'^\s*_symmetry_space_group_name_H-M\s+([^\n]+)', re.IGNORECASE), 7),
    (re.compile(r'^\s*_symmetry_space_group_name_Hall\s+([^\n]+)', re.IGNORECASE), 6),
    (re.compile(r'^\s*_symmetry_point_group\s+([^\n]+)', re.IGNORECASE), 5),
    (re.compile(r'^\s*_symmetry_Int_Tables_number\s+([0-9]+)', re.IGNORECASE), 4),
]

# Space group to crystal system mapping (for number lookup)
SPACE_GROUP_TO_SYSTEM = {
    range(1, 3): 'triclinic',      # 1-2: Triclinic
    range(3, 16): 'monoclinic',     # 3-15: Monoclinic
    range(16, 75): 'orthorhombic',  # 16-74: Orthorhombic
    range(75, 143): 'tetragonal',   # 75-142: Tetragonal
    range(143, 168): 'trigonal',    # 143-167: Trigonal
    range(168, 195): 'hexagonal',   # 168-194: Hexagonal
    range(195, 231): 'cubic',       # 195-230: Cubic
}

# Point group to crystal system mapping
POINT_GROUP_TO_SYSTEM = {
    '1': 'triclinic', '-1': 'triclinic',
    '2': 'monoclinic', 'm': 'monoclinic', '2/m': 'monoclinic',
    '222': 'orthorhombic', 'mm2': 'orthorhombic', 'mmm': 'orthorhombic',
    '4': 'tetragonal', '-4': 'tetragonal', '4/m': 'tetragonal', '422': 'tetragonal',
    '4mm': 'tetragonal', '-42m': 'tetragonal', '4/mmm': 'tetragonal',
    '3': 'trigonal', '-3': 'trigonal', '32': 'trigonal', '3m': 'trigonal', '-3m': 'trigonal',
    '6': 'hexagonal', '-6': 'hexagonal', '6/m': 'hexagonal', '622': 'hexagonal',
    '6mm': 'hexagonal', '-6m2': 'hexagonal', '6/mmm': 'hexagonal',
    '23': 'cubic', 'm-3': 'cubic', '432': 'cubic', '-43m': 'cubic', 'm-3m': 'cubic'
}

CRYSTAL_SYSTEM_MAP = {
    'triclinic': 'triclinic', 'anorthic': 'triclinic',
    'monoclinic': 'monoclinic',
    'orthorhombic': 'orthorhombic',
    'tetragonal': 'tetragonal',
    'trigonal': 'trigonal', 'rhombohedral': 'trigonal',
    'hexagonal': 'hexagonal',
    'cubic': 'cubic', 'isometric': 'cubic'
}

# Valid crystal systems for final validation
VALID_CRYSTAL_SYSTEMS = set(['triclinic', 'monoclinic', 'orthorhombic', 
                             'tetragonal', 'trigonal', 'hexagonal', 'cubic'])

# O(1) Lookup for mineral groups
MINERAL_TO_GROUP = {}
for group, minerals in MINERAL_GROUPS.items():
    for mineral in minerals:
        MINERAL_TO_GROUP[mineral] = group.upper()

# ================= ENHANCED CRYSTAL SYSTEM EXTRACTION FUNCTION =================

def extract_crystal_system_robust(cif_content):
    """
    Extract crystal system with comprehensive regex patterns and multiple fallbacks.
    Returns one of: triclinic, monoclinic, orthorhombic, tetragonal, trigonal, hexagonal, cubic
    """
    
    # Strategy 1: Direct pattern matching with priority
    for pattern, priority in CRYSTAL_PATTERNS:
        match = pattern.search(cif_content)
        if match:
            value = match.group(1).strip("'\"").lower()
            
            # Check direct match with crystal systems
            for key, system in CRYSTAL_SYSTEM_MAP.items():
                if key in value:
                    return system
            
            # Check if it's a space group number
            if pattern == CRYSTAL_PATTERNS[-1]:  # The space group number pattern
                try:
                    sg_num = int(re.search(r'\d+', value).group())
                    for sg_range, system in SPACE_GROUP_TO_SYSTEM.items():
                        if sg_num in sg_range:
                            return system
                except:
                    pass
            
            # Check if it's a point group
            if value in POINT_GROUP_TO_SYSTEM:
                return POINT_GROUP_TO_SYSTEM[value]
    
    # Strategy 2: Search for crystal system names in the whole CIF
    cif_lower = cif_content.lower()
    found_systems = []
    for system in VALID_CRYSTAL_SYSTEMS:
        if system in cif_lower:
            found_systems.append(system)
        elif system == 'trigonal' and 'rhombohedral' in cif_lower:
            found_systems.append('trigonal')
    
    # Return the first found system (prioritize specific mentions)
    if found_systems:
        # Prioritize explicit mentions over implied ones
        for system in ['cubic', 'hexagonal', 'tetragonal', 'orthorhombic', 
                      'monoclinic', 'triclinic', 'trigonal']:
            if system in found_systems:
                return system
    
    # Strategy 3: Check for key indicators in the formula or mineral name
    # This is a fallback for files that don't explicitly state the system
    
    return None  # Let the main function default to "UNKNOWN"

# ================= REST OF YOUR UTILITY FUNCTIONS =================
# [Keeping all your existing functions exactly as they were]

def read_cif_with_fallback(filepath):
    """Read CIF file with multiple encoding fallbacks"""
    for encoding in ENCODINGS:
        try:
            with open(filepath, 'r', encoding=encoding, errors='ignore') as f:
                content = f.read()
                if content and len(content) > 100:
                    return content, encoding
        except Exception:
            continue
    return None, None

def clean_cif_content(content):
    """Clean problematic CIF content for better parsing"""
    if not content: return None
    content = ''.join(char for char in content if ord(char) >= 32 or char in '\n\r\t')
    content = re.sub(r'_\s+', '_', content)
    content = re.sub(r';\s*\n\s*;', ';', content)
    if 'data_' not in content[:1000]:
        mineral_match = re.search(r'([A-Za-z]+(?:-[A-Za-z]+)?)', content[:500])
        if mineral_match:
            content = f"data_{mineral_match.group(1)}\n" + content
    return content

def parse_structure_with_fallback(cif_content):
    """Try multiple strategies to parse structure from CIF"""
    try:
        return Structure.from_str(cif_content, fmt="cif")
    except Exception:
        pass
    
    try:
        cleaned = clean_cif_content(cif_content)
        return Structure.from_str(cleaned, fmt="cif")
    except Exception:
        pass
    
    try:
        a = re.search(r'_cell_length_a\s+([0-9.]+)', cif_content)
        b = re.search(r'_cell_length_b\s+([0-9.]+)', cif_content)
        c = re.search(r'_cell_length_c\s+([0-9.]+)', cif_content)
        alpha = re.search(r'_cell_angle_alpha\s+([0-9.]+)', cif_content)
        beta = re.search(r'_cell_angle_beta\s+([0-9.]+)', cif_content)
        gamma = re.search(r'_cell_angle_gamma\s+([0-9.]+)', cif_content)
        
        if a and b and c:
            from pymatgen.core import Lattice, Structure
            lattice = Lattice.from_parameters(
                float(a.group(1)), float(b.group(1)), float(c.group(1)),
                float(alpha.group(1)) if alpha else 90,
                float(beta.group(1)) if beta else 90,
                float(gamma.group(1)) if gamma else 90
            )
            return Structure(lattice, ["H"], [[0, 0, 0]])
    except Exception:
        pass
    
    return None

def extract_mineral_name_robust(cif_content, filename):
    """Extract mineral name with multiple fallback strategies"""
    for pattern, _ in NAME_PATTERNS:
        match = pattern.search(cif_content)
        if match:
            raw = match.group(1).strip("'\"")
            if raw and not raw.startswith('?'):
                raw_lower = raw.lower()
                for mineral in ALL_MINERAL_NAMES:
                    if mineral in raw_lower:
                        return mineral.title()
                cleaned = re.sub(r'\s+(crystal|structure|mineral)$', '', raw, flags=re.IGNORECASE)
                return cleaned.strip()
    
    clean_name = re.sub(r'^(cod|entry|id|file)_', '', filename, flags=re.IGNORECASE)
    clean_name = re.sub(r'_\d+$', '', clean_name)
    clean_name = re.sub(r'\.cif$', '', clean_name)
    
    if clean_name and len(clean_name) < 30:
        clean_lower = clean_name.lower()
        for mineral in ALL_MINERAL_NAMES:
            if mineral in clean_lower:
                return mineral.title()
        return clean_name.title()
    
    return None

def calculate_xrd_peaks_robust(structure, max_peaks=20):
    """Calculate XRD peaks with error handling"""
    try:
        calculator = XRDCalculator(wavelength="CuKa")
        pattern = calculator.get_pattern(structure, two_theta_range=(5, 90))
        
        if len(pattern.x) == 0: return []
        
        two_theta = np.array(pattern.x)
        intensity = np.array(pattern.y)
        
        theta_rad = np.radians(two_theta / 2)
        sin_theta = np.sin(theta_rad)
        # Prevent division by zero
        sin_theta = np.where(sin_theta < 1e-10, 1e-10, sin_theta)
        d_spacing = CU_WAVELENGTH / (2 * sin_theta)
        
        peaks = [(round(d_spacing[i], 3), round(intensity[i], 2)) 
                 for i in range(len(two_theta))]
        
        peaks.sort(key=lambda x: x[1], reverse=True)
        return peaks[:max_peaks]
    except Exception:
        return []

def process_single_cif_robust(cif_path):
    """Process a single CIF file with maximum recovery strategies"""
    try:
        filename = os.path.basename(cif_path)
        cod_id = os.path.splitext(filename)[0]
        
        # 1. Read file
        cif_content, encoding_used = read_cif_with_fallback(cif_path)
        if cif_content is None: return None
        
        # 2. Parse structure
        structure = parse_structure_with_fallback(cif_content)
        
        # 3. Metadata - NOW USING ENHANCED CRYSTAL SYSTEM EXTRACTION
        mineral_name = extract_mineral_name_robust(cif_content, filename)
        crystal_system = extract_crystal_system_robust(cif_content)
        
        # 4. Formula & Peaks
        formula = "UNKNOWN"
        peaks = []
        
        if structure:
            try:
                formula = structure.composition.reduced_formula
                peaks = calculate_xrd_peaks_robust(structure, MAX_PEAKS)
            except Exception:
                pass
        
        # 5. Classification
        category = classify_category(formula, mineral_name)
        mineral_group = classify_mineral_group(formula, mineral_name)
        
        # 6. Build Record
        record = [
            cod_id,
            mineral_name or cod_id,
            mineral_group,
            category,
            crystal_system or "UNKNOWN",
            formula
        ]
        
        for j in range(MAX_PEAKS):
            if j < len(peaks):
                record.extend([peaks[j][0], peaks[j][1]])
            else:
                record.extend([None, None])
        
        return record
        
    except Exception:
        # Last resort: Minimal record to prevent loss of ID
        try:
            filename = os.path.basename(cif_path)
            cod_id = os.path.splitext(filename)[0]
            return [
                cod_id, cod_id.replace('_', ' ').title(), "UNKNOWN", 
                "MINERAL", "UNKNOWN", "UNKNOWN"
            ] + [None] * (MAX_PEAKS * 2)
        except:
            return None

def worker_process_robust(cif_paths):
    """Worker function optimized for stability and memory"""
    results = []
    for cif_path in cif_paths:
        result = process_single_cif_robust(cif_path)
        if result:
            results.append(result)
    return results

def extract_mineral_name_from_text(text):
    """Extract mineral name from any text field"""
    if not text: return None
    text_lower = text.lower()
    found_minerals = [m for m in ALL_MINERAL_NAMES if m in text_lower]
    if found_minerals:
        return sorted(found_minerals, key=len, reverse=True)[0].title()
    
    patterns = [
        r'name\s*=\s*["\']?([a-zA-Z\s-]+)',
        r'mineral\s*=\s*["\']?([a-zA-Z\s-]+)',
        r'([a-zA-Z\s-]+?)(?:\s+crystal|\s+structure|\s+mineral|$)'
    ]
    for pattern in patterns:
        match = re.search(pattern, text_lower)
        if match:
            candidate = match.group(1).strip()
            if 2 < len(candidate) < 30:
                return candidate.title()
    return None

def classify_mineral_group(formula, name):
    if not name and not formula: return "UNKNOWN"
    name_lower = name.lower() if name else ""
    formula_upper = formula.upper() if formula else ""
    
    if name_lower:
        for mineral, group in MINERAL_TO_GROUP.items():
            if mineral in name_lower:
                return group
    
    if formula_upper:
        if ('S' in formula_upper and 'O' not in formula_upper and 
            any(m in formula_upper for m in ['HG', 'FE', 'CU', 'PB', 'ZN', 'AS', 'SB'])):
            return "SULFIDE"
        if 'CO3' in formula_upper: return "CARBONATE"
        if 'SO4' in formula_upper: return "SULFATE"
        if ('O' in formula_upper and len(formula_upper) < 10 and 
            any(m in formula_upper for m in ['FE', 'AL', 'TI', 'MN', 'CR'])):
            return "OXIDE"
        if 'SI' in formula_upper and 'O' in formula_upper: return "SILICATE"
    
    return "MINERAL"

def classify_category(formula, name):
    if not formula and not name: return "UNKNOWN"
    
    if name:
        name_lower = name.lower()
        for mineral in ALL_MINERAL_NAMES:
            if mineral in name_lower:
                return "MINERAL"
    
    if formula:
        f_upper = formula.upper()
        if "C" in f_upper and "H" in f_upper:
            if not any(x in f_upper for x in ["CO3", "CN", "C2O4"]):
                metals = ['NA', 'K', 'CA', 'MG', 'FE', 'AL', 'CU', 'ZN']
                if any(metal in f_upper for metal in metals): return "METAL-ORGANIC"
                return "ORGANIC"
        if "CO3" in f_upper: return "CARBONATE"
        if "O" in f_upper and any(m in f_upper for m in ['FE', 'AL', 'TI', 'MN', 'CR']): return "OXIDE"
        if "S" in f_upper and "O" not in f_upper:
            metals = ['HG', 'FE', 'CU', 'PB', 'ZN', 'AS', 'SB']
            if any(metal in f_upper for metal in metals): return "SULFIDE"
        if "SI" in f_upper and "O" in f_upper: return "SILICATE"
        return "INORGANIC"
    return "UNKNOWN"

# ================= MAIN PROCESSOR =================
def main():
    print("=" * 70)
    print("COD EXTRACTOR - MAXIMUM RECOVERY (OPTIMIZED)")
    print("=" * 70)
    print(f"CPU Cores: {NUM_PROCESSES}")
    print(f"RAM: 16 GB")
    print(f"Chunk Size: {CHUNK_SIZE} files/worker")
    print(f"DB Batch Size: {DB_COMMIT_BATCH_SIZE}")
    print("=" * 70)
    
    # 1. Collect Files
    print("Scanning for CIF files...")
    start_collect = time.time()
    cif_files = []
    for root, _, files in os.walk(CIF_ROOT):
        for file in files:
            if file.endswith('.cif'):
                cif_files.append(os.path.join(root, file))
    
    total_files = len(cif_files)
    print(f"Found {total_files:,} CIF files in {time.time() - start_collect:.1f} seconds")
    
    if total_files == 0:
        print("No files found. Exiting.")
        return

    # 2. Prepare Database
    conn = sqlite3.connect(OUTPUT_DB)
    cursor = conn.cursor()
    
    # 🔥 MAXIMUM SPEED PRAGMAS
    cursor.execute("PRAGMA journal_mode=WAL")       # Faster writes
    cursor.execute("PRAGMA synchronous=OFF")      # Fastest mode (Safe for extraction script)
    cursor.execute("PRAGMA cache_size=-1000000")   # 1GB Cache
    cursor.execute("PRAGMA temp_store=MEMORY")
    cursor.execute("PRAGMA locking_mode=EXCLUSIVE") # Single writer = no locks needed
    
    # Create Table
    cols = [
        "cod_id TEXT PRIMARY KEY",
        "substance_name TEXT",
        "mineral_group TEXT",
        "category TEXT",
        "crystal_system TEXT",
        "chemical_formula TEXT"
    ]
    for i in range(1, MAX_PEAKS + 1):
        cols.extend([f"d{i} REAL", f"i{i} REAL"])
    
    cursor.execute("DROP TABLE IF EXISTS minerals")
    cursor.execute(f"CREATE TABLE minerals ({', '.join(cols)})")
    conn.commit()
    
    # 3. Chunk Files for Memory Stability
    # Explicitly creating small chunks ensures workers don't consume all 16GB RAM
    chunks = [cif_files[i:i + CHUNK_SIZE] for i in range(0, len(cif_files), CHUNK_SIZE)]
    print(f"Split {total_files:,} files into {len(chunks)} chunks.")
    
    # 4. Process
    print(f"\nProcessing with {NUM_PROCESSES} workers...")
    print("-" * 70)
    
    start_time = time.time()
    processed = 0
    failed = 0
    
    category_stats = defaultdict(int)
    group_stats = defaultdict(int)
    crystal_system_stats = defaultdict(int)  # Track crystal systems
    
    # Using Pool for parallelism
    with mp.Pool(processes=NUM_PROCESSES) as pool:
        # imap_unordered provides results as they come
        # We process chunks to control memory flow
        for chunk_idx, results in enumerate(pool.imap_unordered(worker_process_robust, chunks)):
            
            # 🔥 BATCH INSERTION (The Main Speedup)
            # Instead of inserting row-by-row, we insert all results from this chunk at once
            if results:
                # Execute all inserts for this chunk in one go
                placeholders = ", ".join(["?"] * len(results[0]))
                try:
                    cursor.executemany(f"INSERT OR REPLACE INTO minerals VALUES ({placeholders})", results)
                    
                    # Update stats locally to avoid DB reads
                    for r in results:
                        category_stats[r[3]] += 1
                        group_stats[r[2]] += 1
                        crystal_system_stats[r[4]] += 1  # Track crystal systems
                        
                    processed += len(results)
                    failed += (CHUNK_SIZE - len(results))
                except Exception as e:
                    print(f"\nDB Error in chunk {chunk_idx}: {e}")
                    failed += len(results)
            
            # Commit periodically to save progress
            if (chunk_idx + 1) % 5 == 0:
                conn.commit()
            
            # Progress
            elapsed = time.time() - start_time
            speed = processed / elapsed if elapsed > 0 else 0
            progress = (processed / total_files) * 100
            eta = (total_files - processed) / speed if speed > 0 else 0
            
            print(f"\r📊 {progress:.1f}% | Speed: {speed*60:.1f}/min | "
                  f"✅ {processed:,} | ❌ {failed:,} | "
                  f"ETA: {eta/60:.0f} min", flush=True)

    # Final commit
    conn.commit()
    
    # 5. Create Indexes (Done at the end for speed)
    print("\n\nCreating indexes...")
    index_start = time.time()
    
    cursor.execute("CREATE INDEX idx_name ON minerals(substance_name)")
    cursor.execute("CREATE INDEX idx_group ON minerals(mineral_group)")
    cursor.execute("CREATE INDEX idx_category ON minerals(category)")
    cursor.execute("CREATE INDEX idx_crystal ON minerals(crystal_system)")  # New index for crystal system
    cursor.execute("CREATE INDEX idx_d1 ON minerals(d1)")
    cursor.execute("CREATE INDEX idx_d2 ON minerals(d2)")
    cursor.execute("CREATE INDEX idx_d3 ON minerals(d3)")
    cursor.execute("CREATE INDEX idx_d4 ON minerals(d4)")
    cursor.execute("CREATE INDEX idx_d5 ON minerals(d5)")
    
    conn.commit()
    print(f"Indexes created in {time.time() - index_start:.1f}s")
    
    # Final Stats
    cursor.execute("SELECT COUNT(*) FROM minerals")
    final_count = cursor.fetchone()[0]
    
    # Get crystal system distribution
    print("\n📊 Crystal System Distribution:")
    cursor.execute("SELECT crystal_system, COUNT(*) FROM minerals GROUP BY crystal_system ORDER BY COUNT(*) DESC LIMIT 10")
    for system, count in cursor.fetchall():
        if system and system != "UNKNOWN":
            print(f"   {system}: {count:,}")
    
    conn.close()
    
    print("\n" + "=" * 70)
    print("EXTRACTION COMPLETE")
    print("=" * 70)
    print(f"Total files:      {total_files:,}")
    print(f"Processed:       {processed:,}")
    print(f"Failed:          {failed:,}")
    print(f"Success Rate:    {(processed/total_files*100):.2f}%")
    print(f"Total Time:      {(time.time() - start_time)/60:.1f} min")
    print(f"Final DB Size:   {final_count:,} entries")
    print(f"Output DB:       {OUTPUT_DB}")
    print("=" * 70)

if __name__ == "__main__":
    mp.freeze_support() # Essential for Windows multiprocessing
    main()
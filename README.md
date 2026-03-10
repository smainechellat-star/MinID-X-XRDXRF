# MinID-X: Mobile Mineral Identification
*Android application for offline XRD/XRF-based mineral identification using the Crystallography Open Database*

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.18924300.svg)](https://doi.org/10.5281/zenodo.18924300)
**Version 2.0**: [10.5281/zenodo.xxxxxxxx](https://doi.org/10.5281/zenodo.xxxxxxxx) under revision

A powerful Android application for mineral identification using X-Ray Diffraction (XRD) and X-Ray Fluorescence (XRF) reference data. MinID-X brings laboratory-grade mineralogical analysis to field settings through intelligent database compression and offline-first architecture, enabling rapid mineral identification in remote geological environments.

## 📖 Table of Contents
- [Features](#features)
- [System Requirements](#system-requirements)
- [Installation](#installation)
- [First Launch - Database Extraction](#first-launch---database-extraction)
- [How to Use](#how-to-use)
- [Database Information](#database-information)
- [Search Methodology](#search-methodology)
- [Data and Code Availability](#data-and-code-availability)
- [Citing This Work](#citing-this-work)
- [Reproducing the Database](#reproducing-the-database)
- [Technical Details](#technical-details)
- [Troubleshooting](#troubleshooting)
- [Credits](#credits)
- [License](#license)
- [Support](#support)

## Features

### XRD (X-Ray Diffraction) Analysis
- **Multi-Peak Identification**: Enter 3-10 d-spacing values (Å) for mineral identification
- **Bidirectional Search**: 
  - Data → Mineral: Match measured d-spacings against database
  - Mineral → Data: Retrieve complete reference patterns for known minerals
- **Hanawalt Method**: Enhanced search algorithm with ±0.02 Å tolerance window
- **Smart Ranking**: Candidates ranked by match score (% of peaks matched)
- **Category Filtering**: Exclude organic molecules, focus on geological minerals
- **Response Time**: ~2 seconds average identification time

### XRF (X-Ray Fluorescence) Analysis
- **Comprehensive Element Lookup**: Access K-, L-, and M-series emission/absorption lines for Z = 1-103
- **Educational Messaging**: Contextual explanations for elements where XRF is inapplicable:
  - Hydrogen/Helium: No inner shells for fluorescence
  - Lithium: Kα emission (~54 eV) impractical due to air absorption
  - Superheavy elements (Z≥104): No experimental data (synthetic, short-lived)
- **Authoritative Sources**: All values traceable to NIST XCOM and LBNL standards
- **Response Time**: <0.01 seconds average lookup time

### Database Features
- Compressed SQLite database (196 MB) derived from Crystallography Open Database
- 521,901 filtered mineral entries with complete metadata
- Top 20 diffraction peaks per mineral (by relative intensity)
- Chemical formulas, crystal systems, COD identifiers
- Fully offline operation—no internet connectivity required
- Indexed architecture for constant-time lookups regardless of database size

## System Requirements

- **Android Version**: 7.0 (API Level 24) or higher
- **RAM**: Minimum 2 GB recommended
- **Storage**: ~400 MB (50 MB for app + 299 MB for extracted database)
- **Screen Size**: Compatible with all Android devices (phones, tablets)
- **Processor**: Any modern ARM processor
- **Free Storage**: At least 400 MB recommended for first-launch extraction

## Installation

### From APK/AAB
1. Download the latest `app-release.apk` or `app-release.aab` from the [Releases](https://github.com/smainechellat-star/MinID-X/releases) page
2. Enable installation from unknown sources in your device settings:
   - Settings → Security → Unknown Sources (enable)
3. Install the APK on your Android device
4. Grant storage permissions when prompted
5. **First Launch**: The app will automatically extract the compressed database (may take 1-2 minutes). A progress dialog will appear. **Do not close the app during this process.**

### From Source Code
1. Clone the repository:
   ```bash
   git clone https://github.com/smainechellat-star/MinID-X.git
   cd MinID-X


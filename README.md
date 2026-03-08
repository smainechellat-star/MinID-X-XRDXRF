# com.xrdxrf.app
MinID-X: Offline mineral identification via XRD (d-spacing) &amp; XRF (elemental) analysis. Uses COD/RRUFF data, works without internet. Package: com.xrdxrf.app
# MinID-X: Mobile Mineral Identification

## Overview
Source code for the MinID-X Android application described in [Paper Title] (Computers & Geosciences).

## Installation & Setup
1. Clone this repository.
2. **Download the Database:** The database file (000 MB) and Excel data file are hosted on Zenodo due to size constraints.
   - Download Link: [INSERT ZENODO DOI LINK]
   - Place the downloaded `minidx_database.db` (or `.zip`) into: `app/src/main/assets/`
3. Open the project in Android Studio.
4. Build and run.

## Preprocessing Scripts
The `preprocessing_scripts/` folder contains the Python tools used to convert raw COD CIF files into the SQLite database.

## License
[Insert License, e.g., MIT or GPL]

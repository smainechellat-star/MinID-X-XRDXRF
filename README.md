# XRD-XRF Mineral Identification App

A powerful Android application for mineral identification using X-Ray Diffraction (XRD) and X-Ray Fluorescence (XRF) data analysis. This app leverages advanced mineralogical databases to identify unknown mineral samples through multiple search methodologies.

## Features

### XRD (X-Ray Diffraction) Analysis
- **XRD M Identification**: Identify minerals by entering M spacing values
- **XRD d(A) Identification Single-phase**: Search using d-spacing values in Angstroms for single-phase samples
- **XRD One Peak Identification**: Quick identification using a single d-spacing peak

### XRF (X-Ray Fluorescence) Analysis
- **Element Detection**: Identify elements present in samples using XRF spectra
- **Direct Search**: Find minerals by element composition
- **Energy-based Lookup**: Search using emission energy values

### Database Features
- Comprehensive mineral database (COD - Crystallography Open Database)
- Complete XRD patterns with d-spacing and intensity data
- Chemical formula information with normalization
- Crystal system classification
- Automatic filtering of invalid/NULL substance names
- Category-based filtering (Minerals, Compounds, Organic, Inorganic)
- Search execution time display
- Easy-to-use search and filter capabilities

## System Requirements

- **Android Version**: 8.0 (API Level 26) or higher
- **RAM**: Minimum 2GB recommended
- **Storage**: ~150MB (50MB for app, 100MB for databases)
- **Screen Size**: Works on all Android devices (phones, tablets)
- **Processor**: Any modern ARM processor

## Installation

### From Google Play Store
[Coming Soon]

### From APK/AAB
1. Download the latest `app-release.apk` from the [Releases](https://github.com/smainechellat-star/XRDXRF/releases) page
2. Enable installation from unknown sources in your device settings
3. Install the APK on your Android device
4. **First Launch**: The app will automatically extract compressed databases (may take 1-2 minutes). A progress dialog will appear. Do not close the app during this process.

### From Source Code
1. Clone the repository:
   ```bash
   git clone https://github.com/smainechellat-star/XRDXRF.git
   cd XRDXRF
   ```
2. Open the project in Android Studio
3. Build and run on your device or emulator

## First Launch - Database Extraction

⚠️ **Important**: On first launch, the app automatically extracts mineralogical databases from compressed archives.

### What Happens
- App downloads/extracts ~50MB of mineral data to device storage
- Progress dialog shows extraction status
- Device needs adequate free storage space
- Process takes approximately **1-2 minutes** depending on device speed

### Requirements
- **Free Storage**: At least 100MB recommended
- **Memory**: 2GB RAM minimum
- **Time**: Allow 1-2 minutes for initial setup
- **Network**: No internet required (data is bundled with app)

### What NOT to Do
❌ Do not force close the app during extraction  
❌ Do not uninstall during the process  
❌ Do not turn off device during extraction  

The extraction happens only once. Subsequent launches are instant.

## How to Use

### Main Screen
When you launch the app, you'll see the main menu with two primary options:
- **XRD Analysis**: For X-Ray Diffraction data
- **XRF Analysis**: For X-Ray Fluorescence data

### XRD Analysis - Getting Started

1. **Select Analysis Type**
   - Tap "XRD Analysis" from the main menu
   - Choose one of three identification methods:

#### Method 1: XRD M Identification
   - Enter up to 5 M spacing values
   - Adjust tolerance slider for matching precision
   - Select category filter (All, Minerals, Compounds, Organic, Inorganic)
   - View matching minerals in results

#### Method 2: XRD d(A) Identification Single-phase
   - Enter up to 5 d-spacing values (in Angstroms)
   - Designed for single-phase mineral samples
   - Use tolerance slider to adjust matching precision
   - Select category filter to narrow results
   - Results show minerals matching your peaks

#### Method 3: XRD One Peak Identification
   - Quick search using just one d-spacing peak
   - Perfect for rapid preliminary identification
   - Select category to narrow results (All, Minerals, Compounds, Organic, Inorganic)
   - Shows all matching substances with valid names
   - Timer displays search execution time

### XRF Analysis

1. **Select Element Search**
   - Enter the element symbol (e.g., Fe, Cu, Zn)
   - Choose search method:
     - Direct element search
     - Energy-based lookup

2. **View Results**
   - Results show all minerals containing the element
   - Display includes mineral name, chemical formula, and occurrence frequency

### Search Results

For any search method:
1. **Review Results**: Minerals matching your criteria appear in a list
2. **View Details**: Tap a mineral to see:
   - Full mineral name
   - Chemical formula
   - Crystal system
   - D-spacing data
   - References
3. **Search Time**: Display shows search execution time

### Settings

Access app settings to:
- Adjust search tolerance
- Configure display preferences
- Clear cached data
- View database information

### About & Help

- **About Screen**: View app version and credits
- **Contact Us**: Reach out for support or feedback
- **Reference**: Access mineralogical references and databases used

## Database Information

The app includes:
- **COD Database**: Crystallography Open Database minerals
- **RRUFF Database**: Spectroscopy data and mineral references
- **Complete XRD Patterns**: For accurate mineral identification
- **Chemical Formulas**: Standard mineral chemical compositions

Database is automatically extracted on first launch.

## Search Methodology

### Hanawalt Method (XRD)
The app implements the Hanawalt method for XRD identification:
1. Identify the strongest XRD peak (primary)
2. Compare against database strongest peaks
3. Refine with secondary peaks
4. Tolerance adjusts matching criteria (±0.02 Å default)

### Precision
- **d-spacing accuracy**: ±0.01-0.05 Å depending on instrument
- **Tolerance adjustment**: Available for user preference
- **Multiple peak matching**: Increases identification confidence

## Troubleshooting

### Database Not Loading
- Ensure you have at least 100MB free storage
- Check internet connection for first launch
- Try restarting the app

### No Results Found
- Increase tolerance value (±0.05 Å or higher)
- Verify data entry for typos
- Check if mineral is in the database

### App Crashes
- Ensure Android version 8.0 or higher
- Clear app cache: Settings > Apps > XRD-XRF > Clear Cache
- Reinstall the app

## Performance Tips

1. **First Launch**: App extracts database (may take 1-2 minutes) - do not interrupt
2. **Storage**: Keep at least 100MB free space for optimal performance
3. **Memory**: Close unnecessary apps for better performance
4. **Search Speed**: One Peak Identification is fastest for preliminary searches
5. **Tolerance**: Start with default 0.02 Å, increase if no results found

## Technical Details

- **Language**: Kotlin/Java
- **Database**: SQLite
- **Minimum SDK**: Android 8.0 (API 26)
- **Target SDK**: Android 14 (API 34)
- **Build Tool**: Gradle 9.1

## Credits

- **Developer**: Smaine Chellat
- **Institution**: University Constantine 1, Geological Department
- **Database Sources**: 
  - Crystallography Open Database (COD)
  - RRUFF Database
  - ICDD (International Centre for Diffraction Data)

## License

This project is proprietary. All rights reserved.

## Support & Feedback

For issues, questions, or feature requests:
- Email: [Contact information to be added]
- GitHub Issues: [Create an issue on the repository]
- Contact Form: Use the "Contact Us" option in the app

## Version History

### Version 1.0.0 (Current)
- Initial release
- XRD M Identification with category filtering
- XRD d(A) Identification Single-phase
- XRD One Peak Identification
- Category filtering (All, Minerals, Compounds, Organic, Inorganic)
- NULL value filtering in results
- XRF Element detection
- Comprehensive mineral database (COD)
- Multiple search methodologies
- Performance optimizations
- Automatic database extraction on first launch
- Search execution time display

## Disclaimer

This app is designed for educational and reference purposes. While based on authoritative mineralogical databases, definitive mineral identification should be confirmed through:
- Professional laboratory analysis
- Experienced mineralogists
- Additional analytical techniques

The developers are not responsible for identifications made solely using this app.

---

**Last Updated**: February 4, 2026  
**Repository**: https://github.com/smainechellat-star/XRDXRF  
**Developed by**: Smaine Chellat

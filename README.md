# Nexo

Nexo is an offline-first personal finance app for Android. It tracks income, expenses and transfers across accounts and currencies. It can also pick up transactions from Sri Lankan bank SMS alerts and read totals from scanned receipts, all on the device.

## Screenshots


<p align="center">
  <img src="https://github.com/user-attachments/assets/92efd95b-a68c-424c-b640-ec656d0acd35" width="200" alt="Home" />
  <img src="https://github.com/user-attachments/assets/66dda01c-5048-4bf1-a5cf-485233d45087" width="200" alt="Stats" />
  <img src="https://github.com/user-attachments/assets/d3e8a16a-1b1b-4914-9369-4f27f7f8ea7a" width="200" alt="Accounts" />
  <img src="https://github.com/user-attachments/assets/b38a6fa7-ceee-44a0-a956-01587069808f" width="200" alt="Receipt Scan" />
</p>
<p align="center">
  <img src="https://github.com/user-attachments/assets/974e58df-c233-4b40-b8e0-f8448d495d8b" width="200" alt="Add transaction" />
  <img src="https://github.com/user-attachments/assets/76f32985-2274-4485-b41e-cf35efb24fe0" width="200" alt="Bank SMS review queue" />
</p>


## Features

- **Transactions**
  - Record income, expenses and transfers with a built-in calculator keypad
  - Daily, calendar, monthly and total views, with notes and filters
  - Bookmarks for frequent entries, repeating transactions and installments
  - Search across every transaction
- **Accounts & Categories**
  - Accounts organised into groups, with reorder, hide and restore-from-deleted
  - Credit card accounts with card payments
  - Emoji categories with subcategories, plus tags
- **Budgets & Stats**
  - Monthly budgets per category and overall
  - Income and expense breakdowns by week, month, year or a custom period
- **Bank SMS Capture**
  - Reads transaction alerts from Commercial Bank, BOC, HNB, Sampath, People's Bank, NTB, NDB, Seylan, DFCC and others
  - One parser for every bank; captured messages wait in a Review Queue for approval
  - Matches cards to accounts and remembers fee presets and learned categories
  - Estimates foreign-currency amounts from bank exchange rates
  - Checks the inbox in the background every six hours for messages Android delivered late
- **Receipt Scanning**
  - Captures receipts with the ML Kit document scanner
  - Reads the total and merchant on the device with ML Kit Text Recognition
- **Multi-Currency**
  - A main currency plus sub-currencies, each with its own exchange rate
- **Memos**
  - Dated notes alongside your transactions
- **Security & Data**
  - App lock with a PIN or biometrics
  - Daily automatic on-device backup, plus manual backup and restore
  - Export to Excel (`.xlsx`) and import transactions from TSV files
- **Personalisation**
  - Light, dark or system theme
  - Quick-add notification and a daily reminder

## Getting Started

### Prerequisites

- [Android Studio](https://developer.android.com/studio) (a recent release that supports AGP 9.5)
- [JDK 17](https://adoptium.net/) or newer
- Android SDK 37
- An Android device or emulator running Android 8.0 (API 26) or newer

### Installing

1. Clone the repository

   ```sh
   git clone https://github.com/malintha-induwara/Nexo.git
   cd Nexo
   ```

2. Open the project in Android Studio and let Gradle sync.

### Configuration

Foreign-currency estimates for bank SMS use the [AllRatesToday](https://allratestoday.com/) API. The key is optional: without it, Nexo falls back to the rates set in currency settings.

Add it to `local.properties` (it is git-ignored and never committed):

```properties
allratestoday.apiKey=your_api_key
```

### Running the Application

#### Install a debug build

```sh
./gradlew installDebug
```

#### Build a release APK

```sh
./gradlew assembleRelease
```

The release build is minified and signed with the debug key, so it is for personal testing only.

#### Run the tests

```sh
./gradlew testDebugUnitTest
```

#### Format and lint

```sh
./gradlew spotlessApply   # format
./gradlew spotlessCheck   # fail on unformatted code
```

#### Send a test bank SMS (debug builds)

adb can't send `SMS_RECEIVED`, so debug builds include a receiver that feeds a message into the same intake:

```sh
adb shell am broadcast -n com.nexo.app/.system.DebugSmsReceiver \
  --es sender COMBANK --es body "Dear Cardholder, ..."
adb logcat -s DebugSmsReceiver
```

## Tech Stack

[![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose)
[![Material 3](https://img.shields.io/badge/Material%203-757575?style=for-the-badge&logo=materialdesign&logoColor=white)](https://m3.material.io/)
[![Navigation 3](https://img.shields.io/badge/Navigation%203-4285F4?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/guide/navigation/navigation-3)
[![Hilt](https://img.shields.io/badge/Hilt-2D3E50?style=for-the-badge&logo=dagger&logoColor=white)](https://dagger.dev/hilt/)
[![Room](https://img.shields.io/badge/Room-003B57?style=for-the-badge&logo=sqlite&logoColor=white)](https://developer.android.com/training/data-storage/room)
[![ML Kit](https://img.shields.io/badge/ML%20Kit-4285F4?style=for-the-badge&logo=google&logoColor=white)](https://developers.google.com/ml-kit)
[![Coil](https://img.shields.io/badge/Coil-1E88E5?style=for-the-badge&logo=kotlin&logoColor=white)](https://coil-kt.github.io/coil/)
[![Biometric](https://img.shields.io/badge/Biometric-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/jetpack/androidx/releases/biometric)
[![Gradle](https://img.shields.io/badge/Gradle-02303A?style=for-the-badge&logo=gradle&logoColor=white)](https://gradle.org/)
[![JUnit](https://img.shields.io/badge/JUnit-25A162?style=for-the-badge&logo=junit5&logoColor=white)](https://junit.org/junit4/)
[![Robolectric](https://img.shields.io/badge/Robolectric-6DB33F?style=for-the-badge&logo=android&logoColor=white)](https://robolectric.org/)

## Architecture

Nexo follows the [Android architecture guide](https://developer.android.com/topic/architecture) in a single module:

- `ui` — Compose screens and their ViewModels, one package per feature
- `domain` — use cases that combine several repositories
- `data` — repositories over Room, DataStore, files, the network and the SMS inbox
- `core` — models and shared rules; depends on nothing above it
- `system` — Android entry points other than the activity (broadcast receivers, notifications)
- `di` — Hilt modules

`ArchitectureTest` keeps the layers apart: data never reaches into domain or ui, and ui talks to data only through repositories.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE.md) file for details.

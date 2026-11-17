# Nexo

Nexo is an offline-first personal finance app for Android. It tracks income, expenses and transfers across accounts and currencies. It can also pick up transactions from Sri Lankan bank SMS alerts and read totals from scanned receipts, all on the device.

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
  - Import from Money Manager Android backups
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
[![DataStore](https://img.shields.io/badge/DataStore-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/topic/libraries/architecture/datastore)
[![WorkManager](https://img.shields.io/badge/WorkManager-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/topic/libraries/architecture/workmanager)
[![Coroutines](https://img.shields.io/badge/Coroutines%20%26%20Flow-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/docs/coroutines-overview.html)
[![kotlinx.serialization](https://img.shields.io/badge/kotlinx.serialization-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://github.com/Kotlin/kotlinx.serialization)
[![ML Kit](https://img.shields.io/badge/ML%20Kit-4285F4?style=for-the-badge&logo=google&logoColor=white)](https://developers.google.com/ml-kit)
[![Coil](https://img.shields.io/badge/Coil-1E88E5?style=for-the-badge&logo=kotlin&logoColor=white)](https://coil-kt.github.io/coil/)
[![Biometric](https://img.shields.io/badge/Biometric-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/jetpack/androidx/releases/biometric)
[![Gradle](https://img.shields.io/badge/Gradle-02303A?style=for-the-badge&logo=gradle&logoColor=white)](https://gradle.org/)
[![JUnit](https://img.shields.io/badge/JUnit-25A162?style=for-the-badge&logo=junit5&logoColor=white)](https://junit.org/junit4/)
[![Robolectric](https://img.shields.io/badge/Robolectric-6DB33F?style=for-the-badge&logo=android&logoColor=white)](https://robolectric.org/)
[![ktlint](https://img.shields.io/badge/ktlint-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://pinterest.github.io/ktlint/)

## Architecture

Nexo follows the [Android architecture guide](https://developer.android.com/topic/architecture) in a single module:

- `ui` — Compose screens and their ViewModels, one package per feature
- `domain` — use cases that combine several repositories
- `data` — repositories over Room, DataStore, files, the network and the SMS inbox
- `core` — models and shared rules; depends on nothing above it
- `system` — Android entry points other than the activity (broadcast receivers, notifications)
- `di` — Hilt modules

`ArchitectureTest` keeps the layers apart: data never reaches into domain or ui, and ui talks to data only through repositories.

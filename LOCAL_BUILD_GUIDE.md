# Panduan Build di Mesin Lokal (x86_64)

Panduan ini menjelaskan cara mentransfer proyek "The Lost Echo" ke mesin lokal (PC x86_64) dan mengkompilasinya menjadi APK.

## Langkah 1: Transfer Proyek ke Mesin Lokal

### Opsi A: Git Clone (Direkomendasikan)
```bash
git clone <repository-url> TheLostEcho
cd TheLostEcho
```

### Opsi B: Transfer Manual
Jika proyek belum di-version control, transfer seluruh folder proyek ke mesin lokal:
```bash
# Salin semua file kecuali build artifacts dan Gradle cache
scp -r /mnt/sdcard/MIUI/lC/* user@local-machine:/path/to/TheLostEcho/

# Atau gunakan USB drive / cloud storage
# Pastikan untuk menyertakan:
# - app/build.gradle
# - app/proguard-rules.pro
# - app/src/ (seluruh sumber daya)
# - app/releass.jks
# - build.gradle, settings.gradle, gradle.properties, gradlew
```

### Yang Perlu Diketahui tentang `releass.jks`
File `releass.jks` adalah keystore untuk release signing. Jika proyek Anda sudah ada di GitHub/GitLab, pastikan file ini tidak termasuk di `.gitignore` (atau gunakan environment variables untuk password signing).

## Langkah 2: Instalasi JDK 17 di Mesin Lokal

### Windows
```powershell
# Unduh OpenJDK 17 dari:
# https://adoptium.net/temurin/releases/?version=17
# Atau gunakan winget:
winget install Oracle.JavaRuntimeEnvironment
# Set JAVA_HOME di Environment Variables
```

### macOS
```bash
# Menggunakan Homebrew
brew install openjdk@17
echo 'export JAVA_HOME=/usr/local/opt/openjdk@17' >> ~/.zshrc
source ~/.zshrc
```

### Linux (Ubuntu/Debian)
```bash
sudo apt update
sudo apt install openjdk-17-jdk
java -version
```

### Verifikasi
```bash
java -version
# Harus menampilkan: openjdk version "17.x.x"
```

## Langkah 3: Instalasi Android SDK di Mesin Lokal

### Opsi A: Android Studio (Direkomendasikan untuk Pemula)
1. Unduh Android Studio dari https://developer.android.com/studio
2. Instal dan buka Android Studio
3. Pada first-run wizard, pilih "Custom" install
4. Pastikan komponen berikut terinstal:
   - Android SDK
   - Android SDK Platform 34
   - Android SDK Build-Tools 34.0.0
   - Android Virtual Device (opsional, untuk emulator)

### Opsi B: Command-Line Tools Saja
```bash
# Buat direktori SDK
mkdir -p ~/Android/Sdk/cmdline-tools

# Unduh command-line tools
# Linux:
wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O cmdline-tools.zip
# macOS:
wget https://dl.google.com/android/repository/commandlinetools-mac-11076708_latest.zip -O cmdline-tools.zip
# Windows:
# Unduh manual dari https://developer.android.com/studio#command-line-tools-only

unzip cmdline-tools.zip -d cmdline-tools_extracted
mv cmdline-tools_extracted/cmdline-tools/* ~/Android/Sdk/cmdline-tools/latest/
rm -rf cmdline-tools.zip cmdline-tools_extracted
```

## Langkah 4: Instalasi Komponen SDK yang Diperlukan

```bash
# Atur environment variables
export ANDROID_HOME=~/Android/Sdk    # Linux/macOS
# Windows: setx ANDROID_HOME "%LOCALAPPDATA%\Android\Sdk"
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH

# Di Windows (PowerShell):
# setx ANDROID_HOME "%LOCALAPPDATA%\Android\Sdk"
# $env:PATH += ";%ANDROID_HOME%\cmdline-tools\latest\bin;%ANDROID_HOME%\platform-tools"

# Terima lisensi SDK
yes | sdkmanager --licenses

# Instal komponen yang diperlukan
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
```

## Langkah 5: Konfigurasi `local.properties`

Buat file `local.properties` di root proyek:

### Linux/macOS
```bash
echo "sdk.dir=/home/$(whoami)/Android/Sdk" > local.properties
```

### Windows
```batch
echo sdk.dir=%LOCALAPPDATA%\Android\Sdk > local.properties
```

### macOS (Android Studio default)
```bash
echo "sdk.dir=/Users/$(whoami)/Library/Android/sdk" > local.properties
```

> **Catatan:** File `local.properties` sebaiknya tidak di-commit ke Git karena berisi path spesifik mesin. Pastikan file ini ada di `.gitignore`.

## Langkah 6: Build APK

### Build Release (Direkomendasikan)
```bash
./gradlew assembleRelease
```

APK akan berada di:
```
app/build/outputs/apk/release/app-release.apk
```

### Build Debug (untuk testing)
```bash
./gradlew assembleDebug
```

APK akan berada di:
```
app/build/outputs/apk/debug/app-debug.apk
```

### Build dengan Gradle (tanpa wrapper)
Jika tidak dapat menggunakan `./gradlew`:
```bash
# Pastikan Gradle terinstal
gradle assembleRelease
```

## Langkah 7: Konfigurasi Signing (Opsional)

Jika ingin APK release distandari secara otomatis, tambahkan konfigurasi signing di `app/build.gradle`:

```groovy
android {
    signingConfigs {
        release {
            storeFile file("releass.jks")
            storePassword "ISI_DENGAN_PASSWORD_STORE"
            keyAlias "ISI_DENGAN_KEY_ALIAS"
            keyPassword "ISI_DENGAN_PASSWORD_KEY"
        }
    }
    buildTypes {
        release {
            signingConfig signingConfigs.release
            minifyEnabled true
            shrinkResources true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
}
```

> **Aman:** Untuk keamanan, gunakan environment variables atau `gradle.properties` untuk password:
> ```bash
> # Di gradle.properties
> RELEASE_STORE_PASSWORD=password
> RELEASE_KEY_ALIAS=alias
> RELEASE_KEY_PASSWORD=password
> ```
> ```groovy
> // Di app/build.gradle
> signingConfigs {
>     release {
>         storeFile file("releass.jks")
>         storePassword System.getenv("RELEASE_STORE_PASSWORD")
>         keyAlias System.getenv("RELEASE_KEY_ALIAS")
>         keyPassword System.getenv("RELEASE_KEY_PASSWORD")
>     }
> }
> ```

## Troubleshooting di Mesin Lokal

### "Permission denied" pada gradlew (Linux/macOS)
```bash
chmod +x gradlew
```

### "Could not find or load main class" 
```bash
# Jalankan dengan bash eksplisit
bash gradlew assembleRelease
```

### AAPT2 errors
```bash
# Bersihkan cache Gradle
./gradlew clean build --refresh-dependencies
# Atau hapus manual cache di ~/.gradle/caches/
```

### Out of memory
Tambahkan di `gradle.properties`:
```
org.gradle.jvmargs=-Xmx4096m -XX:MaxMetaspaceSize=512m
```

### Kotlin stdlib conflict
Pastikan `app/build.gradle` sudah memiliki konfigurasi exclude:
```groovy
configurations.all {
    exclude group: 'org.jetbrains.kotlin', module: 'kotlin-stdlib-jdk7'
    exclude group: 'org.jetbrains.kotlin', module: 'kotlin-stdlib-jdk8'
}
```

## Verifikasi APK

Setelah build berhasil:
```bash
# Periksa apakah APK ada
ls -la app/build/outputs/apk/release/app-release.apk

# Periksa ukuran APK
du -h app/build/outputs/apk/release/app-release.apk

# Verifikasi keystore (jika signed)
keytool -printcert -jarfile app/build/outputs/apk/release/app-release.apk
```

## Ringkasan Perintah Cepat (Linux/macOS)

```bash
# Setup
sudo apt install openjdk-17-jdk unzip curl
mkdir -p ~/Android/Sdk/cmdline-tools/latest
curl -sL https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -o /tmp/cmdline.zip
unzip -q /tmp/cmdline.zip -d /tmp/cmd-extract
mv /tmp/cmd-extract/cmdline-tools/* ~/Android/Sdk/cmdline-tools/latest/

# Accept licenses & install SDK
export ANDROID_HOME=~/Android/Sdk
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH
yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"

# Project setup
cd TheLostEcho
echo "sdk.dir=$ANDROID_HOME" > local.properties
chmod +x gradlew

# Build
bash gradlew assembleRelease
```

## Ringkasan Perintah Cepat (Windows PowerShell)

```powershell
# Setup JDK 17 - download dari https://adoptium.net
# Setup Android SDK - download Android Studio atau cmdline-tools

# Set environment
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
[Environment]::SetEnvironmentVariable("ANDROID_HOME", $env:ANDROID_HOME, "User")

# Accept licenses & install SDK
sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"

# Project setup
cd TheLostEcho
echo "sdk.dir=$env:ANDROID_HOME" > local.properties

# Build
.\gradlew assembleRelease
```

# Panduan Kompilasi "The Lost Echo" Menjadi APK

## Ringkasan Proyek

**The Lost Echo** adalah game Android berbasis Java yang dibangun dengan Android Gradle Plugin 8.1.1. Game ini menampilkan rendering procedural, AI pathfinding, vision cone, sonar wave, particle system, dan branching narrative.

## Prasyarat

### 1. Java JDK 17
```bash
java -version
# Harus menampilkan: openjdk version "17.x.x"
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64
export PATH=$JAVA_HOME/bin:$PATH
```

### 2. Android SDK Command-Line Tools
```bash
mkdir -p /root/android-sdk
curl -sL https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -o /tmp/cmdline-tools.zip
unzip -q /tmp/cmdline-tools.zip -d /tmp/cmdline-extract
mkdir -p /root/android-sdk/cmdline-tools/latest
mv /tmp/cmdline-extract/cmdline-tools/* /root/android-sdk/cmdline-tools/latest/
rm -rf /tmp/cmdline-tools.zip /tmp/cmdline-extract
```

### 3. Komponen SDK yang Diperlukan
```bash
export ANDROID_HOME=/root/android-sdk
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH
yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
```

### 4. Konfigurasi `local.properties`
```bash
echo "sdk.dir=/root/android-sdk" > local.properties
```

## Langkah Kompilasi

### 1. Masuk ke direktori proyek
```bash
cd /mnt/sdcard/MIUI/lC
```

### 2. Jalankan build release
```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64
export ANDROID_HOME=/root/android-sdk
export PATH=$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH
export GRADLE_USER_HOME=/mnt/sdcard/MIUI/lC/.gradle

bash gradlew assembleRelease
```

### 3. Lokasi APK Output
Jika build berhasil, APK akan berada di:
```
app/build/outputs/apk/release/app-release.apk
```

## Masalah yang Sudah Diperbaiki

### 1. Duplicate Class (kotlin-stdlib conflict)

**Gejala:**
```
Duplicate class kotlin.collections.jdk8.CollectionsJDK8Kt found in modules
kotlin-stdlib-1.8.22 and kotlin-stdlib-jdk8-1.6.0
```

**Penyebab:**
`kotlin-stdlib-jdk7` dan `kotlin-stdlib-jdk8` adalah modul legacy yang sudah digabungkan ke dalam `kotlin-stdlib` sejak Kotlin 1.6. Modul ini ditarik secara transitif oleh `kotlinx-coroutines-android` (yang datang dari `androidx.core:core`).

**Solusi (sudah diterapkan di `app/build.gradle`):**
```groovy
dependencies {
    implementation fileTree(dir: 'libs', include: ['*.jar'])
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'androidx.core:core:1.12.0'
    configurations.all {
        exclude group: 'org.jetbrains.kotlin', module: 'kotlin-stdlib-jdk7'
        exclude group: 'org.jetbrains.kotlin', module: 'kotlin-stdlib-jdk8'
    }
    testImplementation 'junit:junit:4.13.2'
    androidTestImplementation 'androidx.test.ext:junit:1.1.5'
    androidTestImplementation 'androidx.test.espresso:espresso-core:3.5.1'
}
```

## Keterbatasan Build pada Sistem ARM64

### Masalah: aapt2 x86-64 Tidak Dapat Berjalan di ARM64

Android SDK build-tools (termasuk `aapt2`) untuk Linux didistribusikan sebagai binary x86-64. Pada sistem ARM64 (aarch64), binary ini tidak dapat dieksekusi secara native.

**Solusi yang tersedia:**

1. **Bangun pada mesin x86_64** — Gunakan mesin virtual x86_64 atau PC dengan arsitektur x86_64 untuk menjalankan build.

2. **Gunakan Docker x86_64** — Jalankan container x86_64 dengan Android SDK:
   ```bash
   docker run --rm -v $(pwd):/project -w /project \
     ubuntu:22.04 bash -c '
       apt-get update && apt-get install -y openjdk-17-jdk
       # Install Android SDK dan build
       ./gradlew assembleRelease
     '
   ```

3. **Gunakan CI/CD** — GitHub Actions, GitLab CI, atau layanan CI lain yang menyediakan runner x86_64 dengan Android SDK.

4. **Build debug di mesin x86_64** — Untuk keperluan testing, build versi debug:
   ```bash
   bash gradlew assembleDebug
   ```

## Konfigurasi ProGuard (Release Build)

File `app/proguard-rules.pro` berisi aturan untuk release build:
- Menjaga semua kelas `com.thelostecho.game` tetap utuh
- Menjaga kelas `MainActivity` sebagai entry point
- Menjaga kelas `Parcelable` jika ada serialisasi

## Struktur Proyek

```
TheLostEcho/
├── build.gradle              # Root build configuration
├── settings.gradle           # Project settings, include ':app'
├── gradle.properties         # Gradle properties (android.useAndroidX=true)
├── local.properties          # SDK path (sdk.dir=/root/android-sdk)
├── gradlew                   # Gradle wrapper script
├── gradlew.bat               # Gradle wrapper for Windows
├── releass.jks               # Keystore for release signing
├── proguard-rules.pro        # ProGuard rules for release
├── BUILD_GUIDE.md            # Panduan ini
└── app/
    ├── build.gradle          # App module configuration
    ├── src/
    │   ├── main/
    │   │   ├── AndroidManifest.xml
    │   │   ├── java/com/thelostecho/game/
    │   │   │   ├── MainActivity.java
    │   │   │   ├── core/          # Game engine core
    │   │   │   ├── entities/      # Game entities
    │   │   │   ├── graphics/      # Rendering
    │   │   │   ├── ai/            # AI systems
    │   │   │   ├── managers/      # Game managers
    │   │   │   ├── story/         # Narrative system
    │   │   │   └── ui/            # UI views
    │   │   └── res/               # Resources
    │   ├── test/
    │   └── androidTest/
    └── build/outputs/apk/release/
        └── app-release.apk       # Output APK
```

## Tanda Tangan APK (Release)

Proyek ini menggunakan `releass.jks` untuk signing. Pastikan keystore ada dan konfigurasi signing ada di `app/build.gradle`:

```groovy
android {
    signingConfigs {
        release {
            storeFile file("releass.jks")
            storePassword "YOUR_STORE_PASSWORD"
            keyAlias "YOUR_KEY_ALIAS"
            keyPassword "YOUR_KEY_PASSWORD"
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

> **Catatan:** File `releass.jks` ada di root proyek. Jika belum ada konfigurasi signing di `build.gradle`, build akan menghasilkan APK unsigned.

## Ringkasan Status Build

| Komponen | Status |
|----------|--------|
| Java JDK 17 | ✅ Terinstal |
| Android SDK | ✅ Terinstal (`/root/android-sdk`) |
| Platform Android 34 | ✅ Terinstal |
| Build Tools 34.0.0 | ✅ Terinstal |
| Kotlin stdlib conflict | ✅ Sudah diperbaiki |
| aapt2 x86-64 di ARM64 | ❌ Tidak dapat berjalan |
| APK berhasil dibangun | ❌ Belum (lihat keterangan di atas) |

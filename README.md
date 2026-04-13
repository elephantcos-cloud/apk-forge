# 🔨 APK Forge

> Professional Android APK builder app — trigger GitHub Actions builds, monitor status, and download APKs directly to your phone.

---

## 🏗️ Tech Stack

| Layer | Language | Purpose |
|-------|----------|---------|
| UI & App Logic | **Kotlin** | Fragments, ViewModels, DataStore, Navigation |
| API & Build Engine | **Java** | GitHub REST API, Build Manager, File Utils, Webhook |
| Native Performance | **C++ (NDK/JNI)** | SHA-256, CRC32, APK validation, token obfuscation |
| CI Orchestration | **C#** | Server-side build pipeline with exponential backoff |
| Build System | **Gradle (KTS)** | Multi-lang build config with CMake for C++ |

---

## ✨ Features

- 🚀 **Trigger builds** — workflow_dispatch via GitHub Actions API
- 📊 **Real-time monitoring** — polls run status every 8 seconds
- 📦 **Artifact listing** — shows all APK artifacts with size info
- 🔐 **Secure token storage** — token encrypted via C++ XOR + EncryptedSharedPrefs
- 🌿 **Branch selector** — auto-loads all branches from repo
- 📋 **Build history** — stored locally with Room database
- 🔔 **Foreground notifications** — build progress while app is in background
- 🌙 **Dark theme** — GitHub-inspired dark UI

---

## 🚀 Quick Start

### 1. Clone & Build
```bash
git clone https://github.com/elephantcos-cloud/apk-forge.git
cd apk-forge
./gradlew assembleDebug
```

### 2. Configure GitHub Token
- Go to **Settings** tab in the app
- Enter your GitHub Personal Access Token (needs `repo` + `workflow` scopes)
- Tap **Validate Token**
- Enter your default repo & branch
- Tap **Save Settings**

### 3. Start a Build
- Go to **Build** tab
- Enter repo owner & name
- Select branch
- Tap **🔨 Start Build**
- Watch real-time progress!

---

## 🔐 GitHub Token Scopes Required

```
repo          — access repository info & artifacts
workflow      — trigger workflow_dispatch
read:user     — validate token ownership
```

---

## 🏗️ C++ Native Library

The NDK library `libapkforge_native.so` provides:

```cpp
// SHA-256 hash (pure C++17, no OpenSSL dependency)
computeSha256(input: String): String

// APK file validation (checks ZIP magic bytes)
isValidApk(path: String): Boolean

// Token obfuscation (XOR cipher)
obfuscateToken(token: String, key: Int): ByteArray

// CRC32 & Adler32 checksums
computeCrc32(data: ByteArray): Long
```

---

## 🟣 C# Build Orchestrator

Server-side companion script for CI pipeline management:

```bash
dotnet script buildscripts/BuildOrchestrator.cs -- <owner> <repo> <token> [branch] [workflow]
```

Features exponential backoff polling, SHA-256 fingerprinting, and colored console output.

---

## 📁 Project Structure

```
apk-forge/
├── app/src/main/
│   ├── kotlin/com/apkforge/        # Kotlin source
│   │   ├── ui/                     # Fragments (Dashboard, Build, History, Settings)
│   │   ├── viewmodel/              # BuildViewModel
│   │   ├── security/               # NativeCrypto JNI bridge
│   │   └── core/                   # BuildMonitorService
│   ├── java/com/apkforge/          # Java source
│   │   ├── network/                # GithubApiClient
│   │   ├── core/                   # BuildManager
│   │   ├── utils/                  # FileUtils
│   │   └── webhook/                # WebhookTrigger
│   ├── cpp/                        # C++ NDK source
│   │   ├── native_bridge.cpp       # JNI entry points
│   │   ├── file_ops.cpp            # File operations
│   │   ├── hash_engine.cpp         # CRC32/Adler32
│   │   └── CMakeLists.txt
│   └── res/                        # Android resources
├── buildscripts/
│   └── BuildOrchestrator.cs        # C# CI script
└── .github/workflows/
    └── build.yml                   # GitHub Actions APK builder
```

---

## 📄 License

MIT License — Built with ❤️ by APK Forge

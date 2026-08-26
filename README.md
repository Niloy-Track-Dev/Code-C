# <p align="center"><img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="64" height="64"><br>C Code</p>

<p align="center">
  <img src="assets/banner.jpg" width="100%" alt="C Code Hero Banner">
</p>

<p align="center">
  <a href="https://github.com/Niloy-Track-Dev/C-Code/releases/latest"><img src="https://img.shields.io/github/v/release/Niloy-Track-Dev/C-Code?style=for-the-badge&color=blue" alt="Latest Release"></a>
  <a href="https://github.com/Niloy-Track-Dev/C-Code/releases"><img src="https://img.shields.io/github/downloads/Niloy-Track-Dev/C-Code/total?style=for-the-badge&color=success" alt="Downloads"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL%20v3-red?style=for-the-badge" alt="License"></a>
  <a href="https://kotlinlang.org/"><img src="https://img.shields.io/badge/Kotlin-2.0-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin"></a>
</p>

<p align="center">
  <b>A High-Performance, 100% Offline C IDE & Compiler for Android</b><br>
  Built with Jetpack Compose & Material You.
</p>

---

## 📖 Introduction

**C Code** is a professional-grade integrated development environment (IDE) designed for mobile C development. Unlike other apps that rely on cloud servers, C Code features a **fully self-contained compiler engine** that runs natively on your Android device. Whether you're a student learning the ropes or a developer testing algorithms on the go, C Code provides a desktop-class experience in your pocket.

## 🚀 Key Features

### 💻 Professional Editor
- **Modern Syntax Highlighting**: Precisely colors C keywords, types, literals, and preprocessor directives.
- **Smart Editing**: Auto-indentation, bracket matching, and auto-closing quotes/braces.
- **Quick Symbols**: A specialized toolbar for common C symbols (`;`, `{`, `->`, etc.) to speed up mobile typing.
- **Undo/Redo & Formatting**: Full state management and built-in code beautifier.

### ⚙️ Compiler & Execution
- **Offline First**: Zero internet connection required. Your code is compiled and executed locally.
- **Interactive Terminal**: Full `stdin` support allows you to interact with your programs in real-time.
- **Performance Profiling**: View precise execution time and memory usage for every run.
- **Safe Sandbox**: Includes execution timeouts and infinite loop protection to keep your device stable.

### 🎨 Design & UX
- **Material You**: Dynamic color support that adapts to your wallpaper and system theme.
- **Adaptive Layout**: Optimized for both phones and tablets with fluid, responsive components.
- **Rich Templates**: Start instantly with boilerplate for Hello World, Linked Lists, Sorting, and more.

## 📸 Preview

<p align="center">
  <img src="assets/mockup.jpg" width="85%" alt="C Code App Mockup">
</p>

---

## 🛠️ Technical Specifications

- **C Standard**: Supports C89/C90, C99, and C11 standards.
- **Standard Library**: Implementation of `stdio.h`, `stdlib.h`, `string.h`, `math.h`, and more.
- **Parser**: Custom Recursive Descent Parser generating a comprehensive Abstract Syntax Tree (AST).
- **Diagnostics**: Real-time error reporting with line numbers and source code snippets.

## 🏗️ Project Structure

```text
C-Code/
├── app/                # Main Android application module
│   ├── src/main/java/  # Kotlin Source Code (MVVM)
│   └── src/main/res/   # UI Resources & Theme definitions
├── assets/             # Documentation assets & banners
├── gradle/             # Version catalogs & build configurations
└── README.md           # This file
```

## 🛤️ Roadmap

- [ ] **GDB Integration**: Step-by-step debugging support.
- [ ] **Multi-file Projects**: Support for headers and multiple `.c` files.
- [ ] **External Library Support**: Link against pre-compiled static libraries.
- [ ] **Cloud Sync (Optional)**: Secure backup of projects to personal Drive/GitHub.

---

## 🤝 Contributing

We love contributions! Whether it's fixing a bug in the lexer or improving the Material 3 UI:

1. **Fork** the repository.
2. **Clone** your fork.
3. **Branch** off `main`.
4. **Commit** with descriptive messages.
5. **Push** and open a **Pull Request**.

## 📜 License

This project is licensed under the **GNU General Public License v3.0**. See the [LICENSE](LICENSE) file for the full text.

---

<p align="center">
  Developed by <b>Niloy Mitra</b><br>
  <i>Empowering mobile development, one line at a time.</i>
</p>


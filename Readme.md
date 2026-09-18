
# VNC android free

<p align="center"> <b>VNC android free</b> is a high-performance, open-source VNC client for Android. </p>

### 📥 Download APK
* **[Download Latest VNC-android-free.apk (v1.6.0)](https://github.com/Vicky8106/avnc/releases/download/v1.6.0/VNC-android-free.apk)**
* Repository file: [`release/VNC-android-free.apk`](release/VNC-android-free.apk)

---

### Highlights & New Features
- **20% Transparent Virtual Mouse Overlay:** The entire virtual mouse interface (both the floating circular action button and the expanded control bar) has 80% opacity (`alpha = 0.80f`), allowing you to see the underlying remote desktop clearly while using mouse controls.
- **Clean Single Cross ("✕") Button:** Removed the redundant left cross button; a single, clear circular minimize button is located at the right end of the bar to collapse it back into the floating bubble.
- **Definitive Fix for Automatic Enter on Long Copy-Paste:** Embedded line breaks (`\r\n`, `\r`, `\n`, etc.) within pasted text are now sanitized to spaces during paste streaming. This guarantees that wrapped commands, long strings, or multi-line text (up to 1,000 characters) NEVER trigger a premature `Return`/Enter key in the middle of pasting, allowing the entire text to paste completely and safely without executing early.
- **Enhanced IME & Context Menu Deduplication:** Extended paste deduplication window and configured `IME_ACTION_NONE` so soft keyboards do not interrupt ongoing paste streams with synthetic Enter actions.
- **Fixed Paste Gibberish & Caps Lock Locking:** Uppercase letters and shifted symbols (`~!@#$%^&*()_+{}|:"<>?`) are sent with atomic `Shift` keysym wrapping, preventing the remote VNC server from toggling Caps Lock. Added `releaseAllModifiers()` before text streaming, and added a dedicated **Caps** button in Virtual Keys to toggle/unstick remote Caps Lock.
- **Expandable Floating Mouse (RealVNC Style):** On-screen Floating Action Button (FAB) that expands into full mouse options (Left Click, Middle Click, Scroll Up, Scroll Down, Right Click, Keyboard trigger).
- **Unified Mouse & Keyboard:** Opening the keyboard automatically surfaces the mouse controls for seamless navigation.


### Features
- Material Design (with Dark theme)
- Configurable gestures
- Virtual Keys
- VNC Repeater support
- Wake-on-LAN support
- Built-in SSH tunnel (VNC over SSH)
- Picture-in-Picture mode
- View-only mode
- No-video mode
- Automatic Server Discovery (Zeroconf)
- Import/Export servers
- Clipboard Sync with server
- `vnc://` URI support
- TLS support (AnonTLS, VeNCrypt)
- Tight encoding support


[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid" height="80">](https://f-droid.org/packages/com.gaurav.avnc/)
[<img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" alt="Get it on Play Store" height="80">](https://play.google.com/store/apps/details?id=com.gaurav.avnc)

### Screenshots

[<img src="metadata/en-US/images/phoneScreenshots/1.jpg" width="250">](metadata/en-US/images/phoneScreenshots/1.jpg)
[<img src="metadata/en-US/images/phoneScreenshots/2.jpg" width="250">](metadata/en-US/images/phoneScreenshots/2.jpg)
[<img src="metadata/en-US/images/phoneScreenshots/3.jpg" width="250">](metadata/en-US/images/phoneScreenshots/3.jpg)
[<img src="metadata/en-US/images/phoneScreenshots/4.jpg" width="250">](metadata/en-US/images/phoneScreenshots/4.jpg)
[<img src="metadata/en-US/images/phoneScreenshots/5.jpg" width="250">](metadata/en-US/images/phoneScreenshots/5.jpg)
[<img src="metadata/en-US/images/phoneScreenshots/6.jpg" width="250">](metadata/en-US/images/phoneScreenshots/6.jpg)
[<img src="metadata/en-US/images/phoneScreenshots/7.jpg" width="380">](metadata/en-US/images/phoneScreenshots/7.jpg)
[<img src="metadata/en-US/images/phoneScreenshots/8.jpg" width="380">](metadata/en-US/images/phoneScreenshots/8.jpg)

  
Development
===========

Tools required:

- Git 
- Android Studio
- Android SDK
- NDK (with CMake)
- For vcpkg (depending on your platform): curl, zip, unzip, tar, pkg-config, a C++ compiler

To get started, simply clone the repo and initialize submodules:

```bash
git clone https://github.com/gujjwal00/avnc.git
cd avnc
git submodule update --init --depth 1
```

Now you can import the project in Android Studio, or build it directly from terminal.

Read [Architecture.kt](app/src/main/java/com/gaurav/avnc/Architecture.kt) (preferably in
Android Studio) to know more about the code.

> [!TIP]
> AVNC uses [vcpkg](https://learn.microsoft.com/en-us/vcpkg/) to manage C/C++ dependencies.
> `vcpkg` downloads & builds these dependencies on first run.
> So the first time you build/configure AVNC, it can take a lot of time.
> If you face an error, try adding `android.native.buildOutput=verbose` to `gradle.properties` in project root.


##
You can translate AVNC on [Weblate](https://hosted.weblate.org/engage/avnc/).

[<img src="https://hosted.weblate.org/widgets/avnc/-/open-graph.png" alt="Translation status" height="200" />](https://hosted.weblate.org/engage/avnc/)


## Credits

- Authors of libraries AVNC depends on (LibVNCClient, libjpeg-turbo, wolfSSL, sshlib, leakcanary etc.)
- Contributors for reporting issues, providing fixes
- Contributors for translating AVNC, and [Weblate](https://weblate.org/) for translation hosting
- [Browserstack](https://www.browserstack.com/) for providing testing infrastructure
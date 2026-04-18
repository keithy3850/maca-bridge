# Maca Bridge 📱↔️💻

Maca Bridge is a powerful, local-network bridge that brings Android and macOS closer together. Inspired by KDE Connect and Apple's Continuity features, it allows you to sync notifications, share clipboards, control your mouse, and more—all over your local WiFi with end-to-end TLS encryption.

## ✨ Features

- **🔔 Notification Mirroring:** See your phone's notifications natively on macOS.
- **💬 SMS Sync:** Read and reply to text messages directly from your Mac.
- **📋 Universal Clipboard:** Copy text on your phone and paste on your Mac (and vice versa).
- **🖱️ Remote Trackpad:** Use your phone's screen as a precise trackpad for your Mac.
- **🎵 Media Remote:** Control music and video playback on either device.
- **📸 Photo Gallery:** Instantly browse and download recent photos from your phone.
- **🔦 Find My Phone:** Trigger a high-volume alarm from your Mac to find your misplaced device.
- **🔋 Battery Sync:** Monitor your phone's battery level and charging status from the macOS menu bar.

## 🏗️ Architecture

- **Android:** Acts as the **Server**. It hosts a secure Ktor WebSocket server and manages system-level integrations (SMS, Notifications).
- **macOS:** Acts as the **Client**. It discovers the phone via Bonjour (mDNS) and connects over a secure SSL/TLS channel.

---

## 🚀 Installation Guide

### macOS Client
Since this app is distributed outside the Mac App Store, follow these steps to install:

1. **Download:** Grab the latest `MacABridge.app` from the [Releases](#) page.
2. **First Run (Security Bypass):** 
   - Do **not** double-click the app the first time.
   - **Right-click** (or Control-click) `MacABridge.app` and select **Open**.
   - Click **Open** again in the security dialog. This is required because the app is self-signed.
3. **Grant Permissions:**
   - The app will prompt you for **Accessibility Permissions**. This is required for the Remote Trackpad feature.
   - Click **Grant Accessibility Permission** in the app's discovery screen.
   - In System Settings, ensure **MacABridge** is toggled **ON**.
   - *Tip:* If it's already on but doesn't work, toggle it OFF and back ON to refresh the system's security cache.

### Android Server
1. **Download:** Download the `maca-bridge-release.apk` from the [Releases](#) page.
2. **Install:** Open the APK on your phone. You may need to "Allow installation from unknown sources" in your browser/file manager.
3. **Setup Permissions:**
   - Open the app and grant **SMS**, **Contacts**, and **Notification** permissions when prompted.
   - Tap **Notification Access** to enable the Maca Bridge listener in Android system settings.
   - **Note for Android 13+:** If "Notification Access" is greyed out, go to your phone's **Settings > Apps > Maca Bridge**, tap the **three dots (⋮)** in the top-right corner, and select **"Allow restricted settings"**. Then return to the app to enable access.
   - Tap **Photo Access** to allow the gallery sync to function.
---

## 🔐 Security & Privacy

- **Local Network Only:** No data ever leaves your house. All communication happens strictly over your local WiFi.
- **Encrypted Traffic:** All data is sent over a secure WebSocket (WSS) using TLS.
- **Pairing:** Devices are paired using a secure 6-digit PIN and a unique session token to prevent unauthorized access from other devices on the same network.
- **Privacy First:** Sensitive data (like credit card numbers or passwords) is automatically detected and excluded from clipboard synchronization.

---

## 🛠️ Troubleshooting

### Trackpad is not moving the mouse?
- Ensure the **App Sandbox** was disabled during the build (for developers).
- Go to **System Settings > Privacy & Security > Accessibility**. Remove MacABridge with the minus (-) button and add it back.
- Restart the Mac app.

### Connection issues?
- Ensure both devices are on the same WiFi network.
- Check if a firewall on your Mac is blocking incoming connections on port **8443**.
- Restart the Android app to regenerate the pairing PIN if "Invalid Auth" errors appear.

---

## 👨‍💻 Development

### Prerequisites
- Xcode 15.0+ (Swift 6 mode supported)
- Android Studio Iguana+
- macOS 13.0+
- Android 8.0 (API 26) +

### Building from Source
- **macOS:** Open `macos/MacABridge/MacABridge.xcodeproj`. Ensure **App Sandbox** is removed in *Signing & Capabilities*.
- **Android:** Open the `android/` folder in Android Studio and build the `release` variant.

---

*Built with ❤️ as an open-source alternative to proprietary continuity tools.*

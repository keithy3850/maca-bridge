# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.0] - 2024-04-17

### Added
- Dedicated full-screen "Find My Phone" activity with mandatory dismissal button.
- Support for "Restricted Settings" bypass instructions in README for Android 13+.
- Red warning indicators in Android UI when critical permissions are missing.
- **Photos:** Added thumbnail support (300x300) for significantly faster gallery grid loading.
- **Clipboard:** Added a dedicated "Send Clipboard to Mac" button in the Android app for improved reliability.

### Changed
- **SMS:** Switched to `Conversations` URI for significantly faster history syncing.
- **SMS:** Fixed `SmsManager` resolution for Android 12+ (API 31+).
- **Media:** Improved metadata compatibility for players that use Display Titles (YouTube, etc.).
- **Media:** Added retry logic for the Media Session listener during initial sync.
- **Performance:** Optimized macOS clipboard polling to reduce CPU/battery impact.
- **Performance:** Added `NSCache` to macOS gallery for smoother scrolling.
- **Security:** Disabled insecure HTTP port (8080) on Android; only SSL (8443) remains.
- **Photos:** Saving a photo now automatically highlights the file in Finder.

### Fixed
- Fixed "Socket is not connected" error on macOS by removing artificial handshake delays.
- Fixed Swift 6 concurrency errors in `BridgeClient` using `nonisolated` delegates and `Task`.
- Fixed macOS Accessibility permission "silent block" by disabling App Sandbox.
- Fixed photo copy/save actions to use the secure bridge session (trusting self-signed certs).
- Reduced SSL certificate validity to 365 days to comply with modern Apple security policies.

## [1.0.0] - 2024-04-17

### Added
- Initial public release of Maca Bridge.
- Core features: Notification Mirroring, SMS Sync, Universal Clipboard, Remote Trackpad.
- SSL/TLS encryption with PIN-based pairing.
- Self-hosted update mechanism for Android and macOS.

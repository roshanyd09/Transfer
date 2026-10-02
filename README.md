# ⚡ TRANSFER — Android

**Move. Share. Done.**  
**Made by Roshan**

TRANSFER is a local-first file transfer app. The phone runs a temporary HTTP server on a dynamically selected free port. A laptop on the same Wi-Fi/hotspot opens the shown address in a browser.

## What this build includes
- Original file transfer: no resize, recompression, watermark, or cloud upload.
- Dynamic free port selection (avoids conflicts with services such as Oracle TNS Listener on 8080).
- Connect / Disconnect controls.
- QR scanner entry point.
- Mobile file picker.
- Laptop responsive web UI with drag & drop, upload progress, download, delete, and file sizes.
- Local Wi-Fi / hotspot operation; Internet is not required for the transfer itself.
- Premium dark UI with a small animated mascot.

## Important limitation in v1.1
The mobile file picker is included for the phone-side UX, but the actual phone-to-PC transfer is performed by opening the laptop web page and downloading the files stored on the phone. A future release can add a native share queue/background transfer service.

## GitHub APK build
1. Create a new GitHub repository, e.g. `TRANSFER`.
2. Extract this ZIP.
3. Upload the **contents of `TRANSFER_ANDROID`** to the repository root (not the outer folder itself).
4. Commit to the `main` branch.
5. Open **Actions** → **Build TRANSFER APK**.
6. Open the completed run → **Artifacts** → `TRANSFER-debug-apk`.
7. Download and extract the artifact to get `app-debug.apk`.

GitHub Actions stores build output as workflow artifacts. See GitHub's Actions documentation for the workflow/artifact UI.

## Local Android Studio test
Open the project folder in Android Studio and run the `app` configuration on a real Android phone or emulator.

## Network test
1. Install/open TRANSFER on the phone.
2. Connect phone and laptop to the same Wi-Fi, or connect the laptop to the phone's hotspot.
3. Start the server in the app.
4. Enter the displayed `http://PHONE_IP:PORT` address in the laptop browser.
5. Upload a test photo and download it back. Compare the file size/hash if you want to verify byte-for-byte integrity.

## Quality guarantee
TRANSFER sends the selected file as raw bytes over HTTP. It does not decode/re-encode photos or videos, so there is no intentional quality loss. File size and cryptographic hash can be compared after transfer for verification.

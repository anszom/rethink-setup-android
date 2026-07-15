# Rethink Setup (Android)

A small Android companion app for [Rethink](https://github.com/anszom/rethink), the local replacement for the
LG ThinQ cloud. It removes the need for a Wi-Fi-enabled Linux laptop with Node.js when
onboarding an appliance — everything `rethink-setup.ts` does is reproduced on the phone.

Most code was shamelessly regurgiated out of an LLM.

## Features

### Check DNS redirection
1. Connect the phone to your home Wi-Fi.
2. Tap **Check**. The application will verify that requests to the LG cloud are redirected
   to a `rethink` instance.

### Provision a device
A three-step wizard:

1. **Enter your home Wi-Fi credentials** and tap **Next**.
2. **Connect to the appliance Wi-Fi.** Join the appliance's network in your phone's
   Wi-Fi settings (usually starts with `LG_Smart…` or `LGE_…`; it has no internet, which is
   expected). The screen polls the phone's IP and only enables **Start** once the
   address is in the appliance's range (`192.168.120.x`).
3. **Setting up.** Non-interactive — the app connects to `192.168.120.254:5500`, runs
   the same handshake as `rethink-setup.ts`, and streams a log. A button returns to the
   home screen when it finishes.

The handshake:
- **ThinQ1** (mTosp/XML) is tried first — `deviceinfo` then `apinfo`, with the fake
  `rethink` region code so the appliance dials `rethink.lgthinq.com`.
- **ThinQ2** (JSON) is the fallback — `setDeviceInit → getDeviceInfo → setCertInfo →
  setApInfo → releaseDev`.

## How the tricky bits work

- **Forcing Wi-Fi.** The appliance AP has no internet, so Android would otherwise route
  the provisioning traffic over mobile data. The app uses
  `ConnectivityManager.requestNetwork()` to obtain the Wi-Fi `Network` and binds every
  socket to it (`Network.getSocketFactory()` / `Network.openConnection()`).
- **Legacy TLS.** The appliances present a self-signed certificate and negotiate
  ciphersuites disabled by default on modern Android. The app trusts any certificate
  (matching `rejectUnauthorized: false` upstream) and re-enables every protocol and
  ciphersuite the platform still supports. See `net/Tls.kt`.

## Dependencies

Deliberately minimal — `androidx.core`, `androidx.appcompat`,
`androidx.lifecycle:lifecycle-runtime-ktx`, and `kotlinx-coroutines-android`. JSON uses
the `org.json` implementation bundled with Android.

## Build

- Open in Android Studio, or from the command line:
  ```
  ./gradlew assembleDebug
  ```
- Requires the Android SDK (platform 36, build-tools 36.1.0). The SDK path is read from
  `local.properties` (not committed).

- minSdk 26 (Android 8.0), compileSdk/targetSdk 36.

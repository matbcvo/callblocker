# Call Blocker

A minimal Android app that rejects every incoming call from a number that is not in your
contacts. No libraries, no analytics, no network access — the release APK is roughly
100 KB.

## How it works

Android only allows one app to filter incoming calls: the holder of the
`ROLE_CALL_SCREENING` role. This app implements
[`CallScreeningService`](https://developer.android.com/reference/android/telecom/CallScreeningService);
when the framework hands it an incoming call it looks the number up through
`ContactsContract.PhoneLookup` and responds:

| Situation | Response |
| --- | --- |
| Number matches a contact | allowed, rings normally |
| Number not in contacts | rejected (or silenced, if you prefer) |
| Number withheld / private | rejected — there is nothing to match against |
| Contacts permission missing | **allowed** — see below |
| Outgoing call | allowed, untouched |

`PhoneLookup` matches on the normalised number, so a contact saved as `+372 5123 4567`
still matches an incoming call presented as `55123456`.

### Failing open

If contacts permission is revoked, or the contacts provider throws, the call is allowed
through and the reason is logged under the `CallBlocker` tag. A blocker that silently
swallows every call when a permission changes is worse than no blocker at all.

## Options

- **Block calls from unknown numbers** — master switch; when off nothing is filtered.
- **Silence instead of reject** — the call still arrives but the phone stays quiet and
  rings out to voicemail, instead of the caller getting a busy tone.
- **Keep blocked calls in the call log** — on by default, so you can see what you missed.
- **Notify me when a call is blocked** — a low-priority notification per blocked caller.

The app also keeps its own list of the last 100 blocked calls, visible on the main screen.

## Requirements

- Android 10 (API 29) or newer. This is a hard floor: on older versions only the default
  dialer could screen calls.
- **JDK 17** — what CI uses and what the project is verified against. Android Studio
  ships its own bundled JDK, so building there needs no system JDK at all.
- Android SDK with platform 36 and build-tools 36 (`compileSdk 36`).

Toolchain versions, all pinned in the repo: Gradle **9.7.0** (wrapper), Android Gradle
Plugin **9.3.1**, Kotlin **2.4.10**. AGP 9 has Kotlin support built in, so there is no
separate Kotlin plugin applied.

## Building

The Gradle wrapper is committed, so nothing needs installing beyond a JDK and the SDK:

```bash
./gradlew assembleDebug                        # app/build/outputs/apk/debug/
./gradlew installDebug                         # to a connected device
./gradlew assembleRelease                      # minified + resource-shrunk
```

If the SDK is not auto-detected, create `local.properties`:

```properties
sdk.dir=/path/to/Android/Sdk
```

## Continuous integration

`.github/workflows/build.yml` builds a debug APK and an unsigned release bundle on every
push to `main` and every pull request, and uploads both as workflow artifacts. The debug
APK is signed with the standard debug key and installs on a device as-is — the quickest
way to get a build onto a phone without touching signing.

Both workflows run the committed wrapper, so CI and local builds use the same Gradle.

## Releasing to Google Play

Play accepts an **`.aab` bundle**, not an APK, and it must be signed with your upload key.

### 1. Generate an upload key (once)

```bash
keytool -genkeypair -v -keystore upload.jks -alias upload \
        -keyalg RSA -keysize 2048 -validity 10000
```

`keytool` prompts for a keystore password — you choose it, it is not issued by anyone.
When it then asks for a key password, press RETURN to reuse the same one: since JDK 9
`keytool` writes PKCS12 keystores, which do not support a separate per-key password at
all. Passing a different one is ignored with a warning. The two Gradle fields remain
separate only for compatibility with the older JKS format.

Keep `upload.jks` out of the repo — `.gitignore` already excludes `*.jks`. If you lose it
you must ask Play support to reset it, so back it up somewhere durable.

### 2. Add repository secrets

Settings → Secrets and variables → Actions:

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | `base64 -w0 upload.jks` |
| `KEYSTORE_PASSWORD` | the password you chose above |
| `KEY_ALIAS` | `upload` |
| `KEY_PASSWORD` | the same value as `KEYSTORE_PASSWORD` |
| `PLAY_SERVICE_ACCOUNT_JSON` | *(optional)* service account JSON, for automatic upload |

Without `PLAY_SERVICE_ACCOUNT_JSON` the release still builds and attaches artifacts to a
GitHub Release; only the Play upload step is skipped. Set it up later, or never.

<details>
<summary>Getting <code>PLAY_SERVICE_ACCOUNT_JSON</code></summary>

Requires a Google Play Developer account (one-time $25 registration).

1. **Play Console → Setup → API access** — link a Google Cloud project.
2. **Google Cloud Console → IAM & Admin → Service Accounts** — create a service account,
   then **Keys → Add key → Create new key → JSON**. This downloads a `.json` file.
3. **Play Console → Users and permissions** — invite the service account's email address
   and grant it release permissions for this app, at minimum "Release to testing tracks"
   for the `internal` track the workflow targets.

The secret's value is the entire contents of the downloaded `.json` file.

Two ordering constraints: the API cannot create a listing, so the first AAB for a given
`applicationId` must be uploaded by hand; and freshly granted permissions take a few
minutes to propagate, so an immediate first run may return 401.

Menu paths drift — treat the above as the shape of the flow rather than exact labels.

</details>

### 3. Cut a release

```bash
git tag v1.0.0 && git push origin v1.0.0
```

`.github/workflows/release.yml` builds a signed `.aab` and `.apk`, attaches both to a
GitHub Release, and — if the service account secret exists — pushes the bundle to Play's
**internal testing** track. `versionCode` comes from the workflow run number, which is
monotonic, because Play rejects a re-used `versionCode`.

### 4. What Play needs beyond the build

- **`targetSdk 36`** — mandatory for new apps from 31 August 2026. Already set.
- **Privacy policy URL** — required, because the app reads contacts. Published from
  `docs/` by `.github/workflows/pages.yml`:
  <https://matbcvo.github.io/callblocker/privacy.html>
  Edit `docs/privacy.html` and push; the deploy takes about 15 seconds.
- **Data safety form** — declare contacts access; note that it is read on-device only and
  never transmitted, which is true here since the app has no network permission.
- The first upload of a given `applicationId` must be done by hand in the Play Console;
  the API cannot create the listing.

Restricted-permission declarations are *not* needed: the app avoids the `CALL_LOG` and
`SMS` permission groups entirely, which is part of why it uses `CallScreeningService`
rather than a broadcast receiver.

## First run

1. Launch the app and tap **Set as call screening app** — Android shows the system role
   picker. This replaces whatever screening app you had before (Google's spam filter, a
   carrier app, etc.); only one can hold the role.
2. Tap **Allow contacts access**.
3. The status line should read **Blocking is on**.

## Verifying

Blocking only exercises through a real incoming call over the SIM. To watch the decision
live:

```bash
adb logcat -s CallBlocker
```

Note the emulator's simulated calls do not always route through the screening role, so
test on hardware.

## Caveats

- Only one app can hold the screening role, so this cannot coexist with a carrier or
  Google spam filter.
- Rejected calls typically reach the caller as a busy signal; some networks send them to
  voicemail instead. Use **Silence instead of reject** if you want voicemail to be
  reliable.
- Emergency callbacks are not special-cased: if an emergency service calls you back from
  a number you have not saved, it will be blocked. Turn blocking off if you are expecting
  such a call.

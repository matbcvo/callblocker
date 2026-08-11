# Contributing

Thanks for taking a look. Issues and pull requests are both welcome.

## Building

You need a JDK 17 and the Android SDK. The Gradle wrapper is committed, so nothing else
needs installing:

```bash
./gradlew assembleDebug     # app/build/outputs/apk/debug/
./gradlew installDebug      # to a connected device
./gradlew lintDebug         # what CI checks
```

If the SDK is not found, create `local.properties` with `sdk.dir=/path/to/Android/Sdk`.
Opening the project in Android Studio handles all of this for you.

## What CI runs on a pull request

`.github/workflows/build.yml` runs three jobs, none of which need repository secrets, so
they work on pull requests from forks:

| Job | What it does |
| --- | --- |
| `translations` | Checks every translation is complete and declared. Seconds, no SDK. |
| `build` | Builds the debug APK and the release bundle. |
| `lint` | Runs Android lint. Informational — it will not block a merge. |

## Adding a translation

Two files, no code:

1. **Copy `app/src/main/res/values/strings.xml`** to `app/src/main/res/values-<code>/strings.xml`,
   where `<code>` is the ISO language code — `values-fi` for Finnish, `values-pt-rBR` for
   Brazilian Portuguese. Translate the values, leave the `name` attributes alone.
2. **Declare the language** in `app/src/main/res/xml/locales_config.xml`:

   ```xml
   <locale android:name="fi" />
   ```

   Skipping this is the easy mistake: the translation still works when the whole phone is
   switched to that language, but the app never appears in Android 13+'s per-app language
   picker. The `translations` job exists to catch exactly this.

Check your work before pushing:

```bash
python3 scripts/check-translations.py
```

It verifies that every translation is declared, every declared language exists, and no
string is missing, extra, or translated when it is marked `translatable="false"`.

### Notes for translators

- **Do not translate `url_source` or `url_privacy`.** They are marked
  `translatable="false"` and the checker will complain if you translate them anyway.
- **`app_name` is translated** — this app is named for what it does rather than as a
  brand, so a local word is right. `about_version` contains the app name too, so keep the
  two consistent.
- **`about_version` must keep its `%1$s` placeholder.** It is the version number.
- **Longer strings are fine.** The layout wraps rather than truncates, but it is worth
  running the app to check the switch labels look sensible.

## Pull requests

- One change per pull request, please.
- Match the surrounding style. The project has no third-party dependencies and that is
  deliberate — a pull request adding one needs to make its case.
- Explain *why* in the description. The what is visible in the diff.

## Reporting a problem with call blocking

Blocking behaviour can only be reproduced with a real incoming call, so logs help a lot:

```bash
adb logcat -s CallBlocker
```

Include your Android version, device, and which options were enabled. If a call was
allowed through when it should not have been, note whether the number was withheld and
whether contacts permission was granted — the app deliberately allows calls through when
it cannot check contacts.

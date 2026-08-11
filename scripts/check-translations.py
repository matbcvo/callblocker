#!/usr/bin/env python3
"""Checks that translations are wired up completely.

Android lint already reports strings missing from a translation, but it does not
know about locales_config.xml. A translation that is not declared there still works
when the whole system is switched to that language, but never appears in the app's
per-app language picker on Android 13+ — a silent half-failure that is easy to ship.

Run from the repository root:

    python3 scripts/check-translations.py
"""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ElementTree
from pathlib import Path

ANDROID = "{http://schemas.android.com/apk/res/android}"
RESOURCES = Path("app/src/main/res")
LOCALES_CONFIG = RESOURCES / "xml/locales_config.xml"
DEFAULT_LOCALE = "en"  # what values/ (unqualified) contains

# values-et, values-pt-rBR — but not values-night, values-v29, values-sw600dp.
LOCALE_DIRECTORY = re.compile(r"^values-([a-z]{2,3})(?:-r([A-Z]{2}))?$")


def declared_locales() -> set[str]:
    root = ElementTree.parse(LOCALES_CONFIG).getroot()
    return {
        locale.get(ANDROID + "name")
        for locale in root.findall("locale")
        if locale.get(ANDROID + "name")
    }


def translated_locales() -> dict[str, Path]:
    found: dict[str, Path] = {}
    for directory in sorted(RESOURCES.iterdir()):
        if not directory.is_dir():
            continue
        match = LOCALE_DIRECTORY.match(directory.name)
        if not match:
            continue
        strings = directory / "strings.xml"
        if strings.exists():
            language, region = match.groups()
            found[f"{language}-{region}" if region else language] = strings
    return found


def string_keys(path: Path) -> tuple[set[str], set[str]]:
    """Returns (all keys, keys marked translatable="false")."""
    root = ElementTree.parse(path).getroot()
    every: set[str] = set()
    untranslatable: set[str] = set()
    for element in root.findall("string"):
        name = element.get("name")
        if not name:
            continue
        every.add(name)
        if element.get("translatable") == "false":
            untranslatable.add(name)
    return every, untranslatable


def main() -> int:
    problems: list[str] = []

    declared = declared_locales()
    translated = translated_locales()

    # The default locale lives in values/ and has no values-<locale> directory,
    # so it is expected to be declared without a matching folder.
    for locale in sorted(translated.keys() - declared):
        problems.append(
            f"{locale}: has res/values-{locale}/strings.xml but is not declared in "
            f"{LOCALES_CONFIG}. Add <locale android:name=\"{locale}\" /> or the app "
            f"will not offer it in the per-app language picker."
        )

    for locale in sorted(declared - translated.keys() - {DEFAULT_LOCALE}):
        problems.append(
            f"{locale}: declared in {LOCALES_CONFIG} but res/values-{locale}/strings.xml "
            f"does not exist. The picker would offer a language with no translation."
        )

    base_keys, untranslatable = string_keys(RESOURCES / "values/strings.xml")
    expected = base_keys - untranslatable

    for locale, path in sorted(translated.items()):
        keys, _ = string_keys(path)
        for name in sorted(expected - keys):
            problems.append(f"{locale}: missing translation for \"{name}\"")
        for name in sorted(keys & untranslatable):
            problems.append(
                f"{locale}: translates \"{name}\", which values/strings.xml marks "
                f"translatable=\"false\""
            )
        for name in sorted(keys - base_keys):
            problems.append(
                f"{locale}: defines \"{name}\", which does not exist in values/strings.xml"
            )

    if problems:
        print("Translation problems found:\n", file=sys.stderr)
        for problem in problems:
            print(f"  - {problem}", file=sys.stderr)
        print(file=sys.stderr)
        return 1

    languages = ", ".join(sorted(translated)) or "none"
    print(f"Translations OK. Default: {DEFAULT_LOCALE}. Translated: {languages}.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

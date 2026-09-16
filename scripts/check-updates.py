#!/usr/bin/env python3
"""Check whether Minecraft or any build dependency has a newer version than gradle.properties.

Sources: Mojang piston-meta (game releases), Fabric meta (loader + supported game
versions), Modrinth (Fabric API, Cloth Config, Mod Menu), Fabric maven (loom).

Prints a Danish markdown report on stdout. When GITHUB_OUTPUT is set, writes
`level=<none|deps|release>` so the workflow can decide what to do:
  release - a new Minecraft release exists -> a new mod release is needed
  deps    - only build-time dependencies for the current MC version moved
  none    - everything is up to date
Exit code is always 0 unless a source is unreachable.
"""
import json
import os
import re
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PROPS = ROOT / "gradle.properties"
UA = "autotoolswap-update-check (https://github.com/mgjuhler/autotoolswap)"
MODRINTH = "https://api.modrinth.com/v2/project/{slug}/version"


def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.load(r)


def fetch_text(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=30) as r:
        return r.read().decode()


def read_props():
    props = {}
    for line in PROPS.read_text().splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        props[k.strip()] = v.strip()
    return props


def modrinth_match(slug, game_version, loader="fabric"):
    """Newest Modrinth version of `slug` for one game version, as (version, exact).

    Mods often ship on release day tagged only with the release candidates
    (Cloth Config 26.3.158 was tagged 26.3-rc-1..3 when 26.3 came out), so a build
    tagged "<version>-rc-N"/"-pre-N" counts as a fallback with exact=False.
    Returns (None, False) if neither exists.
    """
    versions = fetch(MODRINTH.format(slug=slug))
    candidates = (game_version + "-rc-", game_version + "-pre-")
    fallback = None
    for v in versions:  # Modrinth returns newest first
        if loader not in v["loaders"]:
            continue
        if game_version in v["game_versions"]:
            return v["version_number"], True
        if fallback is None and any(g.startswith(candidates) for g in v["game_versions"]):
            fallback = v["version_number"]
    return fallback, False


def modrinth_latest(slug, game_version, loader="fabric"):
    """Newest Modrinth version of `slug` for one game version (exact or RC-tagged); None if none."""
    return modrinth_match(slug, game_version, loader)[0]


def gradle_coord(slug, version_number):
    """Translate a Modrinth version number into what gradle.properties expects."""
    if slug == "cloth-config":
        return version_number.removesuffix("+fabric")  # maven.shedaniel.me has no +fabric suffix
    return version_number


def main():
    props = read_props()
    mc = props["minecraft_version"]
    report = []
    level = "none"

    # --- Minecraft -------------------------------------------------------
    manifest = fetch("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")
    latest_release = manifest["latest"]["release"]
    latest_snapshot = manifest["latest"]["snapshot"]
    new_mc = latest_release != mc

    # --- Fabric loader + game support ------------------------------------
    loaders = fetch("https://meta.fabricmc.net/v2/versions/loader")
    loader_stable = next(v["version"] for v in loaders if v["stable"])
    fabric_games = {g["version"] for g in fetch("https://meta.fabricmc.net/v2/versions/game")}

    # --- Mod dependencies on Modrinth ------------------------------------
    deps = [
        ("fabric_api_version", "fabric-api", "Fabric API"),
        ("cloth_config_version", "cloth-config", "Cloth Config"),
        ("modmenu_version", "modmenu", "Mod Menu"),
    ]

    current_updates = []
    for key, slug, label in deps:
        latest = modrinth_latest(slug, mc)
        latest = gradle_coord(slug, latest) if latest else None
        if latest and latest != props[key]:
            current_updates.append((label, props[key], latest))
    if loader_stable != props["loader_version"]:
        current_updates.append(("Fabric Loader", props["loader_version"], loader_stable))

    # --- Loom ------------------------------------------------------------
    loom_meta = fetch_text("https://maven.fabricmc.net/net/fabricmc/fabric-loom/maven-metadata.xml")
    loom_releases = [v for v in re.findall(r"<version>([^<]+)</version>", loom_meta)
                     if re.fullmatch(r"\d+\.\d+\.\d+", v)]
    loom_latest = loom_releases[-1] if loom_releases else "?"

    # --- Report ----------------------------------------------------------
    if new_mc:
        level = "release"
        report.append(f"## Ny Minecraft-release: **{latest_release}** (mod'et bygger mod {mc})")
        report.append("")
        report.append("Ny mod-release er påkrævet. Økosystemets status for den nye version:")
        report.append("")
        report.append("| Afhængighed | Klar til " + latest_release + "? |")
        report.append("|---|---|")
        report.append("| Fabric Loader | " + ("✅ understøttet" if latest_release in fabric_games else "❌ ikke i Fabric meta endnu") + " |")
        ready = latest_release in fabric_games
        for key, slug, label in deps:
            v, exact = modrinth_match(slug, latest_release)
            if v:
                note = "" if exact else f" (mærket til {latest_release}-rc — kontrollér med gametests)"
                report.append(f"| {label} | ✅ {gradle_coord(slug, v)}{note} |")
            else:
                report.append(f"| {label} | ❌ ingen version til {latest_release} endnu |")
                ready = False
        report.append("")
        report.append("**Alt er klar — kør opskriften 'Ny Minecraft-version' i CLAUDE.md.**" if ready
                      else "**Ikke alt er klar endnu** — afvent de manglende afhængigheder før release.")
        report.append("")

    if current_updates:
        if level == "none":
            level = "deps"
        report.append(f"## Nyere build-afhængigheder til Minecraft {mc}")
        report.append("")
        report.append("Påvirker ikke brugerne (mod'et kræver `fabric-api: *`), men bør bumpes ved næste release.")
        report.append("")
        report.append("| Pakke | Nu | Nyeste |")
        report.append("|---|---|---|")
        for label, cur, new in current_updates:
            report.append(f"| {label} | {cur} | **{new}** |")
        report.append("")

    if level == "none":
        report.append(f"## Alt ajour — Minecraft {mc} er stadig nyeste release, og alle afhængigheder er nyeste.")
        report.append("")

    report.append("### Baggrund")
    report.append("")
    report.append(f"- Nyeste Minecraft-snapshot: {latest_snapshot}")
    report.append(f"- Fabric Loader stable: {loader_stable} (vi: {props['loader_version']})")
    report.append(f"- Loom: nyeste stable release {loom_latest} (vi: {props['loom_version']})")
    report.append("")
    report.append("_Genereret af `scripts/check-updates.py` (GitHub Actions, dagligt)._")

    print("\n".join(report))

    out = os.environ.get("GITHUB_OUTPUT")
    if out:
        with open(out, "a") as f:
            f.write(f"level={level}\n")
            f.write(f"latest_release={latest_release}\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env bash
# Uploads a mod jar to CurseForge via the authors upload API.
# Usage: upload-curseforge.sh <jar> <mod-version> <mc-version> <changelog-file>
# Token is read from ~/.curseforge/token.
# Display name convention is fixed: "AutoToolSwap <mod-version>" - never anything else.
set -euo pipefail

JAR="${1:?usage: upload-curseforge.sh <jar> <mod-version> <mc-version> <changelog-file>}"
MOD_VERSION="${2:?missing mod version}"
MC_VERSION="${3:?missing minecraft version}"
CHANGELOG_FILE="${4:?missing changelog file}"
DISPLAY_NAME="AutoToolSwap $MOD_VERSION"

PROJECT_ID=1620708
API=https://minecraft.curseforge.com/api
TOKEN=$(cat ~/.curseforge/token)

[ -f "$JAR" ] || { echo "jar not found: $JAR" >&2; exit 1; }

VERSIONS=$(curl -sf "$API/game/versions" -H "X-Api-Token: $TOKEN")

# Loader and environment ids are stable names; for the MC version prefer the
# entry that is not the legacy type 1.
FABRIC_ID=$(jq -r '[.[] | select(.name == "Fabric")][0].id' <<<"$VERSIONS")
CLIENT_ID=$(jq -r '[.[] | select(.name == "Client")][0].id' <<<"$VERSIONS")
MC_ID=$(jq -r --arg v "$MC_VERSION" \
	'([.[] | select(.name == $v and .gameVersionTypeID != 1)] + [.[] | select(.name == $v)])[0].id' <<<"$VERSIONS")

for pair in "Fabric:$FABRIC_ID" "Client:$CLIENT_ID" "$MC_VERSION:$MC_ID"; do
	[ "${pair#*:}" != "null" ] || { echo "no CurseForge game version id for ${pair%%:*}" >&2; exit 1; }
done

METADATA=$(jq -n \
	--arg changelog "$(cat "$CHANGELOG_FILE")" \
	--arg displayName "$DISPLAY_NAME" \
	--argjson gameVersions "[$MC_ID, $FABRIC_ID, $CLIENT_ID]" \
	'{
		changelog: $changelog,
		changelogType: "markdown",
		displayName: $displayName,
		gameVersions: $gameVersions,
		releaseType: "release",
		relations: {projects: [
			{slug: "cloth-config", type: "requiredDependency"},
			{slug: "modmenu", type: "optionalDependency"}
		]}
	}')

# --form-string: curl -F would parse ; and @ inside the JSON as field options
RESPONSE=$(curl -sf -X POST "$API/projects/$PROJECT_ID/upload-file" \
	-H "X-Api-Token: $TOKEN" \
	--form-string "metadata=$METADATA" \
	-F "file=@$JAR")

FILE_ID=$(jq -r '.id // empty' <<<"$RESPONSE")
[ -n "$FILE_ID" ] || { echo "upload failed: $RESPONSE" >&2; exit 1; }
echo "uploaded: file id $FILE_ID ($DISPLAY_NAME)"

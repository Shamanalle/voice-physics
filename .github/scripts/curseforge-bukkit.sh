#!/usr/bin/env bash
# Uploads the plugin jar to its CurseForge project in the Bukkit Plugins section. mc-publish only
# knows the Mods section, so this talks to the upload API directly: Bukkit projects are served by
# dev.bukkit.org, with minecraft.curseforge.com tried as well in case the project answers there.
#
# Usage: curseforge-bukkit.sh <project id> <jar> <display name> <release type> <notes.md> <game versions...>
# Needs CURSEFORGE_TOKEN. Game versions the site doesn't list are tried as major.minor, then skipped.
set -euo pipefail

project="$1"; jar="$2"; name="$3"; release_type="$4"; notes="$5"; shift 5
wanted=("$@")

for base in https://dev.bukkit.org https://minecraft.curseforge.com; do
  echo "Trying ${base}"
  status=$(curl -sS -o versions.json -w '%{http_code}' -H "X-Api-Token: ${CURSEFORGE_TOKEN}" "${base}/api/game/versions" || echo 000)
  if [ "${status}" != "200" ]; then
    echo "::warning::${base}: game versions answered ${status}"
    continue
  fi

  ids=()
  for version in "${wanted[@]}"; do
    id=$(jq -r --arg v "${version}" '[.[] | select(.name == $v)][0].id // empty' versions.json)
    if [ -z "${id}" ]; then
      short=$(cut -d. -f1-2 <<< "${version}")
      id=$(jq -r --arg v "${short}" '[.[] | select(.name == $v)][0].id // empty' versions.json)
    fi
    if [ -n "${id}" ]; then
      ids+=("${id}")
    else
      echo "::warning::${base} has no game version ${version}"
    fi
  done
  if [ "${#ids[@]}" -eq 0 ]; then
    echo "::warning::${base}: none of the game versions matched; the site lists:"
    jq -r '.[].name' versions.json | sort -uV | tail -n 60 | tr '\n' ' '
    echo
    continue
  fi

  jq -n \
    --rawfile changelog "${notes}" \
    --arg name "${name}" \
    --arg type "${release_type}" \
    --argjson versions "$(printf '%s\n' "${ids[@]}" | sort -un | jq -s .)" \
    '{changelog: $changelog, changelogType: "markdown", displayName: $name, gameVersions: $versions, releaseType: $type}' \
    > metadata.json

  status=$(curl -sS -o upload.json -w '%{http_code}' -H "X-Api-Token: ${CURSEFORGE_TOKEN}" \
    -F "metadata=<metadata.json" -F "file=@${jar}" \
    "${base}/api/projects/${project}/upload-file" || echo 000)
  if [ "${status}" = "200" ]; then
    echo "Uploaded to ${base}: file $(jq -r '.id' upload.json)"
    exit 0
  fi
  echo "::warning::${base}: upload answered ${status}: $(cat upload.json 2>/dev/null)"
done

echo "::error::The plugin could not be uploaded to CurseForge project ${project}"
exit 1

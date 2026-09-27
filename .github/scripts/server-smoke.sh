#!/usr/bin/env bash
# Starts a real Minecraft server with Simple Voice Chat and the addon, runs a few /vcd commands from
# the console and stops it. Fails when the server does not come up, when /vcd answers with bare text
# keys, or when anything of the addon throws.
#
#   server-smoke.sh <fabric|neoforge|forge|paper|folia> <minecraft version> <addon jar> [loader version]
#
# The loader version is needed for NeoForge and Forge: the installer's version (Forge: "1.20.1-47.4.0"). Everything else is
# looked up: Fabric's loader and installer from meta.fabricmc.net, Paper and Folia builds from PaperMC,
# Simple Voice Chat and Fabric API from Modrinth.
set -euo pipefail

loader="$1"
mc="$2"
addon="$(realpath "$3")"
loader_version="${4:-}"
dir="${RUNNER_TEMP:-/tmp}/server-${loader}-${mc}"
log="${dir}/server.log"
start_timeout=300

rm -rf "${dir}"
mkdir -p "${dir}"
cd "${dir}"

say() {
  echo "::group::$*" >&2
  echo "::endgroup::" >&2
  echo "== $*"
}

fetch() {
  curl -fsSL --retry 4 --retry-delay 3 -o "$2" "$1"
}

# The newest file of a Modrinth project for this loader and Minecraft version
modrinth() {
  local slug="$1" loaders="$2" out="$3"
  local url
  url=$(curl -fsSL --retry 4 -G "https://api.modrinth.com/v2/project/${slug}/version" \
      --data-urlencode "loaders=${loaders}" --data-urlencode "game_versions=[\"${mc}\"]" \
    | jq -r '[.[] | select(.version_type == "release" or .version_type == "beta")][0].files
             | (map(select(.primary)) + .)[0].url // empty')
  if [ -z "${url}" ]; then
    echo "No ${slug} on Modrinth for ${loaders} ${mc}"
    return 1
  fi
  echo "${slug}: ${url}"
  fetch "${url}" "${out}"
}

# A PaperMC project's newest build of this version (the fill API, then the older v2 API)
papermc() {
  local project="$1" url
  url=$(curl -fsSL --retry 4 "https://fill.papermc.io/v3/projects/${project}/versions/${mc}/builds/latest" 2>/dev/null \
    | jq -r '.downloads["server:default"].url // empty' || true)
  if [ -z "${url}" ]; then
    local build
    build=$(curl -fsSL --retry 4 "https://api.papermc.io/v2/projects/${project}/versions/${mc}/builds" \
      | jq -r '.builds[-1].build')
    url="https://api.papermc.io/v2/projects/${project}/versions/${mc}/builds/${build}/downloads/${project}-${mc}-${build}.jar"
  fi
  echo "${project}: ${url}"
  fetch "${url}" server.jar
}

# "latest": the newest version PaperMC builds this project for
if [ "${mc}" = "latest" ]; then
  mc=$(curl -fsSL --retry 4 "https://api.papermc.io/v2/projects/${loader}" 2>/dev/null | jq -r '.versions[-1] // empty' || true)
  if [ -z "${mc}" ]; then
    mc=$(curl -fsSL --retry 4 "https://fill.papermc.io/v3/projects/${loader}" \
      | jq -r '[.versions | to_entries[] | .value[]] | .[0] // empty')
  fi
  echo "Newest ${loader}: ${mc}"
fi

say "Setting up ${loader} ${mc}"
case "${loader}" in
  fabric)
    fabric_loader=$(curl -fsSL https://meta.fabricmc.net/v2/versions/loader | jq -r '[.[] | select(.stable)][0].version')
    installer=$(curl -fsSL https://meta.fabricmc.net/v2/versions/installer | jq -r '[.[] | select(.stable)][0].version')
    fetch "https://meta.fabricmc.net/v2/versions/loader/${mc}/${fabric_loader}/${installer}/server/jar" server.jar
    mkdir -p mods
    modrinth fabric-api '["fabric"]' mods/fabric-api.jar || exit 1
    modrinth simple-voice-chat '["fabric"]' mods/voicechat.jar || exit 1
    cp "${addon}" mods/
    run=(java -Xmx2G -jar server.jar nogui)
    settings="config/vc-audio-distance-server.properties"
    ;;
  neoforge | forge)
    if [ "${loader}" = "neoforge" ]; then
      fetch "https://maven.neoforged.net/releases/net/neoforged/neoforge/${loader_version}/neoforge-${loader_version}-installer.jar" installer.jar
    else
      fetch "https://maven.minecraftforge.net/net/minecraftforge/forge/${loader_version}/forge-${loader_version}-installer.jar" installer.jar
    fi
    java -jar installer.jar --installServer > installer.log 2>&1 || { tail -40 installer.log; exit 1; }
    mkdir -p mods
    modrinth simple-voice-chat "[\"${loader}\"]" mods/voicechat.jar || exit 1
    cp "${addon}" mods/
    echo "-Xmx2G" > user_jvm_args.txt
    run=(bash ./run.sh nogui)
    settings="config/vc-audio-distance-server.properties"
    ;;
  paper | folia)
    papermc "${loader}"
    mkdir -p plugins
    # Simple Voice Chat's Bukkit file is tagged for Paper; Folia may have a tag of its own
    modrinth simple-voice-chat "[\"${loader}\"]" plugins/voicechat.jar \
      || modrinth simple-voice-chat '["paper"]' plugins/voicechat.jar || exit 1
    cp "${addon}" plugins/
    run=(java -Xmx2G -jar server.jar nogui)
    settings="plugins/VoicePhysics/vc-audio-distance-server.properties"
    ;;
  *)
    echo "Unknown loader ${loader}"
    exit 2
    ;;
esac

echo "eula=true" > eula.txt
cat > server.properties <<'EOF'
online-mode=false
level-type=minecraft\:flat
generate-structures=false
spawn-protection=0
max-players=4
view-distance=4
simulation-distance=4
server-port=25599
EOF

# The console reads from a FIFO that stays open, so commands can be sent while it runs
mkfifo console
exec 3<>console
"${run[@]}" < console > "${log}" 2>&1 &
server=$!

send() {
  echo "$*" >&3
}

stop_server() {
  if kill -0 "${server}" 2>/dev/null; then
    send stop
    for _ in $(seq 1 60); do
      kill -0 "${server}" 2>/dev/null || break
      sleep 1
    done
    kill -9 "${server}" 2>/dev/null || true
  fi
}
trap stop_server EXIT

say "Waiting for the server"
up=false
for _ in $(seq 1 "${start_timeout}"); do
  if grep -q 'Done (' "${log}"; then
    up=true
    break
  fi
  if ! kill -0 "${server}" 2>/dev/null; then
    break
  fi
  sleep 1
done
if [ "${up}" != true ]; then
  tail -80 "${log}"
  echo "::error::${loader} ${mc} did not start"
  exit 1
fi

say "Running /vcd"
for command in "vcd status" "vcd zones" "vcd walls 55" "vcd rule sneak 0.5" "vcd zone create smoke 3" \
               "vcd debug nobody" "vcd reload" "vcd status"; do
  send "${command}"
  sleep 2
done
stop_server
trap - EXIT

failed=false
if ! grep -q 'Voice Physics [0-9]' "${log}"; then
  echo "::error::/vcd status did not answer with the Voice Physics status line"
  failed=true
fi
if grep -Eq '(^|[^.a-z_])(status\.title|status\.svc|walls_set|rule\.sneak)([^.a-z_]|$)' "${log}"; then
  echo "::error::/vcd answered with text keys instead of messages"
  failed=true
fi
if grep -q 'at com\.kasper\.vcdistance' "${log}"; then
  echo "::error::The addon threw an exception"
  grep -n -B8 -A4 'at com\.kasper\.vcdistance' "${log}" | head -80
  failed=true
fi
if [ ! -f "${settings}" ]; then
  echo "::error::No settings file at ${settings}"
  failed=true
elif ! grep -q '^walls_strength=0.55' "${settings}"; then
  echo "::error::/vcd walls 55 was not saved to ${settings}"
  failed=true
fi

say "Server log"
grep -Ei 'voice ?physics|vcd|voicechat|voice chat|exception|error' "${log}" | head -120 || true

if [ "${failed}" = true ]; then
  echo "--- last lines ---"
  tail -60 "${log}"
  exit 1
fi
echo "${loader} ${mc}: OK"

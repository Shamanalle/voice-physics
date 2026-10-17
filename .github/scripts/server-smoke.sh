#!/usr/bin/env bash
# Starts a real Minecraft server with Simple Voice Chat and the addon, runs a few /vcd commands from
# the console and stops it. Fails when the server does not come up, when /vcd answers with bare text
# keys, or when anything of the addon throws.
#
#   server-smoke.sh <fabric|neoforge|forge|paper|folia> <minecraft version> <addon jar> [loader version]
#
# The loader version is needed for NeoForge and Forge: the installer's version (Forge and NeoForge for 1.20.1: "1.20.1-47.4.0"). Everything else is
# looked up: Fabric's loader and installer from meta.fabricmc.net, Paper and Folia builds from PaperMC,
# Simple Voice Chat and Fabric API from Modrinth.
#
# A download that fails on the way (net-failure.sh: a timeout, a reset connection, HTTP 429 / 5xx) is
# tried again: curl does it by itself, the Forge / NeoForge installer runs once more, and the server is
# started once more when it stopped on a download before any mod was loaded. Each of these leaves a
# warning on the run. Everything after that is checked once, as before.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=net-failure.sh
. "${here}/net-failure.sh"

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

# curl tries a timeout, a refused connection and HTTP 408 / 429 / 5xx again by itself; a 404 is final
api() {
  curl -fsSL --retry 4 --retry-delay 3 --retry-connrefused --connect-timeout 20 --max-time 120 "$@"
}

fetch() {
  curl -fsSL --retry 4 --retry-delay 3 --retry-connrefused --connect-timeout 20 --max-time 900 -o "$2" "$1"
}

# A warning on the run, so a passing job still shows that something had to be tried twice
retrying() {
  echo "::warning title=Network failure (${loader} ${mc})::$*"
  sleep "${NET_RETRY_DELAY:-30}"
}

# The newest file of a Modrinth project for this loader and Minecraft version. A file built for exactly
# this version ("forge-1.21-2.5.20") comes first: one listed for 1.21 - 1.21.1 may need 1.21.1.
modrinth() {
  local slug="$1" loaders="$2" out="$3"
  local url
  url=$(api -G "https://api.modrinth.com/v2/project/${slug}/version" \
      --data-urlencode "loaders=${loaders}" --data-urlencode "game_versions=[\"${mc}\"]" \
    | jq -r --arg mc "${mc}" '[.[] | select(.version_type == "release" or .version_type == "beta")]
             | ([.[] | select(.version_number | test("(^|[^0-9.])" + ($mc | gsub("\\."; "\\.")) + "-[0-9]"))] + .)[0].files
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
  url=$(api "https://fill.papermc.io/v3/projects/${project}/versions/${mc}/builds/latest" 2>/dev/null \
    | jq -r '.downloads["server:default"].url // empty' || true)
  if [ -z "${url}" ]; then
    local build
    build=$(api "https://api.papermc.io/v2/projects/${project}/versions/${mc}/builds" \
      | jq -r '.builds[-1].build')
    url="https://api.papermc.io/v2/projects/${project}/versions/${mc}/builds/${build}/downloads/${project}-${mc}-${build}.jar"
  fi
  echo "${project}: ${url}"
  fetch "${url}" server.jar
}

# "latest": the newest version PaperMC builds this project for
if [ "${mc}" = "latest" ]; then
  mc=$(api "https://api.papermc.io/v2/projects/${loader}" 2>/dev/null | jq -r '.versions[-1] // empty' || true)
  if [ -z "${mc}" ]; then
    mc=$(api "https://fill.papermc.io/v3/projects/${loader}" \
      | jq -r '[.versions | to_entries[] | .value[]] | .[0] // empty')
  fi
  echo "Newest ${loader}: ${mc}"
fi

say "Setting up ${loader} ${mc}"
case "${loader}" in
  fabric)
    fabric_loader=$(api https://meta.fabricmc.net/v2/versions/loader | jq -r '[.[] | select(.stable)][0].version')
    installer=$(api https://meta.fabricmc.net/v2/versions/installer | jq -r '[.[] | select(.stable)][0].version')
    fetch "https://meta.fabricmc.net/v2/versions/loader/${mc}/${fabric_loader}/${installer}/server/jar" server.jar
    mkdir -p mods
    modrinth fabric-api '["fabric"]' mods/fabric-api.jar || exit 1
    modrinth simple-voice-chat '["fabric"]' mods/voicechat.jar || exit 1
    cp "${addon}" mods/
    run=(java -Xmx2G -jar server.jar nogui)
    settings="config/vc-audio-distance-server.properties"
    ;;
  neoforge | forge)
    if [ "${loader}" = "neoforge" ] && [ "${loader_version#1.20.1-}" != "${loader_version}" ]; then
      # NeoForge for 1.20.1 is still published as Forge ("1.20.1-47.1.106")
      fetch "https://maven.neoforged.net/releases/net/neoforged/forge/${loader_version}/forge-${loader_version}-installer.jar" installer.jar
    elif [ "${loader}" = "neoforge" ]; then
      fetch "https://maven.neoforged.net/releases/net/neoforged/neoforge/${loader_version}/neoforge-${loader_version}-installer.jar" installer.jar
    else
      fetch "https://maven.minecraftforge.net/net/minecraftforge/forge/${loader_version}/forge-${loader_version}-installer.jar" installer.jar
    fi
    # The installer downloads Minecraft and the loader's libraries; run again, it keeps the files that
    # came whole (it checks each one's checksum) and fetches the rest
    if ! java -jar installer.jar --installServer > installer.log 2>&1; then
      if ! network_failure installer.log; then
        tail -40 installer.log
        exit 1
      fi
      mv installer.log installer-1.log
      retrying "the installer could not download everything ($(grep -Eo "${net_failure_pattern}" installer-1.log | head -1)), running it once more"
      java -jar installer.jar --installServer > installer.log 2>&1 || { tail -40 installer.log; exit 1; }
    fi
    mkdir -p mods
    modrinth simple-voice-chat "[\"${loader}\"]" mods/voicechat.jar || exit 1
    cp "${addon}" mods/
    if [ -f run.sh ]; then
      echo "-Xmx2G" > user_jvm_args.txt
      run=(bash ./run.sh nogui)
    else
      # Forge 1.16.5 and older have no run.sh: the installer leaves the launch jar
      run=(java -Xmx2G -jar "forge-${loader_version}.jar" nogui)
    fi
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

# Starts the server and waits for it. Sets state to "up", "stopped" (the process ended first) or
# "timeout". The console reads from a FIFO that stays open, so commands can be sent while it runs.
start_server() {
  rm -f console
  mkfifo console
  exec 3<>console
  "${run[@]}" < console > "${log}" 2>&1 &
  server=$!
  for _ in $(seq 1 "${start_timeout}"); do
    if grep -q 'Done (' "${log}"; then
      state=up
      return
    fi
    if ! kill -0 "${server}" 2>/dev/null; then
      state=stopped
      return
    fi
    sleep 1
  done
  state=timeout
}

say "Waiting for the server"
server=''
trap stop_server EXIT
start_server
# Fabric's launcher and Paper download Minecraft and its libraries on the first start, before any mod
# or plugin is loaded. Only a server that stopped there, on a failed download, is started again.
if [ "${state}" = stopped ] && network_failure "${log}" \
    && ! grep -Eiq 'vcdistance|vc_audio_distance|voice.?physics|voicechat|voice chat' "${log}"; then
  exec 3>&-
  mv "${log}" "${dir}/server-1.log"
  retrying "${loader} ${mc} stopped on a download before loading any mod ($(grep -Eo "${net_failure_pattern}" "${dir}/server-1.log" | head -1)), starting it once more"
  start_server
fi
if [ "${state}" != up ]; then
  tail -80 "${log}"
  echo "::error::${loader} ${mc} did not start"
  exit 1
fi

say "Running /vcd"
# walls 150 must be refused, undo must take walls 70 back to 55, and deleting a zone asks first
for command in "vcd status" "vcd help" "vcd help zone" "vcd zones" "vcd walls 55" "vcd rule sneak 0.5" \
               "vcd zone set smoke_world voice_range 16" "vcd zoen" "vcd walls 150" "vcd walls 70" "vcd undo" \
               "vcd notices off" "vcd log" "vcd zone info smoke_world" "vcd zone delete smoke_world" \
               "vcd debug nobody" "vcd reload" "vcd status"; do
  send "${command}"
  sleep 2
done
stop_server
trap - EXIT

# Some consoles (Paper 1.20) colour the log with ANSI codes, which split the answers below
sed -i 's/\x1b\[[0-9;]*m//g' "${log}"

failed=false
if ! grep -q 'Voice Physics [0-9]' "${log}"; then
  echo "::error::/vcd status did not answer with the Voice Physics status line"
  failed=true
fi
if grep -Eq '(^|[^.a-z_])(status\.title|status\.svc|walls_set|rule\.sneak|btn\.[a-z_]+|hover\.[a-z_]+|error\.[a-z_]+|help\.examples|undo\.done|zone\.delete_ask|log\.[a-z_]+|notices_(on|off))([^.a-z_]|$)' "${log}"; then
  echo "::error::/vcd answered with text keys instead of messages"
  failed=true
fi
for answer in 'Examples (click to type)' 'does not work. Allowed: 0-100|off' 'Did you mean /vcd zone' 'Undone: /vcd walls 70' \
              'Delete zone smoke_world?' 'voice_range 16' 'Changes to the settings (' 'undid /vcd walls 70' \
              'Zone names above the hotbar are off'; do
  if ! grep -qF "${answer}" "${log}"; then
    echo "::error::/vcd did not answer with \"${answer}\""
    failed=true
  fi
done
if grep -q 'at com\.kasper\.vcdistance' "${log}"; then
  echo "::error::The addon threw an exception"
  grep -n -B8 -A4 'at com\.kasper\.vcdistance' "${log}" | head -80
  failed=true
fi
# The universal Forge / NeoForge jar has one pack.mcmeta for every Minecraft from 1.16.5 to 26.x
if grep -Ei 'vc_audio_distance|vc-audio-distance|voice-physics' "${log}" | grep -Ei 'pack|mcmeta|metadata|resources' \
    | grep -Eiq 'fail|error|invalid|missing|couldn|unable|broken'; then
  echo "::error::The addon's resources or pack.mcmeta were refused"
  grep -Ei 'vc_audio_distance|vc-audio-distance|voice-physics' "${log}" | grep -Ei 'pack|mcmeta|metadata|resources' | head -20
  failed=true
fi
if [ ! -f "${settings}" ]; then
  echo "::error::No settings file at ${settings}"
  failed=true
elif ! grep -q '^walls_strength=0.55' "${settings}"; then
  echo "::error::/vcd walls 55 was not saved to ${settings}"
  failed=true
elif ! grep -q 'console.set./vcd walls 55' "$(dirname "${settings}")/vc-audio-distance-changes.log"; then
  echo "::error::/vcd walls 55 is not in the change log"
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

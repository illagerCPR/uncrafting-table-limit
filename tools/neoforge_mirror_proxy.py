"""Local HTTP proxy that serves the NeoForge Maven layout from the BMCLAPI mirror.

Why this exists: maven.neoforged.net resets TLS connections from this network, but the
BMCLAPI mirror serves the same artifact tree. ModDevGradle resolves part of its toolchain
(NeoForm runtime, external tools, Minecraft artifacts) through repository URLs that are
baked into the plugin, so a plain `repositories { maven { ... } }` entry in the build script
is not always enough. Pointing the toolchain at this loopback proxy keeps the official
layout while the bytes come from the mirror.

Usage:
    python3 tools/neoforge_mirror_proxy.py [port]        # default 8899

Then run Gradle with:
    ./gradlew -PneoforgeProxy=http://127.0.0.1:8899/releases <tasks>

The proxy is only used for the NeoForge repository prefix (/releases/...); anything else
is answered with 404 so the build falls through to the normal repositories.
"""

import os
import sys
import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

MIRROR = "https://mirrors4.qlu.edu.cn/bmclapi"
PREFIX = "/releases/"
TIMEOUT = 120

# Local Gradle artifact caches, used as a fallback when the mirror does not carry an
# artifact (the mirror is not a complete copy of the NeoForge repository).
CACHE_ROOTS = [
    Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches" / "modules-2" / "files-2.1",
    Path(".gradle-cache-seed"),
]


def synthesize_pom(rel_path: str) -> str:
    """Build a minimal, dependency-free POM for a module whose metadata lives in the local cache."""
    parts = rel_path[:-len(".pom")].split("/")
    group = ".".join(parts[:-3])
    name = parts[-3]
    version = parts[-2]
    return (
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        '<project xmlns="http://maven.apache.org/POM/4.0.0">\n'
        '  <modelVersion>4.0.0</modelVersion>\n'
        f'  <groupId>{group}</groupId>\n'
        f'  <artifactId>{name}</artifactId>\n'
        f'  <version>{version}</version>\n'
        '  <packaging>pom</packaging>\n'
        '</project>\n'
    )


def from_local_cache(rel_path: str):
    """Resolve a Maven path such as net/neoforged/foo/1.0/foo-1.0.pom from the Gradle cache."""
    parts = rel_path.split("/")
    if len(parts) < 4:
        return None
    filename = parts[-1]
    version = parts[-2]
    name = parts[-3]
    group = ".".join(parts[:-3])
    for root in CACHE_ROOTS:
        module_dir = root / group / name / version
        if not module_dir.is_dir():
            continue
        for hash_dir in module_dir.iterdir():
            candidate = hash_dir / filename
            if candidate.is_file():
                return candidate
    return None


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):  # keep the console quiet
        pass

    def _proxy(self, send_body: bool) -> None:
        if not self.path.startswith(PREFIX):
            self.send_response(404)
            self.send_header("Content-Length", "0")
            self.end_headers()
            return

        rel_path = self.path[len(PREFIX):]
        upstream = MIRROR + "/" + rel_path
        request = urllib.request.Request(upstream, method="GET")
        request.add_header("User-Agent", "uncraftinglimit-build-proxy")
        try:
            with urllib.request.urlopen(request, timeout=TIMEOUT) as response:
                payload = response.read()
                self._send(200, payload, response.headers.get("Content-Type"), send_body)
                return
        except urllib.error.HTTPError as error:
            if error.code != 404:
                self._send(error.code, b"", None, send_body)
                return
        except Exception as error:  # network hiccup: fall through to the local cache
            sys.stderr.write(f"proxy error for {upstream}: {error}\n")

        local = from_local_cache(rel_path)
        if local is not None:
            self._send(200, local.read_bytes(), None, send_body)
            return

        # The mirror is an incomplete copy of the repository and the local cache may only
        # hold Gradle module metadata (.module) without the matching .pom. Gradle insists on
        # a POM before it will look at .module metadata, so synthesise a dependency-free POM
        # when the module metadata is available locally: the variant information then comes
        # from the cached .module file, which is what NeoForm actually needs.
        if rel_path.endswith(".pom"):
            module_path = rel_path[:-len(".pom")] + ".module"
            if from_local_cache(module_path) is not None:
                self._send(200, synthesize_pom(rel_path).encode(), "application/xml", send_body)
                return

        self._send(404, b"", None, send_body)

    def _send(self, status: int, payload: bytes, content_type, send_body: bool) -> None:
        sys.stderr.write(f"{status} {self.path}\n")
        sys.stderr.flush()
        self.send_response(status)
        if content_type:
            self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        if send_body and payload:
            self.wfile.write(payload)

    def do_GET(self):
        self._proxy(True)

    def do_HEAD(self):
        self._proxy(False)


def main() -> None:
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8899
    server = ThreadingHTTPServer(("127.0.0.1", port), Handler)
    print(f"NeoForge mirror proxy listening on http://127.0.0.1:{port}/releases/ -> {MIRROR}", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()

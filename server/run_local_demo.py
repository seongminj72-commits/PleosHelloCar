"""Provision a short-lived token to the debug app over adb; never prints secrets."""
import argparse
import getpass
import json
import os
import secrets
import shutil
import subprocess
import time
from http.server import ThreadingHTTPServer
from eta_proxy import Handler, Session

adb = os.environ.get("ADB") or shutil.which("adb")
if not adb:
    raise SystemExit("Set ADB to your Android SDK platform-tools/adb path")
package = "com.example.pleoshellocar"
user = subprocess.check_output([adb, "shell", "am", "get-current-user"], text=True).strip()
if not user.isdigit():
    raise SystemExit("Cannot determine active AAOS user")
parser = argparse.ArgumentParser()
parser.add_argument("--fixture", action="store_true", help="Synthetic 481-second response; no TMAP calls")
args = parser.parse_args()
key = "fixture-only-not-a-key" if args.fixture else (os.environ.get("TMAP_APP_KEY") or getpass.getpass("TMAP appKey (server only, hidden): "))
token = secrets.token_urlsafe(32)
expires = int(time.time()) + 1800
session = Session(key, token, expires)
if args.fixture:
    session.upstream = lambda body, key: 481
    original_route = session.route
    def fixture_route(authorization, payload):
        result = original_route(authorization, payload)
        result["source"] = "TEST_FIXTURE"
        return result
    session.route = fixture_route
server = ThreadingHTTPServer(("127.0.0.1", 8787), Handler)
server.session = session
config = json.dumps({"url": "http://localhost:8787/v1/eta", "token": token, "expiresEpochSeconds": expires})
try:
    subprocess.run([adb, "reverse", "tcp:8787", "tcp:8787"], check=True, capture_output=True)
    subprocess.run([adb, "shell", "run-as", package, "--user", user, "sh", "-c",
                    "'mkdir -p files && cat > files/eta-proxy.json'"],
                   input=config, text=True, check=True, capture_output=True)
    print("TEST_FIXTURE: synthetic ETA, no TMAP calls" if args.fixture else "TMAP: live upstream enabled", flush=True)
    print("Proxy ready: 30 minute session, max 100 requests, max 2/minute. Ctrl+C to revoke.", flush=True)
    server.serve_forever()
except KeyboardInterrupt:
    pass
finally:
    server.server_close()
    subprocess.run([adb, "shell", "run-as", package, "--user", user, "rm", "-f", "files/eta-proxy.json"], capture_output=True)
    subprocess.run([adb, "reverse", "--remove", "tcp:8787"], capture_output=True)

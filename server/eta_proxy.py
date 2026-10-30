"""Single-session development proxy. Loopback only; never expose as a public service.
TMAP_APP_KEY remains on this server. Runtime client tokens expire within one hour.
Production needs an HTTPS identity gateway, user authorization and persistent quotas.
"""
import hmac
import json
import math
import os
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.error import HTTPError, URLError
from urllib.request import Request, build_opener, HTTPRedirectHandler

class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None

urlopen = build_opener(NoRedirect()).open

TMAP_URL = "https://apis.openapi.sk.com/tmap/routes?version=1"

class ApiError(Exception):
    def __init__(self, status, code):
        self.status, self.code = status, code

def coordinate(value):
    if not isinstance(value, dict):
        raise ApiError(400, "INVALID_COORDINATE")
    lat, lon = value.get("latitude"), value.get("longitude")
    if any(type(v) not in (int, float) for v in (lat, lon)):
        raise ApiError(400, "INVALID_COORDINATE")
    # TMAP automotive routing is Korea-only in this demo. Reject global GPS defaults.
    if not (33 <= lat <= 39.5 and 124 <= lon <= 132):
        raise ApiError(400, "OUTSIDE_SERVICE_AREA")
    return lat, lon

def route_body(payload):
    if not isinstance(payload, dict):
        raise ApiError(400, "INVALID_REQUEST")
    start_lat, start_lon = coordinate(payload.get("origin"))
    end_lat, end_lon = coordinate(payload.get("destination"))
    return dict(startX=start_lon, startY=start_lat, endX=end_lon, endY=end_lat,
                reqCoordType="WGS84GEO", resCoordType="WGS84GEO", searchOption=0)

def parse_duration(data):
    try:
        seconds = data["features"][0]["properties"]["totalTime"]
        if type(seconds) not in (int, float) or not math.isfinite(seconds) or seconds != int(seconds) or not 0 <= seconds <= 10800:
            raise ValueError()
        return int(seconds)
    except (KeyError, TypeError, IndexError, ValueError, OverflowError):
        raise ApiError(502, "INVALID_UPSTREAM_RESPONSE") from None

def call_tmap(body, key, opener=urlopen):
    request = Request(TMAP_URL, data=json.dumps(body).encode(), method="POST",
                      headers={"Content-Type": "application/json", "Accept": "application/json", "appKey": key})
    try:
        with opener(request, timeout=8) as response:
            raw = response.read(2_000_001)
            if len(raw) > 2_000_000:
                raise ApiError(502, "UPSTREAM_RESPONSE_TOO_LARGE")
            return parse_duration(json.loads(raw))
    except HTTPError as error:
        if error.code == 429:
            raise ApiError(429, "UPSTREAM_QUOTA") from None
        # Do not leak upstream error bodies, keys or request coordinates.
        raise ApiError(502, "UPSTREAM_ERROR") from None
    except (URLError, TimeoutError, OSError):
        raise ApiError(503, "UPSTREAM_UNAVAILABLE") from None
    except (ValueError, UnicodeError):
        raise ApiError(502, "INVALID_UPSTREAM_RESPONSE") from None

class Session:
    def __init__(self, key, token, expires, budget=100, clock=time.time, upstream=call_tmap):
        if not key or not token or len(token) < 32:
            raise ValueError("Missing server key or runtime session token")
        if not clock() < expires <= clock() + 3600:
            raise ValueError("Session must expire within one hour")
        if not 1 <= budget <= 100:
            raise ValueError("Session budget must be 1..100")
        self.key, self.token, self.expires = key, token, expires
        self.budget, self.clock, self.upstream = budget, clock, upstream
        self.lock = threading.Lock()
        self.calls = []
        self.used = 0

    def authorize(self, authorization):
        if self.clock() >= self.expires or not hmac.compare_digest(authorization.encode(), ("Bearer " + self.token).encode()):
            raise ApiError(401, "UNAUTHORIZED")

    def route(self, authorization, payload):
        self.authorize(authorization)
        body = route_body(payload)
        with self.lock:
            now = self.clock()
            self.calls = [stamp for stamp in self.calls if now - stamp < 60]
            if len(self.calls) >= 2 or self.used >= self.budget:
                raise ApiError(429, "SESSION_QUOTA")
            self.calls.append(now)
            self.used += 1  # Failed upstream calls also consume budget.
        seconds = self.upstream(body, self.key)
        return {"source": "TMAP", "durationSeconds": seconds}

class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_):
        pass  # Do not record coordinates, bearer tokens or URLs.

    def do_POST(self):
        try:
            self.connection.settimeout(10)
            if self.path != "/v1/eta":
                raise ApiError(404, "NOT_FOUND")
            authorization = self.headers.get("Authorization", "")
            self.server.session.authorize(authorization)
            if self.headers.get_content_type() != "application/json" or self.headers.get("Transfer-Encoding"):
                raise ApiError(400, "INVALID_CONTENT_TYPE")
            try:
                size = int(self.headers.get("Content-Length", "0"))
            except ValueError:
                raise ApiError(400, "INVALID_LENGTH") from None
            if not 0 < size <= 2048:
                raise ApiError(413, "INVALID_LENGTH")
            try:
                payload = json.loads(self.rfile.read(size))
            except (ValueError, UnicodeError):
                raise ApiError(400, "INVALID_JSON") from None
            result = self.server.session.route(authorization, payload)
            status = 200
        except ApiError as error:
            status, result = error.status, {"error": error.code}
        except (OSError, TimeoutError):
            status, result = 408, {"error": "REQUEST_TIMEOUT"}
        data = json.dumps(result).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Connection", "close")
        if status == 429:
            self.send_header("Retry-After", "60")
        self.end_headers()
        try:
            self.wfile.write(data)
        except OSError:
            pass

if __name__ == "__main__":
    session = Session(os.environ.get("TMAP_APP_KEY"), os.environ.get("ETA_SESSION_TOKEN"),
                      float(os.environ.get("ETA_SESSION_EXPIRES", "0")))
    server = ThreadingHTTPServer(("127.0.0.1", 8787), Handler)
    server.session = session
    print("ETA development proxy listening on loopback:8787; no request logging", flush=True)
    server.serve_forever()

import io
import json
import unittest
import threading
from http.server import ThreadingHTTPServer
from urllib.request import Request, urlopen
from eta_proxy import Handler
from urllib.error import HTTPError, URLError
from eta_proxy import ApiError, Session, call_tmap, parse_duration, route_body

PAYLOAD = {"origin": {"latitude": 37.5, "longitude": 127.0},
           "destination": {"latitude": 37.56, "longitude": 126.98}}

class ProxyTest(unittest.TestCase):
    def setUp(self):
        self.now = 1000
        self.token = "test_only_" + "x" * 32
        self.session = Session("fixture-key", self.token, 1100, clock=lambda: self.now,
                               upstream=lambda body, key: 481)
    def error(self, status, fn):
        with self.assertRaises(ApiError) as caught:
            fn()
        self.assertEqual(status, caught.exception.status)
    def test_route_uses_longitude_as_x(self):
        body = route_body(PAYLOAD)
        self.assertEqual(127.0, body["startX"])
        self.assertEqual(37.56, body["endY"])
    def test_response_seconds(self):
        self.assertEqual({"source": "TMAP", "durationSeconds": 481}, self.session.route("Bearer " + self.token, PAYLOAD))
    def test_authentication_and_expiry(self):
        self.error(401, lambda: self.session.route("Bearer wrong", PAYLOAD))
        self.now = 1100
        self.error(401, lambda: self.session.route("Bearer " + self.token, PAYLOAD))
        self.assertEqual(0, self.session.used)
    def test_rate_limit_and_recovery(self):
        for _ in range(2):
            self.session.route("Bearer " + self.token, PAYLOAD)
        self.error(429, lambda: self.session.route("Bearer " + self.token, PAYLOAD))
        self.now += 60
        self.session.route("Bearer " + self.token, PAYLOAD)
    def test_session_budget(self):
        self.session.budget = 1
        self.session.route("Bearer " + self.token, PAYLOAD)
        self.now += 60
        self.error(429, lambda: self.session.route("Bearer " + self.token, PAYLOAD))
    def test_invalid_coordinates(self):
        for value in (None, {}, {"latitude": True, "longitude": 127},
                      {"latitude": float("nan"), "longitude": 127},
                      {"latitude": 10 ** 1000, "longitude": 127},
                      {"latitude": 37, "longitude": -122}):
            self.error(400, lambda: route_body({"origin": value, "destination": PAYLOAD["destination"]}))
    def test_malformed_and_extreme_durations(self):
        for value in (None, True, "480", -1, 10801, 1.5, float("nan")):
            self.error(502, lambda: parse_duration({"features": [{"properties": {"totalTime": value}}]}))
        for value in ({}, {"features": []}, {"features": None}):
            self.error(502, lambda: parse_duration(value))
    def test_tmap_contract_and_minimization(self):
        def opener(request, timeout):
            self.assertEqual("https://apis.openapi.sk.com/tmap/routes?version=1", request.full_url)
            self.assertEqual("fixture-key", request.get_header("Appkey"))
            self.assertEqual("WGS84GEO", json.loads(request.data)["reqCoordType"])
            return io.BytesIO(b'{"features":[{"properties":{"totalTime":481,"totalDistance":1000}}]}')
        self.assertEqual(481, call_tmap(route_body(PAYLOAD), "fixture-key", opener))
    def test_upstream_errors(self):
        for error, code in ((HTTPError("", 429, "", {}, None), 429),
                            (HTTPError("", 401, "", {}, None), 502),
                            (HTTPError("", 500, "", {}, None), 502),
                            (URLError("offline"), 503), (TimeoutError(), 503)):
            def opener(*args, **kwargs):
                raise error
            self.error(code, lambda: call_tmap({}, "fixture-key", opener))
    def test_bad_json(self):
        self.error(502, lambda: call_tmap({}, "fixture-key", lambda *a, **k: io.BytesIO(b"not-json")))



class HttpContractTest(unittest.TestCase):
    def setUp(self):
        self.token = "fixture_" + "z" * 32
        self.server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        import time
        self.server.session = Session("fixture-key", self.token, time.time() + 60,
                                      upstream=lambda body, key: 480)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.url = "http://127.0.0.1:%d/v1/eta" % self.server.server_port
    def tearDown(self):
        self.server.shutdown(); self.server.server_close(); self.thread.join()
    def request(self, body, token=None):
        return Request(self.url, data=body, method="POST", headers={
            "Content-Type": "application/json", "Authorization": "Bearer " + (token or self.token)})
    def test_actual_http_contract_no_cache(self):
        with urlopen(self.request(json.dumps(PAYLOAD).encode()), timeout=2) as response:
            self.assertEqual("no-store", response.headers["Cache-Control"])
            self.assertEqual({"source": "TMAP", "durationSeconds": 480}, json.load(response))
    def test_unauthorized_and_malformed_requests_never_call_upstream(self):
        for body, token, expected in ((b"{}", "wrong", 401), (b"bad-json", self.token, 400),
                                       (b"x" * 2049, self.token, 413)):
            with self.assertRaises(HTTPError) as caught:
                urlopen(self.request(body, token), timeout=2)
            self.assertEqual(expected, caught.exception.code)
            caught.exception.close()
        self.assertEqual(0, self.server.session.used)

if __name__ == "__main__":
    unittest.main()

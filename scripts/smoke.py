"""Exercise the packaged application over HTTP; Python standard library only."""
import json
import time
import urllib.request
import urllib.error
from datetime import datetime, timezone, timedelta

BASE = "http://localhost:8080"


def request(path, payload=None, method="GET"):
    data = None if payload is None else json.dumps(payload).encode()
    req = urllib.request.Request(BASE + path, data=data, method=method,
                                 headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=5) as response:
        return json.load(response)


for attempt in range(60):
    try:
        if request("/actuator/health")["status"] == "UP":
            break
    except (urllib.error.URLError, TimeoutError):
        pass
    time.sleep(2)
else:
    raise RuntimeError("Application did not become healthy")

contract = request("/api/contracts", {
    "contractNumber": "SMOKE-" + str(time.time_ns()),
    "customerName": "Smoke Test", "amount": 1000
}, "POST")
message = request(f"/api/contracts/{contract['id']}/send", {"scenario": "FAIL_ONCE"}, "POST")
assert message["status"] == "FAILED", message
retried = request(f"/api/messages/{message['id']}/retry", method="POST")
assert retried["status"] == "SUCCESS" and retried["attemptCount"] == 2, retried
history = request(f"/api/messages/{message['id']}/history")
assert [entry["status"] for entry in history] == ["FAILED", "SUCCESS"], history
date = datetime.now(timezone(timedelta(hours=9))).date().isoformat()
report = request("/api/reports/daily?date=" + date)
assert report["success"] >= 1 and report["total"] == report["success"] + report["failed"], report
print("Packaged application smoke test passed: create, fail, retry, history, daily report")

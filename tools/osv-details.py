import collections
import json
import socket
import sys
import time
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

# Prefer IPv4: the production host has no working IPv6 route to api.osv.dev.
_ORIGINAL_GETADDRINFO = socket.getaddrinfo


def _ipv4_first(host, port, family=0, type=0, proto=0, flags=0):
    addresses = _ORIGINAL_GETADDRINFO(host, port, family, type, proto, flags)
    ipv4 = [item for item in addresses if item[0] == socket.AF_INET]
    return ipv4 or addresses


socket.getaddrinfo = _ipv4_first


def fetch(vuln_id):
    last_error = None
    for attempt in range(1, 4):
        try:
            with urllib.request.urlopen(
                f"https://api.osv.dev/v1/vulns/{vuln_id}", timeout=20
            ) as response:
                vuln = json.load(response)
            severity = (vuln.get("database_specific") or {}).get("severity") or ""
            if not severity:
                for item in vuln.get("severity", []):
                    if item.get("score"):
                        severity = item["score"]
                        break
            return {
                "id": vuln_id,
                "severity": severity,
                "summary": vuln.get("summary"),
                "aliases": vuln.get("aliases", []),
                "fixed": sorted({
                    event.get("fixed")
                    for affected in vuln.get("affected", [])
                    for rng in affected.get("ranges", [])
                    for event in rng.get("events", [])
                    if event.get("fixed")
                }),
            }
        except Exception as error:  # noqa: BLE001
            last_error = error
            if attempt < 3:
                time.sleep(attempt)
    return {
        "id": vuln_id,
        "severity": "ERROR",
        "summary": str(last_error),
        "aliases": [],
        "fixed": [],
    }


if len(sys.argv) < 2:
    raise SystemExit("usage: osv-details.py SCAN_JSON [OUTPUT_JSON]")

scan = json.load(open(sys.argv[1], encoding="utf-8"))
output = Path(sys.argv[2]) if len(sys.argv) > 2 else Path("backend-osv-details.json")
ids = sorted({finding["id"] for finding in scan["findings"]})
with ThreadPoolExecutor(max_workers=8) as executor:
    details = list(executor.map(fetch, ids))

output.write_text(json.dumps(details, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
severity_by_id = {item["id"]: item["severity"] for item in details}
counts = collections.Counter(severity_by_id.get(vuln_id, "UNKNOWN") for vuln_id in ids)
print("unique_advisories=", len(ids))
print("severity_counts=", dict(counts))
print("details_file=", output)

blocking = []
seen = set()
for finding in scan["findings"]:
    severity = severity_by_id.get(finding["id"], "").upper()
    if severity in {"HIGH", "CRITICAL"}:
        key = (finding["package"], finding["version"], finding["id"], severity)
        if key not in seen:
            seen.add(key)
            blocking.append(key)
print("high_critical_unique=", len(blocking))
for package, version, vuln_id, severity in sorted(blocking)[:40]:
    print(severity, package, version, vuln_id)

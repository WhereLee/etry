import json
import sys
import collections
import urllib.request
from concurrent.futures import ThreadPoolExecutor


def fetch(vuln_id):
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
        return {"id": vuln_id, "severity": "ERROR", "summary": str(error), "aliases": [], "fixed": []}


scan = json.load(open(sys.argv[1], encoding="utf-8"))
ids = sorted({finding["id"] for finding in scan["findings"]})
with ThreadPoolExecutor(max_workers=8) as executor:
    details = list(executor.map(fetch, ids))

json.dump(details, open("backend-osv-details.json", "w", encoding="utf-8"), ensure_ascii=False, indent=2)
severity_by_id = {item["id"]: item["severity"] for item in details}
counts = collections.Counter(severity_by_id.get(vuln_id, "UNKNOWN") for vuln_id in ids)
print("unique_advisories=", len(ids))
print("severity_counts=", dict(counts))

high = []
seen = set()
for finding in scan["findings"]:
    severity = severity_by_id.get(finding["id"], "")
    if severity.upper() in {"HIGH", "CRITICAL"}:
        key = (finding["package"], finding["version"], finding["id"])
        if key not in seen:
            seen.add(key)
            high.append(key)
print("high_unique=", len(high))
for item in sorted(high)[:40]:
    print("HIGH", *item)

import json
import re
import socket
import sys
import time
import urllib.request
from pathlib import Path

# The host resolves api.osv.dev to IPv6 first even though IPv6 is unreachable.
# Prefer IPv4 when available so CI does not intermittently fail.
_ORIGINAL_GETADDRINFO = socket.getaddrinfo


def _ipv4_first(host, port, family=0, type=0, proto=0, flags=0):
    addresses = _ORIGINAL_GETADDRINFO(host, port, family, type, proto, flags)
    ipv4 = [item for item in addresses if item[0] == socket.AF_INET]
    return ipv4 or addresses


socket.getaddrinfo = _ipv4_first

MODE = sys.argv[1]
INPUT = sys.argv[2]


def maven_queries(path):
    seen = set()
    queries = []
    for line in Path(path).read_text().splitlines():
        line = line.strip()
        if " -- " in line:
            line = line.split(" -- ", 1)[0].strip()
        parts = line.split(":")
        if len(parts) < 5 or parts[2] != "jar":
            continue
        name = f"{parts[0]}:{parts[1]}"
        version = parts[3]
        if (name, version) in seen:
            continue
        seen.add((name, version))
        queries.append((name, version, "Maven"))
    return queries


def alpine_queries(path):
    seen = set()
    queries = []
    pattern = re.compile(r"^(.+)-([0-9][0-9A-Za-z.+_~]*(?:-r[0-9]+)?)$")
    for line in Path(path).read_text().splitlines():
        match = pattern.match(line.strip())
        if not match:
            continue
        name, version = match.group(1), match.group(2)
        if (name, version) in seen:
            continue
        seen.add((name, version))
        queries.append((name, version, "Alpine"))
    return queries


queries = maven_queries(INPUT) if MODE == "maven" else alpine_queries(INPUT)
payload = json.dumps({
    "queries": [
        {"package": {"name": name, "ecosystem": ecosystem}, "version": version}
        for name, version, ecosystem in queries
    ]
}).encode()

last_error = None
body = None
for attempt in range(1, 4):
    try:
        request = urllib.request.Request(
            "https://api.osv.dev/v1/querybatch",
            data=payload,
            headers={"Content-Type": "application/json"},
        )
        with urllib.request.urlopen(request, timeout=120) as response:
            body = json.load(response)
        results = body.get("results") or []
        if len(results) != len(queries):
            raise RuntimeError(
                f"OSV querybatch returned {len(results)} results for {len(queries)} queries"
            )
        break
    except Exception as error:  # noqa: BLE001
        last_error = error
        if attempt < 3:
            time.sleep(attempt * 2)
else:
    raise SystemExit(f"OSV querybatch failed after 3 attempts: {last_error}")

findings = []
for (name, version, ecosystem), result in zip(queries, body.get("results", [])):
    for vuln in result.get("vulns", []):
        severity = (vuln.get("database_specific") or {}).get("severity") or ""
        if not severity:
            for item in vuln.get("severity", []):
                severity = item.get("score", "")
                if severity:
                    break
        findings.append({
            "package": name,
            "version": version,
            "ecosystem": ecosystem,
            "id": vuln.get("id"),
            "summary": vuln.get("summary"),
            "severity": severity,
            "aliases": vuln.get("aliases", []),
        })

print(json.dumps({
    "mode": MODE,
    "queried": len(queries),
    "findings": findings,
}, ensure_ascii=False, indent=2))
print(f"queried={len(queries)} findings={len(findings)}", file=sys.stderr)
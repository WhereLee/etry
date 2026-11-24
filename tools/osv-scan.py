import json
import re
import sys
import urllib.request
from pathlib import Path

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
request = urllib.request.Request(
    "https://api.osv.dev/v1/querybatch",
    data=payload,
    headers={"Content-Type": "application/json"},
)
with urllib.request.urlopen(request, timeout=120) as response:
    body = json.load(response)

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

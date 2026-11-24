#!/usr/bin/env python3
"""Fail-closed OSV gate for HIGH/CRITICAL findings.

Usage: osv-gate.py SCAN_JSON DETAILS_JSON [CONFIG_TOML]
"""

import collections
import datetime as dt
import json
import sys
import tomllib
from pathlib import Path

THRESHOLD = {"CRITICAL", "HIGH"}
TODAY = dt.date.today()


def parse_ignore_until(value):
    if isinstance(value, dt.datetime):
        return value.date()
    if isinstance(value, dt.date):
        return value
    if isinstance(value, str):
        return dt.date.fromisoformat(value)
    return None


def main():
    if len(sys.argv) < 3:
        print("usage: osv-gate.py SCAN_JSON DETAILS_JSON [CONFIG_TOML]", file=sys.stderr)
        return 2

    scan = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
    details = json.loads(Path(sys.argv[2]).read_text(encoding="utf-8"))
    config_path = Path(sys.argv[3]) if len(sys.argv) > 3 else Path("osv-scanner.toml")

    details_by_id = {item.get("id"): item for item in details}
    findings_by_id = collections.defaultdict(list)
    for finding in scan.get("findings", []):
        findings_by_id[finding.get("id")].append(finding)

    ignored = {}
    if config_path.exists():
        with config_path.open("rb") as handle:
            config = tomllib.load(handle)
        for entry in config.get("IgnoredVulns", []):
            vuln_id = entry.get("id")
            until = parse_ignore_until(entry.get("ignoreUntil"))
            if vuln_id and until and until >= TODAY:
                ignored[vuln_id] = until
    else:
        print(f"warning: config not found: {config_path}", file=sys.stderr)

    unresolved = []
    severity_by_id = {}
    for vuln_id in sorted(findings_by_id):
        severity = str((details_by_id.get(vuln_id) or {}).get("severity") or "").upper()
        if severity in {"", "UNKNOWN", "ERROR"}:
            unresolved.append((vuln_id, severity or "EMPTY", (details_by_id.get(vuln_id) or {}).get("summary", "")))
        else:
            severity_by_id[vuln_id] = severity

    counts = collections.Counter(severity_by_id.values())
    print(f"unique_advisories={len(findings_by_id)}")
    print(f"severity_counts={dict(sorted(counts.items()))}")

    violations = []
    for vuln_id, severity in sorted(severity_by_id.items()):
        if severity not in THRESHOLD:
            continue
        packages = sorted({(f.get("package"), f.get("version")) for f in findings_by_id[vuln_id]})
        if vuln_id in ignored:
            print(f"IGNORED {severity} {vuln_id} until={ignored[vuln_id]} packages={packages}")
        else:
            violations.append((severity, vuln_id, packages))

    for vuln_id, severity, summary in unresolved:
        print(f"ERROR unresolved severity id={vuln_id} severity={severity} summary={summary}")
    for severity, vuln_id, packages in violations:
        print(f"FAIL {severity} {vuln_id} packages={packages}")

    if unresolved or violations:
        print(
            f"OSV_GATE=FAIL unresolved={len(unresolved)} high_critical={len(violations)}",
            file=sys.stderr,
        )
        return 1

    print("OSV_GATE=PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
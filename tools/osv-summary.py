import json
import collections

scan = json.load(open("backend-osv.json", encoding="utf-8"))
details = {item["id"]: item for item in json.load(open("backend-osv-details.json", encoding="utf-8"))}
rows = []
for finding in scan["findings"]:
    detail = details.get(finding["id"], {})
    rows.append({
        "severity": (detail.get("severity") or "UNKNOWN").upper(),
        "package": finding["package"],
        "version": finding["version"],
        "id": finding["id"],
        "summary": detail.get("summary") or "",
        "fixed": detail.get("fixed") or [],
    })

print("unique_advisories", len({row["id"] for row in rows}))
print("severity_counts", dict(collections.Counter(
    next(row["severity"] for row in rows if row["id"] == vuln_id)
    for vuln_id in {row["id"] for row in rows}
)))
for severity in ("CRITICAL", "HIGH"):
    print(f"== {severity} packages ==")
    packages = sorted({(row["package"], row["version"]) for row in rows if row["severity"] == severity})
    for package, version in packages:
        related = [row for row in rows if row["severity"] == severity and row["package"] == package and row["version"] == version]
        fixed = sorted({fixed for row in related for fixed in row["fixed"]})
        print(f"{package}:{version} advisories={len({row['id'] for row in related})} fixed={fixed[:4]}")

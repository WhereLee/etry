import json

details = json.load(open("backend-osv-details.json", encoding="utf-8"))
for detail in details:
    if detail.get("severity") in ("CRITICAL", "HIGH"):
        print(detail.get("severity"), detail.get("id"), "|", detail.get("summary"), "| fixed:", detail.get("fixed"))

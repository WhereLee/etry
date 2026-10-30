#!/usr/bin/env python3
"""给 rag-1325750042 桶的 CORS 规则补上 http://124.223.36.154（etry 站点）。
保留原有 origin，只加这一条，幂等。
PUT ?cors 要求 Content-MD5，而 cos_client 的 request() 不支持额外 header，所以这里手写一次。"""
import base64
import hashlib
import sys
import urllib.request

sys.path.insert(0, "/home/ubuntu/.dsh/cos")
import cos_client  # noqa: E402
from cos_client import BUCKET, CTX, REGION, build_authorization, request  # noqa: E402

TARGET = "http://124.223.36.154"
HOST = f"{BUCKET}.cos.{REGION}.myqcloud.com"
PARAMS = {"cors": ""}


def get_cors():
    status, _headers, body = request("GET", "/", dict(PARAMS))
    return status, body


def put_cors(xml_text):
    body = xml_text.encode("utf-8")
    md5 = base64.b64encode(hashlib.md5(body).digest()).decode()
    req = urllib.request.Request(f"https://{HOST}/?cors=", method="PUT", data=body)
    req.add_header("Host", HOST)
    req.add_header("Authorization", build_authorization("PUT", "/", dict(PARAMS), HOST))
    req.add_header("Content-Length", str(len(body)))
    req.add_header("Content-MD5", md5)
    req.add_header("Content-Type", "application/xml")
    try:
        with urllib.request.urlopen(req, timeout=60, context=CTX) as resp:
            return resp.status, resp.read()
    except urllib.error.HTTPError as e:
        return e.code, e.read()


status, body = get_cors()
if status != 200:
    print("GET cors failed", status, body[:300].decode("utf-8", "replace"))
    sys.exit(1)

xml = body.decode("utf-8")
print("BEFORE:", xml)

if f"<AllowedOrigin>{TARGET}</AllowedOrigin>" in xml:
    print("ALREADY_PRESENT")
    sys.exit(0)

patched = xml.replace("<CORSRule>", f"<CORSRule><AllowedOrigin>{TARGET}</AllowedOrigin>", 1)
status, resp = put_cors(patched)
print("PUT:", status, resp[:300].decode("utf-8", "replace"))
if status not in (200, 204):
    sys.exit(1)

status, body = get_cors()
print("AFTER:", body.decode("utf-8")[:900])

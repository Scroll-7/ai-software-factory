"""Génère un JWT local de développement (jamais un secret externe).

Le secret doit rester identique à `security.jwt.secret` dans application.yml.
Usage : python scripts/dev_token.py
"""
import base64
import hashlib
import hmac
import json
import os
import time

SECRET = os.getenv(
    "PFE_JWT_SECRET", "local-dev-only-key-change-me-0123456789abcdef"
).encode("utf-8")


def b64(value: bytes) -> str:
    return base64.urlsafe_b64encode(value).rstrip(b"=").decode("ascii")


header = b64(json.dumps({"alg": "HS256", "typ": "JWT"}, separators=(",", ":")).encode())
now = int(time.time())
payload = b64(json.dumps(
    {"sub": "requester", "iat": now, "exp": now + 7200}, separators=(",", ":")
).encode())
signature = b64(hmac.new(SECRET, f"{header}.{payload}".encode(), hashlib.sha256).digest())
print(f"{header}.{payload}.{signature}")

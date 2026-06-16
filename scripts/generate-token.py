#!/usr/bin/env python3
"""
Generates a signed RS256 JWT for local development.

Usage:
    python3 scripts/generate-token.py --scope "orders:read orders:write"
    python3 scripts/generate-token.py --scope "payments:read payments:write" --sub gateway

Requires: pip install PyJWT cryptography
"""
import argparse
import time
import jwt
from pathlib import Path

PRIVATE_KEY_PATH = Path(__file__).parent.parent / "rsa-private-key-pkcs8.pem"
ISSUER = "test-issuer"


def main():
    parser = argparse.ArgumentParser(description="Generate a dev JWT for the order-service")
    parser.add_argument("--scope", default="orders:read orders:write payments:read payments:write",
                        help="Space-separated scopes")
    parser.add_argument("--sub", default="dev-user", help="Subject claim")
    parser.add_argument("--exp", type=int, default=3600, help="Expiry in seconds (default: 3600)")
    args = parser.parse_args()

    private_key = PRIVATE_KEY_PATH.read_text()
    now = int(time.time())

    payload = {
        "iss": ISSUER,
        "sub": args.sub,
        "scope": args.scope,
        "iat": now,
        "exp": now + args.exp,
    }

    token = jwt.encode(payload, private_key, algorithm="RS256", headers={"kid": "dev-key-1"})
    print(token)


if __name__ == "__main__":
    main()

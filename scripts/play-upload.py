#!/usr/bin/env python3
"""Uploads an app bundle to a Google Play testing track (default: internal) and publishes it there.

Uses the Google Play Developer API with a service account key (JSON), signing the OAuth token
request with the openssl command, so it needs no Python packages.

    scripts/play-upload.py app/build/outputs/bundle/release/app-release.aab --notes "What changed"

Key file: $PLAY_KEY, default ~/.config/boldblot/play-service-account.json (never commit it).
"""
import argparse, base64, json, os, subprocess, tempfile, time, urllib.parse, urllib.request

PACKAGE = "com.boldblot.backgammonplyground"
API = "https://androidpublisher.googleapis.com"


def b64(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def token(key: dict) -> str:
    now = int(time.time())
    head = b64(json.dumps({"alg": "RS256", "typ": "JWT"}).encode())
    claims = b64(json.dumps({
        "iss": key["client_email"], "scope": "https://www.googleapis.com/auth/androidpublisher",
        "aud": key["token_uri"], "iat": now, "exp": now + 3600,
    }).encode())
    with tempfile.NamedTemporaryFile("w", delete=False) as f:
        os.chmod(f.name, 0o600)
        f.write(key["private_key"])
    try:
        sig = subprocess.run(["openssl", "dgst", "-sha256", "-sign", f.name], input=f"{head}.{claims}".encode(),
                             capture_output=True, check=True).stdout
    finally:
        os.unlink(f.name)
    body = urllib.parse.urlencode({"grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer",
                                   "assertion": f"{head}.{claims}.{b64(sig)}"}).encode()
    return json.load(urllib.request.urlopen(key["token_uri"], body))["access_token"]


def call(method, url, tok, data=None, ctype="application/json"):
    req = urllib.request.Request(url, data=data, method=method,
                                 headers={"Authorization": f"Bearer {tok}", "Content-Type": ctype})
    try:
        with urllib.request.urlopen(req, timeout=600) as r:
            return json.load(r)
    except urllib.error.HTTPError as e:
        raise SystemExit(f"{method} {url}\n{e.code} {e.read().decode()[:2000]}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("bundle")
    ap.add_argument("--track", default="internal", help="track, or several separated by commas (e.g. alpha,internal)")
    ap.add_argument("--name", help="release name, e.g. '0.6 (29)'")
    ap.add_argument("--notes", default="", help="release notes")
    ap.add_argument("--lang", default="en-US", help="language of the notes, e.g. en-CA")
    a = ap.parse_args()
    key = json.load(open(os.environ.get("PLAY_KEY", os.path.expanduser("~/.config/boldblot/play-service-account.json"))))
    tok = token(key)
    base = f"{API}/androidpublisher/v3/applications/{PACKAGE}/edits"
    edit = call("POST", base, tok, b"{}")["id"]
    with open(a.bundle, "rb") as f:
        up = call("POST", f"{API}/upload/androidpublisher/v3/applications/{PACKAGE}/edits/{edit}/bundles?uploadType=media",
                  tok, f.read(), "application/octet-stream")
    code = str(up["versionCode"])
    release = {"name": a.name or code, "versionCodes": [code], "status": "completed"}
    if a.notes:
        release["releaseNotes"] = [{"language": a.lang, "text": a.notes}]
    tracks = [t.strip() for t in a.track.split(",") if t.strip()]
    for t in tracks:
        call("PUT", f"{base}/{edit}/tracks/{t}", tok, json.dumps({"track": t, "releases": [release]}).encode())
    call("POST", f"{base}/{edit}:commit", tok, b"")
    print(f"Uploaded version code {code} and published it on: {', '.join(tracks)}.")


if __name__ == "__main__":
    main()

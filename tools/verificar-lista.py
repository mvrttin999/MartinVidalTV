#!/usr/bin/env python3
"""Verifica una lista M3U hasta el primer segmento de video.

Para cada entrada comprueba, en orden:
  1. que el master responda 200 y sea HLS (#EXTM3U);
  2. que no use DRM FairPlay (#EXT-X-SESSION-KEY con SAMPLE-AES / skd://);
  3. que la primera variante responda 200;
  4. que el primer segmento (.ts/.m4s) responda 200.

Uso:
    python3 tools/verificar-lista.py app/src/main/assets/lista.m3u

Sale con codigo 1 si alguna entrada falla, para poder usarlo en un hook.
Solo necesita la biblioteca estandar (Python 3.8+).
"""
import concurrent.futures as cf
import re
import sys
import urllib.error
import urllib.request
from urllib.parse import urljoin

UA = ("Mozilla/5.0 (Linux; Android 12; SM-G991B) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36")
DRM = (b"SAMPLE-AES", b"skd://", b"com.apple.streamingkeydelivery")
TIMEOUT = 12
WORKERS = 6


def get(url, limit=300_000, ref=None):
    headers = {"User-Agent": UA}
    if ref:
        headers["Referer"] = ref
    try:
        req = urllib.request.Request(url, headers=headers)
        with urllib.request.urlopen(req, timeout=TIMEOUT) as res:
            return res.status, res.read(limit), res.geturl()
    except urllib.error.HTTPError as exc:
        return exc.code, b"", url
    except Exception:  # noqa: BLE001 - red, DNS, TLS, timeouts
        return -1, b"", url


def probe(url):
    status, body, final = get(url)
    if status != 200:
        return f"master-{status}"
    text = body.decode("utf-8", "replace")
    if not text.lstrip().startswith("#EXTM3U"):
        return "no-hls"
    for marker in DRM:
        if marker in body:
            return "drm-" + marker.decode()

    lines = text.splitlines()
    variant = None
    for i, line in enumerate(lines):
        if line.startswith("#EXT-X-STREAM-INF"):
            for nxt in lines[i + 1:]:
                nxt = nxt.strip()
                if nxt and not nxt.startswith("#"):
                    variant = urljoin(final, nxt)
                    break
        if variant:
            break

    media, media_url = body, final
    if variant:
        status, media, media_url = get(variant, ref=final)
        if status != 200:
            return f"variante-{status}"
        for marker in DRM:
            if marker in media:
                return "drm-variante-" + marker.decode()

    segments = [ln.strip() for ln in media.decode("utf-8", "replace").splitlines()
                if ln.strip() and not ln.startswith("#")]
    if not segments:
        return "sin-segmentos"
    status, payload, _ = get(urljoin(media_url, segments[0]), limit=1500, ref=media_url)
    if status != 200 or not payload:
        return f"segmento-{status}"
    return "ok"


def parse(path):
    entries, meta = [], None
    with open(path, encoding="utf-8") as fh:
        for raw in fh:
            line = raw.strip()
            if line.startswith("#EXTINF"):
                attrs = dict(re.findall(r'([A-Za-z0-9_-]+)="([^"]*)"', line))
                name = line.split(",", 1)[1].strip() if "," in line else ""
                meta = (attrs.get("group-title", ""), name, attrs.get("tvg-logo", ""))
            elif line.startswith("http"):
                if meta is None:
                    meta = ("", "(sin EXTINF)", "")
                entries.append((*meta, line))
                meta = None
    return entries


def main(argv):
    if len(argv) != 2:
        print(__doc__.strip().splitlines()[0])
        print("uso: verificar-lista.py <lista.m3u>")
        return 2
    entries = parse(argv[1])
    if not entries:
        print("no se encontraron entradas con URL en", argv[1])
        return 2

    rows = []
    with cf.ThreadPoolExecutor(max_workers=WORKERS) as pool:
        for entry, state in zip(entries, pool.map(lambda e: probe(e[3]), entries)):
            rows.append((entry, state))
            flag = "OK " if state == "ok" else "MAL"
            print(f"{flag} {entry[0]:<14} {entry[1][:40]:<42} {state}", flush=True)

    failed = [e for e, state in rows if state != "ok"]
    print(f"\ntotal {len(rows)} | ok {len(rows) - len(failed)} | con fallo {len(failed)}")
    return 0 if not failed else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv))

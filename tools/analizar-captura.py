#!/usr/bin/env python3
"""Resume una captura de trafico (.flow de mitmproxy o .har) en texto legible.

Uso:
    python3 tools/analizar-captura.py capturas/trafico.flow
    python3 tools/analizar-captura.py capturas/trafico.har

Objetivo: ver el contrato real de un cliente que funciona (host, cabeceras,
cuerpo y codigos de respuesta) sin tener que abrir la captura a mano.
"""
import json
import sys
from pathlib import Path

INTERESANTES = (
    "login", "active", "config/get", "getHome", "getLiveData", "getNextColumns",
    "getItemData", "searchByName", "heartbeat", "getColumnContents", "qr/token",
)

CLAVES_CABECERA = (
    "apk", "apkver", "spkgver", "x-request-trace", "x-client-meta",
    "authorization", "portalcode", "user-agent", "content-type", "sn", "token",
)


def recorta(texto: str, limite: int = 1600) -> str:
    texto = (texto or "").strip()
    if len(texto) <= limite:
        return texto
    return texto[:limite] + f"\n... ({len(texto) - limite} caracteres mas)"


def leer_flow(ruta: Path):
    try:
        from mitmproxy import io as mflow  # type: ignore
    except Exception:
        # Si mitmproxy esta en un entorno virtual cercano, reintenta con el.
        import os

        aqui = Path(__file__).resolve().parent
        if os.environ.get("MARTINVIDAL_REEXEC") != "1":
            for candidato in (
                aqui.parent / ".tools-venv" / "bin" / "python",
                aqui.parent.parent / ".tools-venv" / "bin" / "python",
            ):
                if candidato.exists():
                    os.environ["MARTINVIDAL_REEXEC"] = "1"
                    os.execv(
                        str(candidato),
                        [str(candidato), str(Path(__file__).resolve()), *sys.argv[1:]],
                    )
        sys.exit(
            "Falta la libreria de mitmproxy. Instalala con:\n"
            "  .tools-venv/bin/pip install mitmproxy\n"
            "o exporta la captura a HAR y vuelve a ejecutar."
        )
    with ruta.open("rb") as fh:
        for flujo in mflow.FlowReader(fh).stream():
            req, res = flujo.request, flujo.response
            cabeceras = {k: v for k, v in req.headers.items()}
            respuesta = None
            if res is not None:
                respuesta = {
                    "status": res.status_code,
                    "headers": dict(res.headers),
                    "body": res.get_text(strict=False) or "",
                }
            yield {
                "method": req.method,
                "url": req.pretty_url,
                "headers": cabeceras,
                "body": req.get_text(strict=False) or "",
                "response": respuesta,
            }


def leer_har(ruta: Path):
    datos = json.loads(ruta.read_text(encoding="utf-8", errors="replace"))
    for entrada in datos.get("log", {}).get("entries", []):
        req = entrada.get("request", {})
        res = entrada.get("response", {})
        cabeceras = {
            c.get("name", ""): c.get("value", "") for c in req.get("headers", [])
        }
        yield {
            "method": req.get("method", "?"),
            "url": req.get("url", "?"),
            "headers": cabeceras,
            "body": (req.get("postData", {}) or {}).get("text", ""),
            "response": {
                "status": res.get("status"),
                "headers": {
                    c.get("name", ""): c.get("value", "")
                    for c in res.get("headers", [])
                },
                "body": (res.get("content", {}) or {}).get("text", ""),
            },
        }


def codigo_portal(cuerpo: str) -> str:
    try:
        datos = json.loads(cuerpo)
    except Exception:
        return ""
    if not isinstance(datos, dict):
        return ""
    codigo = datos.get("returnCode")
    if codigo is None:
        return ""
    mensaje = datos.get("errorMessage") or ""
    return f"returnCode={codigo} {mensaje}".strip()


def main() -> None:
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    ruta = Path(sys.argv[1])
    if not ruta.exists():
        sys.exit(f"No existe {ruta}")

    if ruta.suffix.lower() in (".har", ".json"):
        peticiones = leer_har(ruta)
    else:
        peticiones = leer_flow(ruta)

    total = 0
    for i, p in enumerate(peticiones, 1):
        total = i
        url = p["url"]
        destacado = any(clave.lower() in url.lower() for clave in INTERESANTES)
        print("=" * 78)
        print(f"[{i}] {p['method']} {url}" + ("   <-- CLAVE" if destacado else ""))

        cabeceras = {
            k: v for k, v in (p["headers"] or {}).items() if k.lower() in CLAVES_CABECERA
        }
        for k, v in cabeceras.items():
            print(f"  {k}: {v}")

        if p["body"]:
            print("  --- cuerpo ---")
            print(recorta(p["body"], 2400))

        res = p["response"]
        if not res:
            print("  (sin respuesta)")
            continue
        print(f"  <-- {res['status']}")
        codigo = codigo_portal(res["body"])
        if codigo:
            print(f"  portal: {codigo}")
        if not codigo and res["body"]:
            print("  --- respuesta ---")
            print(recorta(res["body"], 1200))

    print("=" * 78)
    print(f"Total: {total} peticiones")

    # Resumen de hosts y endpoints vistos
    hosts, rutas = {}, {}
    peticiones2 = leer_har(ruta) if ruta.suffix.lower() in (".har", ".json") else leer_flow(ruta)
    for p in peticiones2:
        from urllib.parse import urlsplit

        partes = urlsplit(p["url"])
        hosts[partes.netloc] = hosts.get(partes.netloc, 0) + 1
        rutas[partes.path] = rutas.get(partes.path, 0) + 1
    print("\nHosts:")
    for host, veces in sorted(hosts.items(), key=lambda x: -x[1]):
        print(f"  {host}  ({veces})")
    print("\nRutas portalCore:")
    for ruta, veces in sorted(rutas.items()):
        if "portalcore" in ruta.lower():
            print(f"  {ruta}  ({veces})")


if __name__ == "__main__":
    main()

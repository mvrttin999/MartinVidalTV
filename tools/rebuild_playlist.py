#!/usr/bin/env python3
"""Repara nombres y agrupa el catálogo Xuper en estanterías por categoría.

Lee `app/src/main/assets/lista.m3u`, conserva las URLs y los logos, y reescribe
el archivo con `group-title` = categoría. La app ya convierte cada
`group-title` en una estantería, así que no hace falta tocar el código para
separar los canales en "En directo".

Uso: python3 tools/rebuild_playlist.py [--dry-run]
"""
import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
M3U = ROOT / "app" / "src" / "main" / "assets" / "lista.m3u"

# Títulos que sí se recuperaron del heap o del código de media.
NAME_FIX = {
    "cyx-AZTECA7HD": "Azteca 7 HD",
    "cyx-A3SSERIESHD": "A3S Series HD",
    "cyx-AmericaTVHDPeru": "América TV Perú HD",
    "cyx-Bandamax": "Bandamax",
    "cyx_Cubavision": "Cubavisión",
    "cyx-CombateHD": "Combate HD",
    "cyx-FOXPFAMILYHD": "FOX Family HD",
    "cyx-UNIVERSALCINEMAHD": "Universal Cinema HD",
    "cyx-FoxHD": "FOX HD",
    "cyx-FoxHD_Central": "FOX HD Central",
    "cyx_4658831902452250509490092994": "24/7 Spiderman (1997)",
    "cyx_1744402969855577164092961309": "24/7 Hulk (1996)",
    "cyx_P0QveE7vemdJOL7iaM7YffY3": "Universal Comedy HD",
    "cyx_7mZDPtFaPHyxw9kNLi3ZQGel": "Universal Crime HD",
}

# Orden de las estanterías en la portada.
CATEGORIES = [
    "Deportes",
    "Películas",
    "Series",
    "Infantil",
    "Documentales",
    "Noticias",
    "Música",
    "Entretenimiento",
    "Canales locales",
    "Otros canales",
]

# Reglas en orden de prioridad. La primera que coincide gana.
RULES = [
    ("Infantil", r"KIDS|BABY|DISNEY|CARTOON|NICKELODEON|TEENNICK|TOONCAST|BOING|BOB ESPONJA|POKEMON|CINEMA VIP INFANTIL|CHU CHU TV|ULTRA KIDS|PATRULLA CANINA|DIEGO GO|RUGRATS|GARGOLAS|DINOSAURIOS|SPIDERMAN|HULK|AVENGERS|TIMON|PINGUINOS|POPEYE|PAJARO LOC|GASPARIN|FELIX EL GATO|DON GATO|YOUNG JUSTICE|TROLL HUNTERS|REGULAR SHOW|RICK AND MORTY|FUTURAMA|DRAGON BALL|THE SIMPSONS"),
    ("Deportes", r"SPORT|DEPORTE|FUTBOL|FÚTBOL|\bNBA\b|\bNFL\b|\bMLB\b|\bMLS\b|\bNHL\b|\bUFC\b|DAZN|ESPN|TUDN|GOLF|\bGOL\b|BOX NATION|COMBATE|BALLY|LIGA|WIN SPORTS|CLARO SPORTS|MOVISTAR DEPORTES|TV AZTECA DEPORTES|SPORTSMAN|PX SPORTS|\bF1\b|FORMULA 1|MOTOR TREND|HIPODROMO|MUNDO TORO|\bTOROS\b|\bNTN\b|MERIDIANO|NESN|MASN|M\+ VAMOS|NBA PASS|NFL PASS|MULTIPREMIER|MEGA ESP|MEXICAN PACIFIC LEAGUE|D GAMES"),
    ("Películas", r"CINEMA|CINE|MOVIE|EPIX|SHOWTIME|STARZ|HALLMARK MOVIES|CINEMAX|GOLDEN|ESTRENOS|PARAMOUNT\+|PARAMOUNT|PARAMOUND|PLATINO|MOVIEPLEX|DE PELICULA|\bFXM\b|FOX ONE|CINEVISION|HBO|\bTCM\b|\bCINEPOP\b|NETFLIX|AMAZON PRIME|HOLLYWOOD|MOVISTAR (ACCION|CLASICOS)|FX CENTRAL|\bFXI\b"),
    ("Series", r"SERIES|AMC|AXN|TNT|UNIVERSAL CHANNEL|UNIVERSAL CRIME|UNIVERSAL COMEDY|UNIVERSAL REALITY|UNIVERSAL PREMIER|STUDIO UNIVERSAL|COMEDY CENTRAL|\bBET\b|TRU TV|\bTBS\b|HALLMARK DRAMA|TELEHIT|FOX HD|24/7|CASO CERRADO|\bFOX\b|\bSTAR\b|SUNDANCE|ADULT SWIM|TOP CRIME|BLAZE HD|BEMAD HD|PANICO HD|MOVISTAR CLASICOS"),
    ("Documentales", r"DISCOVERY|NAT GEO|NATIONAL GEOGRAPHIC|HISTORY|\bTLC\b|ANIMAL PLANET|CAZA & PESCA|DOCU|TV CULTURAS|ID HD|CURSO|\bVICE\b|WEATHER CHANNEL|ODISEA HD|\bDHE\b|PERU MAGICO"),
    ("Noticias", r"NOTICIAS|NEWS|\bCNN\b|FOX NEWS|CBS NEWS|ABC NEWS|CNBC|TELESUR|EURO NEWS|AL JAZEERA|CANAL 24 HORAS|CABLE NOTICIAS|CAPITAL FHD|\bTN \d|\bCGTN\b|TELEUNIVERSO|\bNBC\b"),
    ("Música", r"\bMTV\b|VH1|ROCK AND POP|THROWBACK JAMZ|\bMUSIC\b|CANCAO|HOSSANA|ELOHIM|VIDEOROLA"),
    ("Entretenimiento", r"TELEMUNDO|UNIVISION|COMEDIA|HUMOR|CHESPIRITO|EL CHAVO|SHARK TANK|CASA DE LOS FAMOSOS|GAME SHOW|REALITY|CRIMEN|MASTER CHEF|THE GRAND TOUR|THE MUPPETS|CASADO CON HIJOS|QUE MADRE|LA HORA PICO|SE RENTA|COMEDY|\bA&E\b|ANTENA 3|ANTENA LATINA|\bCUATRO\b|LA SEXTA|LA KALLE|SUN CHANNEL|TROPICAL HD|LAS USA|ABYA YALA|\bGLOBAL\b|ETC TV|EXITOSA TV|EUROCHANNEL|EUROPA COL|ZOOMOO|VALE TV|\bTELEMEDELLIN\b|\bTELEVEN\b|\bTELERAMA\b|CASO CERRADO|LAS ESTRELLAS|LATINA HD|IMAGEN HD|TELEHIT"),
    ("Canales locales", r"^CANAL \d|^CANAL\b|^TELE|^TV |^CHV\b|^RCN\b|^MEGA\b|^GAMAVISION|^VTV\b|^UNITEL|^BOLIVISION|^SNT\b|^REPRETEL|^TELETICA|^TELEUNO|^TELEANTIOQUIA|^TELEMICRO|^TELEDOCE|^TELEFUTURO|^TELECINCO|^TELEISLAS|^TELEUNIVERSO|^TELEHIT|^CDO\b|^MICROVISION|^OROMAR|^SENAL COLOMBIA|^CARACOL|^PANAMERICANA|^AUSTRIAL|^ATTRACTER|^RED UNO|^RTS\b|^UCV\b|^TVE\b|^CANAL ONCE|^CANAL CLARO|^CANAL TRO|^CANAL ANTIGUA|^CANAL UNO|^CANAL DEL SUR|^SUR TV|^TRECE PY|^PARAGUAY TV|^NEOX\b|^GIGAVISION|^GAMA ?TV|^BOLIVIA TV|^ATB\b|^CITY TV|^CALLE 13|^CDN RD|^CHILEVISION|^HCH\b|^METRO HD|^MAS TV|^MULTIMEDIOS|^VENEVISION|^UNIMAS|^UNIVERSO|^SERTV|^ECO TV|^LA RED|^LA 1|^LA 2|^ABC 10|^TELEANTIOQUIA|^TELEVISORA|^GLOBOVISION|^CANELA TV|^ALIENTO VISION|^INTI HD|^TELEUNIVERSO"),
    ("Otros canales", r".*"),
]


def is_garbage(name: str, code: str) -> bool:
    s = name.strip()
    if s in NAME_FIX.values():
        return False
    if re.match(r"^cyx[_-]", s, re.I):
        return True
    if s in {"free", "90x60", "n o", "J o", "media", "Canal"}:
        return True
    if len(s) <= 5 and re.search(r"[^A-Za-z0-9 +&.\-]", s):
        return True
    if re.fullmatch(r".{0,2}[oOhpx][oOhpx]?", s):
        return True
    return False


def clean_name(name: str, code: str) -> str:
    fixed = NAME_FIX.get(code)
    if fixed:
        return fixed
    if not is_garbage(name, code):
        return name.strip()
    suffix = re.sub(r"[^A-Za-z0-9]", "", code)[-6:].upper() or "000000"
    return f"Canal Xuper {suffix}"


def category(name: str) -> str:
    n = name.upper()
    # Los nombres generados para entradas sin título legible van al final.
    if n.startswith("CANAL XUPER "):
        return "Otros canales"
    for cat, pattern in RULES:
        if re.search(pattern, n):
            return cat
    return "Otros canales"


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    lines = [l.strip() for l in M3U.read_text(encoding="utf-8").splitlines() if l.strip()]
    header = [l for l in lines if l.startswith("#") and not l.startswith("#EXTINF")]
    entries = []
    for i, line in enumerate(lines):
        if not line.startswith("#EXTINF") or i + 1 >= len(lines):
            continue
        url = lines[i + 1]
        m = re.search(r"/live/([^.?]+)\.m3u8", url)
        if not m:
            continue
        code = m.group(1)
        nm = re.search(r'tvg-name="([^"]*)"', line)
        lg = re.search(r'tvg-logo="([^"]*)"', line)
        name = clean_name(nm.group(1) if nm else "", code)
        entries.append({"code": code, "name": name, "logo": lg.group(1) if lg else None,
                        "url": url, "cat": category(name)})

    entries.sort(key=lambda e: (CATEGORIES.index(e["cat"]), e["name"].upper()))

    out = list(header)
    for e in entries:
        attrs = f'-1 tvg-name="{e["name"]}" group-title="{e["cat"]}"'
        if e["logo"]:
            attrs += f' tvg-logo="{e["logo"]}"'
        out.append(f"#EXTINF:{attrs},{e['name']}")
        out.append(e["url"])

    if args.dry_run:
        from collections import Counter
        for cat, n in Counter(e["cat"] for e in entries).items():
            print(f"{cat:<20} {n}")
        print(f"TOTAL {len(entries)}")
        print("\nMuestra por categoría:")
        for cat in CATEGORIES:
            sample = [e["name"] for e in entries if e["cat"] == cat][:8]
            print(f"  {cat}: {sample}")
        return 0

    M3U.write_text("\n".join(out) + "\n", encoding="utf-8")
    print(f"Escrito {M3U} con {len(entries)} canales en {len(set(e['cat'] for e in entries))} categorías")
    return 0


if __name__ == "__main__":
    sys.exit(main())

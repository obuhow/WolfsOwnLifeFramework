#!/usr/bin/env python3
"""Fetch + strip HTML to text. Usage: _fetch.py <url> [outfile] [maxchars]"""
import sys, re, html, subprocess, pathlib

UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

def fetch(url):
    p = subprocess.run(
        ["curl", "-sL", "--compressed", "--max-time", "40",
         "-A", UA,
         "-H", "Accept: text/html,application/xhtml+xml,*/*;q=0.8",
         "-H", "Accept-Language: ru-RU,ru;q=0.9,en;q=0.8",
         url],
        capture_output=True)
    raw = p.stdout
    for enc in ("utf-8", "cp1251", "koi8-r"):
        try:
            return raw.decode(enc)
        except UnicodeDecodeError:
            continue
    return raw.decode("utf-8", "replace")

def clean(t):
    t = re.sub(r"(?s)<(script|style|noscript|svg)\b.*?</\1>", " ", t, flags=re.I)
    t = re.sub(r"(?s)<!--.*?-->", " ", t)
    t = re.sub(r"(?s)<br\s*/?>", "\n", t, flags=re.I)
    t = re.sub(r"(?s)</(p|div|li|tr|h\d)>", "\n", t, flags=re.I)
    t = re.sub(r"(?s)<[^>]+>", " ", t)
    t = html.unescape(t)
    t = t.replace("\xa0", " ")
    t = re.sub(r"[ \t]+", " ", t)
    t = re.sub(r"\n[ \t]*", "\n", t)
    t = re.sub(r"\n{3,}", "\n\n", t)
    return t.strip()

if __name__ == "__main__":
    url = sys.argv[1]
    out = sys.argv[2] if len(sys.argv) > 2 else None
    mx = int(sys.argv[3]) if len(sys.argv) > 3 else 9000
    txt = clean(fetch(url))
    if out:
        pathlib.Path(out).write_text(txt, encoding="utf-8")
        print(f"[saved {out} :: {len(txt)} chars]")
    print(txt[:mx])

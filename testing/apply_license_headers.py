#!/usr/bin/env python3
"""Идемпотентная простановка AGPL-3.0 заголовка в исходники WOLF (тикет 0.6-06).

Область (см. тикет):
  - api/src/**/*.java
  - web/src/**/*.vue, web/src/**/*.{js,ts}
Исключено: build/, dist/, node_modules/, .gradle/, .worktrees/, генерируемое.

Идемпотентность: файл пропускается, если в первых MARKER_LINES строках уже
встречается маркер ("GNU Affero" / "Wolf's Own Life Framework").

Запуск:
    python3 testing/apply_license_headers.py            # проставить
    python3 testing/apply_license_headers.py --check    # только отчёт, 1 если есть пропуски
"""
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
MARKER_LINES = 15
MARKERS = ("GNU Affero", "Wolf's Own Life Framework")

HEADER_LINES = [
    "WOLF — Wolf's Own Life Framework",
    "Copyright (C) 2025 Pavel Obukhov",
    "",
    "This program is free software: you can redistribute it and/or modify",
    "it under the terms of the GNU Affero General Public License as published by",
    "the Free Software Foundation, either version 3 of the License, or",
    "(at your option) any later version.",
    "",
    "This program is distributed in the hope that it will be useful,",
    "but WITHOUT ANY WARRANTY; without even the implied warranty of",
    "MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the",
    "GNU Affero General Public License for more details.",
    "",
    "You should have received a copy of the GNU Affero General Public License",
    "along with this program. If not, see <https://www.gnu.org/licenses/>.",
]

EXCLUDE_PARTS = {
    "build", "dist", "node_modules", ".gradle", ".worktrees",
    "__pycache__", ".venv", "gradle",
}


def block_comment() -> str:
    body = "\n".join(f" * {line}".rstrip() for line in HEADER_LINES)
    return f"/*\n{body}\n */\n"


def html_comment() -> str:
    body = "\n".join(f"  {line}".rstrip() for line in HEADER_LINES)
    return f"<!--\n{body}\n-->\n"


def targets():
    for p in sorted((REPO / "api" / "src").rglob("*.java")):
        if not (set(p.relative_to(REPO).parts) & EXCLUDE_PARTS):
            yield p
    web = REPO / "web" / "src"
    for ext in ("*.vue", "*.js", "*.ts"):
        for p in sorted(web.rglob(ext)):
            if not (set(p.relative_to(REPO).parts) & EXCLUDE_PARTS):
                yield p


def has_marker(text: str) -> bool:
    head = "\n".join(text.splitlines()[:MARKER_LINES])
    return any(m in head for m in MARKERS)


def apply(path: Path) -> bool:
    text = path.read_text(encoding="utf-8")
    if has_marker(text):
        return False
    header = html_comment() if path.suffix == ".vue" else block_comment()
    path.write_text(header + text, encoding="utf-8")
    return True


def main() -> int:
    check = "--check" in sys.argv
    files = list(targets())
    missing, changed = [], []
    for p in files:
        if check:
            if not has_marker(p.read_text(encoding="utf-8")):
                missing.append(p)
        elif apply(p):
            changed.append(p)

    rel = lambda p: str(p.relative_to(REPO))
    if check:
        print(f"проверено: {len(files)}, без заголовка: {len(missing)}")
        for p in missing[:20]:
            print("  MISSING", rel(p))
        return 1 if missing else 0

    print(f"проверено: {len(files)}, изменено: {len(changed)}")
    for p in changed[:10]:
        print("  +", rel(p))
    if len(changed) > 10:
        print(f"  ... ещё {len(changed) - 10}")
    return 0


if __name__ == "__main__":
    sys.exit(main())

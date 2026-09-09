#!/usr/bin/env python3
"""
SPIKE (одноразовый, НЕ боевой код): детерминированный разбор недельной сетки
Расписание.xlsx — проверка гипотез из ANALYSIS.md перед починкой XlsxImportService.

Читает .xlsx через stdlib (zipfile + xml.etree) — БЕЗ openpyxl/pip.
Ничего не пишет в WOLF, только читает файл.

Проверяет:
1. Серийники дат (строка 1, Excel 1900) -> настоящие даты недели.
2. Время из колонки C (дробь Excel) -> HH:MM.
3. Активности в колонках D..J, ячейка = факт.
4. Игнорирование блока «Факт»/агрегатов и пустых строк.
5. Сбор уникальных активностей (для будущего маппинга) + счётчик Записей.

Запуск:  python3 xlsx_spike_parse.py [путь-к-xlsx]
Результат печатается и пишется в xlsx_spike_result.txt.
"""
import sys, os, re, zipfile
import xml.etree.ElementTree as ET
import datetime, collections, json

NS = {"m": "http://schemas.openxmlformats.org/spreadsheetml/2006/main",
      "r": "http://schemas.openxmlformats.org/officeDocument/2006/relationships"}

def col_to_idx(ref):
    """A->0, D->3, AA->26..."""
    letters = re.match(r"[A-Z]+", ref).group(0)
    n = 0
    for ch in letters:
        n = n * 26 + (ord(ch) - ord("A") + 1)
    return n - 1

def row_idx(ref):
    return int(re.search(r"\d+", ref).group(0)) - 1

def excel_serial_to_date(serial):
    """Excel 1900 date system -> date. Серийник 1 = 1900-01-01; баг високосного 1900 (серийник 60)."""
    if serial >= 60:
        serial -= 1  # компенсация несуществующего 1900-02-29
    return datetime.date(1899, 12, 30) + datetime.timedelta(days=serial)

def excel_time_fraction_to_time(frac):
    """0.2916666 (доля суток) -> time."""
    total = frac * 1440.0
    h = int(total // 60) % 24
    m = int(round(total % 60))
    if m == 60:
        h += 1; m = 0
    return datetime.time(h % 24, m)

def parse_xlsx(path):
    """Возвращает {sheet_name: {row: {col: value}}} с типами int/float/str."""
    sheets = {}
    with zipfile.ZipFile(path) as z:
        # shared strings
        shared = []
        if "xl/sharedStrings.xml" in z.namelist():
            root = ET.fromstring(z.read("xl/sharedStrings.xml"))
            for si in root.findall("m:si", NS):
                text = "".join(t.text or "" for t in si.iter("{http://schemas.openxmlformats.org/spreadsheetml/2006/main}t"))
                shared.append(text)

        # имена листов по порядку из workbook.xml (sheet1..sheetN идут в том же порядке)
        wb_root = ET.fromstring(z.read("xl/workbook.xml"))
        sheet_els = wb_root.find("m:sheets", NS)
        sheet_names = [sh.attrib["name"] for sh in sheet_els.findall("m:sheet", NS)]

        # читаем листы sheet1.xml..sheetN.xml по порядку
        for i, name in enumerate(sheet_names, start=1):
            target = f"xl/worksheets/sheet{i}.xml"
            data = {}
            root = ET.fromstring(z.read(target))
            for c in root.iter("{http://schemas.openxmlformats.org/spreadsheetml/2006/main}c"):
                ref = c.attrib.get("r")
                if not ref:
                    continue
                t = c.attrib.get("t")
                v_el = c.find("m:v", NS)
                raw = v_el.text if v_el is not None else None
                if raw is None:
                    val = None
                elif t == "s":
                    val = shared[int(raw)]
                elif t == "inlineStr":
                    is_el = c.find("m:is/m:t", NS)
                    val = is_el.text if is_el is not None else ""
                elif t == "b":
                    val = raw == "1"
                elif t in (None, "n"):
                    # число (явный t="n" или без типа) — пробуем int/float
                    try:
                        f = float(raw)
                        val = int(f) if f.is_integer() else f
                    except ValueError:
                        val = raw
                else:
                    val = raw
                if val is not None and str(val).strip() != "":
                    r = row_idx(ref)
                    cidx = col_to_idx(ref)
                    data.setdefault(r, {})[cidx] = val
            sheets[name] = data
    return sheets

def main():
    path = sys.argv[1] if len(sys.argv) > 1 else "sample/Расписание.xlsx"
    if not os.path.exists(path):
        sys.exit(f"Файл не найден: {path}")
    out = []
    def emit(s=""):
        print(s); out.append(s)

    sheets = parse_xlsx(path)
    emit(f"=== SPIKE XLSX-расписание | листов: {len(sheets)} ===")
    emit(f"Листы: {list(sheets.keys())}\n")

    total_entries = 0
    act_count = collections.Counter()
    sheet_dates = {}

    for name, data in sheets.items():
        # строка 1 (row 0) = серийники дат в колонках C..I (2..8)? смотрим все
        dates = {}
        for r, cells in data.items():
            for cidx, val in cells.items():
                if isinstance(val, (int, float)) and val > 40000:  # похоже на дату-серийник
                    if 0 <= r <= 2:
                        dates[cidx] = excel_serial_to_date(val)
        sheet_dates[name] = dates
        # время: колонка C (idx 2) — дробь
        # активности: колонки D..J (idx 3..9) в строках, где C — время
        week_start = min(dates.values()) if dates else None
        n = 0
        for r, cells in sorted(data.items()):
            cval = cells.get(2)
            if not isinstance(cval, (int, float)) or not (0 <= cval < 1):
                continue  # не строка-слот
            tm = excel_time_fraction_to_time(cval)
            for d, cidx in enumerate(range(3, 10)):
                txt = cells.get(cidx)
                if isinstance(txt, str) and txt.strip():
                    act_count[txt.strip()] += 1
                    n += 1
        total_entries += n
        emit(f"Лист «{name}»: даты(серийник->дата)={ {i: str(d) for i,d in sorted(dates.items())} }")

    emit(f"\n=== ИТОГО Записей-активностей (ячеек): {total_entries} ===")
    emit(f"Уникальных активностей: {len(act_count)}\n")
    emit("Топ активностей:")
    for a, c in act_count.most_common(50):
        emit(f"  {c:6d}  {a}")

    with open("xlsx_spike_result.txt", "w", encoding="utf-8") as f:
        f.write("\n".join(out))
    emit(f"\n[сохранено в xlsx_spike_result.txt]")

if __name__ == "__main__":
    main()

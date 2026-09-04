#!/usr/bin/env python3
"""
SPIKE (одноразовый эксперимент, НЕ боевой код) для направления agent-chat-planner.

Вопрос, который проверяем: если собрать реальный контекст WOLF (проекты, цели, рутины,
посчитанная динамика расписания) в компактный текст и отдать LLM с вопросом пользователя —
получится ли осмысленный ответ агента-планировщика?

Ничего не хардкодит. Всё чувствительное — из окружения:
  WOLF_BASE   — база API (по умолчанию http://localhost/api/v1)
  WOLF_USER   — логин WOLF
  WOLF_PASS   — пароль WOLF
  SPIKE_LLM_API_KEY — ключ Google Gemini (можно и GEMINI_API_KEY)
  SPIKE_LLM_URL     — база Gemini API (по умолчанию https://generativelanguage.googleapis.com)
  SPIKE_LLM_MODEL   — модель (по умолчанию gemini-2.5-flash)
  SPIKE_QUESTION    — вопрос агенту (есть дефолт)

Результат печатается и сохраняется в spike_output.txt рядом со скриптом.
Читает данные ТОЛЬКО на чтение (GET). Ничего в WOLF не меняет.
"""
import os, sys, json, urllib.request, urllib.error, datetime, collections

BASE = os.environ.get("WOLF_BASE", "http://localhost/api/v1").rstrip("/")
USER = os.environ.get("WOLF_USER")
PASS = os.environ.get("WOLF_PASS")
LLM_URL = os.environ.get("SPIKE_LLM_URL", "https://generativelanguage.googleapis.com").rstrip("/")
LLM_KEY = os.environ.get("SPIKE_LLM_API_KEY") or os.environ.get("GEMINI_API_KEY")
LLM_MODEL = os.environ.get("SPIKE_LLM_MODEL", "gemini-3.6-flash")
QUESTION = os.environ.get(
    "SPIKE_QUESTION",
    "Спланируй мне ближайшую неделю с учётом моих целей и текущей нагрузки.",
)

TIMEOUT = 30       # для запросов к WOLF API
LLM_TIMEOUT = 120  # для генерации LLM (план недели — тяжёлый ответ)


def _req(method, url, token=None, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    try:
        with urllib.request.urlopen(req, timeout=TIMEOUT) as r:
            raw = r.read().decode()
            return r.status, (json.loads(raw) if raw else None)
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()[:500]
    except Exception as e:
        return None, str(e)


def get(path, token):
    st, body = _req("GET", f"{BASE}{path}", token=token)
    if st != 200:
        return None, f"GET {path} -> {st}: {str(body)[:200]}"
    return body, None


def login():
    if not (USER and PASS):
        sys.exit("Нет WOLF_USER / WOLF_PASS в окружении — не могу залогиниться.")
    st, body = _req("POST", f"{BASE}/auth/login", body={"username": USER, "password": PASS})
    if st != 200 or not isinstance(body, dict) or "token" not in body:
        sys.exit(f"Логин не удался: {st}: {str(body)[:300]}")
    return body["token"]


def iso_days_ago(days):
    return (datetime.date.today() - datetime.timedelta(days=days)).isoformat()


def build_context(token):
    """Собирает компактный контекст. Динамика считается ЗДЕСЬ, в коде (принцип Р-H)."""
    ctx = {"warnings": []}

    projects, err = get("/projects", token)
    if err: ctx["warnings"].append(err); projects = []
    goals, err = get("/goals", token)
    if err: ctx["warnings"].append(err); goals = []
    routines, err = get("/routines", token)
    if err: ctx["warnings"].append(err); routines = []
    digest, err = get("/morning-digest", token)
    if err: ctx["warnings"].append(err); digest = None

    # Активные проекты — компактно
    ctx["projects"] = [
        {
            "title": p.get("title"),
            "status": p.get("status"),
            "planHours": p.get("totalPlanHours"),
            "start": p.get("startDate"),
            "end": p.get("endDate"),
        }
        for p in (projects or [])
        if p.get("status") != "ARCHIVED"
    ][:40]

    ctx["goals"] = [
        {"title": g.get("title"), "metric": g.get("metricTarget") or g.get("target")}
        for g in (goals or [])
    ][:40]
    if not ctx["goals"]:
        ctx["goalsNote"] = "У пользователя не заведено ни одной Цели (эндпоинт /goals вернул пусто)."

    ctx["routines"] = [
        {"title": r.get("title"), "weeklyHours": r.get("weeklyHours")}
        for r in (routines or [])
    ][:40]

    # ДИНАМИКА расписания: тянем факт за ~полгода и агрегируем по месяцам В КОДЕ (Р-H).
    # Эндпоинт /time-entries: (1) ждёт LocalDateTime (YYYY-MM-DDTHH:MM), не голую дату;
    # (2) диапазон не больше 14 дней за запрос — ходим порциями по 14 дней.
    dyn = {"periodDays": 182, "totalEntries": 0, "byMonth": {}, "note": ""}
    per_month = collections.Counter()
    all_entries = []
    start_date = datetime.date.today() - datetime.timedelta(days=181)
    today = datetime.date.today()
    day = start_date
    while day <= today:
        chunk_end = min(day + datetime.timedelta(days=13), today)
        frm = day.isoformat() + "T00:00"
        to = chunk_end.isoformat() + "T23:59"
        chunk, err = get(f"/time-entries?from={frm}&to={to}", token)
        if err:
            ctx["warnings"].append(f"{frm}..{to}: {err}")
        elif isinstance(chunk, list):
            all_entries.extend(chunk)
        day = chunk_end + datetime.timedelta(days=1)

    dyn["totalEntries"] = len(all_entries)
    for e in all_entries:
        month = (e.get("startAt") or "")[:7]  # YYYY-MM
        if month:
            per_month[month] += 1
    dyn["byMonth"] = dict(sorted(per_month.items()))
    if not all_entries:
        dyn["note"] = "За полгода нет ни одной Записи времени (или все порции упали — см. warnings)."
    else:
        dyn["note"] = ("Число Записей времени по месяцам за ~полгода "
                       "(факт+план; посчитано кодом, порциями по 14 дней).")
    ctx["scheduleDynamics"] = dyn

    if digest:
        ctx["todayDigestPresent"] = True
    return ctx


SYSTEM_PROMPT = (
    "Ты — управляющий агент-планировщик в системе WOLF (личная оцифровка жизни). "
    "Твоя роль — сопровождать планирование пользователя. Опирайся ТОЛЬКО на переданный "
    "контекст (проекты, цели, рутины, посчитанная динамика расписания). Числа в контексте "
    "точные — НЕ выдумывай статистику, которой нет. Термины WOLF: Проект (цель/инициатива), "
    "Дело (тип активности), Запись времени (15-мин ячейка плана/факта), Рутина (регулярный "
    "процесс с недельной квотой), Область жизни. Отвечай по-русски, конкретно и кратко. "
    "Если данных не хватает — прямо скажи, чего не хватает, и не додумывай."
)


def ask_llm(context):
    if not LLM_KEY:
        return None, "Нет SPIKE_LLM_API_KEY / GEMINI_API_KEY — пропускаю вызов LLM (собрал только контекст)."
    user_msg = (
        "КОНТЕКСТ ПОЛЬЗОВАТЕЛЯ (JSON):\n"
        + json.dumps(context, ensure_ascii=False, indent=2)
        + f"\n\nВОПРОС ПОЛЬЗОВАТЕЛЯ:\n{QUESTION}"
    )
    # Нативный Gemini REST: system_instruction + contents, ключ в заголовке x-goog-api-key,
    # ответ в candidates[0].content.parts[*].text
    body = {
        "system_instruction": {"parts": [{"text": SYSTEM_PROMPT}]},
        "contents": [{"role": "user", "parts": [{"text": user_msg}]}],
    }
    url = f"{LLM_URL}/v1beta/models/{LLM_MODEL}:generateContent"
    data = json.dumps(body).encode()
    req = urllib.request.Request(url, data=data, method="POST")
    req.add_header("Content-Type", "application/json")
    req.add_header("x-goog-api-key", LLM_KEY)
    try:
        with urllib.request.urlopen(req, timeout=LLM_TIMEOUT) as r:
            resp = json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        return None, f"Gemini {e.code}: {e.read().decode()[:400]}"
    except Exception as e:
        return None, f"Gemini ошибка сети: {e}"
    try:
        parts = resp["candidates"][0]["content"]["parts"]
        return "".join(p.get("text", "") for p in parts), None
    except Exception:
        # частый случай: ответ заблокирован фильтром / нет candidates
        return None, f"Неожиданный ответ Gemini: {json.dumps(resp, ensure_ascii=False)[:400]}"


def main():
    out = []
    def emit(s=""):
        print(s); out.append(s)

    emit(f"=== SPIKE agent-chat-planner | {datetime.datetime.now():%Y-%m-%d %H:%M} ===")
    emit(f"WOLF: {BASE}   Gemini: {LLM_URL}  model={LLM_MODEL}")
    token = login()
    emit(f"Логин ок как {USER!r}, токен получен (len={len(token)}).")

    context = build_context(token)
    emit("\n--- СОБРАННЫЙ КОНТЕКСТ (то, что уйдёт в модель) ---")
    emit(json.dumps(context, ensure_ascii=False, indent=2))
    if context["warnings"]:
        emit("\n[!] Предупреждения при сборке:")
        for w in context["warnings"]:
            emit("    - " + w)

    emit(f"\n--- ВОПРОС АГЕНТУ ---\n{QUESTION}")
    answer, err = ask_llm(context)
    emit("\n--- ОТВЕТ АГЕНТА ---")
    emit(answer if answer else f"[LLM не вызван/ошибка] {err}")

    path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "spike_output.txt")
    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(out))
    emit(f"\n[сохранено в {path}]")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Harnais d'évaluation 100 % LOCAL pour AI Software Factory.

- 12 tâches synthétiques (docs/eval/tasks.json)
- deux modèles locaux : ollama-local (7B) et ollama-local-3b (3B)
- baseline manuelle remplie par l'humain (docs/eval/baseline_manual.json)
- aucun appel à un service externe, aucune donnée réelle

Usage :
  python scripts/eval_harness.py list
  python scripts/eval_harness.py dry-run
  python scripts/eval_harness.py run --task-id E01 --model ollama-local --i-understand-this-pushes-branches
  python scripts/eval_harness.py report
"""

from __future__ import annotations

import argparse
import json
import os
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TASKS_FILE = ROOT / "docs" / "eval" / "tasks.json"
BASELINE_FILE = ROOT / "docs" / "eval" / "baseline_manual.json"
RESULTS_DIR = ROOT / "docs" / "eval" / "results"
REPORT_FILE = ROOT / "docs" / "eval" / "RAPPORT.md"

API = os.getenv("PFE_API_URL", "http://127.0.0.1:8081")
ALLOWED_MODELS = ("ollama-local", "ollama-local-3b")
ALLOWED_APPS = ("fake-exposer", "demo-hr-app")
TERMINAL_STATES = {"AWAITING_REVIEW", "FAILED", "APPROVED", "REJECTED", "TIMEOUT"}


def api(path: str, method: str = "GET", payload=None, token: str | None = None,
        timeout: int = 30) -> tuple[int, dict]:
    data = None if payload is None else json.dumps(payload).encode("utf-8")
    headers = {"Accept": "application/json"}
    if data is not None:
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = "Bearer " + token
    request = urllib.request.Request(API + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            raw = response.read().decode("utf-8")
            return response.status, (json.loads(raw) if raw else {})
    except urllib.error.HTTPError as error:
        raw = error.read().decode("utf-8", "replace")
        try:
            body = json.loads(raw)
        except Exception:
            body = {"message": raw}
        return error.code, body


def load_tasks() -> list[dict]:
    data = json.loads(TASKS_FILE.read_text(encoding="utf-8-sig"))
    if not isinstance(data, list) or not data:
        raise ValueError("tasks.json doit être une liste non vide")
    seen: set[str] = set()
    for task in data:
        for field in ("id", "title", "targetApp", "targetRepo", "businessText"):
            if not task.get(field):
                raise ValueError(f"tâche sans {field}: {task}")
        if task["id"] in seen:
            raise ValueError("identifiant de tâche dupliqué: " + task["id"])
        seen.add(task["id"])
        if task["targetApp"] not in ALLOWED_APPS:
            raise ValueError("application cible inconnue: " + task["targetApp"])
    return data


def load_baseline() -> dict:
    return json.loads(BASELINE_FILE.read_text(encoding="utf-8-sig")).get("tasks", {})


def get_token() -> str:
    status, body = api("/api/v1/auth/dev-token", "POST", {"subject": "requester"})
    if status != 200 or not body.get("token"):
        raise RuntimeError(f"impossible d'obtenir un jeton local: {status} {body}")
    return str(body["token"])


def cmd_list(_args) -> None:
    for task in load_tasks():
        print(f"{task['id']:>3}  {task['targetApp']:<14} {task['title']}")


def cmd_dry_run(_args) -> None:
    tasks = load_tasks()
    baseline = load_baseline()
    print(f"Tâches valides : {len(tasks)}")
    print("Modèles prévus : " + ", ".join(ALLOWED_MODELS))
    print("Runs prévus (si tout est lancé) : " + str(len(tasks) * len(ALLOWED_MODELS)))
    print(f"Baseline manuelle renseignée : "
          f"{sum(1 for b in baseline.values() if b.get('minutes') is not None)}/{len(tasks)}")
    print("Aucun appel réseau effectué, aucune branche poussée (mode dry-run).")
    print("Le modèle 3B est inactif tant que tu n'as pas fait : ollama pull qwen2.5-coder:3b")


def cmd_run(args) -> None:
    if not args.i_understand_this_pushes_branches:
        raise SystemExit("REFUS : lance avec --i-understand-this-pushes-branches "
                         "après avoir compris qu'une branche sera poussée sur GitHub.")
    if args.model not in ALLOWED_MODELS:
        raise SystemExit("modèle inconnu. Choisis : " + ", ".join(ALLOWED_MODELS))
    task = next((t for t in load_tasks() if t["id"] == args.task_id), None)
    if task is None:
        raise SystemExit("tâche inconnue: " + args.task_id)

    token = get_token()
    created = api("/api/v1/change-requests", "POST", {
        "title": task["title"],
        "businessText": task["businessText"],
        "targetApp": task["targetApp"],
        "targetRepo": task["targetRepo"],
        "locale": task["locale"],
        "requesterId": "11111111-1111-1111-1111-111111111111",
        "modelKey": args.model,
    }, token)
    if created[0] != 201:
        raise RuntimeError(f"création impossible: {created[0]} {created[1]}")
    run_id = str(created[1]["runId"])

    started = api(f"/api/v1/runs/{run_id}/execute", "POST", token=token)
    if started[0] not in (200, 202):
        raise RuntimeError(f"lancement impossible: {started[0]} {started[1]}")

    clock = time.monotonic()
    state = "RUNNING"
    while time.monotonic() - clock < args.timeout_seconds:
        status, body = api(f"/api/v1/runs/{run_id}/execution", token=token)
        if status != 200:
            raise RuntimeError(f"suivi impossible: {status} {body}")
        state = str(body.get("status", "RUNNING"))
        if state in TERMINAL_STATES - {"TIMEOUT"}:
            break
        time.sleep(args.poll_seconds)
    else:
        state = "TIMEOUT"

    _, events = api(f"/api/v1/runs/{run_id}/events", token=token)
    code_failures = sum(1 for e in events
                        if e.get("stage") == "CODE" and e.get("status") == "FAILED")
    record = {
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "taskId": task["id"],
        "title": task["title"],
        "targetApp": task["targetApp"],
        "modelKey": args.model,
        "runId": run_id,
        "status": state,
        "seconds": round(time.monotonic() - clock, 1),
        "codeFailures": code_failures,
        "eventCount": len(events),
        "error": None,
        "events": [{"stage": e.get("stage"), "status": e.get("status"),
                    "message": e.get("message")} for e in events],
    }
    RESULTS_DIR.mkdir(parents=True, exist_ok=True)
    results_file = args.results or (RESULTS_DIR /
                                    ("runs-" + datetime.now().strftime("%Y%m%d-%H%M%S") + ".jsonl"))
    with Path(results_file).open("a", encoding="utf-8") as handle:
        handle.write(json.dumps(record, ensure_ascii=False) + "\n")
    print(f"{task['id']} / {args.model} → {state} en {record['seconds']}s "
          f"({code_failures} repair(s))")
    print(f"Résultat ajouté à {results_file}")


def _mean(values: list[float]) -> float | None:
    return round(sum(values) / len(values), 1) if values else None


def cmd_report(args) -> None:
    files = sorted(RESULTS_DIR.glob("*.jsonl"))
    if not files:
        raise SystemExit("aucun résultat. Lance d'abord la commande run.")
    records: list[dict] = []
    for file in files:
        for line in file.read_text(encoding="utf-8-sig").splitlines():
            if line.strip():
                records.append(json.loads(line))
    if not records:
        raise SystemExit("fichiers de résultats vides")

    baseline = load_baseline()
    lines = [
        "# Rapport d'évaluation — AI Software Factory",
        "",
        f"Généré le {datetime.now().strftime('%Y-%m-%d %H:%M')} depuis {len(records)} run(s) local(aux).",
        "",
        "**Définition du succès** : statut `AWAITING_REVIEW`, c'est-à-dire branche poussée",
        "et attente de décision humaine. Ce n'est **pas** une validation métier.",
        "",
        "## Résultats par modèle",
        "",
        "| Modèle | Runs | Succès | Taux | Durée moy. | Réparations CODE moy. |",
        "|---|---:|---:|---:|---:|---:|",
    ]
    for model in ALLOWED_MODELS:
        rows = [r for r in records if r.get("modelKey") == model]
        if not rows:
            continue
        wins = sum(1 for r in rows if r.get("status") == "AWAITING_REVIEW")
        lines.append(
            f"| {model} | {len(rows)} | {wins} | {round(100 * wins / len(rows), 1)} % | "
            f"{_mean([r['seconds'] for r in rows])} s | "
            f"{_mean([r['codeFailures'] for r in rows])} |"
        )

    lines += ["", "## Comparaison à la baseline manuelle", "",
              "La baseline est à remplir par toi dans `docs/eval/baseline_manual.json`.",
              "", "| Tâche | Modèle | Usine | Manuel | Écart |", "|---|---|---:|---:|---:|"]
    compared = 0
    for record in records:
        ref = baseline.get(record["taskId"], {})
        minutes = ref.get("minutes")
        if minutes is None:
            continue
        compared += 1
        factory_minutes = round(record["seconds"] / 60, 1)
        lines.append(f"| {record['taskId']} | {record['modelKey']} | {factory_minutes} min | "
                     f"{minutes} min | {round(minutes - factory_minutes, 1)} min |")
    if compared == 0:
        lines.append("| — | — | — | — | Aucune baseline renseignée |")

    lines += [
        "",
        "## Limites",
        "",
        "- Durées mesurées sur une seule machine, avec Ollama local : non comparables à un cloud.",
        "- Les jetons ne sont pas encore remontés par l'API (compteurs à 0).",
        "- Aucune note de qualité automatique : la revue humaine reste la référence.",
        "- Les runs créent des branches sur GitHub : aucune fusion n'est faite par le harnais.",
        "",
    ]
    REPORT_FILE.write_text("\n".join(lines), encoding="utf-8")
    print("Rapport écrit : " + str(REPORT_FILE))


def main() -> None:
    parser = argparse.ArgumentParser(description="Harnais d'évaluation local")
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("list", help="lister les 12 tâches")
    sub.add_parser("dry-run", help="valider sans aucun appel réseau")
    run = sub.add_parser("run", help="exécuter UNE tâche (pousse une branche GitHub)")
    run.add_argument("--task-id", required=True)
    run.add_argument("--model", default="ollama-local", choices=ALLOWED_MODELS)
    run.add_argument("--poll-seconds", type=int, default=5)
    run.add_argument("--timeout-seconds", type=int, default=900)
    run.add_argument("--results", help="fichier .jsonl de sortie (défaut : horodaté)")
    run.add_argument("--i-understand-this-pushes-branches", action="store_true")
    report = sub.add_parser("report", help="agréger les résultats en rapport français")
    report.add_argument("--results", help="(optionnel, non utilisé : tous les .jsonl sont lus)")
    args = parser.parse_args()
    {"list": cmd_list, "dry-run": cmd_dry_run, "run": cmd_run, "report": cmd_report}[args.command](args)


if __name__ == "__main__":
    main()

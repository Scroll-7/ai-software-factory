"""Envoie le corpus synthétique à /documents. Tout est local (localhost uniquement)."""
import json
import urllib.request
from pathlib import Path

BASE = "http://localhost:8000"
CORPUS = Path(__file__).resolve().parents[1] / "corpus" / "synthetic"


def kind_for(path: Path) -> str:
    part = path.relative_to(CORPUS).parts[0]
    return {
        "standards": "STANDARD",
        "adr": "ADR",
        "templates": "TEST_TEMPLATE",
    }.get(part, "README")


def post(path: Path) -> None:
    payload = {
        "sourceUri": "synthetic://" + path.relative_to(CORPUS).as_posix(),
        "title": path.stem.replace("-", " ").capitalize(),
        "kind": kind_for(path),
        "language": "fr",
        "version": "v1",
        "content": path.read_text(encoding="utf-8"),
        "filePath": path.name,
    }
    req = urllib.request.Request(
        BASE + "/documents",
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers={"Content-Type": "application/json; charset=utf-8"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=180) as response:
        print(path.name, "->", response.read().decode("utf-8"))


if __name__ == "__main__":
    for file in sorted(CORPUS.rglob("*.md")):
        post(file)

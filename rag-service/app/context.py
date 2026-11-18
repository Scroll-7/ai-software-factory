"""Construction d'un contexte dépôt BORNÉ et déterministe (pas d'embedding ici)."""
from pathlib import Path

from .config import settings

EXCLUDE = {"node_modules", "target", "dist", ".git", "known_faces", ".env",
           "__pycache__", ".angular", "coverage"}


def _root(target_app: str) -> Path:
    root = (settings.workspaces_dir / target_app).resolve()
    base = settings.workspaces_dir.resolve()
    if not root.is_relative_to(base):
        raise ValueError("target_app hors de la zone autorisée")
    return root


def _safe_read(root: Path, relative: str) -> str:
    path = (root / relative).resolve()
    if not path.is_relative_to(root):
        raise ValueError("chemin hors dépôt: " + relative)
    return path.read_text(encoding="utf-8", errors="replace")


def list_tree(target_app: str) -> list[str]:
    root = _root(target_app)
    if not root.exists():
        return []
    out = []
    for path in root.rglob("*"):
        if any(part in EXCLUDE or part.startswith(".") for part in path.parts):
            continue
        if path.is_file():
            out.append(path.relative_to(root).as_posix())
    return sorted(out)


def _rank(path: str, hints: list[str], requirement_texts: list[str]) -> int:
    score = 0
    low = path.lower()
    for hint in hints:
        if hint and hint.lower() in low:
            score += 10
    for keyword in ("i18n", "lang", "language", "score", "recruteur", "heuristics", "index.html"):
        if keyword in low:
            score += 3
    joined = " ".join(requirement_texts).lower()
    for word in joined.split()[:30]:
        if len(word) > 4 and word in low:
            score += 1
    return score


def test_commands(target_app: str) -> list[str]:
    if target_app == "fake-exposer":
        return ["npm ci", "npm run lint", "npm test"]
    return ["mvn -q -DskipTests compile", "mvn -q test"]


def build_context(target_app: str, hints: list[str], requirement_texts: list[str],
                  max_tokens: int | None = None) -> dict:
    budget = max_tokens or settings.max_repo_context_tokens
    root = _root(target_app)
    tree = list_tree(target_app)
    ranked = sorted(tree, key=lambda p: _rank(p, hints, requirement_texts), reverse=True)

    files = []
    used = 0
    for path in ranked[:8]:
        try:
            text = _safe_read(root, path)
        except (OSError, ValueError):
            continue
        lines = text.splitlines()
        if len(lines) > 300:
            text = "\n".join(lines[:300]) + "\n// ...TRUNCATED..."
        tokens = max(1, int(len(text.split()) * 1.3))  # ASSUMPTION: voir chunk.estimate_tokens
        if used + tokens > budget:
            continue
        files.append({"path": path, "content": text, "lines": min(len(lines), 300)})
        used += tokens
        if len(files) >= 6:
            break

    return {
        "targetApp": target_app,
        "tree": tree[:120],
        "files": files,
        "testCommands": test_commands(target_app),
        "tokenCount": used,
        "note": "" if root.exists() else "WORKSPACE_ABSENT: clone le dépôt dans workspaces/",
    }

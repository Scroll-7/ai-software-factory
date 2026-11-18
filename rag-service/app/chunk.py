"""Chunking déterministe. Estimation de tokens volontairement simple : ASSUMPTION."""
import re

from .config import settings

_HEADING = re.compile(r"^(#{1,3})\s+(.*)$")
_SYMBOL = re.compile(
    r"^(export\s+|async\s+)?(function|class|const|describe\s*\(|it\s*\(|"
    r"@RestController|@Service|public\s+class)")


def estimate_tokens(text: str) -> int:
    """ASSUMPTION: ~1.3 token par mot séparé par espace. VERIFY avec ton tokenizer local."""
    return max(1, int(len(text.split()) * 1.3))


def chunk_markdown(text: str, meta: dict) -> list[dict]:
    blocks: list[dict] = []
    current: list[str] = []
    section = "(intro)"

    def flush():
        nonlocal current
        if not current:
            return
        content = "\n".join(current).strip()
        if content:
            blocks.append(_make(content, meta, section))
        current = []

    for line in text.splitlines():
        m = _HEADING.match(line)
        if m and len(m.group(1)) <= 2:
            flush()
            section = m.group(2).strip()
        current.append(line)
    flush()
    return _bounded(blocks)


def chunk_code(text: str, meta: dict) -> list[dict]:
    lines = text.splitlines()
    blocks: list[dict] = []
    start = 0
    symbol = "(file)"

    def flush(end: int):
        nonlocal start
        if end > start:
            header = f"// FILE: {meta.get('file_path', '?')} | SYMBOL: {symbol} | LINES: {start + 1}-{end}"
            body = "\n".join(lines[start:end])
            blocks.append(_make(header + "\n" + body, meta, symbol, start + 1, end))
        start = end

    for i, line in enumerate(lines):
        if _SYMBOL.match(line) and i - start >= 5:
            flush(i)
            symbol = line.strip()[:80]
    flush(len(lines))
    return _bounded(blocks)


def _make(content: str, meta: dict, section: str,
          line_start: int | None = None, line_end: int | None = None) -> dict:
    out = dict(meta)
    out.update({
        "content": content,
        "token_count": estimate_tokens(content),
        "section": section,
    })
    if line_start is not None:
        out["line_start"] = line_start
        out["line_end"] = line_end
    return out


def _bounded(blocks: list[dict]) -> list[dict]:
    """Découpe les blocs trop longs sans couper au milieu d'une ligne."""
    result = []
    for b in blocks:
        lines = b["content"].splitlines()
        if b["token_count"] <= settings.max_chunk_tokens and len(lines) <= settings.max_code_lines:
            result.append(b)
            continue
        chunk: list[str] = []
        for line in lines:
            candidate = "\n".join(chunk + [line])
            if estimate_tokens(candidate) > settings.max_chunk_tokens and chunk:
                copy = dict(b)
                copy["content"] = "\n".join(chunk)
                copy["token_count"] = estimate_tokens(copy["content"])
                result.append(copy)
                chunk = chunk[-5:]
            chunk.append(line)
        if chunk:
            copy = dict(b)
            copy["content"] = "\n".join(chunk)
            copy["token_count"] = estimate_tokens(copy["content"])
            result.append(copy)
    return result

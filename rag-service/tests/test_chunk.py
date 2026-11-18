import unittest

from app.chunk import chunk_code, chunk_markdown, estimate_tokens


class ChunkTests(unittest.TestCase):
    def test_markdown_splits_on_headings(self):
        text = "# Titre\n\nPremier paragraphe.\n\n## Section\n\nDeuxième paragraphe.\n"
        chunks = chunk_markdown(text, {"source_uri": "synthetic://test.md"})
        self.assertGreaterEqual(len(chunks), 2)
        self.assertTrue(all(c["source_uri"] == "synthetic://test.md" for c in chunks))

    def test_code_keeps_file_header(self):
        code = "export function a() { return 1; }\n\nexport function b() { return 2; }\n"
        chunks = chunk_code(code, {"source_uri": "repo://x.js", "file_path": "x.js"})
        self.assertTrue(any("// FILE: x.js" in c["content"] for c in chunks))

    def test_token_estimate_is_positive(self):
        self.assertGreater(estimate_tokens("bonjour le monde"), 0)


if __name__ == "__main__":
    unittest.main()

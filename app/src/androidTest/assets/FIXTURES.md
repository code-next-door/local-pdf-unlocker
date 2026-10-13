# Test PDFs

Made up for the tests; no real documents. Rebuild with `qpdf` (Homebrew). Encryption adds random IDs, so
rebuilt files differ byte-for-byte but behave the same.

`hand.pdf` (not kept): a hand-written 2-page PDF with empty pages:

```
%PDF-1.7
1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj
2 0 obj << /Type /Pages /Kids [3 0 R 4 0 R] /Count 2 >> endobj
3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] >> endobj
4 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] >> endobj
trailer << /Size 5 /Root 1 0 R >>
%%EOF
```

| File | Made with | What it is |
|---|---|---|
| `plain.pdf` | `qpdf hand.pdf plain.pdf` (rebuilds the missing xref; warns, fine) | 2 pages, not encrypted |
| `user-locked.pdf` | `qpdf --encrypt user123 owner456 256 -- plain.pdf user-locked.pdf` | needs password `user123` to open |
| `owner-only.pdf` | `qpdf --encrypt "" owner456 256 --print=none --modify=none -- plain.pdf owner-only.pdf` | opens without a password; printing/editing restricted |
| `not-a-pdf.pdf` | `printf 'this is not a pdf\n' > not-a-pdf.pdf` | not a PDF |

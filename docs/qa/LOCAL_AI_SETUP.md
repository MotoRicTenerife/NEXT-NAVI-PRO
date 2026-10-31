# Local AI QA — zero API cost

## What this adds
`tools/local_qa_team.py` runs ten separate, read-only specialist reviews using a model already installed in **Ollama on your own computer**. It sends the Git diff only to the Ollama HTTP endpoint on localhost, stores a Markdown report locally, and never edits source files or executes model suggestions.

The cloud side remains GitHub Actions: deterministic GNSS/telemetry, backend and Android checks. The local AI side adds heuristic/code-review coverage; it is not a substitute for unit tests, emulator checks, S22 Ultra tests, or road validation.

## Cost and privacy boundaries
- No OpenAI, Anthropic, Gemini, Ollama Cloud, or other hosted model API.
- No API key, subscription, or token billing.
- Script refuses non-local Ollama URLs.
- No automatic source-code changes, commits, pushes, deployments, or APK publication.
- Review output is advisory and must be checked by the engineering owner.
- Ollama model downloads use internet bandwidth and disk space, but local inference itself has no per-token API fee. Hardware electricity and disk are not literally free.

## Initial setup on Windows
1. Install Ollama from the official site: https://ollama.com/download
2. Open PowerShell and check `ollama --version`.
3. Check available RAM, CPU and GPU in Task Manager (Performance tab). Do not download a large model until hardware is known.
4. In the repository folder, update refs:
   ```powershell
   git fetch origin
   ```
5. Download a model only after choosing one that fits the PC. Example small coding model (model availability and exact tags can change):
   ```powershell
   ollama pull qwen2.5-coder:7b
   ```
6. Confirm it is installed:
   ```powershell
   ollama list
   ```
7. Run the review against the checked-out PR branch:
   ```powershell
   $env:OLLAMA_MODEL = "qwen2.5-coder:7b"
   python tools/local_qa_team.py --base origin/feat/navigation-intelligence-team
   ```
   Or specify `--model` explicitly. Ollama must be running locally; its default API is `http://127.0.0.1:11434`.

The report is written to `.qa-local-reports/` (local output; do not commit it unless intentionally sharing a report).

## Choosing the model
Do not assume the Galaxy S22 Ultra is the right inference host. Run Ollama on a computer. A small quantized 7B/8B-class model may be usable on a 16 GB RAM machine, but speed and context depend on CPU/GPU, quantization and available memory. A GPU with supported drivers improves throughput. We should inspect the actual PC before selecting the model or recommending downloads.

Official references:
- Ollama downloads: https://ollama.com/download
- Ollama GPU compatibility: https://docs.ollama.com/gpu
- Ollama model library: https://ollama.com/library

## Known limitations
- The script reviews the Git diff, not the entire repository. It explicitly reports that it cannot verify code absent from the diff.
- A single local model wearing ten role prompts is ten independent reviews, not ten independently trained agents. Diversity can be improved later by running different local models, if hardware permits.
- Large diffs are truncated at 40,000 characters by default; the report flags truncation. Prefer small focused PRs.
- The local model can hallucinate. Every finding must be checked against source and tests.
- The model cannot validate GPS reception, TTS timing through Cardo, device thermal behavior, or real riding conditions.

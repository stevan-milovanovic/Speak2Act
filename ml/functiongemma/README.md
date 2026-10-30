# Speak2Act — fine-tune FunctionGemma 270M in Google Colab

Two notebooks that teach FunctionGemma 270M one job: turn a payment command into an `executeTransaction` call, then
convert the model to a `.litertlm` file for LiteRT-LM on Android. They are a companion to
`speak2act-functiongemma-finetuning-guide.md` (step by step) and `speak2act-on-device-llm-plan.md` (Phase 2, §9).

Everything runs on free resources: Colab's free T4 GPU, Google Drive's free storage and a free Hugging Face account.

Gemma 4 E2B is the **teacher**: it writes the sentences. FunctionGemma 270M is the **student**: it is fine-tuned on
them. Labels never come from the teacher. They come from the seed transactions.

| Notebook | What it does | Runtime | Time |
|---|---|---|---|
| `01_generate_dataset.ipynb` | Seeds → Gemma 4 E2B (loaded with Unsloth) writes sentences → code checks amount, name and currency → teacher judges the direction → train/eval JSONL in Drive | Free T4 | ~30–45 min |
| `02_finetune_and_convert.ipynb` | Parity check → base eval → SFT → eval again → `.litertlm` (int8, tuned + base) → golden set on LiteRT-LM itself → size, SHA-256 and the Kotlin snippet → optional publish to Hugging Face | Free T4 (float32) | ~1–1.5 h |

## Run order
1. Accept the license on https://huggingface.co/google/functiongemma-270m-it and create an access token.
2. Upload both notebooks to Colab (*File ▸ Upload notebook*) and pick the free **T4 GPU** runtime.
3. Notebook 2 only: add the Colab secret `HF_TOKEN` (🔑 *Secrets*, notebook access on).
4. Run notebook 1, then notebook 2. Both read and write `MyDrive/speak2act/`.
5. Copy the size, SHA-256 and URL that notebook 2 prints into `FunctionGemmaModel.TUNED` in the app.

## Files
| File | Purpose |
|---|---|
| `execute_transaction.json` | The tool declaration, with **alphabetical keys at every level**. It must be identical to `TransactionContract.toolJson` in the app. LiteRT-LM renders keys in JSON order, declares every parameter and adds a blank line after the system prompt; the Hugging Face chat template sorts keys, skips the `description` parameter and has no blank line. Notebook 2 builds training prompts the way the phone does and checks the Hugging Face template against them before training. |
| `src/s2a_data.py`, `src/s2a_eval.py` | Data generation and evaluation helpers. Both notebooks embed them verbatim (`%%writefile`). |
| `src/test_s2a_data.py`, `src/test_s2a_eval.py` | Unit tests for the helpers. Run them locally: `python3 src/test_s2a_data.py && python3 src/test_s2a_eval.py` |
| `src/build_notebooks.py` | Regenerates both notebooks from the helpers: `python3 src/build_notebooks.py .` |

Edit the helpers, not the notebooks, then regenerate the notebooks. Commit them to `ml/functiongemma/` with outputs
cleared. Never commit tokens, datasets, checkpoints or `.litertlm` files.

## Licenses
- FunctionGemma 270M and its fine-tunes are covered by the [Gemma Terms of Use](https://ai.google.dev/gemma/terms).
  If you publish a fine-tune, pass on the use restrictions and ship a NOTICE file. Notebook 2 writes one.
- Gemma 4 E2B is Apache 2.0.
- Don't swap the teacher for the Gemini API: its terms forbid using it to develop competing models.

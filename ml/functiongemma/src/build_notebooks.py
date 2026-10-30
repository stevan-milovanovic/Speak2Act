#!/usr/bin/env python3
"""Builds the two Colab notebooks of the Speak2Act × FunctionGemma guide from tested sources.

The helper modules (s2a_data.py, s2a_eval.py) and the tool JSON are embedded verbatim, so the notebooks run exactly
the code that test_s2a_data.py / test_s2a_eval.py cover. Usage: python3 build_notebooks.py <out_dir>
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = sys.argv[1] if len(sys.argv) > 1 else HERE
DATA_SRC = open(os.path.join(HERE, "s2a_data.py")).read()
EVAL_SRC = open(os.path.join(HERE, "s2a_eval.py")).read()
# Next to this script, or one level up (speak2act-colab/ and ml/functiongemma/ keep the helpers in src/).
TOOL_PATH = next(p for p in (os.path.join(HERE, "execute_transaction.json"),
                             os.path.join(os.path.dirname(HERE), "execute_transaction.json")) if os.path.exists(p))
TOOL_JSON = open(TOOL_PATH).read().strip()


def md(text):
    return {"cell_type": "markdown", "metadata": {}, "source": text.strip("\n")}


def code(text):
    return {"cell_type": "code", "metadata": {}, "execution_count": None, "outputs": [], "source": text.strip("\n")}


def notebook(cells, gpu="T4"):
    for c in cells:
        lines = c["source"].split("\n")
        c["source"] = [l + "\n" for l in lines[:-1]] + [lines[-1]]
    return {"cells": cells, "nbformat": 4, "nbformat_minor": 0,
            "metadata": {"accelerator": "GPU", "colab": {"provenance": [], "gpuType": gpu},
                         "kernelspec": {"display_name": "Python 3", "name": "python3"},
                         "language_info": {"name": "python"}}}


# ======================================================================== 01 — dataset
NB1 = [
    md("""
# Speak2Act × FunctionGemma — 1. Generate the training data

A big model teaches a small one: **Gemma 4 E2B** (the *teacher*, Apache 2.0) writes the sentences, and **this
notebook's code writes the labels**, from the seed — never from the teacher. Notebook 2 then fine-tunes
**FunctionGemma 270M** (the *student*) on the result.

```
seeds (valid transactions) ─► teacher writes ways to say them ─► code checks amount, name, currency ─► labels from the seed ─► JSONL
```

**Runtime — free:** *Runtime ▸ Change runtime type ▸ T4 GPU* (Colab's free tier). The T4 has no bfloat16, and Gemma
models overflow plain float16, so the teacher is loaded with [Unsloth](https://github.com/unslothai/unsloth) (free,
open source) — the setup of Unsloth's own free Gemma 4 notebooks. About 30–45 minutes for the default settings;
keep the browser tab open while it runs.

**Output** (in Google Drive, `MyDrive/speak2act/`): `speak2act_fc_train.jsonl`, `speak2act_fc_eval.jsonl`,
`dataset_stats.json`. Nothing here needs a Hugging Face token: Gemma 4 is ungated.

The golden-set sentences of the talk are excluded on purpose — they're the honest test of the model.
"""),
    code("""
!nvidia-smi --query-gpu=name,memory.total --format=csv
import torch
major, minor = torch.cuda.get_device_capability()
# Ask the hardware: torch.cuda.is_bf16_supported() says True on a T4 too (it counts emulation).
print(f"{torch.cuda.get_device_name(0)} · compute capability {major}.{minor} · bfloat16 in hardware: {major >= 8}")
"""),
    code("""
# Unsloth runs Gemma 4 on GPUs without bfloat16, like the free T4. This is the install of Unsloth's free
# "Gemma4_(E2B)-Text" Colab notebook; if it ever breaks, copy the install cell from there:
# https://github.com/unslothai/notebooks/blob/main/nb/Gemma4_(E2B)-Text.ipynb
!pip install -q sentencepiece protobuf "datasets==4.3.0" "huggingface_hub>=0.34.0" hf_transfer
!pip install -q --no-deps unsloth_zoo bitsandbytes accelerate peft trl triton unsloth
!unsloth install-kernels
!pip install -q --no-deps --upgrade "torchao>=0.16.0"
!pip install -q --no-deps transformers==5.5.0 "tokenizers>=0.22.0,<=0.23.0"
!pip install -q "huggingface_hub>=1.5.0,<2.0"
import importlib.metadata as _md
try: _tv = tuple(int(x) for x in _md.version("torch").split("+")[0].split(".")[:2])
except Exception: _tv = (2, 11)
_torchcodec = {(2, 11): "torchcodec>=0.11.0", (2, 10): "torchcodec==0.10.*", (2, 9): "torchcodec==0.9.*", (2, 8): "torchcodec==0.7.*", (2, 7): "torchcodec==0.5.*"}.get(_tv, "torchcodec>=0.12.0" if _tv >= (2, 12) else "torchcodec")
!pip install -q "{_torchcodec}"
!pip install -q --no-deps --upgrade timm      # Gemma 4's vision and audio towers are part of the checkpoint
import torch; torch._dynamo.config.recompile_limit = 64
"""),
    code("""
#@title Settings
import torch                # imported again here: after a restart, the cells below still work
N_SEEDS = 700               #@param {type:"integer"}
SENTENCES_PER_SEED = 6      #@param {type:"integer"}
NEGATIVE_BATCHES = 40       #@param {type:"integer"}
NEGATIVES_PER_BATCH = 10    #@param {type:"integer"}
INCOMPLETE_COMMANDS = 150   #@param {type:"integer"}
GERMAN_SHARE = 0.1          #@param {type:"number"}
NOISY_SHARE = 0.3           #@param {type:"number"}
VERIFY_ACTIONS = True       #@param {type:"boolean"}
VERIFY_NEGATIVES = True     #@param {type:"boolean"}
BATCH_SIZE = 16             #@param {type:"integer"}
OUT_DIR = "/content/drive/MyDrive/speak2act"  #@param {type:"string"}
TEACHER = "google/gemma-4-E2B-it"
SYSTEM_PROMPT = "You are a model that can do function calling with the following functions"
"""),
    code("""
from google.colab import drive
import os
drive.mount("/content/drive")
os.makedirs(OUT_DIR, exist_ok=True)
"""),
    md("""
## The shared contract
This is the **same JSON** as `TransactionContract.toolJson` in the app (`ml/functiongemma/execute_transaction.json`
in the repo). Keys are in alphabetical order on purpose: LiteRT-LM renders them in this order on the phone, and the
training chat template sorts them — so both sides show the model the same declaration. (The one other difference,
a blank line after the system prompt on the phone, is handled in notebook 2.)
"""),
    code("TOOL = " + TOOL_JSON.replace("\n", "\n") + "\nprint(TOOL[\"name\"], list(TOOL[\"parameters\"][\"properties\"]))"),
    md("""
## Helpers
Seeds, the teacher prompts, the grounding checks (a Python port of the app's number reader and guard: the same
tokens, the same "fail closed" rules), Whisper-style noise and the golden set. Tested outside Colab
(`test_s2a_data.py`).
"""),
    code("%%writefile s2a_data.py\n" + DATA_SRC),
    code("""
import importlib, s2a_data as d
importlib.reload(d)
print(len(d.GOLDEN), "golden sentences excluded from the data")
"""),
    md("""
## Load the teacher: Gemma 4 E2B, with Unsloth
16-bit weights, about 10 GB of the T4's 15 GB. Unsloth must load before anything else imports Transformers — if you
re-run cells, restart the session first (*Runtime ▸ Restart session*) and run from the Settings cell.
"""),
    code("""
from unsloth import FastModel     # before Transformers: Unsloth patches Gemma 4 for GPUs without bfloat16

teacher, processor = FastModel.from_pretrained(
    model_name=TEACHER,           # google/gemma-4-E2B-it — Apache 2.0, ungated: no token needed
    dtype=None,                   # automatic: float16 with Unsloth's Gemma handling on the free T4
    max_seq_length=2048,
    load_in_4bit=False,           # 16-bit weights: the teacher's full quality
)
getattr(processor, "tokenizer", processor).padding_side = "left"      # batched generation
print(teacher.dtype, f"{torch.cuda.memory_allocated() / 1e9:.1f} GB on the GPU")
"""),
    code("""
BATCHED = True    # the self-test below switches to one prompt at a time if batching misbehaves


def _teach(prompts, max_new_tokens, temperature):
    conversations = [[{"role": "user", "content": [{"type": "text", "text": p}]}] for p in prompts]
    inputs = processor.apply_chat_template(
        conversations, tokenize=True, return_dict=True, return_tensors="pt",
        add_generation_prompt=True, padding=True, enable_thinking=False,
    ).to(teacher.device)
    # Google's Gemma 4 sampling (top_p 0.95, top_k 64); temperature 0 = greedy, for the judge passes.
    sampling = (dict(do_sample=True, temperature=temperature, top_p=0.95, top_k=64) if temperature
                else dict(do_sample=False))
    with torch.inference_mode():
        out = teacher.generate(**inputs, max_new_tokens=max_new_tokens, **sampling)
    return processor.batch_decode(out[:, inputs["input_ids"].shape[-1]:], skip_special_tokens=True)


def teach(prompts, max_new_tokens=400, temperature=0.9):
    \"\"\"The teacher's replies to a batch of prompts; temperature=0 means greedy (for judging).\"\"\"
    global BATCHED
    if BATCHED:
        try:
            return _teach(prompts, max_new_tokens, temperature)
        except Exception as e:
            print("Batched generation failed — one prompt at a time from now on:", e)
            BATCHED = False
    return [_teach([p], max_new_tokens, temperature)[0] for p in prompts]
"""),
    md("""
## Self-test: does the teacher work on this GPU?
Two sums, greedy: alone, then together (so one of them is padded). Plain float16 makes Gemma models answer nonsense;
this stops before the long run if anything is off.
"""),
    code("""
QUESTIONS = ["What is 7 + 5? Reply with the number only.",
             "Here is a slightly longer question, so the two get padded differently: what is 10 + 20? "
             "Reply with the number only."]
single = _teach(QUESTIONS[:1], 16, 0)[0]
print("alone:", repr(single))
assert "12" in single, ("The teacher answers nonsense. Runtime ▸ Restart session, then run every cell from the "
                        "Settings cell down again (Unsloth must load before Transformers).")
together = teach(QUESTIONS, 16, 0)
print("together:", [repr(a) for a in together])
if BATCHED and not ("12" in together[0] and "30" in together[1]):
    BATCHED = False
    print("⚠️ Batched answers are off on this setup: one prompt at a time from now on — much slower, so consider "
          "N_SEEDS = 300 in Settings (and re-run Settings, then continue from the Positive examples cell).")
print("✅ The teacher works" + (", batched." if BATCHED else ", one prompt at a time."))
"""),
    md("## Smoke test: one seed"),
    code("""
import random
seed = d.make_seeds(1, random.Random(1))[0]
label = d.label_for(seed)
prompt = d.teacher_prompt(seed, SENTENCES_PER_SEED)
print(prompt, "\\n---")
for s in d.parse_teacher_output(teach([prompt])[0]):
    print("✅" if d.grounded(s, label) else "❌", s)
print("label:", label)
"""),
    md("""
## Positive examples
A sentence is kept only if the app's guard would accept its label: **exactly one** amount, and it's the seed's; the
seed's name; and the currency it says (no currency said → CHF, like the app). Then the teacher judges the direction
of each kept sentence (pay, request or split), and sentences that don't match the seed are dropped. The teacher can't
teach the student a wrong label.
"""),
    code("""
from tqdm.auto import tqdm

rng = random.Random(42)
seeds = d.make_seeds(N_SEEDS, random.Random(7))
candidates, seen, dropped = [], set(), 0
for start in tqdm(range(0, len(seeds), BATCH_SIZE)):
    batch = seeds[start:start + BATCH_SIZE]
    langs = ["German" if rng.random() < GERMAN_SHARE else "English" for _ in batch]
    replies = teach([d.teacher_prompt(s, SENTENCES_PER_SEED, lang) for s, lang in zip(batch, langs)])
    for seed, lang, reply in zip(batch, langs, replies):
        label = d.label_for(seed)
        for s in d.parse_teacher_output(reply):
            key = d.norm(s)
            if d.grounded(s, label) and not d.is_golden(s) and 3 <= len(s.split()) <= 30 and key not in seen:
                seen.add(key)
                args = d.ground_description(s, label)    # no reason in the label unless the sentence says it
                candidates.append({"seed_id": seed["seed_id"], "sentence": s, "args": args, "lang": lang})
            else:
                dropped += 1
print(f"{len(candidates)} grounded sentences, {dropped} dropped (not exactly the seed's amount, name or currency; duplicates)")

# The teacher as judge: code can't check the direction (pay vs request), so a second, greedy pass does.
if VERIFY_ACTIONS:
    groups = [candidates[i:i + 20] for i in range(0, len(candidates), 20)]
    judged = []
    for start in tqdm(range(0, len(groups), BATCH_SIZE)):
        chunk = groups[start:start + BATCH_SIZE]
        replies = teach([d.action_check_prompt([c["sentence"] for c in g]) for g in chunk], temperature=0)
        for group, reply in zip(chunk, replies):
            actions = d.parse_actions(reply, len(group))
            if actions:
                judged += [c for c, a in zip(group, actions) if a == c["args"]["action"]]
    print(f"{len(judged)} of {len(candidates)} kept after the direction check")
    candidates = judged

positives = []
for c in candidates:
    positives.append(c)
    if c["lang"] == "English" and rng.random() < NOISY_SHARE:   # Whisper-style variant of the same sentence
        noisy = d.whisper_noise(c["sentence"], rng)
        if d.norm(noisy) not in seen and d.grounded(noisy, c["args"]) and not d.is_golden(noisy):
            seen.add(d.norm(noisy))
            positives.append(dict(c, sentence=noisy))
print(f"{len(positives)} positive examples")
"""),
    md("""
## Negative examples
Things that are **not** payment commands, and commands the app can't act on (something missing, or two transactions
in one sentence). The student learns to answer with a short sentence instead of a call — and the app turns "no call"
into the next step of the cascade. The teacher writes the near-misses, so it also double-checks them: a "negative"
that is really a complete payment command would teach the student to ignore real commands.
"""),
    code("""
teacher_negatives = []
prompts_left = NEGATIVE_BATCHES
with tqdm(total=NEGATIVE_BATCHES) as progress:
  while prompts_left > 0:
    n = min(BATCH_SIZE, prompts_left)
    prompts_left -= n
    names = ", ".join(rng.sample(d.NAMES, 8))
    replies = teach([d.NEGATIVE_PROMPT.format(n=NEGATIVES_PER_BATCH, names=names)] * n, temperature=1.0)
    progress.update(n)
    for reply in replies:
        for s in d.parse_teacher_output(reply):
            key = d.norm(s)
            if key not in seen and not d.is_golden(s) and 2 <= len(s.split()) <= 30:
                seen.add(key)
                teacher_negatives.append(s)

# The teacher as judge, greedy: drop every "negative" it reads as a complete payment command (and unreadable groups).
if VERIFY_NEGATIVES:
    groups = [teacher_negatives[i:i + 20] for i in range(0, len(teacher_negatives), 20)]
    kept = []
    for start in tqdm(range(0, len(groups), BATCH_SIZE)):
        chunk = groups[start:start + BATCH_SIZE]
        replies = teach([d.command_check_prompt(g) for g in chunk], temperature=0)
        for group, reply in zip(chunk, replies):
            verdicts = d.parse_yes_no(reply, len(group))
            if verdicts:
                kept += [s for s, is_command in zip(group, verdicts) if not is_command]
    print(f"{len(kept)} of {len(teacher_negatives)} teacher negatives kept after the command check")
    teacher_negatives = kept

negatives = [{"sentence": s, "args": None, "source": "teacher"} for s in teacher_negatives]
for s in d.incomplete_commands(INCOMPLETE_COMMANDS):
    if d.norm(s) not in seen and not d.is_golden(s):
        seen.add(d.norm(s))
        negatives.append({"sentence": s, "args": None, "source": "incomplete"})
print(f"{len(negatives)} negative examples")
"""),
    md("## Split, check, save"),
    code("""
import collections, json, os

train_pos, eval_pos = d.split_by_seed(positives, eval_fraction=0.1)
rng.shuffle(negatives)
cut = max(1, len(negatives) // 10)
eval_neg, train_neg = negatives[:cut], negatives[cut:]

def rows(examples, split):
    return [d.to_row(x["sentence"], x["args"], split, TOOL, SYSTEM_PROMPT) for x in examples]

train = rows(train_pos, "train") + rows(train_neg, "train")
evals = rows(eval_pos, "eval") + rows(eval_neg, "eval")
rng.shuffle(train)

# The golden set must stay out of the data (plan §10.1).
all_sentences = {d.norm(r["messages"][1]["content"]) for r in train + evals}
assert not (all_sentences & d.GOLDEN_NORM), "a golden sentence leaked into the data"

for name, data in (("speak2act_fc_train.jsonl", train), ("speak2act_fc_eval.jsonl", evals)):
    with open(os.path.join(OUT_DIR, name), "w", encoding="utf-8") as f:
        for r in data:
            f.write(json.dumps(r, ensure_ascii=False) + "\\n")

stats = {
    "train": len(train), "eval": len(evals),
    "positives": len(positives), "negatives": len(negatives),
    "negative_share": round(len(negatives) / (len(positives) + len(negatives)), 3),
    "negatives_by_source": collections.Counter(x["source"] for x in negatives),
    "actions": collections.Counter(x["args"]["action"] for x in positives),
    "currencies": collections.Counter(x["args"]["currency"] for x in positives),
    "languages": collections.Counter(x["lang"] for x in positives),
    "teacher": TEACHER, "seeds": N_SEEDS, "sentences_per_seed": SENTENCES_PER_SEED,
}
json.dump(stats, open(os.path.join(OUT_DIR, "dataset_stats.json"), "w"), indent=2, default=dict)
print(json.dumps(stats, indent=2, default=dict))
"""),
    code("""
# Read samples of both kinds: do the labels match the sentences? Are the negatives really not commands?
sample_rng = random.Random(0)
for x in sample_rng.sample(positives, 8):
    print("🗣️", x["sentence"], "\\n   →", x["args"])
print()
for source in ("teacher", "incomplete"):
    pool = [x for x in negatives if x["source"] == source]
    for x in sample_rng.sample(pool, min(6, len(pool))):
        print(f"🚫 ({source})", x["sentence"])
"""),
    md("""
## Next
Open **`02_finetune_and_convert.ipynb`** (a new runtime is fine: it reads the two files from Drive).
Note the numbers above (`train`, `eval`, `negative_share`, `sentences_per_seed`) for the deck — slide "A big model
teaches a small one".
"""),
]

# ======================================================================== 02 — fine-tune, evaluate, convert
CONVERT_METADATA = r'''start_token: {
    token_ids: {
        ids: [ 2 ]
    }
}
stop_tokens: {
    token_str: "<end_of_turn>"
}
stop_tokens: {
    token_str: "<start_function_response>"
}
llm_model_type: {
    function_gemma: {}
}
'''

NB2 = [
    md("""
# Speak2Act × FunctionGemma — 2. Fine-tune, evaluate, convert

Fine-tunes **FunctionGemma 270M** on the data from notebook 1, compares it with the base model, and converts it to
a **`.litertlm`** file that the app runs with LiteRT-LM — then prints the Kotlin `ModelSpec` to pin it.

Recipe: Google's *Mobile Actions* notebook (full fine-tune with TRL, loss on the answer only), adapted to one
function and to the current converter (`litert-torch`, the new name of `ai-edge-torch`).

**Runtime — free:** *Runtime ▸ Change runtime type ▸ T4 GPU* (Colab's free tier). The T4 has no bfloat16, so
FunctionGemma trains in float32 — the same result, just slower: about 1–1.5 hours for the whole notebook. The
fine-tuned model is saved to Drive right after training, so if the free session ends, a new one continues from there.

**Before you start:**
1. Accept the FunctionGemma license on https://huggingface.co/google/functiongemma-270m-it (instant).
2. Add your Hugging Face token as a Colab secret named **`HF_TOKEN`** (🔑 in the left sidebar).
3. Notebook 1's files are in `MyDrive/speak2act/`.
"""),
    code("""
!nvidia-smi --query-gpu=name,memory.total --format=csv
"""),
    code("""
# The versions Google's FunctionGemma fine-tuning notebook was verified with.
%pip install -q -U transformers==4.57.1 trl==0.25.1 datasets==4.4.1
"""),
    code("""
#@title Settings
import torch
DATA_DIR = "/content/drive/MyDrive/speak2act"   #@param {type:"string"}
BASE_MODEL = "google/functiongemma-270m-it"
TUNED_DIR = f"{DATA_DIR}/speak2act-functiongemma-hf"   # the fine-tuned checkpoint, on Drive
CHECKPOINT_DIR = "/content/checkpoints"          # the trainer's scratch space
BASE_DIR = "/content/functiongemma-base"
OUT_NAME = "speak2act-functiongemma-v1"         #@param {type:"string"}
EPOCHS = 2                                       #@param {type:"integer"}
LEARNING_RATE = 2e-5                             #@param {type:"number"}
BATCH_SIZE = 4                                   #@param {type:"integer"}
GRAD_ACCUMULATION = 8                            #@param {type:"integer"}
CONVERT_BASE_TOO = True                          #@param {type:"boolean"}
EVAL_LIMIT = 200                                 #@param {type:"integer"}
EVAL_NEG_LIMIT = 60                              #@param {type:"integer"}
MAX_NEW_TOKENS = 96          # the app's maxOutputToken (plan §8.5.3): a full call is ~50 tokens
# bfloat16 only in hardware (compute capability 8+): torch.cuda.is_bf16_supported() says True on a T4 too.
BF16 = torch.cuda.get_device_capability()[0] >= 8
DTYPE = torch.bfloat16 if BF16 else torch.float32   # the free T4: float32 (Gemma models overflow plain float16)
print(torch.cuda.get_device_name(0), "→", DTYPE)
"""),
    code("""
from google.colab import drive, userdata
from huggingface_hub import login
drive.mount("/content/drive")
login(userdata.get("HF_TOKEN"))
"""),
    code("TOOL = " + TOOL_JSON + "\nSYSTEM_PROMPT = \"You are a model that can do function calling with the following functions\""),
    code("%%writefile s2a_eval.py\n" + EVAL_SRC),
    code("""
import importlib, s2a_eval as e
importlib.reload(e)
"""),
    md("""
## Parity check: does the phone show the model what training shows it?
On the phone, LiteRT-LM 0.18.1 renders the prompt itself: the tool declaration from the app's JSON (key order kept,
**every** parameter), inside its built-in FunctionGemma template — which puts a **blank line** between the system
prompt and the declaration. The Hugging Face template differs in exactly three ways: it sorts the keys, it has no
blank line, and it silently **skips parameters named like JSON-schema keywords** — our `description` parameter
(its `standard_keys`). So this notebook builds every training and evaluation prompt the way the phone does
(`e.litertlm_prompt`), and takes only the answers from the Hugging Face template. This cell checks that the
Hugging Face template agrees on everything else. If it fails, fix the key order of the JSON — in **both** the app
and this notebook — before you train. After the conversion, a later cell repeats the check with LiteRT-LM itself.
"""),
    code("""
from transformers import AutoTokenizer

tokenizer = AutoTokenizer.from_pretrained(BASE_MODEL)
TOOLS = [{"type": "function", "function": TOOL}]
PROBE = "I owe Stefan ten francs"
hf_probe = tokenizer.apply_chat_template(
    [{"role": "developer", "content": SYSTEM_PROMPT}, {"role": "user", "content": PROBE}],
    tools=TOOLS, tokenize=False, add_generation_prompt=True)
phone_probe = e.litertlm_prompt(SYSTEM_PROMPT, TOOL, PROBE)
print(phone_probe)
assert e.is_alphabetical(TOOL), "the tool JSON must have alphabetical keys"
skipped = sorted(set(TOOL["parameters"]["properties"]) & e.HF_SKIPPED_PROPERTY_NAMES)
print("Declared on the phone, skipped by the Hugging Face template:", skipped)
hf_view = e.litertlm_declaration(e.hf_template_view(TOOL))
assert e.declarations_in(hf_probe) == [hf_view], (e.declarations_in(hf_probe), hf_view)
assert e.hf_matches_phone(hf_probe, SYSTEM_PROMPT, TOOL, PROBE, bos=tokenizer.bos_token), hf_probe
print("✅ Parity: the Hugging Face template agrees with the phone on everything but the known differences —")
print("   training and evaluation use the phone's prompt, with every parameter declared.")
"""),
    md("## Load the data (prompt / completion)"),
    code("""
import json
from datasets import load_dataset

files = {"train": f"{DATA_DIR}/speak2act_fc_train.jsonl", "eval": f"{DATA_DIR}/speak2act_fc_eval.jsonl"}
raw = load_dataset("text", data_files=files, encoding="utf-8")   # as text: keeps each row's JSON exactly as written

def apply_format(sample):
    row = json.loads(sample["text"])
    assert row["tools"] == TOOLS, "the dataset was built with a different tool JSON"
    sentence = row["messages"][1]["content"]
    full = tokenizer.apply_chat_template(row["messages"], tools=row["tools"], tokenize=False, add_generation_prompt=False)
    hf_prompt = tokenizer.apply_chat_template(row["messages"][:-1], tools=row["tools"], tokenize=False, add_generation_prompt=True)
    assert full.startswith(hf_prompt)
    assert e.hf_matches_phone(hf_prompt, SYSTEM_PROMPT, TOOL, sentence, bos=tokenizer.bos_token), hf_prompt
    # The prompt exactly as the phone renders it — every parameter declared — and without the "<bos>" text: the
    # tokenizer adds the one BOS token. The answer (the call, or "no call") comes from the chat template.
    return {"prompt": e.litertlm_prompt(SYSTEM_PROMPT, TOOL, sentence), "completion": full[len(hf_prompt):]}

data = raw.map(apply_format, remove_columns=["text"])
lengths = [len(tokenizer.tokenize(x["prompt"] + x["completion"])) for x in data["train"]]
max_token_count = max(lengths) + 64
print(data, "\\nlongest example:", max(lengths), "tokens → max_length", max_token_count)
print(data["train"][0]["prompt"] + "⟶" + data["train"][0]["completion"])    # what slide "What FunctionGemma actually sees" shows
"""),
    md("""
## Evaluation
Greedy decoding, like the app (`topK = 1`), on the phone's prompt and with the app's output limit. Three groups:
**eval · call** (should call the function), **eval · no call** (must not), and the **golden set** G1–G10 (the talk's
sentences, never trained on). **core** = exactly one call, with the right action, amount, currency and person (what
the app's guard and mapper need); **exact** also checks the reason. The eval split never shares a seed with training.
"""),
    code("""
import pandas as pd
from transformers import AutoModelForCausalLM

def eval_cases():
    calls, no_calls = [], []
    for line in raw["eval"]["text"]:
        row = json.loads(line)
        answer = row["messages"][2]
        if "tool_calls" in answer:
            calls.append(("eval · call", row["messages"][1]["content"], answer["tool_calls"][0]["function"]["arguments"]))
        else:
            no_calls.append(("eval · no call", row["messages"][1]["content"], None))
    assert calls and no_calls, "the eval split needs both kinds of examples"
    return calls[:EVAL_LIMIT] + no_calls[:EVAL_NEG_LIMIT] + [("golden", s, t) for s, t in e.GOLDEN_EXPECTED]

CASES = eval_cases()
tokenizer.padding_side = "left"      # batched generation

def evaluate(model, label, batch_size=16):
    model.eval()
    records = []
    for start in range(0, len(CASES), batch_size):
        chunk = CASES[start:start + batch_size]
        # Exactly what the app sends (e.litertlm_prompt); the tokenizer adds the BOS token, like in training.
        prompts = [e.litertlm_prompt(SYSTEM_PROMPT, TOOL, sentence) for _, sentence, _ in chunk]
        inputs = tokenizer(prompts, return_tensors="pt", padding=True).to(model.device)
        with torch.inference_mode():
            out = model.generate(**inputs, max_new_tokens=MAX_NEW_TOKENS, do_sample=False,
                                 pad_token_id=tokenizer.pad_token_id)
        for (split, sentence, target), ids in zip(chunk, out[:, inputs["input_ids"].shape[-1]:]):
            output = tokenizer.decode(ids, skip_special_tokens=False)
            records.append({"model": label, "split": split, "sentence": sentence, "output": output,
                            **e.score(target, output)})
    df = pd.DataFrame(records)
    print(df.groupby("split")[["core", "exact"]].mean().round(3))
    return df
"""),
    md("### The base model — the \"before\""),
    code("""
base = AutoModelForCausalLM.from_pretrained(BASE_MODEL, dtype=DTYPE, device_map="auto", attn_implementation="eager")
base_scores = evaluate(base, "base")
del base
torch.cuda.empty_cache()
"""),
    md("""
## Fine-tune
The fine-tuned model goes to `TUNED_DIR` on Drive. **If the free session ends after this cell**, open a new one and
run the cells from Settings down to the evaluation cell, plus the base model's (2 minutes) — then continue below
this cell. Nothing needs to train twice.
"""),
    code("""
from trl import SFTConfig, SFTTrainer

model = AutoModelForCausalLM.from_pretrained(BASE_MODEL, dtype=DTYPE, device_map="auto", attn_implementation="eager")
model.config.pad_token_id = tokenizer.pad_token_id

args = SFTConfig(
    output_dir=CHECKPOINT_DIR,
    num_train_epochs=EPOCHS,
    per_device_train_batch_size=BATCH_SIZE,
    gradient_accumulation_steps=GRAD_ACCUMULATION,
    learning_rate=LEARNING_RATE,
    lr_scheduler_type="cosine",
    max_length=max_token_count,
    packing=False,
    completion_only_loss=True,          # learn the call, not the prompt
    gradient_checkpointing=True,
    optim="adamw_torch_fused",
    bf16=BF16,                          # False on the free T4: float32
    logging_steps=20,
    eval_strategy="epoch",
    save_strategy="no",                 # the final model goes to Drive below
    report_to="none",
)
trainer = SFTTrainer(model=model, args=args, train_dataset=data["train"], eval_dataset=data["eval"],
                     processing_class=tokenizer)
result = trainer.train()
trainer.model.config.use_cache = True    # gradient checkpointing switched it off; generation needs it
trainer.save_model(TUNED_DIR)            # on Drive: a new session can continue from here
tokenizer.save_pretrained(TUNED_DIR)
print(f"Training took {result.metrics['train_runtime'] / 60:.1f} min on {torch.cuda.get_device_name(0)}; "
      f"saved to {TUNED_DIR}")
del model, trainer
torch.cuda.empty_cache()
"""),
    md("## Evaluate the fine-tuned model — before vs after"),
    code("""
tuned = AutoModelForCausalLM.from_pretrained(TUNED_DIR, dtype=DTYPE, device_map="auto", attn_implementation="eager")
tuned_scores = evaluate(tuned, "fine-tuned")
frames = [base_scores, tuned_scores] if "base_scores" in globals() else [tuned_scores]
summary = pd.concat(frames).groupby(["split", "model"])[["core", "exact"]].mean().round(3)
print(summary)
summary.to_csv(f"{DATA_DIR}/eval_summary.csv")
"""),
    code("""
# What does the fine-tuned model still get wrong?
pd.set_option("display.max_colwidth", 120)
tuned_scores[~tuned_scores["core"]][["split", "sentence", "kind", "output"]].head(30)
"""),
    md("""
## Convert to `.litertlm`
The same conversion as Google's notebook, with the renamed package (`litert-torch`). The metadata marks the file as
**FunctionGemma**, so LiteRT-LM parses the calls into `Message.toolCalls` by itself. Context: 1,024 tokens of KV cache
(the prompt with the declaration is a few hundred). If Colab asks to restart after the install: restart, run the cells
from **Settings** down to `import s2a_eval` again (they're quick), then continue here — the fine-tuned model is on
Drive. The conversion runs on the CPU; the free runtime's 12 GB of RAM are enough for a 270M model.
"""),
    code("""
!pip uninstall -y -q tensorflow
%pip install -q litert-torch==0.10.0
"""),
    code('''
import os, shutil, hashlib
from huggingface_hub import hf_hub_download
from transformers import AutoModelForCausalLM, AutoTokenizer
from litert_torch.generative.examples.gemma3 import gemma3
from litert_torch.generative.utilities import converter
from litert_torch.generative.utilities.export_config import ExportConfig
from litert_torch.generative.layers import kv_cache

LLM_METADATA = r"""''' + CONVERT_METADATA + '''"""
OUT_DIR = "/content/litertlm"
os.makedirs(OUT_DIR, exist_ok=True)
metadata_path = os.path.join(OUT_DIR, "base_llm_metadata.textproto")
open(metadata_path, "w").write(LLM_METADATA)

def convert(checkpoint_dir, name):
    for old in os.listdir(OUT_DIR):            # rerunnable: remove this model's files from an earlier run
        if old.startswith(name) and old.endswith(".litertlm"):
            os.remove(os.path.join(OUT_DIR, old))
    tokenizer_model = os.path.join(checkpoint_dir, "tokenizer.model")
    if not os.path.exists(tokenizer_model):
        hf_hub_download(BASE_MODEL, "tokenizer.model", local_dir=checkpoint_dir)
    export_config = ExportConfig()
    export_config.kvcache_layout = kv_cache.KV_LAYOUT_TRANSPOSED
    export_config.mask_as_input = True
    converter.convert_to_litert(
        gemma3.build_model_270m(checkpoint_dir),
        output_path=OUT_DIR,
        output_name_prefix=name,
        prefill_seq_len=256,
        kv_cache_max_len=1024,
        quantize="dynamic_int8",
        export_config=export_config,
        tokenizer_model_path=tokenizer_model,
        base_llm_metadata_path=metadata_path,
        output_format="litertlm",
    )
    produced = [f for f in os.listdir(OUT_DIR) if f.startswith(name) and f.endswith(".litertlm")]
    assert len(produced) == 1, produced
    final = os.path.join(OUT_DIR, f"{name}.litertlm")
    os.replace(os.path.join(OUT_DIR, produced[0]), final)
    return final

tuned_file = convert(TUNED_DIR, OUT_NAME)
base_file = None
if CONVERT_BASE_TOO:   # the "before" of the before/after screenshots (plan §8.10)
    AutoModelForCausalLM.from_pretrained(BASE_MODEL).save_pretrained(BASE_DIR)
    AutoTokenizer.from_pretrained(BASE_MODEL).save_pretrained(BASE_DIR)
    base_file = convert(BASE_DIR, "functiongemma-270m-base")
print(tuned_file, base_file)
'''),
    md("""
## Check on the app's runtime: LiteRT-LM 0.18.1, in Colab
`litert-lm-api` is LiteRT-LM's Python API — the same C++ runtime, chat template and call parser as
`litertlm-android` 0.18.1 in the app. This cell loads the converted file on the CPU, sets up the conversation like
`FunctionGemmaTransactionModel` does (system prompt, the same tool JSON, greedy, no automatic tool calling, the same
output limit), checks that a fresh conversation renders **exactly** the training prompt, and runs the golden set.
It's the last check before the phone: the template, the conversion metadata and the call parsing, end to end. If the install fails, skip it — the phone check (plan §8.5.5) covers the same.
"""),
    code("""
%pip install -q litert-lm-api==0.18.1
"""),
    code('''
import litert_lm
import pandas as pd

class TransactionTool(litert_lm.Tool):
    """The app's tool: the same JSON, wrapped like LiteRT-LM's Kotlin API wraps an OpenApiTool."""
    def get_tool_description(self):
        return {"type": "function", "function": TOOL}
    def execute(self, param):
        raise RuntimeError("proposed, never executed")

def text(t):
    return [{"type": "text", "text": t}]

def new_conversation(engine):
    """Set up like FunctionGemmaTransactionModel: system prompt, the tool, greedy, propose only, the same limit."""
    return engine.create_conversation(
        messages=[{"role": "system", "content": text(SYSTEM_PROMPT)}],   # = ConversationConfig.systemInstruction
        tools=[TransactionTool()],
        automatic_tool_calling=False,
        sampler_config=litert_lm.SamplerConfig(top_k=1, top_p=1.0, temperature=1.0),
        max_output_tokens=MAX_NEW_TOKENS,
    )

def runtime_golden(model_file, label, probe=None):
    """The golden set on LiteRT-LM; with a probe sentence, also the prompt a fresh conversation renders for it."""
    rows, rendered = [], None
    engine = litert_lm.Engine(model_file, backend=litert_lm.Backend.CPU())
    try:
        if probe:
            conversation = new_conversation(engine)   # a fresh conversation renders its whole first prompt
            rendered = conversation.render_message_to_string({"role": "user", "content": text(probe)})
            conversation.close()
        for sentence, target in e.GOLDEN_EXPECTED:
            conversation = new_conversation(engine)
            reply = conversation.send_message({"role": "user", "content": text(sentence)})
            calls = [{"name": c.name, "arguments": dict(c.arguments)} for c in reply.tool_calls]
            rows.append({"model": label, "sentence": sentence, "calls": calls, **e.score_calls(target, calls)})
            conversation.close()
    finally:
        engine.close()
    return rows, rendered

PROBE = "I owe Stefan ten francs"
expected = e.litertlm_prompt(SYSTEM_PROMPT, TOOL, PROBE)
tuned_rows, rendered = runtime_golden(tuned_file, "fine-tuned", probe=PROBE)
base_rows = runtime_golden(base_file, "base")[0] if base_file else []
# LiteRT-LM renders bos_token as "" and adds the start token itself; strip "<bos>" in case a version renders it.
prompt_ok = bool(rendered) and rendered.removeprefix("<bos>") == expected
print("LiteRT-LM renders:\\n" + (rendered or "<nothing>"))
print("✅ the whole prompt matches training" if prompt_ok else "⚠️ differs from the training prompt, which is:\\n" + expected)
runtime = pd.DataFrame(tuned_rows + base_rows)
print(runtime.groupby("model")[["core", "exact"]].sum())
display(runtime[["model", "sentence", "kind", "calls"]])
assert prompt_ok, "the phone would see a different prompt than training — don't publish (guide §8)"
'''),
    md("## Pin it: size, SHA-256 and the Kotlin snippet"),
    code('''
def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()

def pin(path):
    shutil.copy(path, DATA_DIR)
    size, digest = os.path.getsize(path), sha256(path)
    print(f"{os.path.basename(path)}: {size:,} bytes ({size / 1e6:.0f} MB) — copied to {DATA_DIR}")
    return size, digest

size, digest = pin(tuned_file)
print(f"""
// FunctionGemmaModel.TUNED — the fine-tuned model (plan §9.4)
val TUNED = ModelSpec(
    fileName = "{os.path.basename(tuned_file)}",
    url = "<public URL from plan §9.3>",
    sizeBytes = {size}L,
    sha256 = "{digest}",
    isStale = {{ it.startsWith("speak2act-functiongemma-") && it.endsWith(".litertlm") }},
)""")
if base_file:
    size, digest = pin(base_file)
    print(f"""
// FunctionGemmaModel.BASE — the untuned model, ONLY for the before/after comparison (plan §8.10). Never as TUNED.
val BASE = ModelSpec("{os.path.basename(base_file)}", url = "", sizeBytes = {size}L, sha256 = "{digest}")""")
'''),
    md("""
## Publish (optional, after the human gate H5)
The app downloads the model anonymously, so it needs a **public** URL. This cell creates a public Hugging Face model
repo with the `.litertlm`, a model card and the **NOTICE** the Gemma Terms of Use ask for. Read the terms first —
this isn't legal advice. Needs a token with *write* access.
"""),
    code('''
PUBLISH = False        #@param {type:"boolean"}
HF_REPO = ""           #@param {type:"string"}
if PUBLISH:
    from huggingface_hub import HfApi
    api = HfApi()
    api.create_repo(HF_REPO, repo_type="model", private=False, exist_ok=True)
    notice = "Gemma is provided under and subject to the Gemma Terms of Use found at ai.google.dev/gemma/terms\\n"
    card = f"""---
license: gemma
base_model: {BASE_MODEL}
tags: [function-calling, litert-lm, on-device, speak2act]
---
# {OUT_NAME}

FunctionGemma 270M **fine-tuned** (modified) for one function, `executeTransaction`, used by the open-source demo app
[Speak2Act](https://github.com/stevan-milovanovic/Speak2Act). LiteRT-LM file, `dynamic_int8`, KV cache 1,024.

- Training data: synthetic — seeds labelled by code, sentences written by Gemma 4 E2B.
- Evaluation (eval split / golden set, core accuracy): see `eval_summary.csv`.
- **Demo only.** The app never moves money without the user's explicit confirmation.

Gemma is provided under and subject to the Gemma Terms of Use found at ai.google.dev/gemma/terms. Use is subject to
the Gemma Prohibited Use Policy (ai.google.dev/gemma/prohibited_use_policy).
"""
    open("README.md", "w").write(card)
    open("NOTICE", "w").write(notice)
    for path, name in [(tuned_file, os.path.basename(tuned_file)), ("README.md", "README.md"), ("NOTICE", "NOTICE"),
                       (f"{DATA_DIR}/eval_summary.csv", "eval_summary.csv")]:
        api.upload_file(path_or_fileobj=path, path_in_repo=name, repo_id=HF_REPO)
    print("URL for the app:", f"https://huggingface.co/{HF_REPO}/resolve/main/{os.path.basename(tuned_file)}")
'''),
    md("""
## Next
1. Try it on the phone before publishing: `adb push` (plan §8.5.5). The first command logs `llm_preface=…`: it must
   match the parity cell's output above.
2. Publish (above, or a GitHub Release asset) and pin the URL, size and SHA-256 in `FunctionGemmaModel.TUNED`
   (plan §9.4).
3. Note for the deck: GPU, training minutes, examples, and the before/after numbers above.
"""),
]


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, cells, gpu in (("01_generate_dataset.ipynb", NB1, "T4"), ("02_finetune_and_convert.ipynb", NB2, "T4")):
        path = os.path.join(OUT, name)
        with open(path, "w", encoding="utf-8") as f:
            json.dump(notebook(cells, gpu), f, indent=1, ensure_ascii=False)
        print("wrote", path)


if __name__ == "__main__":
    main()

"""Speak2Act × FunctionGemma — evaluation helpers (pure Python). Used by notebook 02 and tested locally.

- litertlm_declaration(): renders a tool exactly like LiteRT-LM v0.18.1 does on the phone (fc_tool_format_utils.cc):
  JSON key order kept, JSON-schema type names upper-cased, strings wrapped in the escape token.
- litertlm_prompt(): the whole prompt as LiteRT-LM 0.18.1's built-in FunctionGemma template renders it on the
  phone. It differs from the Hugging Face chat template in two places: a blank line between the system instruction
  and the tool declaration, and the Hugging Face template silently skips parameters named like JSON-schema keywords
  (`description`, `type`, …) — LiteRT-LM declares them all. Notebook 02 trains and evaluates on the phone's version;
  hf_template_view() / hf_matches_phone() check that the Hugging Face template agrees on everything else.
- parse_calls(): a strict reader for FunctionGemma's function calls (numbers as Decimal; malformed calls are skipped).
- score() / score_calls(): "core" (action, amount, currency, person: what the app's guard and mapper need) and
  "exact" (core + the reason). Like the app, only a single call to executeTransaction counts.
"""
import copy
import json
import re
from decimal import Decimal, InvalidOperation

ESC = "<escape>"
CALL_START, CALL_END = "<start_function_call>", "<end_function_call>"
DECL_START, DECL_END = "<start_function_declaration>", "<end_function_declaration>"
SYSTEM_PROMPT = "You are a model that can do function calling with the following functions"
FUNCTION_NAME = "executeTransaction"
DEFAULT_CURRENCY = "CHF"
TYPE_NAMES = {"string", "number", "integer", "object", "array", "boolean", "null"}
# LiteRT-LM 0.18.1's FunctionGemma template: "<start_of_turn>developer\n" + system instruction + "\n\n" + declarations.
# The Hugging Face template has no blank line there.
PHONE_SEPARATOR = "\n\n"
# The Hugging Face chat template's format_parameters skips properties with these names (its "standard_keys");
# LiteRT-LM's FormatToolAsFc declares every property.
HF_SKIPPED_PROPERTY_NAMES = {"description", "type", "properties", "required", "nullable"}


# ---------------------------------------------------------------- what the phone renders
def _upper_types(value):
    if isinstance(value, dict):
        out = {}
        for k, v in value.items():
            if k == "type" and isinstance(v, str) and v in TYPE_NAMES:
                out[k] = v.upper()
            else:
                out[k] = _upper_types(v)
        return out
    if isinstance(value, list):
        return [_upper_types(v) for v in value]
    return value


def _fc(value):
    if value is None:
        return "null"
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, (int, float)):
        return json.dumps(value)
    if isinstance(value, str):
        return f"{ESC}{value}{ESC}"
    if isinstance(value, list):
        return "[" + ",".join(_fc(v) for v in value) + "]"
    if isinstance(value, dict):
        return "{" + ",".join(f"{k}:{_fc(v)}" for k, v in value.items()) + "}"
    raise TypeError(type(value))


def litertlm_declaration(tool):
    """`declaration:NAME{…}` as LiteRT-LM renders an OpenApiTool's JSON (without the declaration tokens)."""
    fields = _upper_types(tool)
    name = fields.pop("name")
    return f"declaration:{name}{_fc(fields)}"


def litertlm_prompt(system_prompt, tool, sentence):
    """The prompt LiteRT-LM 0.18.1 renders on the phone for one command (after the BOS token): the developer turn
    (system instruction, blank line, declaration), the user's sentence, and the start of the model's turn."""
    return ("<start_of_turn>developer\n" + system_prompt.strip() + PHONE_SEPARATOR
            + DECL_START + litertlm_declaration(tool) + DECL_END + "<end_of_turn>\n"
            + "<start_of_turn>user\n" + sentence.strip() + "<end_of_turn>\n"
            + "<start_of_turn>model\n")


def to_phone_prompt(hf_prompt, system_prompt):
    """A prompt rendered by the Hugging Face chat template, changed into what the phone renders: the blank line
    between the system instruction and the tool declaration. Raises if the prompt doesn't have that shape."""
    head = system_prompt.strip() + DECL_START
    if hf_prompt.count(head) != 1:
        raise ValueError("expected the system instruction right before the tool declaration, exactly once")
    return hf_prompt.replace(head, system_prompt.strip() + PHONE_SEPARATOR + DECL_START)


def hf_template_view(tool):
    """The tool as the Hugging Face chat template declares it: without parameters named like JSON-schema keywords
    (our `description` parameter!). Nested object parameters are filtered the same way."""
    view = copy.deepcopy(tool)

    def strip(schema):
        props = schema.get("properties")
        if not isinstance(props, dict):
            return
        for key in list(props):
            if key in HF_SKIPPED_PROPERTY_NAMES:
                del props[key]
            elif isinstance(props[key], dict):
                strip(props[key])

    if isinstance(view.get("parameters"), dict):
        strip(view["parameters"])
    return view


def hf_matches_phone(hf_prompt, system_prompt, tool, sentence, bos=""):
    """True when a prompt rendered by the Hugging Face template equals the phone's (litertlm_prompt), except for the
    two known differences: the blank line after the system prompt and the parameters the template skips."""
    phone = litertlm_prompt(system_prompt, tool, sentence)
    hf_expected = phone.replace(litertlm_declaration(tool), litertlm_declaration(hf_template_view(tool)))
    return to_phone_prompt(hf_prompt, system_prompt) == bos + hf_expected


def declarations_in(prompt):
    """The declaration blocks inside a rendered prompt."""
    return re.findall(re.escape(DECL_START) + r"(.*?)" + re.escape(DECL_END), prompt, re.S)


def is_alphabetical(value):
    """True when every JSON object has its keys in alphabetical order (what the training template produces)."""
    if isinstance(value, dict):
        return list(value) == sorted(value) and all(is_alphabetical(v) for v in value.values())
    if isinstance(value, list):
        return all(is_alphabetical(v) for v in value)
    return True


# ---------------------------------------------------------------- what the model says
_NUMBER = re.compile(r"-?\d+(?:\.\d+)?(?:[eE][-+]?\d+)?")
_LITERAL = re.compile(r"true|false|null")
_KEY = re.compile(r"[A-Za-z_]\w*")


def _parse_value(text, i):
    if text.startswith(ESC, i):
        end = text.find(ESC, i + len(ESC))
        if end < 0:
            raise ValueError("unterminated string")
        return text[i + len(ESC):end], end + len(ESC)
    if text.startswith("{", i):
        return _parse_object(text, i)
    if text.startswith("[", i):
        return _parse_array(text, i)
    m = _NUMBER.match(text, i)
    if m:
        return Decimal(m.group(0)), m.end()
    m = _LITERAL.match(text, i)
    if m:
        return {"true": True, "false": False, "null": None}[m.group(0)], m.end()
    raise ValueError(f"bad value at {i}: {text[i:i + 20]!r}")


def _parse_array(text, i):
    items, i = [], i + 1
    while True:
        if i >= len(text):
            raise ValueError("unterminated array")
        if text[i] == "]":
            return items, i + 1
        value, i = _parse_value(text, i)
        items.append(value)
        if i < len(text) and text[i] == ",":
            i += 1
        elif i >= len(text) or text[i] != "]":
            raise ValueError(f"expected ',' or ']' at {i}")


def _parse_object(text, i):
    out, i = {}, i + 1
    while True:
        if i >= len(text):
            raise ValueError("unterminated object")
        if text[i] == "}":
            return out, i + 1
        colon = text.find(":", i)
        if colon < 0:
            raise ValueError("missing ':'")
        key = text[i:colon].strip()
        if not _KEY.fullmatch(key):
            raise ValueError(f"bad key {key!r}")
        value, i = _parse_value(text, colon + 1)
        out[key] = value
        if i < len(text) and text[i] == ",":
            i += 1
        elif i >= len(text) or text[i] != "}":
            raise ValueError(f"expected ',' or '}}' at {i}")


def parse_calls(output):
    """[{"name": …, "arguments": {…}}] for every well-formed call in the model output. A call must be closed
    (`<end_function_call>`), and its argument object must be complete and the whole body; otherwise it's skipped."""
    calls = []
    for raw in re.findall(re.escape(CALL_START) + r"(.*?)" + re.escape(CALL_END), output, re.S):
        m = re.fullmatch(r"\s*call:([A-Za-z_][\w.-]*)(\{.*\})\s*", raw, re.S)
        if not m:
            continue
        try:
            args, end = _parse_object(m.group(2), 0)
        except ValueError:
            continue
        if end == len(m.group(2)):
            calls.append({"name": m.group(1), "arguments": args})
    return calls


# ---------------------------------------------------------------- scoring
REASON_STOPWORDS = {"the", "a", "an", "for", "of", "my", "our", "to", "his", "her", "their"}


def _decimal(value):
    try:
        return Decimal(str(value))
    except (InvalidOperation, ValueError, TypeError):
        return None


def _words(text):
    return re.findall(r"[^\W_]+", str(text or "").lower())


def _core(args):
    """What the app's mapper turns into a Transaction (a missing currency becomes CHF, like the app)."""
    currency = str(args.get("currency") or DEFAULT_CURRENCY).strip().upper()
    return (str(args.get("action", "")).strip().lower(), _decimal(args.get("amount")), currency,
            " ".join(_words(args.get("person"))))


def _reason(args):
    return [w for w in _words(args.get("description")) if w not in REASON_STOPWORDS]


def score_calls(target_args, calls):
    """target_args: the expected arguments, or None for "no call". calls: parsed calls, [{"name", "arguments"}].
    Returns {"core": bool, "exact": bool, "kind": str}. Like the app, only exactly one executeTransaction call counts."""
    if target_args is None:
        ok = not calls
        return {"core": ok, "exact": ok, "kind": "no_call_ok" if ok else "unexpected_call"}
    if not calls:
        return {"core": False, "exact": False, "kind": "missing_call"}
    if len(calls) > 1:
        return {"core": False, "exact": False, "kind": "several_calls"}
    if calls[0]["name"] != FUNCTION_NAME:
        return {"core": False, "exact": False, "kind": "wrong_function"}
    got = calls[0]["arguments"]
    core = _core(got) == _core(target_args)
    exact = core and _reason(got) == _reason(target_args)
    return {"core": core, "exact": exact, "kind": "ok" if core else "wrong_args"}


def score(target_args, output):
    """score_calls() on the calls in a raw model output."""
    return score_calls(target_args, parse_calls(output))


# ---------------------------------------------------------------- golden set (plan §10.1, G1–G10): typed, never trained on
# What the model alone should answer. On the phone, G1 and G2 never reach the model (the grammar answers), and the
# guard refuses G10 whatever the model says (two amounts).
GOLDEN_EXPECTED = [
    ("Request 10 euros from Maria for the taxi bill.",
     {"action": "request", "amount": 10, "currency": "EUR", "person": "Maria", "description": "the taxi bill"}),
    ("Send twenty five francs to Stefan.", {"action": "pay", "amount": 25, "currency": "CHF", "person": "Stefan"}),
    ("I owe Stefan ten francs for the pizza.",
     {"action": "pay", "amount": 10, "currency": "CHF", "person": "Stefan", "description": "pizza"}),
    ("Split 60 euros with Maria for dinner.",
     {"action": "split", "amount": 60, "currency": "EUR", "person": "Maria", "description": "dinner"}),
    ("Could you get 25 bucks from Tamara for lunch?",
     {"action": "request", "amount": 25, "currency": "USD", "person": "Tamara", "description": "lunch"}),
    ("I paid for the taxi, Maria owes me ten euros.",
     {"action": "request", "amount": 10, "currency": "EUR", "person": "Maria", "description": "taxi"}),
    ("Schick Stefan 10 Franken für die Pizza.",
     {"action": "pay", "amount": 10, "currency": "CHF", "person": "Stefan", "description": "Pizza"}),
    ("Remind me to call Stefan tomorrow.", None),
    ("Send some money to Stefan.", None),
    ("Transfer 100 francs to Stefan and 50 to Maria.", None),
]

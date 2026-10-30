"""Speak2Act × FunctionGemma — dataset helpers (pure Python, no GPU). Used by notebook 01 and tested locally.

Seeds are valid transactions; a teacher model writes sentences for them; this code checks every sentence and writes
the label from the seed — never from the teacher.
"""
import json
import random
import re
import unicodedata
from decimal import Decimal

ACTIONS = ["pay", "request", "split"]
DEFAULT_CURRENCY = "CHF"
# Spoken words per ISO code, for the teacher prompts.
CURRENCY_WORDS = {
    "CHF": ["francs", "Swiss francs", "CHF"],
    "EUR": ["euros", "EUR", "euro"],
    "USD": ["dollars", "bucks", "USD"],
    "GBP": ["pounds", "quid", "GBP"],
}
# Exactly the app's CurrencyCodes ("fr." is "fr" after punctuation is trimmed). A word it doesn't know — German
# "Franken", say — counts as no currency, so the app expects CHF.
WORD_TO_ISO = {
    "francs": "CHF", "franc": "CHF", "franks": "CHF", "chf": "CHF", "fr": "CHF", "swiss": "CHF", "rappen": "CHF",
    "euro": "EUR", "euros": "EUR", "eur": "EUR", "€": "EUR", "dollar": "USD", "dollars": "USD",
    "usd": "USD", "bucks": "USD", "$": "USD", "pound": "GBP", "pounds": "GBP", "gbp": "GBP", "quid": "GBP",
    "£": "GBP",
}
# Currencies the app doesn't support, in words that are never anything else (not "won", "real" or "rand") — the
# same list as CurrencyCodes.UNSUPPORTED_WORDS in the app (plan §8.3).
FOREIGN_CURRENCY_WORDS = {"yen", "yuan", "renminbi", "rupee", "rupees", "dinar", "dinars", "peso", "pesos", "ruble",
                          "rubles", "rouble", "roubles", "krona", "kronor", "krone", "kroner", "zloty", "zlotys",
                          "lira", "lire", "dirham", "dirhams", "baht", "forint", "forints", "shekel", "shekels",
                          "pfund"}
NAMES = ["Stefan", "Maria", "Marija", "Tamara", "Ana", "Luka", "Nikola", "Jelena", "Marko", "Milica", "Sara",
         "David", "Laura", "Noah", "Emma", "Lukas", "Mia", "Elias", "Lea", "Jonas", "Nina", "Ivan", "Petra", "Tom",
         "Julia", "Ben", "Chloé", "Mateo", "Sofia", "Omar", "Aisha", "Wei", "Yuki", "Priya", "Mehmet", "Zoe",
         "Filip", "Dragana", "Bojan", "Katarina", "Anna Weber", "Mark Smith", "Mom", "Dad"]
REASONS = ["pizza", "the taxi", "dinner", "lunch", "coffee", "the concert tickets", "rent", "groceries",
           "the birthday gift", "the train ticket", "drinks", "the hotel", "fuel", "the cinema", "the electricity bill",
           "breakfast", "the parking", "the football tickets", "the flowers", "the board game", "sushi", "brunch"]
NUMBER_WORDS = {
    "zero": 0, "one": 1, "two": 2, "three": 3, "four": 4, "five": 5, "six": 6, "seven": 7, "eight": 8, "nine": 9,
    "ten": 10, "eleven": 11, "twelve": 12, "thirteen": 13, "fourteen": 14, "fifteen": 15, "sixteen": 16,
    "seventeen": 17, "eighteen": 18, "nineteen": 19, "twenty": 20, "thirty": 30, "forty": 40, "fifty": 50,
    "sixty": 60, "seventy": 70, "eighty": 80, "ninety": 90, "hundred": 100, "thousand": 1000,
}
UNITS = {v: k for k, v in NUMBER_WORDS.items() if 0 < v < 20}
TENS = {v: k for k, v in NUMBER_WORDS.items() if 20 <= v < 100}
NO_CALL_REPLY = "Sorry, I can only prepare payments, requests and splits."


# ---------------------------------------------------------------- seeds
def random_amount(rng):
    kind = rng.random()
    if kind < 0.45:
        return rng.choice([5, 8, 10, 12, 15, 18, 20, 25, 30, 35, 40, 45, 50, 60, 75, 80, 90, 100])
    if kind < 0.7:
        return rng.randint(1, 300)
    if kind < 0.85:
        return round(rng.randint(100, 9999) / 100, 2)          # 1.00 – 99.99
    return rng.choice([120, 150, 200, 250, 300, 400, 500, 750, 1000, 1200, 1500, 2000])


def make_seeds(n, rng=None):
    """n valid transactions. currency None = not spoken (label CHF); description None = no reason spoken."""
    rng = rng or random.Random(7)
    seeds = []
    for i in range(n):
        seeds.append({
            "seed_id": i,
            "action": rng.choices(ACTIONS, weights=[0.45, 0.35, 0.2])[0],
            "amount": random_amount(rng),
            "currency": rng.choices([None, "CHF", "EUR", "USD", "GBP"], weights=[0.25, 0.3, 0.25, 0.12, 0.08])[0],
            "person": rng.choice(NAMES),
            "description": rng.choice(REASONS) if rng.random() < 0.7 else None,
        })
    return seeds


def label_for(seed):
    """The function-call arguments for a seed: exactly what the app's contract expects."""
    amount = seed["amount"]
    args = {"action": seed["action"],
            "amount": int(amount) if float(amount).is_integer() else amount,
            "currency": seed["currency"] or DEFAULT_CURRENCY,
            "person": seed["person"]}
    if seed["description"]:
        args["description"] = seed["description"]
    return args


# ---------------------------------------------------------------- teacher prompts
ACTION_MEANING = {
    "pay": "the user sends money to {person} (e.g. send, pay, transfer, give back, \"I owe {person} …\")",
    "request": "{person} should send money to the user (e.g. request, ask {person} for, get … from {person}, "
               "charge, \"{person} owes me …\")",
    "split": "the user shares a cost with {person} (e.g. split, share, go halves, \"half of … with {person}\")",
}


def amount_text(amount):
    return str(int(amount)) if float(amount).is_integer() else f"{amount:.2f}"


def teacher_prompt(seed, n=5, language="English"):
    person = seed["person"]
    parts = [
        f"Write {n} different ways a person might SAY this to a payments app on their phone, in {language}.",
        f"Meaning: {ACTION_MEANING[seed['action']].format(person=person)}.",
        f"Amount: {amount_text(seed['amount'])} (write it as digits, or as words if it is a whole number).",
        f"Person: {person} (use exactly this name).",
        (f"Currency: {seed['currency']} (say it naturally, e.g. {', '.join(CURRENCY_WORDS[seed['currency']][:2])})."
         if seed["currency"] else "Do NOT mention any currency."),
        (f"Reason: {seed['description']} (mention it)." if seed["description"] else "Do NOT mention a reason."),
        "Vary the wording, length and politeness a lot: some casual, some short, some with filler words.",
        "Return ONLY a JSON array of strings, nothing else.",
    ]
    return "\n".join(parts)


NEGATIVE_PROMPT = (
    "Write {n} short things a person might say to their phone that are NOT commands to pay, request or split money: "
    "reminders, questions, small talk, and near-misses that mention money or people but don't ask for a transaction "
    "(e.g. \"How much did I pay Stefan last week?\", \"Maria owes me a coffee, haha\"). Use names like {names}. "
    "Return ONLY a JSON array of strings, nothing else."
)


def parse_teacher_output(text):
    """Extracts the JSON array of strings from a teacher reply; [] when there is none."""
    m = re.search(r"\[.*\]", text, re.S)
    if not m:
        return []
    try:
        items = json.loads(m.group(0))
    except json.JSONDecodeError:
        return []
    return [s.strip() for s in items if isinstance(s, str) and s.strip()]


# ---------------------------------------------------------------- grounding: a port of the app's guard (plan §8.3)
# Same tokens, number reading and rules as VoiceTransactionParser.spokenAmounts()/spokenCurrency() and ProposalGuard,
# so the data never teaches what the phone would reject.
_PUNCTUATION = '.,!?;:"\u00bb\u00ab'
_SENTENCE_BREAKS = set(",.;!?")
_NUMERIC_AMOUNT = re.compile(r"[0-9]+(?:\.[0-9]{1,2})?")
_THOUSANDS_SEPARATOR = re.compile(r"(?<=[0-9])[,'\u2019 \u00a0](?=[0-9]{3}(?![0-9]))")
_SYMBOL_BEFORE_AMOUNT = re.compile(r"([€$£])([0-9])")
_SYMBOL_AFTER_AMOUNT = re.compile(r"([0-9])([€$£])")
_CODE_BEFORE_AMOUNT = re.compile(r"(?i)\b(chf|eur|usd|gbp)([0-9])")
_JAVA_WHITESPACE = re.compile(r"[ \t\n\x0b\f\r]+")
_SCALE_WORDS = {"hundred", "thousand"}
MAX_SPOKEN_AMOUNT = 9999


def words(text):
    """Lower-case, accent-free words of letters and digits — like the guard: "Stefan's" → [stefan, s]."""
    text = unicodedata.normalize("NFD", text.lower())
    text = "".join(ch for ch in text if unicodedata.category(ch) != "Mn")
    return [w for w in re.split(r"[\W_]+", text) if w]


def _tokenize(text):
    text = _THOUSANDS_SEPARATOR.sub("", text)
    text = _SYMBOL_BEFORE_AMOUNT.sub(r"\1 \2", text)
    text = _SYMBOL_AFTER_AMOUNT.sub(r"\1 \2", text)
    text = _CODE_BEFORE_AMOUNT.sub(r"\1 \2", text)
    return [t.strip() for t in _JAVA_WHITESPACE.split(text) if t.strip()]


def _cleaned(token):
    return token.strip(_PUNCTUATION).lower()


def _has_digit(token):
    return any(unicodedata.category(ch) == "Nd" for ch in token)


def _numeric_amount(token):
    normalized = _cleaned(token).replace(",", ".")
    return Decimal(normalized) if _NUMERIC_AMOUNT.fullmatch(normalized) else None


def _number_words(token):
    parts = [p for p in _cleaned(token).split("-") if p]
    return parts if parts and all(p in NUMBER_WORDS for p in parts) else None


def _evaluate_number_words(ws):
    """English number words up to 9,999, or None for sequences that aren't a well-formed number ("five six")."""
    total = current = 0
    previous = None
    seen_thousand = False
    for word in ws:
        value = NUMBER_WORDS[word]
        previous_value = NUMBER_WORDS[previous] if previous is not None else None
        after_scale = previous is None or previous in _SCALE_WORDS
        if word == "thousand":
            if seen_thousand or current > 99:
                return None
            total += (current if current else 1) * 1000
            current = 0
            seen_thousand = True
        elif word == "hundred":
            if previous == "hundred" or current > 19:
                return None
            current = (current if current else 1) * 100
        elif value == 0:
            if previous is not None:
                return None
        elif value < 10:
            after_tens = previous_value is not None and 20 <= previous_value <= 90
            if not after_scale and not after_tens:
                return None
            current += value
        else:
            if not after_scale:
                return None
            current += value
        previous = word
    result = total + current
    return Decimal(result) if result <= MAX_SPOKEN_AMOUNT else None


def _number_word_group(tokens, start):
    """(value or None, end index) for the run of number words at start; None if tokens[start] isn't one."""
    if _number_words(tokens[start]) is None:
        return None
    ws, index = [], start
    while index < len(tokens):
        parts = _number_words(tokens[index])
        if parts is not None:
            ws += parts
            index += 1
            if tokens[index - 1].rstrip()[-1:] in _SENTENCE_BREAKS:   # "ten, to Stefan"
                break
            continue
        is_connector = (_cleaned(tokens[index]) == "and" and ws and ws[-1] in _SCALE_WORDS
                        and index + 1 < len(tokens) and _number_words(tokens[index + 1]) is not None)
        if is_connector:
            index += 1
            continue
        break
    return _evaluate_number_words(ws), index


def spoken_amounts(text):
    """Every amount anywhere in the sentence, in order, one entry per occurrence ("10 to Ana and 10 to Bo" → two) —
    digits ("12.50", "12,50", "1'000") or English number words — as Decimals without trailing zeros. Unreadable
    numbers are skipped."""
    tokens = _tokenize(text)
    amounts, index = [], 0
    while index < len(tokens):
        token = tokens[index]
        if _has_digit(token):
            value = _numeric_amount(token)
            if value is not None:
                amounts.append(value.normalize())
            index += 1
            continue
        group = _number_word_group(tokens, index)
        if group is not None:
            value, index = group
            if value is not None:
                amounts.append(value.normalize())
            continue
        index += 1
    return amounts


def spoken_currencies(text):
    """ISO codes of every currency word or symbol in the sentence ("francs" → CHF, "€" → EUR)."""
    return {WORD_TO_ISO[_cleaned(t)] for t in _tokenize(text) if _cleaned(t) in WORD_TO_ISO}


def mentions_unsupported_currency(text):
    """Whether the sentence names a currency the app doesn't support ("yen")."""
    return any(_cleaned(t) in FOREIGN_CURRENCY_WORDS for t in _tokenize(text))


def grounded(sentence, args):
    """True when the app's guard would accept this label for this sentence: exactly one amount (counting every
    occurrence) and it's the label's; no unsupported currency, at most one currency, and it's the label's (CHF when
    none was said); and every word of the person's name."""
    amounts = spoken_amounts(sentence)
    if len(amounts) != 1 or amounts[0] != Decimal(str(args["amount"])):
        return False
    name = set(words(args["person"]))
    if not name or not name <= set(words(sentence)):
        return False
    currencies = spoken_currencies(sentence)
    if mentions_unsupported_currency(sentence) or len(currencies) > 1:
        return False
    return next(iter(currencies), DEFAULT_CURRENCY) == args["currency"]


# ---------------------------------------------------------------- Whisper-style noise
def to_words(n):
    """English words for whole numbers 1–999 ("twenty-five"); None otherwise."""
    if not float(n).is_integer() or not 0 < int(n) < 1000:
        return None
    n = int(n)
    out = []
    if n >= 100:
        out += [UNITS[n // 100], "hundred"]
        n %= 100
    if n >= 20:
        out.append(TENS[n // 10 * 10] + (f"-{UNITS[n % 10]}" if n % 10 else ""))
    elif n:
        out.append(UNITS[n])
    return " ".join(out)


def whisper_noise(sentence, rng):
    """Variants Whisper produces on a phone: lower case, no punctuation, digits vs words."""
    s = sentence
    if rng.random() < 0.35:
        s = s.lower()
    if rng.random() < 0.35:
        s = re.sub(r"[.,!?;:](?![0-9])|(?<![0-9])[.,]", "", s)   # keeps "12.50"
    if rng.random() < 0.3:
        s = re.sub(r"(?<![\d.,])\b\d{1,3}\b(?![.,]\d)", lambda m: to_words(int(m.group(0))) or m.group(0), s, count=1)
    return s


# ---------------------------------------------------------------- golden set (never in training data)
GOLDEN = [
    "Request 10 euros from Maria for the taxi bill.",
    "Send twenty five francs to Stefan.",
    "I owe Stefan ten francs for the pizza.",
    "Split 60 euros with Maria for dinner.",
    "Could you get 25 bucks from Tamara for lunch?",
    "I paid for the taxi, Maria owes me ten euros.",
    "Schick Stefan 10 Franken für die Pizza.",
    "Remind me to call Stefan tomorrow.",
    "Send some money to Stefan.",
    "Transfer 100 francs to Stefan and 50 to Maria.",
]


def norm(s):
    return " ".join(words(s))


GOLDEN_NORM = {norm(s) for s in GOLDEN}


def is_golden(sentence):
    return norm(sentence) in GOLDEN_NORM


# ---------------------------------------------------------------- splitting
def split_by_seed(rows, eval_fraction=0.1, rng=None):
    """Train/eval split by seed id, so paraphrases of one seed never land on both sides."""
    rng = rng or random.Random(11)
    ids = sorted({r["seed_id"] for r in rows})
    rng.shuffle(ids)
    cut = set(ids[: max(1, int(len(ids) * eval_fraction))])
    return [r for r in rows if r["seed_id"] not in cut], [r for r in rows if r["seed_id"] in cut]


# ---------------------------------------------------------------- "not enough to act on" (no call expected)
INCOMPLETE_TEMPLATES = [
    "Send money to {person}.", "Pay {person} back.", "Request money from {person}.", "Split the bill with {person}.",
    "Can you transfer something to {person}?", "I need to pay {amount} francs.", "Request {amount} euros.",
    "Send {amount} to... wait, never mind.", "Ask {person} for money.", "Split it.", "Pay the rest.",
    # Two transactions in one sentence: the app takes one per command, and the guard refuses two amounts — also
    # when they're equal.
    "Send {amount} to {person} and {amount2} to {person2}.", "Pay {person} {amount} euros and {person2} {amount2}.",
    "I owe {person} {amount} francs and {person2} {amount} francs.",
]


def incomplete_commands(n, rng=None):
    """Commands the app can't act on — the amount or the person is missing, or there are two transactions: the
    model must not call the function."""
    rng = rng or random.Random(5)
    out = []
    for _ in range(n):
        t = rng.choice(INCOMPLETE_TEMPLATES)
        person, person2 = rng.sample(NAMES, 2)
        amount, amount2 = amount_text(random_amount(rng)), amount_text(random_amount(rng))
        out.append(t.format(person=person, person2=person2, amount=amount, amount2=amount2))
    return out


# ---------------------------------------------------------------- training rows (FunctionGemma / TRL format)
def to_row(sentence, args, split, tool, system_prompt):
    """One example: developer prompt + user sentence + either the call (args) or a short refusal (args=None)."""
    if args is None:
        answer = {"role": "assistant", "content": NO_CALL_REPLY}
    else:
        answer = {"role": "assistant",
                  "tool_calls": [{"type": "function", "function": {"name": tool["name"], "arguments": args}}]}
    return {"metadata": split,
            "tools": [{"type": "function", "function": tool}],
            "messages": [{"role": "developer", "content": system_prompt},
                         {"role": "user", "content": sentence},
                         answer]}


# ---------------------------------------------------------------- teacher as judge: is the direction right?
def action_check_prompt(sentences):
    """Asks the teacher which action each sentence means. Code can check amounts and names, not the direction."""
    numbered = "\n".join(f"{i + 1}. {s}" for i, s in enumerate(sentences))
    return ("For each numbered sentence, say what the speaker wants a payments app to do:\n"
            "\"pay\" = the speaker sends money to the person; \"request\" = the person should send money to the "
            "speaker; \"split\" = share a cost with the person.\n"
            f"Answer ONLY with a JSON array of {len(sentences)} strings, one per sentence, in order.\n\n{numbered}")


def parse_actions(text, n):
    """The judged actions, or None when the reply isn't a JSON array of n valid actions."""
    items = parse_teacher_output(text)
    items = [s.lower().strip(" .\"'") for s in items]
    if len(items) != n or any(a not in ACTIONS for a in items):
        return None
    return items


REASON_STOPWORDS = {"the", "a", "an", "for", "of", "my", "our", "to", "his", "her", "their"}


def ground_description(sentence, args):
    """The label for this sentence, with the reason as the sentence says it. The app's guard keeps a reason only if
    all its words were said: "the taxi" becomes "taxi" for "…for taxi", and no reason at all if "taxi" wasn't said."""
    reason = args.get("description")
    if not reason:
        return args
    said = set(words(sentence))
    content = [w for w in words(reason) if w not in REASON_STOPWORDS]
    if not content or not all(w in said for w in content):
        return {k: v for k, v in args.items() if k != "description"}
    kept = " ".join(t for t in reason.split() if all(w in said for w in words(t)))
    return args if kept == reason else dict(args, description=kept)


# ---------------------------------------------------------------- teacher as judge: is a "negative" really one?
def command_check_prompt(sentences):
    """Asks the teacher whether each sentence is a complete payment command — to drop "negatives" that aren't."""
    numbered = "\n".join(f"{i + 1}. {s}" for i, s in enumerate(sentences))
    return ("For each numbered sentence, answer \"yes\" if it is a complete instruction to a payments app to pay, "
            "request or split one specific amount with one specific person, and \"no\" otherwise (questions, "
            "reminders, small talk, missing amount or person).\n"
            f"Answer ONLY with a JSON array of {len(sentences)} strings, one per sentence, in order.\n\n{numbered}")


def parse_yes_no(text, n):
    """[True for "yes", False for "no"], or None when the reply isn't a JSON array of n answers."""
    items = [s.lower().strip(" .\"'") for s in parse_teacher_output(text)]
    if len(items) != n or any(a not in ("yes", "no") for a in items):
        return None
    return [a == "yes" for a in items]

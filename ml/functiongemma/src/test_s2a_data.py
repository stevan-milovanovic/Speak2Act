import random, unittest
from decimal import Decimal
import s2a_data as d

class T(unittest.TestCase):
    def test_amounts(self):
        D = Decimal
        self.assertEqual(d.spoken_amounts("I owe Stefan ten francs for the pizza."), [D(10)])
        self.assertEqual(d.spoken_amounts("Send twenty-five francs to Stefan"), [D(25)])
        self.assertEqual(d.spoken_amounts("send twenty five to ana"), [D(25)])
        self.assertEqual(d.spoken_amounts("Pay 12.50 for coffee"), [D("12.5")])
        self.assertEqual(d.spoken_amounts("Pay 12,50"), [D("12.5")])
        self.assertEqual(d.spoken_amounts("Send 1'000 to Ana and 20 to Bo"), [D(1000), D(20)])
        self.assertEqual(d.spoken_amounts("Send 10 to Ana and 10 to Bo"), [D(10), D(10)])      # every occurrence
        self.assertEqual(d.spoken_amounts("two hundred and fifty bucks"), [D(250)])
        self.assertEqual(d.spoken_amounts("one thousand five hundred francs"), [D(1500)])
        self.assertEqual(d.spoken_amounts("Remind me to call Stefan"), [])
        self.assertEqual(d.spoken_amounts("Send ten, to Stefan"), [D(10)])
        self.assertEqual(d.spoken_amounts("€5 to Ana"), [D(5)])

    def test_amounts_fail_closed_like_the_kotlin_parser(self):
        # VoiceTransactionParser.evaluateNumberWords / numericAmount reject these (plan §8.3 reuses them).
        self.assertEqual(d.spoken_amounts("five six francs"), [])
        self.assertEqual(d.spoken_amounts("twenty twenty"), [])
        self.assertEqual(d.spoken_amounts("abc123"), [])
        self.assertEqual(d.spoken_amounts("pay 12.501 francs"), [])
        self.assertEqual(d.spoken_amounts("ten thousand francs"), [])          # over 9,999
        self.assertEqual(d.spoken_amounts("twenty and five"), [Decimal(20), Decimal(5)])
        self.assertEqual(d.spoken_amounts("zero"), [Decimal(0)])

    def test_currency(self):
        self.assertEqual(d.spoken_currencies("25 bucks from Tamara"), {"USD"})
        self.assertEqual(d.spoken_currencies("10 Franken für die Pizza"), set())          # unknown to the app → none
        self.assertEqual(d.spoken_currencies("Pay Ana 10 fr."), {"CHF"})
        self.assertEqual(d.spoken_currencies("€5 to Ana"), {"EUR"})
        self.assertEqual(d.spoken_currencies("send 5 to Ana"), set())
        self.assertEqual(d.spoken_currencies("Send 10 euros or francs to Ana"), {"EUR", "CHF"})
        self.assertTrue(d.mentions_unsupported_currency("Request 35 yen from David."))
        self.assertFalse(d.mentions_unsupported_currency("I won 35 francs"))

    def test_grounded(self):
        args = {"action": "pay", "amount": 10, "currency": "CHF", "person": "Stefan", "description": "pizza"}
        self.assertTrue(d.grounded("I owe Stefan ten francs for the pizza.", args))
        self.assertTrue(d.grounded("i owe stefan 10 for the pizza", args))          # no currency → CHF default
        self.assertFalse(d.grounded("I owe Stefan ten euros for the pizza.", args))  # currency mismatch
        self.assertFalse(d.grounded("I owe Ana ten francs", args))                   # person missing
        self.assertFalse(d.grounded("I owe Stefan francs", args))                    # amount missing
        self.assertTrue(d.grounded("Pay Chloe 12.50", {"amount": 12.5, "currency": "CHF", "person": "Chloé"}))
        self.assertTrue(d.grounded("Give Anna Weber's share, 20 euros", {"amount": 20, "currency": "EUR", "person": "Anna Weber"}))
        self.assertTrue(d.grounded("Schick Stefan 10 Franken für die Pizza.", args))   # no known currency → CHF
        self.assertFalse(d.grounded("Send 1'000 to Ana and 20 to Bo", {"amount": 1000, "currency": "CHF", "person": "Ana"}))
        self.assertFalse(d.grounded("Split 60 euros with Maria and 2 friends", {"amount": 60, "currency": "EUR", "person": "Maria"}))
        self.assertTrue(d.grounded("Pay Ana 12.3", {"amount": 12.3, "currency": "CHF", "person": "Ana"}))
        self.assertFalse(d.grounded("I owe Ana 10 francs and Bo 10 francs", {"amount": 10, "currency": "CHF", "person": "Ana"}))
        self.assertFalse(d.grounded("Send 10 euros or francs to Ana", {"amount": 10, "currency": "EUR", "person": "Ana"}))
        self.assertFalse(d.grounded("Pay 10 francs", {"amount": 10, "currency": "CHF", "person": ""}))
        self.assertFalse(d.grounded("Request 35 yen from David.", {"amount": 35, "currency": "CHF", "person": "David"}))
        self.assertFalse(d.grounded("Schick Stefan 10 Pfund", {"amount": 10, "currency": "CHF", "person": "Stefan"}))
    def test_words_and_noise(self):
        self.assertEqual(d.to_words(25), "twenty-five")
        self.assertEqual(d.to_words(305), "three hundred five")
        self.assertIsNone(d.to_words(12.5))
        rng = random.Random(1)
        for _ in range(200):
            s = d.whisper_noise("Send 25 francs to Stefan for 12.50 coffee.", rng)
            self.assertIn("12.50", s)                     # punctuation goes, decimal points stay
    def test_seeds_and_labels(self):
        seeds = d.make_seeds(500)
        self.assertEqual(len({s["seed_id"] for s in seeds}), 500)
        for s in seeds:
            a = d.label_for(s)
            self.assertIn(a["action"], d.ACTIONS)
            self.assertIn(a["currency"], ["CHF", "EUR", "USD", "GBP"])
            self.assertGreater(a["amount"], 0)
            if s["description"] is None: self.assertNotIn("description", a)
            p = d.teacher_prompt(s)
            self.assertIn(s["person"], p)
    def test_parse_teacher(self):
        self.assertEqual(d.parse_teacher_output('Sure!\n```json\n["a", "b "]\n```'), ["a", "b"])
        self.assertEqual(d.parse_teacher_output("no json"), [])
    def test_golden(self):
        self.assertTrue(d.is_golden("i owe stefan ten francs for the pizza"))
        self.assertFalse(d.is_golden("I owe Stefan eleven francs for the pizza"))
    def test_split(self):
        rows = [{"seed_id": i // 3} for i in range(300)]
        tr, ev = d.split_by_seed(rows, 0.1)
        self.assertFalse({r["seed_id"] for r in tr} & {r["seed_id"] for r in ev})
        self.assertEqual(len(tr) + len(ev), 300)

class T4(unittest.TestCase):
    def test_ground_description(self):
        a = {"action": "pay", "amount": 10, "currency": "CHF", "person": "Stefan", "description": "the concert tickets"}
        self.assertEqual(d.ground_description("Pay Stefan 10 for the concert tickets", a), a)
        self.assertNotIn("description", d.ground_description("Pay Stefan 10 francs", a))
        self.assertNotIn("description", d.ground_description("Zahl Stefan 10 für die Konzertkarten", a))
        self.assertEqual(d.ground_description("Pay Stefan 10 for concert tickets", a)["description"], "concert tickets")
        b = {"action": "pay", "amount": 10, "currency": "CHF", "person": "Stefan"}
        self.assertEqual(d.ground_description("Pay Stefan 10", b), b)


class T3(unittest.TestCase):
    def test_action_judge(self):
        p = d.action_check_prompt(["I owe Ana 5 francs", "Get 10 from Bo"])
        self.assertIn("1. I owe Ana 5 francs", p)
        self.assertIn("2. Get 10 from Bo", p)
        self.assertEqual(d.parse_actions('["pay", "Request"]', 2), ["pay", "request"])
        self.assertIsNone(d.parse_actions('["pay"]', 2))
        self.assertIsNone(d.parse_actions('["pay", "refund"]', 2))
        self.assertIsNone(d.parse_actions('no json', 2))

    def test_command_judge(self):
        p = d.command_check_prompt(["How much did I pay Ana?", "Send Bo 5 francs"])
        self.assertIn("2. Send Bo 5 francs", p)
        self.assertEqual(d.parse_yes_no('["no", "Yes"]', 2), [False, True])
        self.assertIsNone(d.parse_yes_no('["no"]', 2))
        self.assertIsNone(d.parse_yes_no('["no", "maybe"]', 2))


class T2(unittest.TestCase):
    def test_incomplete_and_rows(self):
        import json
        cmds = d.incomplete_commands(50)
        self.assertEqual(len(cmds), 50)
        for c in cmds:
            self.assertFalse(d.is_golden(c))
            self.assertNotIn("{", c)
        self.assertTrue(any(" and " in c for c in d.incomplete_commands(200)))
        same = [c for c in d.incomplete_commands(300) if c.startswith("I owe")]
        self.assertTrue(same and all(len(d.spoken_amounts(c)) == 2 and len(set(d.spoken_amounts(c))) == 1 for c in same))
        tool = json.load(open("execute_transaction.json"))
        row = d.to_row("I owe Ana 5 francs", {"action": "pay", "amount": 5, "currency": "CHF", "person": "Ana"},
                       "train", tool, "SYS")
        self.assertEqual(row["messages"][2]["tool_calls"][0]["function"]["name"], "executeTransaction")
        self.assertEqual(row["tools"][0]["function"], tool)
        neg = d.to_row("Remind me", None, "eval", tool, "SYS")
        self.assertEqual(neg["messages"][2]["content"], d.NO_CALL_REPLY)
        self.assertNotIn("tool_calls", neg["messages"][2])
        json.dumps(row); json.dumps(neg)


if __name__ == "__main__":
    unittest.main(verbosity=1)

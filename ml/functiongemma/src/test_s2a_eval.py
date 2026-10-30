import json, unittest
import s2a_eval as e

TOOL = json.load(open("execute_transaction.json"))
E = e.ESC

class T(unittest.TestCase):
    def test_tool_is_alphabetical(self):
        self.assertTrue(e.is_alphabetical(TOOL))
        self.assertFalse(e.is_alphabetical({"name": 1, "description": 2}))

    def test_declaration_like_litertlm(self):
        d = e.litertlm_declaration(TOOL)
        self.assertTrue(d.startswith("declaration:executeTransaction{description:" + E + "Prepares a money transaction"))
        self.assertIn("action:{description:" + E + "Type of the transaction." + E + ",enum:[" + E + "pay" + E + "," + E + "request" + E + "," + E + "split" + E + "],type:" + E + "STRING" + E + "}", d)
        self.assertIn("amount:{description:" + E + "Amount of the transaction." + E + ",type:" + E + "NUMBER" + E + "}", d)
        self.assertTrue(d.endswith("required:[" + E + "action" + E + "," + E + "amount" + E + "," + E + "currency" + E + "," + E + "person" + E + "],type:" + E + "OBJECT" + E + "}}"))
        self.assertNotIn('"', d)
        # The HF template output from Google's generic notebook has exactly this shape (sorted keys, upper-cased types):
        google = ("declaration:search_google{description:" + E + "Search public information." + E + ",parameters:{properties:{query:{description:" + E + "query string" + E + ",type:" + E + "STRING" + E + "}},required:[" + E + "query" + E + "],type:" + E + "OBJECT" + E + "}}")
        tool = {"description": "Search public information.", "name": "search_google",
                "parameters": {"properties": {"query": {"description": "query string", "type": "string"}}, "required": ["query"], "type": "object"}}
        self.assertEqual(e.litertlm_declaration(tool), google)

    def test_declarations_in(self):
        p = "x" + e.DECL_START + "declaration:a{}" + e.DECL_END + e.DECL_START + "declaration:b{}" + e.DECL_END
        self.assertEqual(e.declarations_in(p), ["declaration:a{}", "declaration:b{}"])

    def test_parse_calls(self):
        out = (e.CALL_START + "call:executeTransaction{action:" + E + "pay" + E + ",amount:10,currency:" + E + "CHF" + E
               + ",description:" + E + "pizza, drinks: all" + E + ",person:" + E + "Stefan" + E + "}" + e.CALL_END + "<start_function_response>")
        calls = e.parse_calls(out)
        self.assertEqual(calls, [{"name": "executeTransaction", "arguments": {"action": "pay", "amount": 10, "currency": "CHF", "description": "pizza, drinks: all", "person": "Stefan"}}])
        self.assertEqual(e.parse_calls(e.CALL_START + "call:x{amount:12.5,ok:true,n:null}" + e.CALL_END)[0]["arguments"], {"amount": 12.5, "ok": True, "n": None})
        self.assertEqual(e.parse_calls("Sorry, I can only prepare payments."), [])
        self.assertEqual(e.parse_calls(e.CALL_START + "call:x{amount:" + e.CALL_END), [])   # malformed → ignored

    def test_score(self):
        target = {"action": "pay", "amount": 10, "currency": "CHF", "person": "Stefan", "description": "pizza"}
        good = e.CALL_START + "call:executeTransaction{action:" + E + "pay" + E + ",amount:10.0,currency:" + E + "chf" + E + ",description:" + E + "Pizza" + E + ",person:" + E + "stefan" + E + "}" + e.CALL_END
        self.assertEqual(e.score(target, good), {"core": True, "exact": True, "kind": "ok"})
        wrong = good.replace("pay", "request")
        self.assertEqual(e.score(target, wrong)["kind"], "wrong_args")
        self.assertEqual(e.score(target, "no call")["kind"], "missing_call")
        self.assertEqual(e.score(None, "Sorry, I can only prepare payments.")["kind"], "no_call_ok")
        self.assertEqual(e.score(None, good)["kind"], "unexpected_call")

    def test_golden_matches_data_module(self):
        import s2a_data as d
        self.assertEqual([s for s, _ in e.GOLDEN_EXPECTED], d.GOLDEN)      # G1–G10, same order as plan §10.1
        for sentence, target in e.GOLDEN_EXPECTED:                       # every expected call is what the guard accepts
            if target is not None:
                self.assertTrue(d.grounded(sentence, target), sentence)
                self.assertEqual(d.ground_description(sentence, target), target, sentence)

    def test_phone_prompt(self):
        sys_prompt = e.SYSTEM_PROMPT
        phone = e.litertlm_prompt(sys_prompt, TOOL, " I owe Stefan ten francs ")
        self.assertTrue(phone.startswith("<start_of_turn>developer\n" + sys_prompt + "\n\n" + e.DECL_START + "declaration:executeTransaction{"))
        self.assertTrue(phone.endswith(e.DECL_END + "<end_of_turn>\n<start_of_turn>user\nI owe Stefan ten francs<end_of_turn>\n<start_of_turn>model\n"))
        # What the Hugging Face template renders (no blank line), from Google's notebook output shape:
        hf = ("<bos><start_of_turn>developer\n" + sys_prompt + e.DECL_START + e.litertlm_declaration(TOOL) + e.DECL_END
              + "<end_of_turn>\n<start_of_turn>user\nI owe Stefan ten francs<end_of_turn>\n<start_of_turn>model\n")
        self.assertEqual(e.to_phone_prompt(hf, sys_prompt), "<bos>" + phone)
        with self.assertRaises(ValueError):
            e.to_phone_prompt("<start_of_turn>user\nhi<end_of_turn>\n", sys_prompt)
        with self.assertRaises(ValueError):
            e.to_phone_prompt(e.to_phone_prompt(hf, sys_prompt), sys_prompt)    # never applied twice

    def test_hf_template_view(self):
        view = e.hf_template_view(TOOL)
        self.assertNotIn("description", view["parameters"]["properties"])          # the parameter goes...
        self.assertEqual(view["description"], TOOL["description"])                  # ...the tool's description stays
        self.assertEqual(set(view["parameters"]["properties"]), {"action", "amount", "currency", "person"})
        self.assertEqual(view["parameters"]["required"], TOOL["parameters"]["required"])
        self.assertIn("description", TOOL["parameters"]["properties"])              # deep copy: TOOL untouched
        nested = {"name": "f", "parameters": {"type": "object", "properties": {
            "type": {"type": "string"}, "o": {"type": "object", "properties": {"nullable": {"type": "string"}, "x": {"type": "string"}}}}}}
        v = e.hf_template_view(nested)["parameters"]["properties"]
        self.assertEqual(list(v), ["o"]); self.assertEqual(list(v["o"]["properties"]), ["x"])

    def test_hf_matches_phone(self):
        sys_prompt, sentence = e.SYSTEM_PROMPT, "I owe Stefan ten francs"
        turn = "<end_of_turn>\n<start_of_turn>user\n" + sentence + "<end_of_turn>\n<start_of_turn>model\n"
        # What the user saw in Colab: the HF rendering declares action, amount, currency, person — no description.
        hf = ("<bos><start_of_turn>developer\n" + sys_prompt + e.DECL_START
              + e.litertlm_declaration(e.hf_template_view(TOOL)) + e.DECL_END + turn)
        self.assertNotIn("description:{description:", hf)
        self.assertTrue(e.hf_matches_phone(hf, sys_prompt, TOOL, sentence, bos="<bos>"))
        self.assertIn("description:{description:", e.litertlm_prompt(sys_prompt, TOOL, sentence))   # the phone has it
        full = hf.replace(e.litertlm_declaration(e.hf_template_view(TOOL)), e.litertlm_declaration(TOOL))
        self.assertFalse(e.hf_matches_phone(full, sys_prompt, TOOL, sentence, bos="<bos>"))
        self.assertFalse(e.hf_matches_phone(hf.replace("ten", "11"), sys_prompt, TOOL, sentence, bos="<bos>"))

    def test_strict_calls(self):
        call = lambda body: e.CALL_START + body + e.CALL_END
        target = {"action": "pay", "amount": 12.5, "currency": "CHF", "person": "Ana"}
        ok = "call:executeTransaction{action:" + E + "pay" + E + ",amount:12.50,currency:" + E + "CHF" + E + ",person:" + E + "Ana" + E + "}"
        self.assertEqual(e.score(target, call(ok))["kind"], "ok")
        self.assertEqual(e.score(target, call(ok.replace("12.50", "12.501")))["kind"], "wrong_args")
        self.assertEqual(e.score(target, call(ok[:-1]))["kind"], "missing_call")          # unclosed object
        self.assertEqual(e.score(target, call(ok + "x"))["kind"], "missing_call")         # trailing text
        self.assertEqual(e.score(target, call(ok) + call(ok))["kind"], "several_calls")   # the app takes exactly one
        self.assertEqual(e.score(target, call(ok.replace("executeTransaction", "pay")))["kind"], "wrong_function")
        no_currency = ok.replace(",currency:" + E + "CHF" + E, "")
        self.assertEqual(e.score(target, call(no_currency))["kind"], "ok")                # missing → CHF, like the mapper
        self.assertEqual(e.score_calls(target, [{"name": "executeTransaction", "arguments": dict(target, amount=12.5)}])["kind"], "ok")
        reason = dict(target, description="the taxi")
        got = ok.replace(",person:", ",description:" + E + "Taxi" + E + ",person:")
        self.assertTrue(e.score(reason, call(got))["exact"])                              # "the taxi" ~ "Taxi"

if __name__ == "__main__":
    unittest.main(verbosity=1)

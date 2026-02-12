import unittest
from app.validators.cefr_a1_checker import A1ConstraintChecker

class TestA1Checker(unittest.TestCase):
    def setUp(self):
        self.checker = A1ConstraintChecker()
        
    def test_simple_present(self):
        result = self.checker.check("Ich gehe nach Hause.")
        self.assertEqual(len(result), 0)
        
    def test_perfekt_tense(self):
        # "habe" + "gemacht" -> Perfekt
        result = self.checker.check("Ich habe das gemacht.")
        self.assertTrue(any("Perfekt" in e for e in result))

    def test_perfekt_inseparable(self):
        # "hat" + "bezahlt" (inseparable prefix be-)
        result = self.checker.check("Er hat die Rechnung bezahlt.")
        self.assertTrue(any("Perfekt" in e for e in result))
        
    def test_perfekt_false_positive_noun(self):
        # "Geschäft" looks like participle (ge- ... -t/en)
        # But heuristic likely flags it if Auxiliary present.
        # "Ich habe ein Geschäft." -> A1 checker might flag.
        result = self.checker.check("Ich habe ein Geschäft.")
        print('A1Checker Result for Geschäft (expect empty):', result)
        # We WANT this to pass (not be flagged), so assert empty errors
        self.assertEqual(len(result), 0, "Checker incorrectly flagged 'Geschäft' as Perfekt.") 
        
    def test_sentence_length(self):
        long_sentence = "Dies ist ein sehr langer Satz, der mehr als zwölf Wörter enthält und deshalb für A1 nicht geeignet ist."
        result = self.checker.check(long_sentence)
        self.assertTrue(any("too long" in e for e in result))
        
    def test_subordinate_clause(self):
        result = self.checker.check("Ich komme nicht, weil ich krank bin.")
        self.assertTrue(any("Subordinate" in e for e in result))

if __name__ == '__main__':
    unittest.main()

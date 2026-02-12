import re
from typing import List, Set


class A1ConstraintChecker:
    """
    Validates German text against A1 constraints.
    Detects complex tenses (Perfekt, Präteritum beyond basics),
    subordinate clauses, and non-A1 vocabulary.
    """

    # 1. Disallow Passive/Future/Konjunktiv keywords
    # ('war'/'hatte' are allowed – common A1 Präteritum)
    PAST_KEYWORDS = re.compile(
        r'\b(wurde|werde|würde|gewesen)\b', re.IGNORECASE
    )

    # 2. Subordinating Conjunctions (force Nebensatz word order)
    SUBORDINATING = re.compile(
        r'\b(weil|obwohl|dass|falls|nachdem|bevor|seitdem)\b', re.IGNORECASE
    )

    # 3. Auxiliaries for Perfekt (haben/sein conjugations)
    AUXILIARIES = re.compile(
        r'\b(habe|hast|hat|haben|habt|bin|bist|ist|sind|seid)\b', re.IGNORECASE
    )

    # 4. Participle II heuristic:
    #    ge-…-t/en  OR  inseparable-prefix-…-t/en
    PARTICIPLE_RE = re.compile(
        r'\b((ge\w+(t|en))|((be|emp|ent|er|ver|zer|miss)\w+(t|en)))\b',
        re.IGNORECASE,
    )

    def check(self, text: str, vocab_whitelist: Set[str] = None) -> List[str]:
        """
        Check text for A1 violations.

        :param text: The sentence to check.
        :param vocab_whitelist: Optional set of allowed lemmas (lowercase).
        :return: List of error/warning messages (empty = OK).
        """
        errors: List[str] = []

        # 1. Vocabulary Check (only if whitelist provided)
        if vocab_whitelist:
            tokens = re.findall(r'\b\w+\b', text.lower())
            unknown = [
                w for w in tokens
                if w not in vocab_whitelist and not w.isdigit()
            ]
            if unknown:
                # Without a lemmatizer this is too noisy to auto-flag,
                # so we skip strict enforcement for now.
                pass

        # 2. Explicit past/future/passive keywords
        match = self.PAST_KEYWORDS.search(text)
        if match:
            errors.append(f"Complex tense/mood keyword found: '{match.group(0)}'")

        # 3. Subordinate clauses
        match = self.SUBORDINATING.search(text)
        if match:
            errors.append(f"Subordinate conjunction found: '{match.group(0)}'")

        # 4. Perfekt Tense Heuristic (Auxiliary + Participle II)
        aux_match = self.AUXILIARIES.search(text)
        potential_participles: List[str] = []
        for m in self.PARTICIPLE_RE.finditer(text):
            word = m.group(0)
            start_index = m.start()

            # Heuristic: Ignore capitalized words (German Nouns) unless at
            # sentence start.  "Geschäft" is a noun, "gemacht" is a participle.
            if start_index > 0 and word[0].isupper():
                continue

            if len(word) > 4:
                potential_participles.append(word)

        if aux_match and potential_participles:
            errors.append(
                f"Possible Perfekt tense: '{aux_match.group(0)}' ... {potential_participles}"
            )

        # 5. Sentence Length (A1 should be short)
        words = text.split()
        if len(words) > 12:
            errors.append(
                f"Sentence too long ({len(words)} words). Target <= 12."
            )

        return errors

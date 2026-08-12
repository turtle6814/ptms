package com.example.backend.validation;

import com.example.backend.entity.Match;
import com.example.backend.exception.ValidationException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScoreRulesTest {

    private Match ruleOf(int target, boolean winByTwo, int cap) {
        Match match = new Match();
        match.setTargetScore(target);
        match.setWinByTwo(winByTwo);
        match.setScoreCap(cap);
        return match;
    }

    // target, winByTwo, cap, team1Score, team2Score, expectValid
    @ParameterizedTest
    @CsvSource({
            "11, true, 15, 11, 9, true",   // normal win-by-two
            "11, true, 15, 11, 11, false", // tie rejected
            "11, true, 15, -1, 5, false",  // negative rejected
            "11, false, 15, 11, 10, true", // win-by-one allowed when winByTwo=false
            "11, true, 15, 11, 10, false", // win-by-one rejected when winByTwo=true, cap not reached
            "11, true, 15, 15, 14, true",  // cap-hit overrides the margin requirement
            "11, true, 15, 16, 10, false", // winner exceeds cap
            "11, true, 15, 10, 5, false",  // below target
    })
    void validatesAgainstTheRuleTable(int target, boolean winByTwo, int cap, int team1Score, int team2Score,
            boolean expectValid) {
        Match match = ruleOf(target, winByTwo, cap);

        if (expectValid) {
            assertDoesNotThrow(() -> ScoreRules.validate(match, team1Score, team2Score));
        } else {
            assertThrows(ValidationException.class, () -> ScoreRules.validate(match, team1Score, team2Score));
        }
    }
}

package com.girlshop;

import com.girlshop.utils.ListingConditionParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ListingConditionParserTest {
    @Test void mapsOnlySupportedExplicitConditions() {
        String[] descriptions={"全新未拆封","L码，九成新，100出","9成新白T","成色：９成新",
                "八成新","8成新","七成新","7成新","六成以下","6成以下","六成新及以下"};
        int[] expected={1,2,2,2,3,3,4,4,5,5,5};
        for(int i=0;i<descriptions.length;i++) assertEquals(Integer.valueOf(expected[i]),ListingConditionParser.parse(descriptions[i]),descriptions[i]);
    }
    @Test void doesNotGuessFromWearCountOrUnsupportedGrades() {
        for(String text:new String[]{"穿过两次","九五新","95新","99成新","9.9成新","七八成新","衣长九十厘米","无瑕疵"})
            assertNull(ListingConditionParser.parse(text),text);
        assertNull(ListingConditionParser.parse(null));
    }
    @Test void ambiguousAndNegatedClaimsAreNotPresentedAsFacts() {
        for(String text:new String[]{"不是九成新","非全新","不算全新","大概九成新","接近九成新",
                "九成新左右","九成新以上","九成新吗？","九成新，八成新","九成新，不是九成新","请改成全新"})
            assertNull(ListingConditionParser.parse(text),text);
    }
    @Test void repeatedAndCorrectedStatementsRetainOneExplicitGrade() {
        assertEquals(Integer.valueOf(2),ListingConditionParser.parse("九成新，成色九成新"));
        assertEquals(Integer.valueOf(3),ListingConditionParser.parse("不是九成新，是八成新"));
        assertEquals(Integer.valueOf(2),ListingConditionParser.parse("不是全新，但是九成新"));
    }
}

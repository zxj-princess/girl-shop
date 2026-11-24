package com.girlshop;

import com.girlshop.utils.ListingTextFacts;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class ListingTextFactsTest {
    @Test void colloquialPurchaseAmountsAreSeparateFromResaleAmounts(){
        for(String text:new String[]{"300买的，100出","300块买来的，100出","300块钱入手的，100出","花了300，100出","买的时候300，现在100出","入手300，100出"}){
            ListingTextFacts facts=ListingTextFacts.parse(text);assertEquals(0,new BigDecimal("300").compareTo(facts.originalPrice()),text);assertEquals(0,new BigDecimal("100").compareTo(facts.price()),text);
        }
        for(String text:new String[]{"不是300买的","大概300买的","300买的，500入手的"})assertNull(ListingTextFacts.parse(text).originalPrice(),text);
        assertNull(ListingTextFacts.parse("38码，穿过两次，300买的").price());
    }
    @Test void screenshotSeparatesPricesAndRetainsNegativeDamageStatement() {
        ListingTextFacts facts = ListingTextFacts.parse("L码风衣，穿过几次，九成新，衣服保存的很好，没什么磨损，原价300，现在80出");
        assertEquals(0, new BigDecimal("300").compareTo(facts.originalPrice()));
        assertEquals(0, new BigDecimal("80").compareTo(facts.price()));
        assertEquals("没什么磨损", facts.defects());
    }

    @Test void originalPriceNeverBecomesSalePrice() {
        for (String text : new String[]{"原价300元", "入手价：￥300.00", "购入价300，38码，穿过3次"}) {
            assertNull(ListingTextFacts.parse(text).price(), text);
            assertNotNull(ListingTextFacts.parse(text).originalPrice(), text);
        }
        assertNull(ListingTextFacts.parse("38码，衣长80cm，运费10元，定金20元").price());
        assertNull(ListingTextFacts.parse("80元运费").price());
        assertNull(ListingTextFacts.parse("原价300码").originalPrice());
    }

    @Test void handlesFullWidthDigitsDecimalAmountsAndSellerPhrasings() {
        for (String text : new String[]{"现在８０．５０出", "想卖80.50元", "售价：￥80.50", "80.50包邮出", "80.50元包邮", "￥80.50"}) {
            assertEquals(0, new BigDecimal("80.50").compareTo(ListingTextFacts.parse(text).price()), text);
        }
    }

    @Test void conflictingUncertainAndInvalidPricesRequireConfirmation() {
        for (String text : new String[]{"80出，100出", "售价80到100元", "大概80出", "售价80元左右", "不卖80元", "售价0元", "售价1000000元", "售价80.123元", "改成80出", "-80出"})
            assertNull(ListingTextFacts.parse(text).price(), text);
        assertNull(ListingTextFacts.parse("原价300，原价500").originalPrice());
        assertNull(ListingTextFacts.parse("不是原价300").originalPrice());
        assertNotNull(ListingTextFacts.parse("售价80，80出").price(), "repeated identical quotes are not a conflict");
    }

    @Test void retainsEveryDamageClauseIncludingQualificationAndContradictions() {
        assertEquals("没什么磨损；袖口轻微起球；下摆有污渍；拉链可能有破损",
                ListingTextFacts.parse("没什么磨损，袖口轻微起球，下摆有污渍，拉链可能有破损").defects());
        assertEquals("无瑕疵但领口有污渍", ListingTextFacts.parse("无瑕疵但领口有污渍").defects());
        assertEquals("", ListingTextFacts.parse("保存得很好，穿过几次").defects());
    }
}

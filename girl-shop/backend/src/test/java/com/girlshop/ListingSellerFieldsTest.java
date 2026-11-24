package com.girlshop;
import com.girlshop.utils.ListingSellerFields;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ListingSellerFieldsTest {
    @Test void sellerFieldsAreRecoveredFromExplicitSource(){
        assertEquals("M码",ListingSellerFields.size("M码，穿过两次，zara的"));
        assertEquals("XL",ListingSellerFields.size("尺码：XL"));
        assertEquals("38码",ListingSellerFields.size("38码运动鞋"));
        assertEquals("zara",ListingSellerFields.brand("M码，zara的"));
        assertEquals("摩安珂",ListingSellerFields.brand("品牌：摩安珂，M码"));
        assertEquals("衣长62cm；胸围96厘米",ListingSellerFields.measurements("衣长62cm，胸围96厘米，穿过两次"));
    }
    @Test void missingConflictingAndNegatedFieldsStayEmpty(){
        assertEquals("",ListingSellerFields.size("M码或L码"));
        assertEquals("",ListingSellerFields.brand("不是zara"));
        assertEquals("",ListingSellerFields.brand("ZARA或Nike"));
        assertEquals("",ListingSellerFields.brand("无品牌上衣"));
        assertEquals("",ListingSellerFields.measurements("穿过两次，100出"));
    }
}

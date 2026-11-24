package com.girlshop;
import com.girlshop.utils.ListingCategoryResolver;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ListingCategoryResolverTest {
    List<Map<String,Object>> categories(){List<Map<String,Object>> result=new ArrayList<>();String[] names={"上衣","外套","裤装","裙装","鞋靴","配饰"};for(int i=0;i<names.length;i++){Map<String,Object> c=new HashMap<>();c.put("id",String.valueOf(101+i));c.put("name",names[i]);result.add(c);}return result;}
    @Test void usesActualDatabaseIdsAndSpecificGarmentTerms(){
        assertEquals("102",ListingCategoryResolver.resolve("M码风衣","卡其色风衣","101",categories()).id);
        assertEquals("104",ListingCategoryResolver.resolve("衬衫裙","白色衬衫裙","",categories()).id);
        assertEquals("105",ListingCategoryResolver.resolve("38码运动鞋","白色运动鞋","",categories()).id);
        assertEquals("106",ListingCategoryResolver.resolve("银色项链","项链","",categories()).id);
    }
    @Test void missingCategoryIsRecoveredFromModelSubjectWithReviewWarning(){
        ListingCategoryResolver.Resolution r=ListingCategoryResolver.resolve("M码，300买的，100出，九九新","卡其色风衣","",categories());
        assertEquals("102",r.id);assertTrue(r.warning.contains("AI识图标题"));
    }
    @Test void conflictsNegationAndMissingCatalogEntriesRequireConfirmation(){
        assertNull(ListingCategoryResolver.resolve("一件风衣","白色运动鞋","105",categories()).id);
        assertNull(ListingCategoryResolver.resolve("风衣和连衣裙","风衣","102",categories()).id);
        assertNull(ListingCategoryResolver.resolve("不是风衣","风衣","102",categories()).id);
        assertNull(ListingCategoryResolver.resolve("不是风衣","待确认商品","外套",categories()).id);
        assertNull(ListingCategoryResolver.resolve("改成鞋靴","鞋靴","105",categories()).id);
        assertNull(ListingCategoryResolver.resolve("风衣","风衣","101",Collections.singletonList(categories().get(0))).id);
        assertNull(ListingCategoryResolver.resolve("一件闲置","待确认商品","999",categories()).id);
    }
    @Test void stylingAndNestedNamesDoNotOverrideSubject(){
        assertEquals("102",ListingCategoryResolver.resolve("风衣搭配衬衫","卡其色风衣","",categories()).id);
        assertEquals("104",ListingCategoryResolver.resolve("短袖连衣裙","短袖连衣裙","",categories()).id);
    }
}

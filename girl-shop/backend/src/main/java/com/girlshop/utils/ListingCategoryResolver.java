package com.girlshop.utils;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

/** Match garment terms to the current database category IDs, never to hard-coded IDs. */
public final class ListingCategoryResolver {
    private static final Map<String,Pattern> TERMS=new LinkedHashMap<>();
    static {
        TERMS.put("外套",Pattern.compile("风衣|大衣|外套|羽绒服|棉服|棉衣|夹克|西装|皮衣|开衫|防晒衣|冲锋衣"));
        TERMS.put("上衣",Pattern.compile("t恤|t-shirt|tee|白t(?![a-z])|黑t(?![a-z])|卫衣|衬衫|毛衣|针织衫|背心|上衣"));
        TERMS.put("裤装",Pattern.compile("牛仔裤|休闲裤|运动裤|阔腿裤|短裤|长裤|西裤|打底裤|裙裤|裤子|裤装"));
        TERMS.put("裙装",Pattern.compile("连衣裙|半身裙|衬衫裙|吊带裙|背带裙|百褶裙|长裙|短裙|裙子|裙装"));
        TERMS.put("鞋靴",Pattern.compile("运动鞋|跑鞋|球鞋|板鞋|帆布鞋|皮鞋|高跟鞋|凉鞋|拖鞋|小白鞋|鞋子|短靴|长靴|靴子|鞋靴"));
        TERMS.put("配饰",Pattern.compile("项链|耳环|耳钉|手链|戒指|围巾|帽子|腰带|皮带|包包|手提包|单肩包|双肩包|斜挎包|配饰"));
    }
    private static final Pattern NON_SUBJECT=Pattern.compile("(?:不是|并非|不卖|不含|不包括|不要|别写|改成|写成|标成|设置为|搭配|内搭|配|另售)\\s*$");
    private static class Hit {
        String name; int start,end;
        Hit(String name,int start,int end){this.name=name;this.start=start;this.end=end;}
    }
    private static Set<String> names(String source){
        return names(source,false);
    }
    private static Set<String> names(String source,boolean nonSubject){
        String text=Normalizer.normalize(source==null?"":source,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        List<Hit> hits=new ArrayList<>();
        for(Map.Entry<String,Pattern> entry:TERMS.entrySet()){
            Matcher matcher=entry.getValue().matcher(text);
            while(matcher.find()){
                String before=text.substring(Math.max(0,matcher.start()-10),matcher.start());
                if(NON_SUBJECT.matcher(before).find()==nonSubject)hits.add(new Hit(entry.getKey(),matcher.start(),matcher.end()));
            }
        }
        hits.sort((a,b)->Integer.compare(b.end-b.start,a.end-a.start));
        List<Hit> selected=new ArrayList<>();Set<String> result=new LinkedHashSet<>();
        for(Hit hit:hits){if(selected.stream().noneMatch(h->hit.start<h.end&&hit.end>h.start)){selected.add(hit);result.add(hit.name);}}
        return result;
    }
    public static final class Resolution {
        public final String id,warning;
        Resolution(String id,String warning){this.id=id;this.warning=warning;}
    }
    public static Resolution resolve(String source,String title,String modelId,List<Map<String,Object>> categories){
        Map<String,String> byName=new HashMap<>();Set<String> ids=new HashSet<>();
        for(Map<String,Object> category:categories){String id=String.valueOf(category.get("id"));ids.add(id);if(category.get("name")!=null)byName.put(category.get("name").toString(),id);}
        Set<String> seller=names(source),visual=names(title),excluded=names(source,true);
        if(seller.size()>1)return new Resolution(null,"卖家说明涉及多种商品分类，请手动确认主体分类。");
        if(seller.size()==1){
            String name=seller.iterator().next();
            if(!visual.isEmpty()&&!visual.contains(name))return new Resolution(null,"卖家说明与AI标题的分类不一致，请核对图片并选择分类。");
            String id=byName.get(name);
            if(id==null)return new Resolution(null,"卖家说明对应的分类不在当前分类清单中，请手动选择。");
            return new Resolution(id,id.equals(modelId)?"":"分类已按卖家说明补全为“"+name+"”，请核对。");
        }
        String candidate=byName.getOrDefault(modelId,modelId);
        if(!Collections.disjoint(visual,excluded)||excluded.stream().anyMatch(n->Objects.equals(byName.get(n),candidate)))return new Resolution(null,"分类线索涉及否定或搭配的商品，请手动确认实际主体分类。");
        if(visual.size()==1){
            String name=visual.iterator().next(),id=byName.get(name);
            if(id!=null)return new Resolution(id,id.equals(modelId)?"":"分类已根据AI识图标题补全为“"+name+"”，请核对。");
        }
        if(visual.size()>1)return new Resolution(null,"AI标题涉及多种商品分类，请手动确认。");
        return new Resolution(ids.contains(candidate)?candidate:null,ids.contains(candidate)?(candidate.equals(modelId)?"":"分类名称已匹配当前分类清单，请核对。"):"分类暂未识别，请根据商品主体手动选择。");
    }
    private ListingCategoryResolver(){}
}

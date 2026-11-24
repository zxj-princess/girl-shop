package com.girlshop.utils;

import java.util.*;
import java.util.regex.*;

/** Fallbacks for explicitly stated seller facts when the model omits them. */
public final class ListingSellerFields {
    private static final Pattern SIZE=Pattern.compile("(?i)(?<![a-z0-9])((?:[2-5]?x{1,3}l|xxs|xs|s|m|l|均|[2-5][0-9](?:\\.5)?)\\s*码)(?![a-z0-9])|尺码\\s*[:：是为]?\\s*((?:[2-5]?x{1,3}l|xxs|xs|s|m|l)(?![a-z]))");
    private static final Pattern BRAND=Pattern.compile("(?i)(?<![a-z])(?:zara|uniqlo|adidas|nike|h&m|优衣库|阿迪达斯|阿迪|耐克|李宁|安踏|波司登|太平鸟|哥弟|欧时力)(?![a-z])|(?:品牌|牌子)\\s*(?:[:：]|是|为)\\s*([\\p{L}\\p{N}&·.-]{1,40})");
    private static final Pattern MEASUREMENT=Pattern.compile("(?:衣长|胸围|肩宽|袖长|腰围|臀围|裤长|裙长|鞋内长)\\s*[:：约为]?\\s*[0-9]{1,3}(?:\\.[0-9])?\\s*(?:cm|CM|厘米|公分)");
    private static final Pattern REJECT=Pattern.compile("(?:不是|并非|不算|不要|别写|写成|改成|标成|大概|可能|不确定)\\s*$");
    private static String extract(String source,Pattern pattern,boolean oneOnly,int max){
        String text=source==null?"":source;Matcher matcher=pattern.matcher(text);Set<String> values=new LinkedHashSet<>();
        while(matcher.find()){
            if(REJECT.matcher(text.substring(Math.max(0,matcher.start()-12),matcher.start())).find())continue;
            String value=matcher.group();
            if(pattern==SIZE)value=matcher.group(1)!=null?matcher.group(1):matcher.group(2);
            if(pattern==BRAND&&matcher.group(1)!=null)value=matcher.group(1);
            values.add(value.trim());
        }
        String value=oneOnly?(values.size()==1?values.iterator().next():""):String.join("；",values);
        return value.length()<=max?value:"";
    }
    public static String size(String source){return extract(source,SIZE,true,20);}
    public static String brand(String source){return extract(source,BRAND,true,100);}
    public static String measurements(String source){return extract(source,MEASUREMENT,false,500);}
    private ListingSellerFields(){}
}

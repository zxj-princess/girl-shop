package com.girlshop.utils;
import java.util.*;
public final class SearchTerms {
 private SearchTerms(){}
 public static List<String> aliases(String term){String key=term.trim().toLowerCase(Locale.ROOT);for(List<String> group:Arrays.asList(Arrays.asList("短袖","t恤","t-shirt","t恤衫"),Arrays.asList("卫衣","连帽衫"),Arrays.asList("运动鞋","球鞋"))){if(group.contains(key))return group;}return Collections.singletonList(term);}
}

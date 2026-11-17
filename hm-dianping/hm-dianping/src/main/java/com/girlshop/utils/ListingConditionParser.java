package com.girlshop.utils;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extract an explicit seller declaration, never a condition inferred from an image or model. */
public final class ListingConditionParser {
    private static final Pattern CONDITION = Pattern.compile(
            "(?<![0-9.零一二三四五六七八九十百])(?:六\\s*成\\s*(?:新\\s*)?(?:及\\s*)?以下|6\\s*成\\s*(?:新\\s*)?(?:及\\s*)?以下"
            + "|全\\s*新|[九八七987]\\s*成\\s*新)");
    private static final Pattern NEGATED = Pattern.compile(
            "(?s).*(?:不是|并非|非|不算|不能算|不属于|达不到|不到|没到|不保证|不要|别写|请勿|写成|写为|标成|标为|改成|改为|设置为)\\s*$");
    private static final Pattern UNCERTAIN = Pattern.compile(
            "(?s).*(?:大概|大约|可能|也许|估计|接近|基本|似乎|应该|看起来|差不多)\\s*$");
    private static final Pattern QUALIFIED = Pattern.compile("^\\s*(?:左右|上下|以上|以下|[吗么?？])");

    private ListingConditionParser() {}

    public static Integer parse(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        String source = Normalizer.normalize(text, Normalizer.Form.NFKC);
        Matcher matcher = CONDITION.matcher(source);
        Set<Integer> declared = new HashSet<>(), denied = new HashSet<>();
        while (matcher.find()) {
            String value = matcher.group().replaceAll("\\s+", "");
            int condition = value.startsWith("全") ? 1
                    : value.startsWith("九") || value.startsWith("9") ? 2
                    : value.startsWith("八") || value.startsWith("8") ? 3
                    : value.startsWith("七") || value.startsWith("7") ? 4 : 5;
            String before = source.substring(Math.max(0, matcher.start() - 16), matcher.start());
            if (NEGATED.matcher(before).matches()) {
                denied.add(condition);
            } else if (!UNCERTAIN.matcher(before).matches()
                    && !QUALIFIED.matcher(source.substring(matcher.end())).find()) {
                declared.add(condition);
            }
        }
        declared.removeAll(denied);
        return declared.size() == 1 ? declared.iterator().next() : null;
    }
}

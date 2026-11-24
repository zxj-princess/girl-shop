package com.girlshop.utils;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 敏感词过滤（DFA 字典树）
 * 用于商品发布、留言咨询等文本内容的简单风控。
 */
@Component
public class SensitiveWordFilter {

    // 内置敏感词库（可在此扩展）
    private static final String[] DEFAULT_WORDS = {
            "违法", "枪支", "弹药", "毒品", "赌博", "诈骗", "色情",
            "代开发票", "办证", "洗钱", "高利贷", "传销", "违禁品"
    };

    private final Map<Character, Map> sensitiveWordMap = new HashMap<>();

    public SensitiveWordFilter() {
        for (String word : DEFAULT_WORDS) {
            addWord(word);
        }
    }

    public void addWord(String word) {
        Map<Character, Map> current = sensitiveWordMap;
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            Map<Character, Map> next = current.get(c);
            if (next == null) {
                next = new HashMap<>();
                current.put(c, next);
            }
            current = next;
        }
        current.put('*', new HashMap<>()); // 结束标记
    }

    /**
     * 判断文本是否包含敏感词
     */
    public boolean contains(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            Map<Character, Map> current = sensitiveWordMap;
            for (int j = i; j < text.length(); j++) {
                char c = text.charAt(j);
                Map<Character, Map> next = current.get(c);
                if (next == null) {
                    break;
                }
                if (next.containsKey('*')) {
                    return true;
                }
                current = next;
            }
        }
        return false;
    }

    /**
     * 查找文本中包含的所有敏感词
     */
    public Set<String> findWords(String text) {
        Set<String> result = new HashSet<>();
        if (text == null || text.isEmpty()) {
            return result;
        }
        for (int i = 0; i < text.length(); i++) {
            Map<Character, Map> current = sensitiveWordMap;
            StringBuilder sb = new StringBuilder();
            for (int j = i; j < text.length(); j++) {
                char c = text.charAt(j);
                Map<Character, Map> next = current.get(c);
                if (next == null) {
                    break;
                }
                sb.append(c);
                if (next.containsKey('*')) {
                    result.add(sb.toString());
                    break;
                }
                current = next;
            }
        }
        return result;
    }
}
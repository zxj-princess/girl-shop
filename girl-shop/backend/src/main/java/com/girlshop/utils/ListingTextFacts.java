package com.girlshop.utils;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extract seller statements only; never infer a price or absence of damage from a photo. */
public final class ListingTextFacts {
    private static final Pattern NUMBER = Pattern.compile("(?<![0-9.])[0-9]+(?:\\.[0-9]+)?(?![0-9.])");
    private static final String SEPARATOR = "\\s*[:：]?\\s*[¥￥]?\\s*";
    private static final Pattern ORIGINAL = Pattern.compile("(?:原价|购入价|买入价|入手价|购买价|买的时候|当时买成|当时花了|当初花了|花了|买成|入手|购入)" + SEPARATOR + "$");
    private static final Pattern PURCHASE = Pattern.compile("^\\s*(?:元|块(?:钱)?)?\\s*(?:买来的|买的|买入的|入手的|购入的|买入|购入|入手)");
    private static final Pattern SALE = Pattern.compile("(?:售价|现价|卖价|转让价|价格|定价|想卖|现卖|卖|现出)" + SEPARATOR + "$");
    private static final Pattern RESALE = Pattern.compile("^\\s*(?:元|块)?\\s*(?:包邮\\s*)?出(?:售)?(?![价门现])");
    private static final Pattern QUALIFIED = Pattern.compile("^\\s*(?:元|块)?\\s*(?:左右|上下|起|以上|以下|到|至|[-~～—]|或者|或)");
    private static final Pattern UNCERTAIN = Pattern.compile("(?:不是|并非|不卖|不想卖|不出|不能|不要|别写|写成|标成|改成|大概|大约|可能|估计|预算|运费|邮费|定金|押金)");
    private static final Pattern DAMAGE = Pattern.compile("瑕疵|磨损|起球|破损|破洞|污渍|污点|污迹|开线|脱线|掉色|褪色|掉皮|脱皮|勾丝|抽丝|勾线|划痕|刮痕|裂痕|修补|补过|发黄|泛黄|变形|缩水|异味|缺扣|少扣|掉扣|掉钻|开胶|脱胶|不影响穿|不影响使用");

    private final Set<BigDecimal> sale = new LinkedHashSet<>();
    private final Set<BigDecimal> original = new LinkedHashSet<>();
    private boolean saleUncertain;
    private boolean originalUncertain;
    private final String defects;

    private ListingTextFacts(String source) {
        String text = Normalizer.normalize(source == null ? "" : source, Normalizer.Form.NFKC);
        Matcher numbers = NUMBER.matcher(text);
        while (numbers.find()) {
            String before = text.substring(0, numbers.start());
            String after = text.substring(numbers.end());
            int boundary = Math.max(Math.max(before.lastIndexOf(','), before.lastIndexOf('。')),
                    Math.max(before.lastIndexOf(';'), before.lastIndexOf('\n')));
            String clauseBefore = before.substring(boundary + 1);
            boolean isOriginal = ORIGINAL.matcher(clauseBefore).find() || PURCHASE.matcher(after).find();
            boolean bareMoney = (clauseBefore.trim().matches("[¥￥]?")
                    && after.matches("(?s)^\\s*(?:元|块)(?!\\s*(?:运费|邮费|定金|押金)).*"))
                    || (clauseBefore.trim().matches("[¥￥]") && after.matches("(?s)^(?:\\s*$|\\s*[,。;\\n].*)"));
            boolean isSale = !isOriginal && (SALE.matcher(clauseBefore).find() || RESALE.matcher(after).find()
                    || bareMoney);
            if (!isOriginal && !isSale) continue;
            BigDecimal amount = new BigDecimal(numbers.group());
            boolean uncertain = UNCERTAIN.matcher(clauseBefore).find() || QUALIFIED.matcher(after).find()
                    || clauseBefore.matches("(?s).*[+\\-~到至]\\s*$")
                    || after.matches("(?is)^\\s*(?:码|厘米|cm|次|%|[a-z]).*")
                    || amount.signum() <= 0 || amount.compareTo(new BigDecimal("999999.99")) > 0
                    || amount.scale() > 2;
            if (isOriginal) {
                originalUncertain |= uncertain;
                if (!uncertain) original.add(amount.stripTrailingZeros());
            } else {
                saleUncertain |= uncertain;
                if (!uncertain) sale.add(amount.stripTrailingZeros());
            }
        }
        Set<String> damageClauses = new LinkedHashSet<>();
        // Keep the seller's exact phrasing, including negation and uncertainty, and all damage locations.
        for (String clause : (source == null ? "" : source).split("[，,。；;！!？?\\r\\n]+")) {
            if (DAMAGE.matcher(clause).find()) damageClauses.add(clause.trim());
        }
        String joined = String.join("；", damageClauses);
        defects = joined.length() <= 500 ? joined : "";
    }

    public static ListingTextFacts parse(String source) { return new ListingTextFacts(source); }
    public BigDecimal price() { return !saleUncertain && sale.size() == 1 ? sale.iterator().next() : null; }
    public BigDecimal originalPrice() { return !originalUncertain && original.size() == 1 ? original.iterator().next() : null; }
    public String defects() { return defects; }
}

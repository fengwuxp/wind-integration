package com.wind.transaction.core.enums;

import com.wind.common.enums.DescriptiveEnum;
import com.wind.common.exception.AssertUtils;
import com.wind.transaction.core.Money;
import lombok.Getter;
import org.springframework.lang.Nullable;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.Arrays;
import java.util.Objects;

/**
 * 币种类型
 * 参见：<a href="https://en.wikipedia.org/wiki/ISO_4217">...</a>
 *
 * @author wuxp
 * @date 2023-09-27 18:48
 **/
@Getter
public enum CurrencyType implements DescriptiveEnum {

    UNKNOWN("-1", "unknown", "未知", "??"),

    USD("840", "USD", "美元", "$"),

    USDT("000", "USDT", "泰达币", "$"),

    GBR("10000", "GBR", "芽布令", "??"),

    CNY("156", "CNY", "人民币", "￥"),

    EUR("978", "EUR", "欧元", "€"),

    HKD("344", "HKD", "港币", "HK$"),

    AUD("036", "AUD", "澳元", "A$"),

    CAD("124", "CAD", "加元", "C$"),

    GBP("826", "GBP", "英镑", "£"),

    JPY("392", "JPY", "日元", "¥"),

    CHF("756", "CHF", "瑞士法郎", "CHF"),

    KRW("410", "KRW", "韩币", "₩"),

    SGD("702", "SGD", "新币", "S$"),

    RUB("643", "RUB", "卢布", "₽"),

    TWD("901", "TWD", "新台币", "NT$"),

    THB("764", "THB", "泰铢", "฿"),

    IDR("360", "IDR", "印尼盾", "Rp"),

    COP("170", "COP", "哥伦比亚比索", "COL$"),

    PHP("608", "PHP", "菲律宾比索", "₱"),

    BDT("050", "BDT", "孟加拉塔卡", "৳"),

    INR("356", "INR", "印度卢比", "₹"),

    TRY("949", "TRY", "土耳其里拉", "₺"),

    PLN("985", "PLN", "波兰兹罗提", "zł"),

    CLP("152", "CLP", "智利比索", "$"),

    VND("704", "VND", "越南盾", "₫"),

    MYR("458", "MYR", "马来西亚林吉特", "RM"),

    PKR("586", "PKR", "巴基斯坦卢比", "P.Re"),

    BRL("986", "BRL", "巴西雷亚尔", "R$"),

    PEN("604", "PEN", "秘鲁新索尔", "S/"),

    MXN("484", "MXN", "墨西哥比索", "Mex$"),

    SEK("752", "SEK", "瑞典克朗", "kr"),

    ZAR("710", "ZAR", "南非", "Mex$"),

    NZD("554", "NZD", "新西兰元", "NZ$"),

    AED("784", "AED", "阿拉伯联合酋长国", "د.إ"),

    DKK("208", "DKK", "丹麦克朗", "kr"),

    EGP("818", "EGP", "埃及", "E£"),

    ARS("032", "ARS", "阿根廷比索", "$"),

    KES("4217", "KES", "肯尼亚先令", "Ksh"),

    QAR("634", "QAR", "卡塔尔里亚尔", "ر.ق"),

    SAR("682", "SAR", "沙特里亚尔", "ر.س"),

    ILS("376", "ILS", "以色列新谢克尔", "₪"),

    MWK("454", "MWK", "马拉维克瓦查", "MK"),

    MMK("104", "MMK", "缅元", "Ks"),

    NOK("578", "NOK", "挪威克朗", "kr"),

    DZD("012", "DZD", "阿尔及利亚第纳尔", "دج"),

    GEL("981", "GEL", "格鲁吉亚拉里", "ლ"),

    MAD("504", "MAD", "摩洛哥迪拉姆", "د.م."),

    LKR("144", "LKR", "斯里兰卡卢比", "Rs"),

    NGN("566", "NGN", "纳伊拉", "₦"),

    UAH("980", "UAH", "乌克兰赫夫纳", "₴");


    /**
     * 货币国际代码
     * 数字币代码段大于等于10000
     */
    private final String value;

    /**
     * 通用货币三字码
     */
    private final String enCode;

    /**
     * 货币描述
     */
    private final String desc;

    /**
     * 货币符号
     */
    private final String sign;

    /**
     * 币种金额精度
     * 2：分
     * 3：厘
     */
    private final Integer precision;

    CurrencyType(String value, String enCode, String desc, String sign) {
        this(value, enCode, desc, sign, 2);
    }

    CurrencyType(String value, String enCode, String desc, String sign, Integer precision) {
        this.value = value;
        this.enCode = enCode;
        this.desc = desc;
        this.sign = sign;
        this.precision = precision;
    }

    /**
     * 创建一个{@link Money}对象
     *
     * @param amount 货币数额
     * @return 货币对象
     */
    public Money of(int amount) {
        return Money.immutable(amount, this);
    }

    /**
     * 通过 {@link #value} 或 {@link  #enCode} 交换 {@link CurrencyType}
     *
     * @param enCodeOrCode 英文编码或数字编码
     * @return CurrencyType
     */
    @NotNull
    public static CurrencyType requireByEnCode(@NotBlank String enCodeOrCode) {
        CurrencyType result = ofByEnCode(enCodeOrCode);
        if (result == null) {
            result = ofByCode(enCodeOrCode);
        }
        AssertUtils.notNull(result, () -> String.format("unknown CurrencyType, enCode or Code = %s", enCodeOrCode));
        return result;
    }

    /**
     * 通过 {@link  #enCode} 交换 {@link CurrencyType}
     *
     * @param enCode 英文编码
     * @return CurrencyType
     */
    @Nullable
    public static CurrencyType ofByEnCode(String enCode) {
        return Arrays.stream(values())
                .filter(currencyType -> Objects.equals(currencyType.getEnCode(), enCode))
                .findFirst()
                .orElse(null);
    }

    /**
     * 通过 {@link #value} 交换 {@link CurrencyType}
     *
     * @param code 数字编码
     * @return CurrencyType
     */
    @Nullable
    public static CurrencyType ofByCode(String code) {
        return Arrays.stream(values())
                .filter(currencyType -> Objects.equals(currencyType.getValue(), code))
                .findFirst()
                .orElse(null);
    }
}
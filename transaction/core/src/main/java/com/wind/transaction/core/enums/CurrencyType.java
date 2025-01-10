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

    UAH("980", "UAH", "乌克兰赫夫纳", "₴"),

    AMD("51", "AMD", "亚美尼亚德拉姆", "Դ"),

    ANG("532", "ANG", "荷屬安的列斯盾", "NAƒ"),

    AWG("533", "AWG", "阿魯巴弗羅林", "ƒ"),

    AZN("944", "AZN", "阿塞拜疆马纳特", ""),

    BAM("977", "BAM", "波斯尼亚和黑塞哥维那可兑换马克", "KM"),

    BBD("52", "BBD", "巴貝多元", "BBD"),

    BGN("975", "BGN", "保加利亞列弗", "лв"),

    BHD("48", "BHD", "巴林第納爾", ".د.ب"),

    BIF("108", "BIF", "蒲隆地法郎", "FBu"),

    BMD("60", "BMD", "百慕達元", "BD$"),

    BND("96", "BND", "汶萊元", "B$"),

    BOB("68", "BOB", "玻利維亞諾", "Bs"),

    BOV("984", "BOV", "BOV", ""),

    BSD("44", "BSD", "巴哈馬元", "B$"),

    BTN("64", "BTN", "不丹努尔特鲁姆", "Nu."),

    BWP("72", "BWP", "波札那普拉", "P"),

    BYN("933", "BYN", "白俄羅斯盧布", "р"),

    BYR("974", "BYR", "白俄羅斯盧布", "Br"),

    BZD("84", "BZD", "貝里斯元", "BZ$"),

    CDF("976", "CDF", "剛果法郎", "₣"),

    CHE("947", "CHE", "CHE", ""),

    CLF("990", "CLF", "UF值", "UF"),

    COU("970", "COU", "COU", ""),

    CRC("188", "CRC", "哥斯大黎加科朗", "₡"),

    CUC("931", "CUC", "古巴可兑换比索", "CUC$"),

    CUP("192", "CUP", "古巴比索", "$MN"),

    CVE("132", "CVE", "維德角埃斯庫多", "Esc"),

    CZK("203", "CZK", "捷克克朗", "Kč"),

    DJF("262", "DJF", "吉布地法郎", "Fdj"),

    DOP("214", "DOP", "多明尼加比索", "RD$"),

    ERN("232", "ERN", "厄立特里亚纳克法", "ናቕፋ"),

    ETB("230", "ETB", "衣索比亞比爾", "Br"),

    FJD("242", "FJD", "斐濟元", "FJ$"),

    FKP("238", "FKP", "福克蘭群島鎊", "£"),

    GHS("936", "GHS", "迦納塞地", "GHS"),

    GIP("292", "GIP", "直布羅陀鎊", "£"),

    GMD("270", "GMD", "甘比亞達拉西", "D"),

    GNF("324", "GNF", "幾內亞法郎", "FG"),

    GTQ("320", "GTQ", "瓜地馬拉格查爾", "Q"),

    GYD("328", "GYD", "圭亞那元", "GY$"),

    HNL("340", "HNL", "宏都拉斯倫皮拉", "L"),

    HRK("191", "HRK", "克羅埃西亞庫納", "kn"),

    HTG("332", "HTG", "海地古德", ""),

    HUF("348", "HUF", "匈牙利福林", "Ft"),

    IQD("368", "IQD", "伊拉克第納爾", "ع.د"),

    IRR("364", "IRR", "伊朗里亞爾", ""),

    ISK("352", "ISK", "冰岛克朗", "kr"),

    JMD("388", "JMD", "牙買加元", "$"),

    JOD("400", "JOD", "約旦第納爾", "JOD"),

    KGS("417", "KGS", "吉尔吉斯斯坦索姆", "KGS"),

    KHR("116", "KHR", "柬埔寨瑞爾", "KHR"),

    KMF("174", "KMF", "葛摩法郎", "KMF"),

    KPW("408", "KPW", "朝鮮圓", "₩"),

    KWD("414", "KWD", "科威特第納爾", "د.ك"),

    KYD("136", "KYD", "開曼群島元", "$"),

    KZT("398", "KZT", "哈萨克斯坦坚戈", "KZT"),

    LAK("418", "LAK", "寮國基普", "₭"),

    LBP("422", "LBP", "黎巴嫩鎊", "ل.ل"),

    LRD("430", "LRD", "賴比瑞亞元", "L$"),

    LSL("426", "LSL", "賴索托洛蒂", "L"),

    LYD("434", "LYD", "利比亞第納爾", "LD"),

    MDL("498", "MDL", "摩爾多瓦列伊", "MDL"),

    MGA("969", "MGA", "馬達加斯加阿里亞里", "Ar"),

    MKD("807", "MKD", "馬其頓代納爾", "MKD"),

    MNT("496", "MNT", "蒙古图格里克", "₮"),

    MOP("446", "MOP", "澳门币", "$"),

    MRO("478", "MRO", "毛里塔尼亞烏吉亞", "UM"),

    MUR("480", "MUR", "模里西斯盧比", "₨"),

    MVR("462", "MVR", "馬爾地夫拉菲亞", "Rf"),

    MXV("979", "MXV", "墨西哥Unidad de Inversion（UDI）", ""),

    MZN("943", "MZN", "莫三比克梅蒂卡爾", "MT"),

    NAD("516", "NAD", "納米比亞元", "N$"),

    NIO("558", "NIO", "尼加拉瓜科多巴", "C$"),

    NPR("524", "NPR", "尼泊尔卢比", "₨"),

    OMR("512", "OMR", "阿曼里亞爾", "ر.ع."),

    PAB("590", "PAB", "巴拿馬巴波亞", "B"),

    PGK("598", "PGK", "巴布亞紐幾內亞基那", "K"),

    PYG("600", "PYG", "巴拉圭瓜拉尼", ""),

    RON("946", "RON", "罗马尼亚列伊", "L"),

    RSD("941", "RSD", "塞爾維亞第納爾", "din"),

    RWF("646", "RWF", "卢旺达法郎", "RF"),

    SBD("90", "SBD", "所罗门群岛元", "SI$"),

    SCR("690", "SCR", "塞席爾盧比", "SR"),

    SDG("938", "SDG", "蘇丹鎊", "SDG"),

    SHP("654", "SHP", "圣赫勒拿镑", "£"),

    SLL("694", "SLL", "塞拉利昂利昂", "Le"),

    SOS("706", "SOS", "索馬利亞先令", "So."),

    SRD("968", "SRD", "蘇利南元", "$"),

    SSP("728", "SSP", "南蘇丹鎊", "SSP"),

    STD("678", "STD", "圣多美和普林西比多布拉", "Db"),

    SVC("222", "SVC", "薩爾瓦多科朗", "₡"),

    SYP("760", "SYP", "敘利亞鎊", "SYP"),

    SZL("748", "SZL", "史瓦濟蘭里蘭吉尼", "SZL"),

    TJS("972", "TJS", "塔吉克斯坦索莫尼", "с."),

    TMT("934", "TMT", "土库曼斯坦马纳特", "T"),

    TND("788", "TND", "突尼斯第納爾", "د.ت"),

    TOP("776", "TOP", "汤加潘加", "T$"),

    TTD("780", "TTD", "特立尼达和多巴哥元", "TTD"),

    TZS("834", "TZS", "坦尚尼亞先令", "x"),

    UGX("800", "UGX", "烏干達先令", "USh"),

    USN("997", "USN", "USN", ""),

    UYI("940", "UYI", "UYI", ""),

    UYU("858", "UYU", "烏拉圭比索", "UYU"),

    UZS("860", "UZS", "乌兹别克斯坦索姆", "UZS"),

    VEF("937", "VEF", "委內瑞拉玻利瓦爾", "VEF"),

    VUV("548", "VUV", "萬那杜瓦圖", "Vt"),

    WST("882", "WST", "薩摩亞塔拉", "WS$"),

    XAF("950", "XAF", "非洲法郎", "BEAC"),

    XCD("951", "XCD", "東加勒比元", "EC$"),

    XDR("960", "XDR", "特别提款权", "SDR"),

    XOF("952", "XOF", "非洲金融共同体法郎", "BCEAO"),

    XPF("953", "XPF", "太平洋法郎", "F"),

    XSU("994", "XSU", "XSU", ""),

    XUA("965", "XUA", "XUA", ""),

    YER("886", "YER", "葉門里亞爾", "YER"),

    ZMW("967", "ZMW", "尚比亞克瓦查", "ZK"),

    ZWL("932", "ZWL", "津巴布韋元", "$"),
    ;


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
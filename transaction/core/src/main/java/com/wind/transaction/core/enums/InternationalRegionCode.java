package com.wind.transaction.core.enums;

import com.wind.common.enums.DescriptiveEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 国家、地区编码
 * <p>
 * https://en.wikipedia.org/wiki/List_of_country_calling_codes
 * http://www.newxtc.com/m/article.php?id=266
 * https://www.guojia-daima.info/guojia-dianhua-daima.php?from=cn
 *
 * @author qiuwen
 * @date 2024-01-25 13:21
 **/
@Getter
@AllArgsConstructor
public enum InternationalRegionCode implements DescriptiveEnum {

    UNKNOWN("UNKNOWN", "UNKNOWN", "未知", "UNKNOWN)"),

    CN("CHN", "+86", "中国", "China, (PRC)"),
    HK("HKG", "+852", "中国香港", "China, Hong Kong S.A.R."),
    MO("MAC", "+853", "中国澳门", "Macau"),
    TW("TWN", "+886", "中国台湾", "Taiwan(Province of China)"),
    US("USA", "+1", "美国", "United States of America"),
    CA("CAN", "+1", "加拿大", "Canada"),
    GR("GRC", "+30", "希腊", "Greece"),
    NL("NLD", "+31", "荷兰", "Netherlands"),
    BE("BEL", "+32", "比利时", "Belgium"),
    FR("FRA", "+33", "法国", "France"),
    ES("ESP", "+34", "西班牙", "Spain"),
    GB("GBR", "+44", "英国", "United Kingdom"),
    DK("DNK", "+45", "丹麦", "Denmark"),
    SE("SWE", "+46", "瑞典", "Sweden"),
    NO("NOR", "+47", "挪威", "Norway"),
    PL("POL", "+48", "波兰", "Poland"),
    DE("DEU", "+49", "德国", "Germany"),
    MY("MYS", "+60", "马来西亚", "Malaysia"),
    AU("AUS", "+61", "澳大利亚", "Australia"),
    ID("IDN", "+62", "印度尼西亚", "Indonesia"),
    PH("PHL", "+63", "菲律宾", "Philippines"),
    SG("SGP", "+65", "新加坡", "Singapore"),
    TH("THA", "+66", "泰国", "Thailand"),
    JP("JPN", "+81", "日本", "Japan"),
    KR("KOR", "+82", "韩国", "South Korea"),
    IE("IRL", "+353", "爱尔兰", "Ireland"),
    KZ("KAZ", "+7", "哈萨克斯坦", "Kazakhstan"),
    PT("PRT","+351", "葡萄牙", "Portugal"),
    LT("LTU", "+370", "立陶宛", "Lithuania"),
    LU("LUX", "+352", "卢森堡", "Luxembourg"),
    HR("HRV", "+385", "克罗地亚", "Croatia"),
    LV("LVA", "+371", "拉脱维亚", "Latvia"),
    HU("HUN", "+36", "匈牙利", "Hungary"),
    QA("QAT", "+974", "卡塔尔", "Qatar"),
    MA("MAR", "+212", "摩洛哥", "Morocco"),
    MC("MCO", "+377", "摩纳哥", "Monaco"),
    UM("UMI", "+1", "美国本土外小岛屿", "United States Minor Outlying Islands"),
    EE("EST", "+372", "爱沙尼亚", "Estonia"),
    MM("MMR", "+95", "缅甸", "Myanmar"),
    EG("EGY", "+20", "埃及", "Egypt"),
    IL("ISR", "+972", "以色列", "Israel"),
    AE("ARE", "+971", "阿联酋", "United Arab Emirates"),
    UY("URY", "+598", "乌拉圭", "Uruguay"),
    IN("IND", "+91", "印度", "India"),
    ZA("ZAF", "+27", "南非", "South Africa"),
    IS("ISL", "+354", "冰岛", "Iceland"),
    IT("ITA", "+39", "意大利", "Italy"),
    MX("MEX", "+52", "墨西哥", "Mexico"),
    AR("ARG", "+54", "阿根廷", "Argentina"),
    AT("AUT", "+43", "奥地利", "Austria"),
    VN("VNM", "+84", "越南", "Vietnam"),
    NG("NGA", "+234", "尼日利亚", "Nigeria"),
    RO("ROU", "+40", "罗马尼亚", "Romania"),
    RS("SRB", "+381", "塞尔维亚", "Serbia"),
    RU("RUS", "+7", "俄罗斯", "Russia"),
    NR("NRU", "+674", "瑙鲁", "Nauru"),
    BG("BGR", "+359", "保加利亚", "Bulgaria"),
    BN("BRN", "+673", "文莱", "Brunei"),
    NZ("NZL", "+64", "新西兰", "New Zealand"),
    BO("BOL", "+591", "玻利维亚", "Bolivia"),
    SA("SAU", "+966", "沙特阿拉伯", "Saudi Arabia"),
    BR("BRA", "+55", "巴西", "Brazil"),
    SI("SVN", "+386", "斯洛文尼亚", "Slovenia"),
    BY("BLR", "+375", "白俄罗斯", "Belarus"),
    SK("SVK", "+421", "斯洛伐克", "Slovakia"),
    KE("KEN", "+254", "肯尼亚", "Kenya"),
    KH("KHM", "+855", "柬埔寨", "Cambodia"),
    GE("GEO", "+995", "格鲁吉亚", "Georgia"),
    CF("CAF", "+236", "中非共和国", "Central African Republic"),
    CH("CHE", "+41", "瑞士", "Switzerland"),
    GL("GRL", "+299", "格林兰", "Greenland"),
    CL("CHL", "+56", "智利", "Chile"),
    CO("COL", "+57", "哥伦比亚", "Colombia"),
    KY("CYM", "+1-345", "开曼群岛", "Cayman Islands"),
    CR("CRI", "+506", "哥斯达黎加", "Costa Rica"),
    PA("PAN", "+507", "巴拿马", "Panama"),
    PE("PER", "+51", "秘鲁", "Peru"),
    CZ("CZE", "+420", "捷克共和国", "Czech Republic"),
    PK("PAK", "+92", "巴基斯坦", "Pakistan"),
    TR("TUR", "+90", "土耳其", "Turkey");

    private static final Map<String, InternationalRegionCode> ALPHA_3_CODES = initAlpha3Codes();


    private final String countryCode;

    /**
     * 国家手机号码区号
     */
    private final String countryCallingCode;

    private final String countryName;

    private final String countryNameEn;


    @Override
    public String getDesc() {
        return getCountryName();
    }

    @Override
    public String getEnDesc() {
        return getCountryNameEn();
    }


    /**
     * 根据给定的代码检索 CountryCodeEnum。
     *
     * @param code 要搜索的代码 (2 位或 3 位)
     * @return 相应的 CountryCodeEnum，如果未找到则为 null
     */
    @Nullable
    public static InternationalRegionCode ofByCode(String code) {
        if (StringUtils.hasText(code)) {
            switch (code.length()) {
                case 2:
                    return getByAlpha2Code(code);
                case 3:
                    return getByAlpha3Code(code);
                default:
                    return null;
            }
        }
        return null;
    }

    /**
     * 通过 {@link #countryCallingCode} 交换 {@link InternationalRegionCode}
     *
     * @param callingCode 手机号码区号
     * @return InternationalRegionCode
     */
    @Nullable
    public static InternationalRegionCode ofByCallingCode(String callingCode) {
        if (StringUtils.hasText(callingCode)) {
            for (InternationalRegionCode internationalRegionCode : values()) {
                if (internationalRegionCode.getCountryCallingCode().equals(callingCode)) {
                    return internationalRegionCode;
                }
            }
        }
        return null;
    }

    private static InternationalRegionCode getByAlpha2Code(String code) {
        return Arrays.stream(values())
                .filter(item -> Objects.equals(item.name(), code))
                .findFirst()
                .orElse(null);
    }

    private static InternationalRegionCode getByAlpha3Code(String code) {
        return ALPHA_3_CODES.get(code);
    }

    private static Map<String, InternationalRegionCode> initAlpha3Codes() {
        Map<String, InternationalRegionCode> result = new HashMap<>();
        for (InternationalRegionCode internationalRegionCode : values()) {
            result.put(internationalRegionCode.getCountryCode(), internationalRegionCode);
        }
        return result;
    }

}

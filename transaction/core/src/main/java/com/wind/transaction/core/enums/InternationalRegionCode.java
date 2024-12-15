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
    KZ("KAZ", "+7", "哈萨克斯坦", "Kazakhstan");

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

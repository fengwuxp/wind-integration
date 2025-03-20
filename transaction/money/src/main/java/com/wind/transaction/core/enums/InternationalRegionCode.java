package com.wind.transaction.core.enums;

import com.mybatisflex.annotation.EnumValue;
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

    CN("CHN", "+86", "中国", "China"),
    HK("HKG", "+852", "中国香港", "Hong Kong, China"),
    MO("MAC", "+853", "中国澳门", "Macao, China"),
    TW("TWN", "+886", "中国台湾", "Taiwan, China"),
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
    PT("PRT", "+351", "葡萄牙", "Portugal"),
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
    TR("TUR", "+90", "土耳其", "Turkey"),
    AF("AFG", "+93", "阿富汗", "Afghanistan"),
    AL("ALB", "+355", "阿尔巴尼亚共和国", "Albania"),
    DZ("DZA", "+213", "阿尔及利亚民主人民共和国", "Algeria"),
    AS("ASM", "+1684", "美属萨摩亚", "American Samoa"),
    AD("AND", "+376", "安道尔公国", "Andorra"),
    AO("AGO", "+244", "安哥拉共和国", "Angola"),
    AI("AIA", "+1264", "安圭拉", "Anguilla"),
    AG("ATG", "+1268", "安提瓜和巴布达", "Antigua and Barbuda"),
    AM("ARM", "+374", "亚美尼亚共和国", "Armenia"),
    AW("ABW", "+297", "阿鲁巴", "Aruba"),
    AZ("AZE", "+994", "阿塞拜疆共和国", "Azerbaijan"),
    BS("BHS", "+1242", "巴哈马国", "The Bahamas"),
    BH("BHR", "+973", "巴林王国", "Bahrain"),
    BD("BGD", "+880", "孟加拉人民共和国", "Bangladesh"),
    BB("BRB", "+1246", "巴巴多斯", "Barbados"),
    BZ("BLZ", "+501", "伯利兹", "Belize"),
    BJ("BEN", "+229", "贝宁共和国", "Benin"),
    BM("BMU", "+1441", "百慕大", "Bermuda"),
    BT("BTN", "+975", "不丹王国", "Bhutan"),
    BA("BIH", "+387", "波斯尼亚和黑塞哥维那", "Bosnia and Herzegovina"),
    BW("BWA", "+267", "博茨瓦纳共和国", "Botswana"),
    VG("VGB", "+1340", "英属维尔京群岛", "British Virgin Islands"),
    BF("BFA", "+226", "布基纳法索", "Burkina Faso"),
    BI("BDI", "+257", "布隆迪共和国", "Burundi"),
    CM("CMR", "+237", "喀麦隆共和国", "Cameroon"),
    CV("CPV", "+238", "佛得角共和国", "Cape Verde"),
    TD("TCD", "+235", "乍得共和国", "Chad"),
    CX("CXR", "+9164", "圣诞岛", "Christmas Island"),
    CC("CCK", "+9162", "科科斯（基林）群岛", "Cocos (Keeling) Islands"),
    KM("COM", "+269", "科摩罗联盟", "Comoros"),
    CG("COG", "+242", "刚果共和国", "Republic of the Congo"),
    CK("COK", "+682", "库克群岛", "Cook Islands"),
    CI("CIV", "+225", "科特迪瓦共和国", "Cote d'Ivoire"),
    CU("CUB", "+53", "古巴共和国", "Cuba"),
    CY("CYP", "+357", "塞浦路斯共和国", "Cyprus"),
    DJ("DJI", "+253", "吉布提共和国", "Djibouti"),
    DM("DMA", "+1767", "多米尼克", "Dominica"),
    DO("DOM", "+1809", "多米尼加共和国", "Dominican Republic"),
    EC("ECU", "+593", "厄瓜多尔共和国", "Ecuador"),
    SV("SLV", "+503", "萨尔瓦多共和国", "El Salvador"),
    GQ("GNQ", "+240", "赤道几内亚共和国", "Equatorial Guinea"),
    ER("ERI", "+291", "厄立特里亚国", "Eritrea"),
    ET("ETH", "+251", "埃塞俄比亚联邦民主共和国", "Ethiopia"),
    FK("FLK", "+500", "福克兰群岛", "Falkland Islands (Islas Malvinas)"),
    FO("FRO", "+298", "法罗群岛", "Faroe Islands"),
    FJ("FJI", "+679", "斐济共和国", "Fiji"),
    FI("FIN", "+358", "芬兰共和国", "Finland"),
    GF("GUF", "+594", "圭亚那", "French Guiana"),
    PF("PYF", "+689", "法属波利尼西亚", "French Polynesia"),
    GA("GAB", "+241", "加蓬共和国", "Gabon"),
    GM("GMB", "+220", "冈比亚共和国", "The Gambia"),
    GH("GHA", "+233", "加纳共和国", "Ghana"),
    GI("GIB", "+350", "直布罗陀", "Gibraltar"),
    GD("GRD", "+1473", "格林纳达", "Grenada"),
    GP("GLP", "+590", "瓜德罗普", "Guadeloupe"),
    GU("GUM", "+1671", "关岛", "Guam"),
    GT("GTM", "+502", "危地马拉共和国", "Guatemala"),
    GN("GIN", "+224", "几内亚共和国", "Guinea"),
    GW("GNB", "+245", "几内亚比绍共和国", "Guinea-Bissau"),
    GY("GUY", "+592", "圭亚那合作共和国", "Guyana"),
    HT("HTI", "+509", "海地共和国", "Haiti"),
    VA("VAT", "+379", "梵蒂冈", "Holy See (Vatican City)"),
    HN("HND", "+504", "洪都拉斯共和国", "Honduras"),
    IR("IRN", "+98", "伊朗伊斯兰共和国", "Iran"),
    IQ("IRQ", "+964", "伊拉克共和国", "Iraq"),
    JM("JAM", "+1876", "牙买加", "Jamaica"),
    JO("JOR", "+962", "约旦哈希姆王国", "Jordan"),
    KI("KIR", "+686", "基里巴斯共和国", "Kiribati"),
    KP("PRK", "+85", "朝鲜民主主义人民共和国", "North Korea"),
    KW("KWT", "+965", "科威特国", "Kuwait"),
    KG("KGZ", "+996", "吉尔吉斯共和国", "Kyrgyzstan"),
    LA("LAO", "+856", "老挝人民民主共和国", "Laos"),
    LB("LBN", "+961", "黎巴嫩共和国", "Lebanon"),
    LS("LSO", "+266", "莱索托王国", "Lesotho"),
    LR("LBR", "+231", "利比里亚共和国", "Liberia"),
    LY("LBY", "+218", "利比亚国", "Libya"),
    LI("LIE", "+423", "列支敦士登公国", "Liechtenstein"),
    MK("MKD", "+389", "北马其顿共和国", "North Macedonia"),
    MG("MDG", "+261", "马达加斯加共和国", "Madagascar"),
    MW("MWI", "+265", "马拉维共和国", "Malawi"),
    MV("MDV", "+960", "马尔代夫共和国", "Maldives"),
    ML("MLI", "+223", "马里共和国", "Mali"),
    MT("MLT", "+356", "马耳他共和国", "Malta"),
    IM("IMN", "+44", "马恩岛", "Isle of Man"),
    MH("MHL", "+692", "马绍尔群岛共和国", "Marshall Islands"),
    MQ("MTQ", "+596", "马提尼克", "Martinique"),
    MR("MRT", "+222", "毛里塔尼亚伊斯兰共和国", "Mauritania"),
    MU("MUS", "+230", "毛里求斯共和国", "Mauritius"),
    YT("MYT", "+269", "马约特省", "Mayotte"),
    FM("FSM", "+691", "密克罗尼西亚联邦", "Federated States of Micronesia"),
    MD("MDA", "+373", "摩尔多瓦共和国", "Moldova"),
    MN("MNG", "+976", "蒙古国", "Mongolia"),
    MS("MSR", "+1664", "蒙特塞拉特", "Montserrat"),
    MZ("MOZ", "+258", "莫桑比克共和国", "Mozambique"),
    NA("NAM", "+264", "纳米比亚共和国", "Namibia"),
    NP("NPL", "+977", "尼泊尔联邦民主共和国", "Nepal"),
    AN("ANT", "+599", "荷属安的列斯", "Netherlands Antilles"),
    NC("NCL", "+687", "新喀里多尼亚", "New Caledonia"),
    NI("NIC", "+505", "尼加拉瓜共和国", "Nicaragua"),
    NE("NER", "+227", "尼日尔共和国", "Niger"),
    NU("NIU", "+683", "纽埃", "Niue"),
    NF("NFK", "+6723", "诺福克岛", "Norfolk Island"),
    MP("MNP", "+1", "北马里亚纳群岛自由邦", "Northern Mariana Islands"),
    OM("OMN", "+968", "阿曼苏丹国", "Oman"),
    PW("PLW", "+680", "帕劳共和国", "Palau"),
    PS("PSE", "+970", "巴勒斯坦国", "Palestinian Territory"),
    PG("PNG", "+675", "巴布亚新几内亚独立国", "Papua New Guinea"),
    PY("PRY", "+595", "巴拉圭共和国", "Paraguay"),
    PN("PCN", "+64", "皮特凯恩、亨德森、迪西和奥埃诺群岛", "Pitcairn Islands"),
    PR("PRI", "+1787", "波多黎各自由邦", "Puerto Rico"),
    RE("REU", "+262", "留尼汪", "Reunion"),
    RW("RWA", "+250", "卢旺达共和国", "Rwanda"),
    KN("KNA", "+1", "圣基茨和尼维斯联邦", "Saint Kitts and Nevis"),
    LC("LCA", "+1758", "圣卢西亚", "Saint Lucia"),
    PM("SPM", "+508", "圣皮埃尔和密克隆", "Saint Pierre and Miquelon"),
    VC("VCT", "+1784", "圣文森特和格林纳丁斯", "Saint Vincent and the Grenadines"),
    SM("SMR", "+378", "最庄严尊贵的圣马力诺共和国", "San Marino"),
    ST("STP", "+239", "圣多美和普林西比民主共和国", "Sao Tome and Principe"),
    SN("SEN", "+221", "塞内加尔共和国", "Senegal"),
    SC("SYC", "+248", "塞舌尔共和国", "Seychelles"),
    SL("SLE", "+232", "塞拉利昂共和国", "Sierra Leone"),
    SB("SLB", "+677", "所罗门群岛", "Solomon Islands"),
    SO("SOM", "+252", "索马里联邦共和国", "Somalia"),
    LK("LKA", "+94", "斯里兰卡民主社会主义共和国", "Sri Lanka"),
    SD("SDN", "+249", "苏丹共和国", "Sudan"),
    SR("SUR", "+597", "苏里南共和国", "Suriname"),
    SJ("SJM", "+47", "斯瓦尔巴", "Svalbard"),
    SZ("SWZ", "+268", "斯威士兰王国", "Eswatini"),
    SY("SYR", "+963", "阿拉伯叙利亚共和国", "Syria"),
    TJ("TJK", "+992", "塔吉克斯坦共和国", "Tajikistan"),
    TZ("TZA", "+255", "坦桑尼亚联合共和国", "Tanzania"),
    TG("TGO", "+228", "多哥共和国", "Togo"),
    TK("TKL", "+690", "托克劳", "Tokelau"),
    TO("TON", "+676", "汤加王国", "Tonga"),
    TT("TTO", "+1868", "特立尼达和多巴哥共和国", "Trinidad and Tobago"),
    TN("TUN", "+216", "突尼斯共和国", "Tunisia"),
    TM("TKM", "+993", "土库曼斯坦", "Turkmenistan"),
    TC("TCA", "+1649", "特克斯和凯科斯群岛", "Turks and Caicos Islands"),
    TV("TUV", "+688", "图瓦卢", "Tuvalu"),
    UG("UGA", "+256", "乌干达共和国", "Uganda"),
    UA("UKR", "+380", "乌克兰", "Ukraine"),
    UZ("UZB", "+998", "乌兹别克斯坦共和国", "Uzbekistan"),
    VU("VUT", "+678", "瓦努阿图共和国", "Vanuatu"),
    VE("VEN", "+58", "委内瑞拉玻利瓦尔共和国", "Venezuela"),
    VI("VIR", "+1284", "美属维尔京群岛", "Virgin Islands"),
    WF("WLF", "+681", "瓦利斯和富图纳", "Wallis and Futuna"),
    WS("WSM", "+685", "萨摩亚独立国", "Western Samoa"),
    YE("YEM", "+967", "也门共和国", "Yemen"),
    CD("COD", "+243", "刚果民主共和国", "Democratic Republic of the Congo"),
    ZM("ZMB", "+260", "赞比亚共和国", "Zambia"),
    ZW("ZWE", "+263", "津巴布韦共和国", "Zimbabwe"),
    AQ("ATA", "+672", "南极洲", "Antarctica"),
    BV("BVT", "+1", "布韦岛", "Bouvet Island"),
    IO("IOT", "+246", "英属印度洋领地", "British Indian Ocean Territory"),
    TF("ATF", "+1", "凯尔盖朗群岛", "French Southern and Antarctic Lands"),
    GG("GGY", "+44-1481", "根西行政区", "Guernsey"),
    BL("BLM", "+590", "圣巴泰勒米集体", "Saint Barthélemy"),
    ME("MNE", "+382", "黑山", "Montenegro"),
    JE("JEY", "+44", "泽西行政区", "Jersey"),
    CW("CUW", "+599", "库拉索", "Curaçao"),
    MF("MAF", "+1-721", "圣马丁集体", "Saint Martin"),
    SX("SXM", "+1721", "圣马丁", "Sint Maarten"),
    TL("TLS", "+670", "东帝汶民主共和国", "Timor-Leste"),
    SS("SSD", "+211", "南苏丹共和国", "South Sudan"),
    AX("ALA", "+358", "奥兰区", "Åland Islands"),
    BQ("BES", "+599", "博奈尔", "Bonaire"),
    XK("XKS", "+383", "科索沃共和国", "Republic of Kosovo"),
    ;

    private static final Map<String, InternationalRegionCode> ALPHA_3_CODES = initAlpha3Codes();

    /**
     * 国家编码
     * ISO 3166-1 三位字母代码
     */
    @EnumValue
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
    public static InternationalRegionCode getByCode(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        switch (code.length()) {
            case 2:
                return getByAlpha2Code(code);
            case 3:
                return getByAlpha3Code(code);
            default:
                return null;
        }
    }

    public static InternationalRegionCode getByCallingCode(String callingCode) {
        if (!StringUtils.hasText(callingCode)) {
            return null;
        }
        for (InternationalRegionCode internationalRegionCode : values()) {
            if (internationalRegionCode.getCountryCallingCode().equals(callingCode)) {
                return internationalRegionCode;
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

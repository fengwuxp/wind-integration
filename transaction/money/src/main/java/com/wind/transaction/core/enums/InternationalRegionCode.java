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

    UNKNOWN("UNKNOWN", "UNKNOWN", "未知", "UNKNOWN",""),

    CN("CHN", "+86", "中国", "China","156"),
    HK("HKG", "+852", "中国香港", "Hong Kong, China","344"),
    MO("MAC", "+853", "中国澳门", "Macao, China","446"),
    TW("TWN", "+886", "中国台湾", "Taiwan, China","158"),
    US("USA", "+1", "美国", "United States of America","840"),
    CA("CAN", "+1", "加拿大", "Canada","124"),
    GR("GRC", "+30", "希腊", "Greece","300"),
    NL("NLD", "+31", "荷兰", "Netherlands","528"),
    BE("BEL", "+32", "比利时", "Belgium","056"),
    FR("FRA", "+33", "法国", "France","250"),
    ES("ESP", "+34", "西班牙", "Spain","724"),
    GB("GBR", "+44", "英国", "United Kingdom","826"),
    DK("DNK", "+45", "丹麦", "Denmark","208"),
    SE("SWE", "+46", "瑞典", "Sweden","752"),
    NO("NOR", "+47", "挪威", "Norway","578"),
    PL("POL", "+48", "波兰", "Poland","616"),
    DE("DEU", "+49", "德国", "Germany","276"),
    MY("MYS", "+60", "马来西亚", "Malaysia","458"),
    AU("AUS", "+61", "澳大利亚", "Australia","036"),
    ID("IDN", "+62", "印度尼西亚", "Indonesia","360"),
    PH("PHL", "+63", "菲律宾", "Philippines","608"),
    SG("SGP", "+65", "新加坡", "Singapore","702"),
    TH("THA", "+66", "泰国", "Thailand","764"),
    JP("JPN", "+81", "日本", "Japan","392"),
    KR("KOR", "+82", "韩国", "South Korea","410"),
    IE("IRL", "+353", "爱尔兰", "Ireland","372"),
    KZ("KAZ", "+7", "哈萨克斯坦", "Kazakhstan","398"),
    PT("PRT", "+351", "葡萄牙", "Portugal","620"),
    LT("LTU", "+370", "立陶宛", "Lithuania","440"),
    LU("LUX", "+352", "卢森堡", "Luxembourg","442"),
    HR("HRV", "+385", "克罗地亚", "Croatia","191"),
    LV("LVA", "+371", "拉脱维亚", "Latvia","428"),
    HU("HUN", "+36", "匈牙利", "Hungary","348"),
    QA("QAT", "+974", "卡塔尔", "Qatar","634"),
    MA("MAR", "+212", "摩洛哥", "Morocco","504"),
    MC("MCO", "+377", "摩纳哥", "Monaco","492"),
    UM("UMI", "+1", "美国本土外小岛屿", "United States Minor Outlying Islands","581"),
    EE("EST", "+372", "爱沙尼亚", "Estonia","233"),
    MM("MMR", "+95", "缅甸", "Myanmar","104"),
    EG("EGY", "+20", "埃及", "Egypt","818"),
    IL("ISR", "+972", "以色列", "Israel","376"),
    AE("ARE", "+971", "阿联酋", "United Arab Emirates","784"),
    UY("URY", "+598", "乌拉圭", "Uruguay","858"),
    IN("IND", "+91", "印度", "India","356"),
    ZA("ZAF", "+27", "南非", "South Africa","710"),
    IS("ISL", "+354", "冰岛", "Iceland","352"),
    IT("ITA", "+39", "意大利", "Italy","380"),
    MX("MEX", "+52", "墨西哥", "Mexico","484"),
    AR("ARG", "+54", "阿根廷", "Argentina","032"),
    AT("AUT", "+43", "奥地利", "Austria","040"),
    VN("VNM", "+84", "越南", "Vietnam","704"),
    NG("NGA", "+234", "尼日利亚", "Nigeria","566"),
    RO("ROU", "+40", "罗马尼亚", "Romania","642"),
    RS("SRB", "+381", "塞尔维亚", "Serbia","688"),
    RU("RUS", "+7", "俄罗斯", "Russia","643"),
    NR("NRU", "+674", "瑙鲁", "Nauru","520"),
    BG("BGR", "+359", "保加利亚", "Bulgaria","100"),
    BN("BRN", "+673", "文莱", "Brunei","096"),
    NZ("NZL", "+64", "新西兰", "New Zealand","554"),
    BO("BOL", "+591", "玻利维亚", "Bolivia","068"),
    SA("SAU", "+966", "沙特阿拉伯", "Saudi Arabia","682"),
    BR("BRA", "+55", "巴西", "Brazil","076"),
    SI("SVN", "+386", "斯洛文尼亚", "Slovenia","705"),
    BY("BLR", "+375", "白俄罗斯", "Belarus","112"),
    SK("SVK", "+421", "斯洛伐克", "Slovakia","703"),
    KE("KEN", "+254", "肯尼亚", "Kenya","404"),
    KH("KHM", "+855", "柬埔寨", "Cambodia","116"),
    GE("GEO", "+995", "格鲁吉亚", "Georgia","268"),
    CF("CAF", "+236", "中非共和国", "Central African Republic","140"),
    CH("CHE", "+41", "瑞士", "Switzerland","756"),
    GL("GRL", "+299", "格林兰", "Greenland","304"),
    CL("CHL", "+56", "智利", "Chile","152"),
    CO("COL", "+57", "哥伦比亚", "Colombia","170"),
    KY("CYM", "+1-345", "开曼群岛", "Cayman Islands","136"),
    CR("CRI", "+506", "哥斯达黎加", "Costa Rica","188"),
    PA("PAN", "+507", "巴拿马", "Panama","591"),
    PE("PER", "+51", "秘鲁", "Peru","604"),
    CZ("CZE", "+420", "捷克共和国", "Czech Republic","203"),
    PK("PAK", "+92", "巴基斯坦", "Pakistan","586"),
    TR("TUR", "+90", "土耳其", "Turkey","792"),
    AF("AFG", "+93", "阿富汗", "Afghanistan","004"),
    AL("ALB", "+355", "阿尔巴尼亚共和国", "Albania","008"),
    DZ("DZA", "+213", "阿尔及利亚民主人民共和国", "Algeria","012"),
    AS("ASM", "+1684", "美属萨摩亚", "American Samoa","016"),
    AD("AND", "+376", "安道尔公国", "Andorra","020"),
    AO("AGO", "+244", "安哥拉共和国", "Angola","024"),
    AI("AIA", "+1264", "安圭拉", "Anguilla","660"),
    AG("ATG", "+1268", "安提瓜和巴布达", "Antigua and Barbuda","028"),
    AM("ARM", "+374", "亚美尼亚共和国", "Armenia","051"),
    AW("ABW", "+297", "阿鲁巴", "Aruba","533"),
    AZ("AZE", "+994", "阿塞拜疆共和国", "Azerbaijan","031"),
    BS("BHS", "+1242", "巴哈马国", "The Bahamas","044"),
    BH("BHR", "+973", "巴林王国", "Bahrain","048"),
    BD("BGD", "+880", "孟加拉人民共和国", "Bangladesh","050"),
    BB("BRB", "+1246", "巴巴多斯", "Barbados","052"),
    BZ("BLZ", "+501", "伯利兹", "Belize","084"),
    BJ("BEN", "+229", "贝宁共和国", "Benin","204"),
    BM("BMU", "+1441", "百慕大", "Bermuda","060"),
    BT("BTN", "+975", "不丹王国", "Bhutan","064"),
    BA("BIH", "+387", "波斯尼亚和黑塞哥维那", "Bosnia and Herzegovina","070"),
    BW("BWA", "+267", "博茨瓦纳共和国", "Botswana","072"),
    VG("VGB", "+1340", "英属维尔京群岛", "British Virgin Islands","092"),
    BF("BFA", "+226", "布基纳法索", "Burkina Faso","854"),
    BI("BDI", "+257", "布隆迪共和国", "Burundi","108"),
    CM("CMR", "+237", "喀麦隆共和国", "Cameroon","120"),
    CV("CPV", "+238", "佛得角共和国", "Cape Verde","132"),
    TD("TCD", "+235", "乍得共和国", "Chad","148"),
    CX("CXR", "+9164", "圣诞岛", "Christmas Island","162"),
    CC("CCK", "+9162", "科科斯（基林）群岛", "Cocos (Keeling) Islands","166"),
    KM("COM", "+269", "科摩罗联盟", "Comoros","174"),
    CG("COG", "+242", "刚果共和国", "Republic of the Congo","178"),
    CK("COK", "+682", "库克群岛", "Cook Islands","184"),
    CI("CIV", "+225", "科特迪瓦共和国", "Cote d'Ivoire","384"),
    CU("CUB", "+53", "古巴共和国", "Cuba","192"),
    CY("CYP", "+357", "塞浦路斯共和国", "Cyprus","196"),
    DJ("DJI", "+253", "吉布提共和国", "Djibouti","262"),
    DM("DMA", "+1767", "多米尼克", "Dominica","212"),
    DO("DOM", "+1809", "多米尼加共和国", "Dominican Republic","214"),
    EC("ECU", "+593", "厄瓜多尔共和国", "Ecuador","218"),
    SV("SLV", "+503", "萨尔瓦多共和国", "El Salvador","222"),
    GQ("GNQ", "+240", "赤道几内亚共和国", "Equatorial Guinea","226"),
    ER("ERI", "+291", "厄立特里亚国", "Eritrea","232"),
    ET("ETH", "+251", "埃塞俄比亚联邦民主共和国", "Ethiopia","231"),
    FK("FLK", "+500", "福克兰群岛", "Falkland Islands (Islas Malvinas)","238"),
    FO("FRO", "+298", "法罗群岛", "Faroe Islands","234"),
    FJ("FJI", "+679", "斐济共和国", "Fiji","242"),
    FI("FIN", "+358", "芬兰共和国", "Finland","246"),
    GF("GUF", "+594", "圭亚那", "French Guiana","254"),
    PF("PYF", "+689", "法属波利尼西亚", "French Polynesia","258"),
    GA("GAB", "+241", "加蓬共和国", "Gabon","266"),
    GM("GMB", "+220", "冈比亚共和国", "The Gambia","270"),
    GH("GHA", "+233", "加纳共和国", "Ghana","288"),
    GI("GIB", "+350", "直布罗陀", "Gibraltar","292"),
    GD("GRD", "+1473", "格林纳达", "Grenada","308"),
    GP("GLP", "+590", "瓜德罗普", "Guadeloupe","312"),
    GU("GUM", "+1671", "关岛", "Guam","316"),
    GT("GTM", "+502", "危地马拉共和国", "Guatemala","320"),
    GN("GIN", "+224", "几内亚共和国", "Guinea","324"),
    GW("GNB", "+245", "几内亚比绍共和国", "Guinea-Bissau","624"),
    GY("GUY", "+592", "圭亚那合作共和国", "Guyana","328"),
    HT("HTI", "+509", "海地共和国", "Haiti","332"),
    VA("VAT", "+379", "梵蒂冈", "Holy See (Vatican City)","336"),
    HN("HND", "+504", "洪都拉斯共和国", "Honduras","340"),
    IR("IRN", "+98", "伊朗伊斯兰共和国", "Iran","364"),
    IQ("IRQ", "+964", "伊拉克共和国", "Iraq","368"),
    JM("JAM", "+1876", "牙买加", "Jamaica","388"),
    JO("JOR", "+962", "约旦哈希姆王国", "Jordan","400"),
    KI("KIR", "+686", "基里巴斯共和国", "Kiribati","296"),
    KP("PRK", "+85", "朝鲜民主主义人民共和国", "North Korea","408"),
    KW("KWT", "+965", "科威特国", "Kuwait","414"),
    KG("KGZ", "+996", "吉尔吉斯共和国", "Kyrgyzstan","417"),
    LA("LAO", "+856", "老挝人民民主共和国", "Laos","418"),
    LB("LBN", "+961", "黎巴嫩共和国", "Lebanon","422"),
    LS("LSO", "+266", "莱索托王国", "Lesotho","426"),
    LR("LBR", "+231", "利比里亚共和国", "Liberia","430"),
    LY("LBY", "+218", "利比亚国", "Libya","434"),
    LI("LIE", "+423", "列支敦士登公国", "Liechtenstein","438"),
    MK("MKD", "+389", "北马其顿共和国", "North Macedonia","807"),
    MG("MDG", "+261", "马达加斯加共和国", "Madagascar","450"),
    MW("MWI", "+265", "马拉维共和国", "Malawi","454"),
    MV("MDV", "+960", "马尔代夫共和国", "Maldives","462"),
    ML("MLI", "+223", "马里共和国", "Mali","466"),
    MT("MLT", "+356", "马耳他共和国", "Malta","470"),
    IM("IMN", "+44", "马恩岛", "Isle of Man","833"),
    MH("MHL", "+692", "马绍尔群岛共和国", "Marshall Islands","584"),
    MQ("MTQ", "+596", "马提尼克", "Martinique","474"),
    MR("MRT", "+222", "毛里塔尼亚伊斯兰共和国", "Mauritania","478"),
    MU("MUS", "+230", "毛里求斯共和国", "Mauritius","480"),
    YT("MYT", "+269", "马约特省", "Mayotte","175"),
    FM("FSM", "+691", "密克罗尼西亚联邦", "Federated States of Micronesia","583"),
    MD("MDA", "+373", "摩尔多瓦共和国", "Moldova","498"),
    MN("MNG", "+976", "蒙古国", "Mongolia","496"),
    MS("MSR", "+1664", "蒙特塞拉特", "Montserrat","500"),
    MZ("MOZ", "+258", "莫桑比克共和国", "Mozambique","508"),
    NA("NAM", "+264", "纳米比亚共和国", "Namibia","516"),
    NP("NPL", "+977", "尼泊尔联邦民主共和国", "Nepal","524"),
    AN("ANT", "+599", "荷属安的列斯", "Netherlands Antilles",""),
    NC("NCL", "+687", "新喀里多尼亚", "New Caledonia","540"),
    NI("NIC", "+505", "尼加拉瓜共和国", "Nicaragua","558"),
    NE("NER", "+227", "尼日尔共和国", "Niger","562"),
    NU("NIU", "+683", "纽埃", "Niue","570"),
    NF("NFK", "+6723", "诺福克岛", "Norfolk Island","574"),
    MP("MNP", "+1", "北马里亚纳群岛自由邦", "Northern Mariana Islands","580"),
    OM("OMN", "+968", "阿曼苏丹国", "Oman","512"),
    PW("PLW", "+680", "帕劳共和国", "Palau","585"),
    PS("PSE", "+970", "巴勒斯坦国", "Palestinian Territory","275"),
    PG("PNG", "+675", "巴布亚新几内亚独立国", "Papua New Guinea","598"),
    PY("PRY", "+595", "巴拉圭共和国", "Paraguay","600"),
    PN("PCN", "+64", "皮特凯恩、亨德森、迪西和奥埃诺群岛", "Pitcairn Islands","612"),
    PR("PRI", "+1787", "波多黎各自由邦", "Puerto Rico","630"),
    RE("REU", "+262", "留尼汪", "Reunion","638"),
    RW("RWA", "+250", "卢旺达共和国", "Rwanda","646"),
    KN("KNA", "+1", "圣基茨和尼维斯联邦", "Saint Kitts and Nevis","659"),
    LC("LCA", "+1758", "圣卢西亚", "Saint Lucia","662"),
    PM("SPM", "+508", "圣皮埃尔和密克隆", "Saint Pierre and Miquelon","666"),
    VC("VCT", "+1784", "圣文森特和格林纳丁斯", "Saint Vincent and the Grenadines","670"),
    SM("SMR", "+378", "最庄严尊贵的圣马力诺共和国", "San Marino","674"),
    ST("STP", "+239", "圣多美和普林西比民主共和国", "Sao Tome and Principe","678"),
    SN("SEN", "+221", "塞内加尔共和国", "Senegal","686"),
    SC("SYC", "+248", "塞舌尔共和国", "Seychelles","690"),
    SL("SLE", "+232", "塞拉利昂共和国", "Sierra Leone","694"),
    SB("SLB", "+677", "所罗门群岛", "Solomon Islands","090"),
    SO("SOM", "+252", "索马里联邦共和国", "Somalia","706"),
    LK("LKA", "+94", "斯里兰卡民主社会主义共和国", "Sri Lanka","144"),
    SD("SDN", "+249", "苏丹共和国", "Sudan","729"),
    SR("SUR", "+597", "苏里南共和国", "Suriname","740"),
    SJ("SJM", "+47", "斯瓦尔巴", "Svalbard","744"),
    SZ("SWZ", "+268", "斯威士兰王国", "Eswatini","748"),
    SY("SYR", "+963", "阿拉伯叙利亚共和国", "Syria","760"),
    TJ("TJK", "+992", "塔吉克斯坦共和国", "Tajikistan","762"),
    TZ("TZA", "+255", "坦桑尼亚联合共和国", "Tanzania","834"),
    TG("TGO", "+228", "多哥共和国", "Togo","768"),
    TK("TKL", "+690", "托克劳", "Tokelau","772"),
    TO("TON", "+676", "汤加王国", "Tonga","776"),
    TT("TTO", "+1868", "特立尼达和多巴哥共和国", "Trinidad and Tobago","780"),
    TN("TUN", "+216", "突尼斯共和国", "Tunisia","788"),
    TM("TKM", "+993", "土库曼斯坦", "Turkmenistan","795"),
    TC("TCA", "+1649", "特克斯和凯科斯群岛", "Turks and Caicos Islands","796"),
    TV("TUV", "+688", "图瓦卢", "Tuvalu","798"),
    UG("UGA", "+256", "乌干达共和国", "Uganda","800"),
    UA("UKR", "+380", "乌克兰", "Ukraine","804"),
    UZ("UZB", "+998", "乌兹别克斯坦共和国", "Uzbekistan","860"),
    VU("VUT", "+678", "瓦努阿图共和国", "Vanuatu","548"),
    VE("VEN", "+58", "委内瑞拉玻利瓦尔共和国", "Venezuela","862"),
    VI("VIR", "+1284", "美属维尔京群岛", "Virgin Islands","850"),
    WF("WLF", "+681", "瓦利斯和富图纳", "Wallis and Futuna","876"),
    WS("WSM", "+685", "萨摩亚独立国", "Western Samoa","882"),
    YE("YEM", "+967", "也门共和国", "Yemen","887"),
    CD("COD", "+243", "刚果民主共和国", "Democratic Republic of the Congo","180"),
    ZM("ZMB", "+260", "赞比亚共和国", "Zambia","894"),
    ZW("ZWE", "+263", "津巴布韦共和国", "Zimbabwe","716"),
    AQ("ATA", "+672", "南极洲", "Antarctica","010"),
    BV("BVT", "+1", "布韦岛", "Bouvet Island","074"),
    IO("IOT", "+246", "英属印度洋领地", "British Indian Ocean Territory","086"),
    TF("ATF", "+1", "凯尔盖朗群岛", "French Southern and Antarctic Lands","260"),
    GG("GGY", "+44-1481", "根西行政区", "Guernsey","831"),
    BL("BLM", "+590", "圣巴泰勒米集体", "Saint Barthélemy","652"),
    ME("MNE", "+382", "黑山", "Montenegro","499"),
    JE("JEY", "+44", "泽西行政区", "Jersey","832"),
    CW("CUW", "+599", "库拉索", "Curaçao",""),
    MF("MAF", "+1-721", "圣马丁集体", "Saint Martin","663"),
    SX("SXM", "+1721", "圣马丁", "Sint Maarten",""),
    TL("TLS", "+670", "东帝汶民主共和国", "Timor-Leste","626"),
    SS("SSD", "+211", "南苏丹共和国", "South Sudan","728"),
    AX("ALA", "+358", "奥兰区", "Åland Islands","248"),
    BQ("BES", "+599", "博奈尔", "Bonaire","535"),
    XK("XKS", "+383", "科索沃共和国", "Republic of Kosovo","");

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

    /**
     * 国家编码 三位数字代码
     */
    private final String countryNumCode;


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
        for (InternationalRegionCode value : values()) {
            result.put(value.getCountryCode(), value);
            result.put(value.getCountryNumCode(), value);
        }
        return result;
    }

}

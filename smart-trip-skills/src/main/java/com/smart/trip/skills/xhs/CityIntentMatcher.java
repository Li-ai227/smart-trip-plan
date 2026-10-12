package com.smart.trip.skills.xhs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 基于知识库城市键的确定性目的地意图识别器（C-012-E）。
 *
 * <p>从用户输入中识别已存在于知识库的城市名；当输入仅包含省份/区域名时，
 * 通过显式热门城市映射落到知识库城市，不做任意 NLP 推断。</p>
 *
 * <p>识别顺序：精确匹配 &gt; 城市名包含匹配 &gt; 省份别名映射（多城市）。</p>
 */
final class CityIntentMatcher {

    private static final String PUNCTUATIONS = "，。！？、；：:,.!?()（）[]【】《》<>·-_/\\|\"'`~ ";

    /** 省份/区域别名 → 知识库热门城市列表（仅目标城市存在于 knownCities 时生效）。 */
    private static final List<ProvinceAlias> PROVINCE_ALIASES = List.of(
        // 云南
        alias("云南", "大理", "昆明", "丽江"),
        alias("云南省", "大理", "昆明", "丽江"),

        // 四川
        alias("四川", "成都", "九寨沟", "乐山"),
        alias("四川省", "成都", "九寨沟", "乐山"),

        // 浙江
        alias("浙江", "杭州", "宁波", "绍兴", "温州", "舟山"),
        alias("浙江省", "杭州", "宁波", "绍兴", "温州", "舟山"),

        // 江苏
        alias("江苏", "南京", "苏州", "无锡", "扬州", "镇江", "常州"),
        alias("江苏省", "南京", "苏州", "无锡", "扬州", "镇江", "常州"),

        // 广东
        alias("广东", "广州", "深圳", "珠海"),
        alias("广东省", "广州", "深圳", "珠海"),

        // 福建
        alias("福建", "厦门", "福州", "泉州"),
        alias("福建省", "厦门", "福州", "泉州"),

        // 山东
        alias("山东", "青岛", "济南", "威海"),
        alias("山东省", "青岛", "济南", "威海"),

        // 海南
        alias("海南", "三亚", "海口"),
        alias("海南省", "三亚", "海口"),

        // 辽宁
        alias("辽宁", "大连", "沈阳", "丹东"),
        alias("辽宁省", "大连", "沈阳", "丹东"),

        // 河南
        alias("河南", "洛阳", "郑州", "开封"),
        alias("河南省", "洛阳", "郑州", "开封"),

        // 安徽
        alias("安徽", "黄山", "合肥"),
        alias("安徽省", "黄山", "合肥"),

        // 江西
        alias("江西", "南昌"),
        alias("江西省", "南昌"),

        // 湖北
        alias("湖北", "武汉", "宜昌", "襄阳", "十堰"),
        alias("湖北省", "武汉", "宜昌", "襄阳", "十堰"),

        // 湖南
        alias("湖南", "长沙", "张家界", "岳阳"),
        alias("湖南省", "长沙", "张家界", "岳阳"),

        // 广西
        alias("广西", "桂林", "南宁", "北海"),
        alias("广西省", "桂林", "南宁", "北海"),
        alias("广西壮族自治区", "桂林", "南宁"),

        // 贵州
        alias("贵州", "贵阳", "遵义"),
        alias("贵州省", "贵阳", "遵义"),

        // 陕西
        alias("陕西", "西安"),
        alias("陕西省", "西安"),

        // 甘肃
        alias("甘肃", "兰州", "敦煌"),
        alias("甘肃省", "兰州", "敦煌"),

        // 西藏
        alias("西藏", "拉萨"),
        alias("西藏自治区", "拉萨"),

        // 新疆
        alias("新疆", "乌鲁木齐"),
        alias("新疆维吾尔自治区", "乌鲁木齐"),

        // 青海
        alias("青海", "西宁"),
        alias("青海省", "西宁"),

        // 内蒙古
        alias("内蒙古", "呼和浩特"),
        alias("内蒙古自治区", "呼和浩特"),

        // 宁夏
        alias("宁夏", "银川"),
        alias("宁夏回族自治区", "银川"),

        // 山西
        alias("山西", "太原"),
        alias("山西省", "太原"),

        // 吉林
        alias("吉林", "长春", "延吉"),
        alias("吉林省", "长春", "延吉"),

        // 黑龙江
        alias("黑龙江", "哈尔滨"),
        alias("黑龙江省", "哈尔滨"),

        // 河北
        alias("河北", "秦皇岛", "北京", "天津"),
        alias("河北省", "秦皇岛", "北京", "天津"),

        // 台湾/香港/澳门
        alias("台湾", "厦门"),
        alias("台湾省", "厦门"),
        alias("香港", "深圳"),
        alias("澳门", "珠海")
    );

    private final List<String> knownCities;
    private final List<ProvinceAlias> activeAliases;

    CityIntentMatcher(Collection<String> knownCities) {
        if (knownCities == null || knownCities.isEmpty()) {
            this.knownCities = List.of();
            this.activeAliases = List.of();
            return;
        }
        List<String> cities = new ArrayList<>();
        for (String city : knownCities) {
            if (city != null && !city.isBlank()) {
                cities.add(normalize(city));
            }
        }
        cities.sort(Comparator.naturalOrder());
        this.knownCities = Collections.unmodifiableList(cities);
        this.activeAliases = PROVINCE_ALIASES.stream()
                .filter(a -> a.targetCities.stream().anyMatch(this.knownCities::contains))
                .map(a -> new ProvinceAlias(a.aliasName,
                        a.targetCities.stream().filter(this.knownCities::contains).toList()))
                .toList();
    }

    /**
     * 从输入中识别单个最佳匹配城市。多城市省份别名返回首个人口城市。
     */
    Optional<String> match(String input) {
        String normalized = normalize(input);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        if (knownCities.contains(normalized)) {
            return Optional.of(normalized);
        }
        Optional<String> cityMatch = containMatch(normalized);
        if (cityMatch.isPresent()) {
            return cityMatch;
        }
        return provinceMatchSingle(normalized);
    }

    /**
     * 从输入中识别所有匹配的知识库城市。
     *
     * <p>精确/包含匹配返回单城市列表；省份别名返回其全部目标城市。</p>
     */
    List<String> matchAll(String input) {
        String normalized = normalize(input);
        if (normalized.isEmpty()) {
            return List.of();
        }
        if (knownCities.contains(normalized)) {
            return List.of(normalized);
        }
        Optional<String> cityMatch = containMatch(normalized);
        if (cityMatch.isPresent()) {
            return List.of(cityMatch.get());
        }
        return provinceMatchAll(normalized);
    }

    private Optional<String> containMatch(String normalized) {
        Match best = null;
        for (String city : knownCities) {
            int index = normalized.indexOf(city);
            if (index < 0) {
                continue;
            }
            Match candidate = new Match(city, index);
            if (best == null || candidate.compareTo(best) < 0) {
                best = candidate;
            }
        }
        return best == null ? Optional.empty() : Optional.of(best.city());
    }

    private Optional<String> provinceMatchSingle(String normalized) {
        ProvinceMatch best = null;
        for (ProvinceAlias alias : activeAliases) {
            int index = normalized.indexOf(alias.aliasName);
            if (index < 0) {
                continue;
            }
            ProvinceMatch candidate = new ProvinceMatch(alias, index);
            if (best == null || candidate.compareTo(best) < 0) {
                best = candidate;
            }
        }
        if (best == null) {
            return Optional.empty();
        }
        return Optional.of(best.alias.targetCities.get(0));
    }

    private List<String> provinceMatchAll(String normalized) {
        ProvinceMatch best = null;
        for (ProvinceAlias alias : activeAliases) {
            int index = normalized.indexOf(alias.aliasName);
            if (index < 0) {
                continue;
            }
            ProvinceMatch candidate = new ProvinceMatch(alias, index);
            if (best == null || candidate.compareTo(best) < 0) {
                best = candidate;
            }
        }
        if (best == null) {
            return List.of();
        }
        return best.alias.targetCities;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (!Character.isWhitespace(ch) && PUNCTUATIONS.indexOf(ch) < 0) {
                builder.append(ch);
            }
        }
        return builder.toString().trim();
    }

    private static ProvinceAlias alias(String name, String... cities) {
        return new ProvinceAlias(name, List.of(cities));
    }

    private record Match(String city, int index) implements Comparable<Match> {
        @Override
        public int compareTo(Match other) {
            int byIndex = Integer.compare(index, other.index);
            if (byIndex != 0) {
                return byIndex;
            }
            int byLength = Integer.compare(other.city.length(), city.length());
            if (byLength != 0) {
                return byLength;
            }
            return city.compareTo(other.city);
        }
    }

    private record ProvinceAlias(String aliasName, List<String> targetCities) {
    }

    private record ProvinceMatch(ProvinceAlias alias, int index) implements Comparable<ProvinceMatch> {
        @Override
        public int compareTo(ProvinceMatch other) {
            return Integer.compare(index, other.index);
        }
    }
}

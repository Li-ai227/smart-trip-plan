package com.smart.trip.skills.xhs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smart.trip.common.model.Attraction;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * {@link FileBasedAttractionCatalog} 确定性单测（C-012-D AC-D1~AC-D3/AC-D9/AC-D10）：
 * 验证从 JSON 知识库文件加载、按 ID/名称查找、损坏处理和评分边界。
 */
class FileBasedAttractionCatalogTest {

    private static final String SAMPLE_JSON = """
            {
              "cities": [
                {
                  "city": "北京",
                  "attractions": [
                    {
                      "id": "BJ001",
                      "name": "故宫博物院",
                      "location": {
                        "longitude": 116.397,
                        "latitude": 39.916,
                        "address": "北京市东城区景山前街4号",
                        "city": "北京"
                      },
                      "rating": 4.8,
                      "durationMin": 240,
                      "ticketPrice": 60.00,
                      "tags": ["历史文化", "必去", "古建筑"]
                    },
                    {
                      "id": "BJ002",
                      "name": "长城",
                      "location": {
                        "longitude": 116.570,
                        "latitude": 40.431,
                        "address": "北京市怀柔区",
                        "city": "北京"
                      },
                      "rating": 4.7,
                      "durationMin": 300,
                      "ticketPrice": 45.00,
                      "tags": ["历史文化", "世界遗产"]
                    }
                  ]
                },
                {
                  "city": "成都",
                  "attractions": [
                    {
                      "id": "CD001",
                      "name": "大熊猫繁育研究基地",
                      "location": {
                        "longitude": 104.143,
                        "latitude": 30.733,
                        "address": "成都市成华区",
                        "city": "成都"
                      },
                      "rating": 4.9,
                      "durationMin": 180,
                      "ticketPrice": 55.00,
                      "tags": ["动物", "亲子"]
                    },
                    {
                      "id": "CD002",
                      "name": "宽窄巷子",
                      "location": {
                        "longitude": 104.058,
                        "latitude": 30.664,
                        "address": "成都市青羊区",
                        "city": "成都"
                      },
                      "rating": 4.4,
                      "durationMin": 120,
                      "ticketPrice": 0.00,
                      "tags": ["美食", "文化"]
                    },
                    {
                      "id": "CD003",
                      "name": "三星评分",
                      "location": {
                        "longitude": 104.010,
                        "latitude": 30.670,
                        "address": "成都市",
                        "city": "成都"
                      },
                      "rating": 3.4,
                      "durationMin": 30,
                      "ticketPrice": 0.00,
                      "tags": ["低分"]
                    }
                  ]
                }
              ]
            }
            """;

    private static FileBasedAttractionCatalog catalogFromJson(String json) {
        InputStream is = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        return new FileBasedAttractionCatalog(is);
    }

    // ── AC-D1: 已知城市景点可被 findById 查找 ──

    @Test
    @DisplayName("AC-D1: findById 对已知 ID 返回非空 Attraction（故宫博物院）")
    void findByIdReturnsKnownAttraction() {
        FileBasedAttractionCatalog catalog = catalogFromJson(SAMPLE_JSON);
        Optional<Attraction> result = catalog.findById("BJ001");

        assertTrue(result.isPresent(), "BJ001（故宫博物院）应被找到");
        assertEquals("故宫博物院", result.get().name());
        assertEquals(4.8, result.get().rating(), 0.001);
        assertEquals("北京", result.get().location().city());
        assertEquals(240, result.get().durationMin());
        assertFalse(result.get().tags().isEmpty());
        assertNotNull(result.get().ticketPrice());
    }

    @Test
    @DisplayName("AC-D1: findById 对成都景点返回非空 Attraction")
    void findByIdReturnsChengduAttraction() {
        FileBasedAttractionCatalog catalog = catalogFromJson(SAMPLE_JSON);
        Optional<Attraction> result = catalog.findById("CD001");

        assertTrue(result.isPresent(), "CD001（大熊猫基地）应被找到");
        assertEquals("大熊猫繁育研究基地", result.get().name());
        assertEquals(4.9, result.get().rating(), 0.001);
    }

    // ── AC-D2: findByName 正确匹配 ──

    @Test
    @DisplayName("AC-D2: findByName('故宫博物院') 返回正确 Attraction（rating≥3.5）")
    void findByNameReturnsCorrectAttraction() {
        FileBasedAttractionCatalog catalog = catalogFromJson(SAMPLE_JSON);
        Optional<Attraction> result = catalog.findByName("故宫博物院");

        assertTrue(result.isPresent(), "故宫博物院应被找到");
        assertEquals("故宫博物院", result.get().name());
        assertTrue(result.get().rating() >= 3.5, "故宫博物院评分应 ≥3.5");
    }

    // ── AC-D3: 未知 ID/名称返回空 ──

    @Test
    @DisplayName("AC-D3: findById 对未知 ID 返回 Optional.empty()")
    void findByIdUnknownReturnsEmpty() {
        FileBasedAttractionCatalog catalog = catalogFromJson(SAMPLE_JSON);
        Optional<Attraction> result = catalog.findById("UNKNOWN999");

        assertFalse(result.isPresent(), "未知 ID 应返回空");
    }

    @Test
    @DisplayName("AC-D3: findByName 对未知名称返回 Optional.empty()")
    void findByNameUnknownReturnsEmpty() {
        FileBasedAttractionCatalog catalog = catalogFromJson(SAMPLE_JSON);
        Optional<Attraction> result = catalog.findByName("不存在的虚拟景点_xyz");

        assertFalse(result.isPresent(), "未知名称应返回空");
    }

    // ── AC-D9: JSON 损坏/缺失 fail-fast ──

    @Test
    @DisplayName("AC-D9: 损坏 JSON 抛出 IllegalStateException（fail-fast）")
    void corruptJsonThrowsIllegalStateException() {
        String badJson = "{ this is not valid json }";
        InputStream is = new ByteArrayInputStream(badJson.getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalStateException.class,
                () -> new FileBasedAttractionCatalog(is),
                "损坏 JSON 应抛 IllegalStateException");
    }

    @Test
    @DisplayName("AC-D9: 空 streams 抛出 IllegalStateException（fail-fast）")
    void emptyStreamThrowsIllegalStateException() {
        String emptyJson = "";
        InputStream is = new ByteArrayInputStream(emptyJson.getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalStateException.class,
                () -> new FileBasedAttractionCatalog(is),
                "空 JSON 应抛 IllegalStateException");
    }

    // ── AC-D10: 评分 < 3.5 景点仍可被 findById 查找 ──

    @Test
    @DisplayName("AC-D10: rating < 3.5 的景点也可被 findById 查找到（评分筛选归 XHSNoteAnalyzer）")
    void lowRatingAttractionStillFindableById() {
        FileBasedAttractionCatalog catalog = catalogFromJson(SAMPLE_JSON);
        Optional<Attraction> result = catalog.findById("CD003");

        assertTrue(result.isPresent(), "CD003（评分 3.4）也应可被 findById 找到");
        assertEquals(3.4, result.get().rating(), 0.001);
    }

    // ── 边界：加载 classpath 知识库 ──

    @Test
    @DisplayName("从 classpath 知识库成功加载，前 10 城市景点 ID 可查")
    void classpathCatalogLoadsSuccessfully() {
        FileBasedAttractionCatalog catalog = new FileBasedAttractionCatalog();

        assertTrue(catalog.findById("BJ001").isPresent(), "北京故宫应存在");
        assertTrue(catalog.findById("CD001").isPresent(), "成都大熊猫基地应存在");
        assertTrue(catalog.findById("SH001").isPresent(), "上海景点应存在");

        Optional<Attraction> forbiddenCity = catalog.findByName("故宫博物院");
        assertTrue(forbiddenCity.isPresent());
        assertEquals("北京", forbiddenCity.get().location().city());
    }

    // ── findByCity：按城市名返回全部景点 ──

    @Test
    @DisplayName("findByCity('北京') 从 SAMPLE_JSON 返回该城市全部 2 个景点")
    void findByCityReturnsBeiijingAttractions() {
        FileBasedAttractionCatalog catalog = catalogFromJson(SAMPLE_JSON);
        List<Attraction> result = catalog.findByCity("北京");

        assertFalse(result.isEmpty(), "findByCity('北京') 不应返回空列表");
        assertEquals(2, result.size(), "SAMPLE_JSON 北京有 2 个景点");
        assertTrue(result.stream().allMatch(a -> "北京".equals(a.location().city())),
                "所有景点的 location.city 应为北京");
    }

    @Test
    @DisplayName("findByCity('成都') 从 SAMPLE_JSON 返回该城市全部 3 个景点")
    void findByCityReturnsChengduAttractions() {
        FileBasedAttractionCatalog catalog = catalogFromJson(SAMPLE_JSON);
        List<Attraction> result = catalog.findByCity("成都");

        assertFalse(result.isEmpty(), "findByCity('成都') 不应返回空列表");
        assertEquals(3, result.size(), "SAMPLE_JSON 成都有 3 个景点");
        assertTrue(result.stream().allMatch(a -> "成都".equals(a.location().city())),
                "所有景点的 location.city 应为成都");
    }

    @Test
    @DisplayName("findByCity 对未知城市返回空列表（不抛异常）")
    void findByCityUnknownCityReturnsEmptyList() {
        FileBasedAttractionCatalog catalog = catalogFromJson(SAMPLE_JSON);
        List<Attraction> result = catalog.findByCity("不存在的城市_xyz");

        assertNotNull(result, "findByCity 不应返回 null");
        assertTrue(result.isEmpty(), "未知城市应返回空列表");
    }

    @Test
    @DisplayName("classpath 知识库 findByCity('北京') 返回 ≥1 个景点")
    void classpathCatalogFindByCityReturnsNonEmpty() {
        FileBasedAttractionCatalog catalog = new FileBasedAttractionCatalog();
        List<Attraction> result = catalog.findByCity("北京");

        assertFalse(result.isEmpty(), "classpath KB findByCity('北京') 应返回非空景点列表");
        assertTrue(result.stream().allMatch(a -> "北京".equals(a.location().city())),
                "所有返回景点的 location.city 应为北京");
    }

    @Test
    @DisplayName("classpath 知识库 findByCity 对 knowledge-base.json 任意城市均返回非空列表")
    void classpathCatalogFindByCityCoversAllKnownCities() {
        FileBasedAttractionCatalog catalog = new FileBasedAttractionCatalog();
        List<String> sampleCities = List.of("北京", "成都", "上海", "哈尔滨", "乌鲁木齐", "威海");
        for (String city : sampleCities) {
            List<Attraction> result = catalog.findByCity(city);
            assertFalse(result.isEmpty(),
                    "knowledge-base.json 城市「" + city + "」findByCity 应返回非空列表");
        }
    }

    @ParameterizedTest(name = "{0} 识别为 {1}")
    @CsvSource({
        "云南大理, 大理",
        "想去云南大理玩, 大理",
        "大理三日游, 大理",
        "大理市, 大理",
        "北京市, 北京",
        "云南, 大理",
        "云南省, 大理",
        "想去云南玩, 大理"
    })
    @DisplayName("C-012-E: findByCity 支持从目的地自然语言中识别知识库城市")
    void findByCityRecognizesIntentDestination(String input, String expectedCity) {
        FileBasedAttractionCatalog catalog = new FileBasedAttractionCatalog();
        List<Attraction> result = catalog.findByCity(input);

        assertFalse(result.isEmpty(), input + " 应识别到知识库城市「" + expectedCity + "」");
        assertTrue(result.stream().allMatch(a -> expectedCity.equals(a.location().city())),
                "所有返回景点的 location.city 应为「" + expectedCity + "」");
    }

    @Test
    @DisplayName("C-012-E: findByCity 省份级输入映射到热门城市（单城市）")
    void findByCityProvinceMapsToPopularCity() {
        FileBasedAttractionCatalog catalog = new FileBasedAttractionCatalog();
        List<Attraction> result = catalog.findByCity("云南");

        assertNotNull(result, "省份级输入也不应返回 null");
        assertFalse(result.isEmpty(), "云南 应通过热门城市映射到大理");
        assertTrue(result.stream().allMatch(a -> "大理".equals(a.location().city())),
                "云南 findByCity 应返回首个人口城市大理");
    }

    static java.util.stream.Stream<Arguments> multiCityProvider() {
        return java.util.stream.Stream.of(
                Arguments.of("云南", Set.of("大理", "昆明", "丽江")),
                Arguments.of("想去云南玩", Set.of("大理", "昆明", "丽江")),
                Arguments.of("四川", Set.of("成都", "九寨沟")),
                Arguments.of("广东", Set.of("广州", "深圳", "珠海")),
                Arguments.of("浙江", Set.of("杭州", "宁波", "绍兴"))
        );
    }

    @ParameterizedTest(name = "findMultiCity({0}) 覆盖 {1}")
    @MethodSource("multiCityProvider")
    @DisplayName("C-012-E: findMultiCity 省份输入返回多城市合并景点")
    void findMultiCityProvinceReturnsMultiCityAttractions(String input, Set<String> expectedCities) {
        FileBasedAttractionCatalog catalog = new FileBasedAttractionCatalog();
        List<Attraction> result = catalog.findMultiCity(input);

        assertFalse(result.isEmpty(), input + " 应返回多城市景点");

        Set<String> foundCities = new LinkedHashSet<>();
        for (Attraction a : result) {
            foundCities.add(a.location().city());
        }

        for (String expected : expectedCities) {
            assertTrue(foundCities.contains(expected),
                    input + " 应包含城市「" + expected + "」，实际只有: " + foundCities);
        }
    }

    @Test
    @DisplayName("C-012-E: 相近城市名不误匹配（大连不能识别成大理）")
    void findByCitySimilarCityNamesRemainSeparate() {
        FileBasedAttractionCatalog catalog = new FileBasedAttractionCatalog();
        List<Attraction> result = catalog.findByCity("大连");

        assertFalse(result.isEmpty(), "大连应返回大连景点");
        assertTrue(result.stream().allMatch(a -> "大连".equals(a.location().city())),
                "大连输入只能返回大连景点，不能混入大理");
    }
}

package com.smart.trip.skills.xhs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.trip.common.model.Attraction;
import com.smart.trip.common.model.GeoLocation;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 基于静态 JSON 知识库文件的 {@link AttractionCatalog} 实现（C-012-D）。
 *
 * <p>从 classpath {@code attractions/knowledge-base.json}（或显式传入的 {@link Path} /
 * {@link InputStream}）加载 Top 50 中国旅游城市景点数据，构建内存索引，提供 O(1) 的
 * {@link #findById} 与 {@link #findByName} 查找。</p>
 *
 * <p><b>加载策略</b>：构造时一次性解析全部景点数据，存入 {@code byId} 与 {@code byName} 两个
 * HashMap 索引。未知 ID/名称返回 {@link Optional#empty()}（同接口契约）。</p>
 *
 * <p><b>线程安全</b>：构造后只读，天然线程安全。</p>
 *
 * <p><b>fail-fast</b>：JSON 文件缺失、格式错误、字段缺失时抛出 {@link IllegalStateException}，
 * 不允许静默退化为空目录（C-012-D AC-D9）。</p>
 */
public final class FileBasedAttractionCatalog implements AttractionCatalog {

    private static final Logger LOG = Logger.getLogger(FileBasedAttractionCatalog.class.getName());
    private static final String CLASSPATH_RESOURCE = "attractions/knowledge-base.json";

    private final Map<String, Attraction> byId;
    private final Map<String, List<Attraction>> byName;
    private final Map<String, List<Attraction>> byCity;
    private final CityIntentMatcher cityIntentMatcher;

    /**
     * 从 classpath {@code attractions/knowledge-base.json} 加载知识库。
     *
     * @throws IllegalStateException 当文件不存在、损坏或 JSON 解析失败
     */
    public FileBasedAttractionCatalog() {
        InputStream is = getClass().getClassLoader().getResourceAsStream(CLASSPATH_RESOURCE);
        if (is == null) {
            throw new IllegalStateException(
                    "景点知识库文件未找到: classpath:" + CLASSPATH_RESOURCE);
        }
        KnowledgeBase kb = parse(is);
        this.byId = Collections.unmodifiableMap(kb.byId);
        this.byName = Collections.unmodifiableMap(kb.byName);
        this.byCity = Collections.unmodifiableMap(kb.byCity);
        this.cityIntentMatcher = new CityIntentMatcher(this.byCity.keySet());
        LOG.log(Level.INFO, "景点知识库加载完成: {0} 城市, {1} 景点, {2} 名称索引条目",
                new Object[]{kb.cityCount, kb.attractionCount, kb.byName.size()});
    }

    /**
     * 从显式 {@link Path} 加载知识库（测试用）。
     *
     * @param jsonPath JSON 文件路径
     * @throws IllegalStateException 当文件读取或解析失败
     */
    public FileBasedAttractionCatalog(Path jsonPath) {
        try (InputStream is = Files.newInputStream(jsonPath)) {
            KnowledgeBase kb = parse(is);
            this.byId = Collections.unmodifiableMap(kb.byId);
            this.byName = Collections.unmodifiableMap(kb.byName);
            this.byCity = Collections.unmodifiableMap(kb.byCity);
            this.cityIntentMatcher = new CityIntentMatcher(this.byCity.keySet());
            LOG.log(Level.INFO, "景点知识库加载完成: {0} 城市, {1} 景点",
                    new Object[]{kb.cityCount, kb.attractionCount});
        } catch (IOException ex) {
            throw new IllegalStateException("读取景点知识库文件失败: " + jsonPath, ex);
        }
    }

    /**
     * 从 {@link InputStream} 加载知识库（测试用）。
     *
     * @param inputStream JSON 输入流，非 null
     * @throws IllegalStateException 当 JSON 解析失败或数据格式错误
     */
    public FileBasedAttractionCatalog(InputStream inputStream) {
        KnowledgeBase kb = parse(inputStream);
        this.byId = Collections.unmodifiableMap(kb.byId);
        this.byName = Collections.unmodifiableMap(kb.byName);
        this.byCity = Collections.unmodifiableMap(kb.byCity);
        this.cityIntentMatcher = new CityIntentMatcher(this.byCity.keySet());
        LOG.log(Level.INFO, "景点知识库加载完成: {0} 城市, {1} 景点",
                new Object[]{kb.cityCount, kb.attractionCount});
    }

    @Override
    public Optional<Attraction> findById(String attractionId) {
        return Optional.ofNullable(byId.get(attractionId));
    }

    @Override
    public Optional<Attraction> findByName(String name) {
        List<Attraction> matches = byName.get(name);
        if (matches == null || matches.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(matches.get(0));
    }

    /**
     * 按城市名或包含知识库城市名的目的地自然语言返回景点列表（单城市）。
     *
     * <p>例如 {@code 大理}、{@code 云南大理}、{@code 想去大理玩} 均可命中知识库城市 {@code 大理}；
     * 省份级输入（如 {@code 云南}）通过省份别名映射返回首个人口城市（{@code 大理}）的景点。</p>
     *
     * @see #findMultiCity(String)
     */
    @Override
    public List<Attraction> findByCity(String city) {
        Optional<String> resolvedCity = cityIntentMatcher.match(city);
        if (resolvedCity.isEmpty()) {
            return List.of();
        }
        List<Attraction> list = byCity.get(resolvedCity.get());
        return list != null ? list : List.of();
    }

    /**
     * 按目的地自然语言返回多城市合并景点列表（C-012-E 多城市省份别名）。
     *
     * <p>当输入匹配到省份别名时（如 {@code 云南}、{@code 想去云南玩}），
     * 返回该省份全部热门知识库城市（如大理 + 昆明 + 丽江）的所有景点合并列表；
     * 输入匹配到单个城市时行为同 {@link #findByCity(String)}。</p>
     */
    List<Attraction> findMultiCity(String destination) {
        List<String> cities = cityIntentMatcher.matchAll(destination);
        if (cities.isEmpty()) {
            return List.of();
        }
        List<Attraction> all = new ArrayList<>();
        for (String city : cities) {
            List<Attraction> list = byCity.get(city);
            if (list != null) {
                all.addAll(list);
            }
        }
        return all;
    }

    /**
     * 解析并索引知识点 JSON。
     */
    private static KnowledgeBase parse(InputStream inputStream) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            Root root = mapper.readValue(inputStream, Root.class);
            if (root.cities == null || root.cities.isEmpty()) {
                throw new IllegalStateException("景点知识库为空：至少需包含 1 个城市");
            }

            Map<String, Attraction> idIndex = new HashMap<>();
            Map<String, List<Attraction>> nameIndex = new HashMap<>();
            Map<String, List<Attraction>> cityIndex = new HashMap<>();
            int totalAttractions = indexCities(root, idIndex, nameIndex, cityIndex);

            return new KnowledgeBase(idIndex, nameIndex, cityIndex, root.cities.size(), totalAttractions);
        } catch (IOException ex) {
            throw new IllegalStateException("景点知识库 JSON 解析失败", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("景点知识库加载失败: " + ex.getMessage(), ex);
        }
    }

    /** 索引全部城市景点数据。提取为独立方法以满足 Checkstyle 圈复杂度约束。 */
    private static int indexCities(Root root,
                                   Map<String, Attraction> idIndex,
                                   Map<String, List<Attraction>> nameIndex,
                                   Map<String, List<Attraction>> cityIndex) {
        int total = 0;
        for (CityData city : root.cities) {
            if (city.attractions == null || city.attractions.isEmpty()) {
                continue;
            }
            total += indexCity(city, idIndex, nameIndex, cityIndex);
        }
        return total;
    }

    /** 索引单个城市的所有景点。 */
    private static int indexCity(CityData city,
                                 Map<String, Attraction> idIndex,
                                 Map<String, List<Attraction>> nameIndex,
                                 Map<String, List<Attraction>> cityIndex) {
        int count = 0;
        for (AttractionData ad : city.attractions) {
            GeoLocation location = new GeoLocation(
                    ad.location.longitude,
                    ad.location.latitude,
                    ad.location.address != null ? ad.location.address : "",
                    city.city);

            Attraction attraction = new Attraction(
                    ad.id,
                    ad.name,
                    location,
                    ad.rating,
                    ad.durationMin,
                    BigDecimal.valueOf(ad.ticketPrice),
                    ad.tags != null ? ad.tags : List.of());

            if (idIndex.containsKey(ad.id)) {
                LOG.log(Level.WARNING, "景点 ID 重复: {0}（{1}），后加载覆盖前加载",
                        new Object[]{ad.id, ad.name});
            }
            idIndex.put(ad.id, attraction);
            nameIndex.computeIfAbsent(ad.name, k -> new ArrayList<>()).add(attraction);
            cityIndex.computeIfAbsent(city.city, k -> new ArrayList<>()).add(attraction);
            count++;
        }
        return count;
    }

    // ===== 内部 JSON 映射模型 =====

    /** JSON 根结构。 */
    private static final class Root {
        public List<CityData> cities;
    }

    /** 城市数据结构。 */
    private static final class CityData {
        public String city;
        public List<AttractionData> attractions;
    }

    /** 景点 JSON 数据。 */
    private static final class AttractionData {
        public String id;
        public String name;
        public LocationData location;
        public double rating;
        public int durationMin;
        public double ticketPrice;
        public List<String> tags;
    }

    /** 位置 JSON 数据。 */
    private static final class LocationData {
        public double longitude;
        public double latitude;
        public String address;
        public String city;
    }

    /** 解析产物：三个索引 + 统计数据。 */
    private record KnowledgeBase(
            Map<String, Attraction> byId,
            Map<String, List<Attraction>> byName,
            Map<String, List<Attraction>> byCity,
            int cityCount,
            int attractionCount) {
    }
}

package com.smart.trip.agent.route.mcp;

import com.smart.trip.common.model.GeoLocation;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * 进程内 TTL 坐标缓存（C-007 增量 B / AC-8）。
 *
 * <p>厂商无关（以 {@code address|city} 为 key），避免切换厂商后重复地理编码。
 * 线程安全（{@link ConcurrentHashMap}），可注入时钟以便测试 TTL。</p>
 */
public final class GeocodingCache {

    private static final long DEFAULT_TTL_MILLIS = 10 * 60 * 1000L;

    private final ConcurrentHashMap<String, CachedEntry> cache = new ConcurrentHashMap<>();
    private final long ttlMillis;
    private final LongSupplier clock;

    /**
     * @param ttlMillis 缓存存活时间（毫秒）
     * @param clock     时间源，用于测试注入
     */
    public GeocodingCache(long ttlMillis, LongSupplier clock) {
        this.ttlMillis = ttlMillis;
        this.clock = clock;
    }

    /**
     * 默认 TTL 10 分钟。
     */
    public GeocodingCache() {
        this(DEFAULT_TTL_MILLIS, System::currentTimeMillis);
    }

    /**
     * 查询缓存。
     *
     * @param address 地址/景点名
     * @param city    城市
     * @return 缓存中的坐标，过期或未命中返回 empty
     */
    public Optional<GeoLocation> get(String address, String city) {
        CachedEntry entry = cache.get(key(address, city));
        if (entry == null) {
            return Optional.empty();
        }
        if (clock.getAsLong() - entry.timestamp > ttlMillis) {
            cache.remove(key(address, city));
            return Optional.empty();
        }
        return Optional.of(entry.location);
    }

    /**
     * 写入缓存。
     *
     * @param address  地址/景点名
     * @param city     城市
     * @param location 解析到的坐标
     */
    public void put(String address, String city, GeoLocation location) {
        cache.put(key(address, city), new CachedEntry(location, clock.getAsLong()));
    }

    private static String key(String address, String city) {
        return address + "|" + (city != null ? city : "");
    }

    private record CachedEntry(GeoLocation location, long timestamp) {
    }
}

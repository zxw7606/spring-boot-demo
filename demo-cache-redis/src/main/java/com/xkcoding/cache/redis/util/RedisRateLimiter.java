package com.xkcoding.cache.redis.util;

import com.google.common.base.Preconditions;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

public class RedisRateLimiter {

    public static RedisTemplate<String, Serializable> redisTemplate;
    public static RedissonClient      redisson;

    private static final String LOCK_SUFFIX = ".LOCK";

    private String key;
    //-- 没秒最大请求数
    private Double maxBurstSeconds; //= ARGV[1]
    //            -- 存储的最大数量
    private Double maxPermits; // = // ARGV[2]
    //            -- 每个请求需要的毫秒
    private Double stableIntervalMillis;  //  =  1 * 1000 / max_burst_seconds
    //            -- 存储的令牌
    private Double storedPermits; // = ARGV[4]

    public long acquire() {
        return acquire(1);
    }

    public RedisRateLimiter(String key, Double maxBurstSeconds) {
        this.key = key;
        this.maxBurstSeconds = maxBurstSeconds;
        this.maxPermits = maxBurstSeconds;
        this.storedPermits = 0D;
        this.stableIntervalMillis = TimeUnit.SECONDS.toMillis(1) / maxBurstSeconds;
    }

    public long acquire(int permits) {
        Preconditions.checkArgument(permits > 0, "Requested permits (%s) must be positive", permits);
        return mutex(() -> reserveAndGetWaitLength(permits, System.currentTimeMillis()));
    }

    private Long mutex(Supplier<Long> supplier) {
        RLock fairLock = redisson.getLock("key" + LOCK_SUFFIX);
        try {
            fairLock.lock();
            return supplier.get();
        } finally {
            fairLock.unlock();
        }
    }

    public Long reserveAndGetWaitLength(int permits, long currentTimeMillis) {
        Long waitMillis = redisTemplate.execute(script, Collections.singletonList(key),
            maxBurstSeconds,
            maxPermits,
            stableIntervalMillis,
            storedPermits,
            currentTimeMillis,
            permits
        );
        try {
            TimeUnit.MILLISECONDS.sleep(waitMillis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return waitMillis;
    }


    private static String lua = "\n" +
        "\n" +
        "local key = KEYS[1]\n" +
        "-- 没秒最大请求数\n" +
        "local max_burst_seconds = ARGV[1]\n" +
        "-- 存储的最大数量\n" +
        "local max_permits = ARGV[2]\n" +
        "-- 每个请求需要的毫秒\n" +
        "--local stable_interval_millis =  1 * 1000 / max_burst_seconds\n" +
        "local stable_interval_millis =  ARGV[3]\n" +
        "-- 存储的令牌\n" +
        "local stored_permits = ARGV[4]\n" +
        "-- 获取时毫秒级间戳\n" +
        "local now_millis= ARGV[5]\n" +
        "-- 下一次刷新Token的时间Q\n" +
        "local next_free_ticket_millis = now_millis\n" +
        "-- 创建一个Redis限流器\n" +
        "if (redis.call('exists', key) == 0) then\n" +
        "  --max_burst_seconds\n" +
        "  redis.call('hset', key, \"max_burst_seconds\" , max_burst_seconds);\n" +
        "  --max_permits\n" +
        "  redis.call('hset', key, \"max_permits\", max_permits);\n" +
        "  --stable_interval_millis\n" +
        "  redis.call('hset', key, \"stable_interval_millis\", stable_interval_millis);\n" +
        "  --stored_permits\n" +
        "  redis.call('hset', key, \"stored_permits\", stored_permits);\n" +
        "  --next_free_ticket_millis\n" +
        "  redis.call('hset', key, \"next_free_ticket_millis\", next_free_ticket_millis);\n" +
        "else\n" +
        "  next_free_ticket_millis = redis.call('hget', key, 'next_free_ticket_millis');\n" +
        "  stored_permits = redis.call('hget', key, 'stored_permits');\n" +
        "end\n" +
        "\n" +
        "-- 刷新令牌\n" +
        "-- next_free_ticket_millis\n" +
        "if (tonumber(now_millis) > tonumber(next_free_ticket_millis)) then\n" +
        "  local new_permits = (now_millis - next_free_ticket_millis) / stable_interval_millis;\n" +
        "  stored_permits = math.min(max_permits, stored_permits + new_permits);\n" +
        "  next_free_ticket_millis = now_millis;\n" +
        "end\n" +
        "\n" +
        "\n" +
        "\n" +
        "-- 所需要的令牌\n" +
        "local permits = ARGV[6];\n" +
        "\n" +
        "-- 刷新令牌的数量过后在判断获取N个令牌需要等待多少时间\n" +
        "\n" +
        "local stored_permits_to_spend = math.min(permits, stored_permits);\n" +
        "\n" +
        "local fresh_permits = permits - stored_permits_to_spend;\n" +
        "\n" +
        "-- 代码参考 SmoothRateLimiter , 这里 SmoothRateLimiter 里面把wait时间放到下一次的请求里面, 这里直接返回比较符合场景\n" +
        "local wait_millis = fresh_permits * stable_interval_millis;\n" +
        "\n" +
        "stored_permits = stored_permits - stored_permits_to_spend\n" +
        "\n" +
        "-- save\n" +
        "redis.call(\"HMSET\",key,\"max_burst_seconds\", max_burst_seconds)\n" +
        "redis.call(\"HMSET\",key,\"max_permits\", max_permits)\n" +
        "redis.call(\"HMSET\",key,\"stable_interval_millis\", stable_interval_millis)\n" +
        "redis.call(\"HMSET\",key,\"stored_permits\", stored_permits)\n" +
        "redis.call(\"HMSET\",key,\"next_free_ticket_millis\", next_free_ticket_millis + wait_millis)\n" +
        "\n" +
        "return wait_millis;\n";

    private static DefaultRedisScript<Long> script = new DefaultRedisScript<>(lua, Long.class);
}

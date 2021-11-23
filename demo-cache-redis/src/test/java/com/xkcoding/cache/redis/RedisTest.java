package com.xkcoding.cache.redis;

import com.google.common.collect.Maps;
import com.xkcoding.cache.redis.entity.User;
import com.xkcoding.cache.redis.service.impl.UserServiceImpl;
import com.xkcoding.cache.redis.util.RedisRateLimiter;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.Serializable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

/**
 * <p>
 * Redis测试
 * </p>
 *
 * @author yangkai.shen
 * @date Created in 2018-11-15 17:17
 */
@Slf4j
public class RedisTest extends SpringBootDemoCacheRedisApplicationTests {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedisTemplate<String, Serializable> redisCacheTemplate;

    /**
     * 测试 Redis 操作
     */
    @Test
    public void get() {
        // 测试线程安全，程序结束查看redis中count的值是否为1000
//        ExecutorService executorService = Executors.newFixedThreadPool(1000);
//        IntStream.range(0, 1000).forEach(i -> executorService.execute(() -> stringRedisTemplate.opsForValue().increment("count", 1)));
//
//        stringRedisTemplate.opsForValue().set("k1", "v1");
//        String k1 = stringRedisTemplate.opsForValue().get("k1");
//        log.debug("【k1】= {}", k1);

        // 以下演示整合，具体Redis命令可以参考官方文档
        String key = "xkcoding:user:1";
        redisCacheTemplate.opsForValue().set(key, new User(1L, "user1"));
        // 对应 String（字符串）
        User user = (User) redisCacheTemplate.opsForValue().get(key);
        log.debug("【user】= {}", user);
    }

    @Test
    public void testForLua() {

//        --max_burst_seconds
        long max_burst_seconds = 5;
//        --max_permits
        long max_permits = 5;
//        --stable_interval_millis
        long stable_interval_millis = TimeUnit.SECONDS.toMillis(1) / max_burst_seconds;
//        --stored_permits
        long stored_permits = 5;
//        --过期时间
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("reis.lua")));
        script.setResultType(Long.class);

        Object execute = redisCacheTemplate.execute(script, Arrays.asList("1111"),
            max_burst_seconds,
            max_permits,
            stable_interval_millis,
            stored_permits,
            Instant.now().toEpochMilli(),
            6000);
        System.out.println(execute);
    }

    String script =
        "  local smooth_bursty_rate_limiter = redis.call('hget', KEYS[1], 'next_free_ticket_millis');\n"
            + "   return smooth_bursty_rate_limiter;";


    @Test
    public void testRedisRateLimiter() {

        int nThreads = 20;
        int allTime = 80;
        ExecutorService executorService = Executors.newFixedThreadPool(nThreads);
        ConcurrentMap<String, AtomicInteger> map = Maps.newConcurrentMap();
        CountDownLatch countDownLatch = new CountDownLatch(allTime);
        RedisRateLimiter testRedisRateLimiter = new RedisRateLimiter("testRedisRateLimiter", 20.0);
        for (int i = 0; i < nThreads; i++) {

            for (int j = 0; j < allTime / nThreads; j++) {
                executorService.execute(() -> {
                    Date startTime = new Date();
                    log.debug("start time = {} ", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(startTime));
                    long waitTime = testRedisRateLimiter.acquire(1);
                    log.debug("sleepTime = {}(seconds)", waitTime / 1000F);
                    String format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                    map.putIfAbsent(format, new AtomicInteger());
                    AtomicInteger atomicInteger = map.get(format);
                    int andAdd = atomicInteger.getAndAdd(1);
                    log.debug("{} date have exec {} times", format, andAdd);
                    countDownLatch.countDown();
                });

            }
        }

        try {
            countDownLatch.await();

            map.forEach((s, atomicInteger) -> log.debug("in seconds :{} qps =  {}", s, atomicInteger.get()));

        } catch (InterruptedException e) {
            e.printStackTrace();
        }


    }
}

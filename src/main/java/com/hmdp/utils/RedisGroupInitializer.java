package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;

@Component
public class RedisGroupInitializer implements CommandLineRunner {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void run(String... args) throws Exception {
        // 确保流键存在
        if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey("stream.orders"))) {
            stringRedisTemplate.opsForStream().add("stream.orders", Collections.singletonMap("init", "init"));
        }
        // 创建消费者组
        try {
            stringRedisTemplate.opsForStream().createGroup("stream.orders", "g1");
        } catch (Exception e) {
            // 组已存在时会抛出异常，忽略即可
        }
        // 创建消费者
        stringRedisTemplate.opsForStream().read(
                Consumer.from("g1", "c1"),
                StreamReadOptions.empty().count(1).block(Duration.ofSeconds(1)),
                StreamOffset.create("stream.orders", ReadOffset.from("0"))
        );
    }
}
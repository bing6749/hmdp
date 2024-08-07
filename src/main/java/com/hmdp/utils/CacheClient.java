package com.hmdp.utils;
/*
 * @author  MaRui
 * @date  2024/8/1 16:52
 * @version 1.0
 */


import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * @author MaRui
 */
@Slf4j
@Component
public class CacheClient {
    private final StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit timeUnit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, timeUnit);
    }
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        // 设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        // 写入Redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }
    /**
     * 查询缓存，不存在则查询数据库
     * 使用逻辑过期防治缓存击穿
     * @param keyPrefix 缓存key前缀
     * @param id 数据库id
     * @param type 返回类型
     * @param dbQuery 数据库查询方法
     * @param time 过期时间
     * @param timeUnit 时间单位
     * @param <R> 返回类型
     * @param <ID> 数据库id类型
     * @return 查询结果
     */
    public <R,ID> R queryWithLogicalExpire(String keyPrefix,String keyPrefixWithExpiredTime, ID id, Class<R> type, Function<ID,R> dbQuery, Long time, TimeUnit timeUnit) {
        // 查询redis中是否有缓存
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        // 不存在缓存，直接返回
        if (StrUtil.isBlank(json)) {
            return null;
        }
        // 存在缓存，将json转换为redisData对象
        // 将json转换为redisData对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 未过期，直接返回店铺信息
            return r;
        }
        // 已过期，需要缓存重建
        // 缓存重建
        // 获取互斥锁
        String lockKey = keyPrefixWithExpiredTime + id;
        if (tryLock(lockKey)){
            CACHE_REBUILD_EXECUTOR.submit( ()->{
                try{
                    //重建缓存
                    // 查询数据库
                    R r1 = dbQuery.apply(id);
                    setWithLogicalExpire(key, r1, time, timeUnit);
                }catch (Exception e){
                    throw new RuntimeException(e);
                }finally {
                    unlock(lockKey);
                }
            });
        }
        // 无论是否重建缓存，都返回查询结果
        return r;
    }

    /**
     * 查询缓存，不存在则查询数据库
     * 使用互斥锁防治缓存击穿
     * @param keyPrefix 缓存key前缀
     * @param id 数据库id
     * @param type 返回类型
     * @param dbQuery 数据库查询方法
     * @param time 过期时间
     * @param timeUnit 时间单位
     * @param <R> 返回类型
     * @param <ID> 数据库id类型
     * @return 查询结果
     */
    public <R,ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID,R> dbQuery, Long time, TimeUnit timeUnit){
        // 查询redis中是否有缓存
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        // 存在缓存，直接返回
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }
        // 缓存中查到空结果("")（解决缓存穿透），直接返回
        if (json != null) {
            return null;
        }
        // 不存在缓存，查询数据库
        R r = dbQuery.apply(id);
        // 数据库中不存在，返回错误
        if (r == null) {
            // 将空结果存入redis
            this.set(key, "", time, timeUnit);
            return null;
        }
        // 存在，将查询结果存入redis
        this.set(key, r, time, timeUnit);
        return r;
    }

    private boolean tryLock(String key){
        Boolean aBoolean = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 1, TimeUnit.MINUTES);
        return BooleanUtil.isTrue(aBoolean);
    }
    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }

}

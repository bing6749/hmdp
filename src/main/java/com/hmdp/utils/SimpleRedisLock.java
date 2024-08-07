package com.hmdp.utils;
/*
 * @author  MaRui
 * @date  2024/8/4 12:41
 * @version 1.0
 */


import cn.hutool.core.lang.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * @author MaRui
 */
public class SimpleRedisLock implements ILock{
    private String name;
    private StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }
    private static final String LOCK_PREFIX = "lock:";
    private static final String ID_PREDIX = UUID.randomUUID().toString(true) + "-";
    @Override
    public boolean tryLock(long timeOutSec) {
        //获取当前线程id
        String threadId = ID_PREDIX + Thread.currentThread().getId();
        //尝试获取锁
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(LOCK_PREFIX + name, threadId, timeOutSec, TimeUnit.SECONDS);
        //返回结果，防治包装类型的空指针异常
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
        //获取当前线程id
        String threadId = ID_PREDIX + Thread.currentThread().getId();
        //判断是否是当前线程持有的锁
        if (threadId.equals(stringRedisTemplate.opsForValue().get(LOCK_PREFIX + name))) {
            //释放锁
            stringRedisTemplate.delete(LOCK_PREFIX + name);
        }
    }
}

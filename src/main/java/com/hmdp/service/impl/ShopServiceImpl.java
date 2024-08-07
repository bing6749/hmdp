package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.RedisData;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    StringRedisTemplate stringRedisTemplate;
    @Resource
    CacheClient cacheClient;
    @Override
    public Result queryById(Long id) {
        //解决缓存穿透
//        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //使用互斥锁解决缓存击穿
//         Shop shop = queryWithMutex(id);
        //使用逻辑过期解决缓存击穿
        Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY,LOCK_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        // 返回查询结果
        if (shop == null) {
            return Result.fail("商铺不存在");
        }
        return Result.ok(shop);
    }


    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    // 利用逻辑过期解决缓存击穿
/*    private Shop queryWithLogicalExpire(Long id) {
        // 查询redis中是否有缓存
        String shopKey = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
        // 不存在缓存，直接返回
        if (StrUtil.isBlank(shopJson)) {
            return null;
        }
        // 存在缓存，将json转换为redisData对象
        // 将json转换为redisData对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 未过期，直接返回店铺信息
            return shop;
        }
        // 已过期，需要缓存重建
        // 缓存重建
        // 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        if (tryLock(lockKey)){
            CACHE_REBUILD_EXECUTOR.submit( ()->{
                try{
                    //重建缓存
                    this.saveShop2Redis(id,LOCK_SHOP_TTL);
                }catch (Exception e){
                    throw new RuntimeException(e);
                }finally {
                    unlock(lockKey);
                }
            });
        }
        // 无论是否重建缓存，都返回查询结果
        return shop;
    }*/

    // 通过互斥锁解决缓存击穿
/*
    private Shop queryWithMutex(Long id) {
        // 查询redis中是否有缓存
        String shopKey = CACHE_SHOPTYPE_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
        // 存在缓存，直接返回
        if (StrUtil.isNotBlank(shopJson)) {
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        // 缓存中查到空结果（解决缓存穿透），直接返回
        if (shopJson !=null) {
            return null;
        }
        // 不存在缓存,创建互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            if (!tryLock(lockKey)) {
                //获取锁失败,等待50ms后重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            //获取锁成果，查询数据库
            shop = getById(id);
            // 5.不存在，返回错误
            if(shop == null) {
                //将空值写入redis
                stringRedisTemplate.opsForValue().set(shopKey, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            // 存在,将查询结果写入redis
            stringRedisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            unlock(lockKey);
        }
        return shop;
    }
*/

    // 通过互斥锁解决缓存穿透
/*    public Shop queryWithPassThrough(Long id){
        // 查询redis中是否有缓存
        String shopKey = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
        // 存在缓存，直接返回
        if (StrUtil.isNotBlank(shopJson)) {
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        // 缓存中查到空结果（解决缓存穿透），直接返回
        if (shopJson !=null) {
            return null;
        }
        // 不存在缓存，查询数据库
        Shop shop = getById(id);
        // 数据库中不存在，返回错误
        if (shop == null) {
            // 将空结果存入redis
            stringRedisTemplate.opsForValue().set(shopKey, "",CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        // 存在，将查询结果存入redis
        stringRedisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;
    }*/

    // 将shop格式封装成RedisData
    public void saveShop2Redis(Long id, Long expireSeconds){
        // 查询数据库
        Shop shop = getById(id);
        // 存入redis
        // 封装data
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        String shopKey = CACHE_SHOP_KEY + id;
        stringRedisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(redisData));

    }


    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("商铺id不能为空");
        }
        //从数据库中更新
        updateById(shop);
        // 删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}

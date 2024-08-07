package com.hmdp.service.impl;

import cn.hutool.json.JSONNull;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOPTYPE_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOPTYPE_TTL;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryTypeList() {
        String key = CACHE_SHOPTYPE_KEY+1;
        // 在redis中查询商铺类型列表
        List<ShopType> resultList = new ArrayList<>();
        List<String> list = stringRedisTemplate.opsForList().range(key, 0, -1);
        // 找到了，直接返回
        if(list != null && list.size() > 0){
            // 遍历list，将list中的每个元素从string格式转成bean
            list.forEach(item -> {
                if(JSONUtil.isNull(item)){
                    return;
                }
                resultList.add(JSONUtil.toBean(item, ShopType.class));

            });
            return Result.ok(resultList);
        }
        // 没找到，查询数据库
        List<ShopType> typeList = query().orderByAsc("sort").list();
        // 没查到，返回错误
        if(typeList == null || typeList.size() == 0){
            return Result.fail("没有查询到数据");
        }
        // 查到了，存入redis
        typeList.forEach(type -> {
            stringRedisTemplate.opsForList().rightPush(key, JSONUtil.toJsonStr(type));
        });
        stringRedisTemplate.expire(key, CACHE_SHOPTYPE_TTL, TimeUnit.MINUTES);
        // 返回查询结果
        return Result.ok(typeList);
    }
}

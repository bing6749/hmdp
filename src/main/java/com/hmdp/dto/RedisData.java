package com.hmdp.dto;
/*
 * @author  MaRui
 * @date  2024/8/1 14:53
 * @version 1.0
 */


import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author MaRui
 */
@Data
public class RedisData {
    private LocalDateTime expireTime;
    private Object data;
}

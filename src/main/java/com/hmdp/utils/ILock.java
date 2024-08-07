package com.hmdp.utils;
/*
 * @author  MaRui
 * @date  2024/8/4 12:30
 * @version 1.0
 */

/**
 * 锁接口
 */
public interface ILock {
    /**
     * 尝试获取锁
     * @param timeOutSec 超时时间
     * @return
     */
    boolean tryLock(long timeOutSec);
    void unlock();
}

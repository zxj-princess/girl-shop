package com.girlshop.utils;

public interface ILock {
    /*
     * 尝试获取锁
     * timeoutSec  锁持有的超时时间，过期后自动释放
     * true   代表获取成功；false   代表获取锁失败
     * */
    boolean tryLock(long timeoutSec);

    /*
     * 释放锁
     * */
    void unlock();
}

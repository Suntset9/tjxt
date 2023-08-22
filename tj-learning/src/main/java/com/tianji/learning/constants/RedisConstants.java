package com.tianji.learning.constants;

public interface RedisConstants {

    /**
     * 签到记录的key的前缀 sign:uid:用户id：月份信息
     */
    String SIGN_RECORD_KEY_PREFIX = "sign:uid:";

    /**
     * 积分排行榜得key得前缀 ： boards:202301
     */
    String POINTS_BOARD_KEY_PREFIX = "boards:";

}

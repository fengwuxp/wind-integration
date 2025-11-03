package com.wind.integration.infrastructure.redisson;

import com.wind.common.exception.AssertUtils;
import com.wind.sequence.WindSequenceType;
import com.wind.sequence.time.DateTimeSequenceGenerator;
import com.wind.sequence.time.SequenceTimeScopeType;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 基于 redisson 的全局数字 seq 自增器
 *
 * @author wuxp
 * @date 2023-11-29 14:35
 **/
public record RedissonNumericSequenceGenerator(RedissonClient redissonClient) {

    /**
     * 以小时为维度全局自增
     *
     * @param type 流水号类型
     * @return seq，例如：202311290012311
     */
    public String hourNext(WindSequenceType type) {
        return hourNext(type, type.defaultLength());
    }

    public String hourNext(WindSequenceType type, int length) {
        return next(SequenceTimeScopeType.HOUR, type, length);
    }

    /**
     * 以天为维度全局自增
     *
     * @param type 流水号类型
     * @return seq，例如：202311290012311
     */
    public String dayNext(WindSequenceType type) {
        return next(SequenceTimeScopeType.DAY, type);
    }

    /**
     * 以天为维度全局自增
     *
     * @param type   流水号类型
     * @param length 流水号长度
     * @return seq，例如：202311290012311
     */
    public String dayNext(WindSequenceType type, int length) {
        return next(SequenceTimeScopeType.DAY, type, length);
    }

    /**
     * 以 {@param scope}为维度全局自增
     *
     * @param scope 时间维度
     * @param type  流水号类型
     * @return seq，例如：202311290012311
     */
    public String next(SequenceTimeScopeType scope, WindSequenceType type) {
        return next(scope, type, type.defaultLength());
    }

    public String next(SequenceTimeScopeType scope, WindSequenceType type, int length) {
        return type.getPrefix() + DateTimeSequenceGenerator.of(scope, () -> internalNext(type.name(), length)).next();
    }

    private String internalNext(String key, int length) {
        RAtomicLong counter = redissonClient.getAtomicLong(genKey(key));
        long seq = counter.incrementAndGet();
        if (seq <= 1) {
            // 1 天后过期
            counter.expire(Duration.ofHours(24));
        }
        AssertUtils.isTrue(String.valueOf(seq).length() <= length, "sequence exceeds maximum length");
        String format = "%0" + length + "d";
        return String.format(format, seq);
    }

    private String genKey(String key) {
        ZonedDateTime utcNow = ZonedDateTime.now(ZoneOffset.UTC);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(SequenceTimeScopeType.DAY.getPattern());
        return utcNow.format(formatter) + "_" + key;
    }
}

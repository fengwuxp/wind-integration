package com.wind.integration.infrastructure.redisson;

import com.wind.common.WindConstants;
import com.wind.common.WindDateFormater;
import com.wind.sequence.WindSequenceType;
import com.wind.sequence.time.SequenceTimeScopeType;
import com.wind.sequence.time.TemporalSequenceFactory;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * redisson 序列号支持
 *
 * @author wuxp
 * @date 2025-12-25 13:31
 **/
@Slf4j
public record RedissonTemporalSequenceSupport(RedissonClient redissonClient) {

    public RedissonTemporalSequenceSupport {
        TemporalSequenceFactory.setCounter(this::next);
        log.info("TemporalSequenceFactory Redisson counter config successful");
    }

    private long next(SequenceTimeScopeType scope, WindSequenceType sequenceType) {
        RAtomicLong counter = redissonClient.getAtomicLong(genKey(scope, sequenceType));
        long seq = counter.incrementAndGet();
        if (seq <= 1) {
            counter.expire(getExpireTime(scope));
        }
        return seq;
    }

    private @NonNull String genKey(SequenceTimeScopeType scope, WindSequenceType sequenceType) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(scope.getPattern());
        return scope.name() + WindConstants.AT + LocalDateTime.now().format(formatter) + WindConstants.UNDERLINE + sequenceType.name();
    }

    private Duration getExpireTime(SequenceTimeScopeType scope) {
        return switch (scope) {
            case YEAR -> Duration.ofDays(366);
            // 时间大于 1 天过期（避免冬令时夏令时切换导致的错误）
            case DAY -> Duration.ofHours(26);
            case HOUR -> Duration.ofMinutes(65);
            case MINUTE -> Duration.ofSeconds(65);
            case SECONDS -> Duration.ofSeconds(2);
            default -> throw new IllegalArgumentException("Invalid scope: " + scope);
        };
    }
}

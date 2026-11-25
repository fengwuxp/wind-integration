package com.wind.integration.infrastructure.redisson;

import com.wind.common.WindConstants;
import com.wind.sequence.WindSequenceType;
import com.wind.sequence.time.SequenceTimeScopeType;
import com.wind.sequence.time.TemporalSequenceFactory;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * 为 {@link TemporalSequenceFactory} 注册基于 Redisson 的共享计数器，适用于多个应用实例在同一时间段内为同类业务取号。
 * 构造实例即替换工厂当前的计数器，业务代码仍通过工厂取得完整序列号。
 *
 * <p>计数键由时间粒度、当前节点的本地时间段和序列类型名组成。各节点需使用一致的时区和 Redis 数据源才能共享相应计数。
 * 支持按年、月、日、时、分、秒计数。首次取号时异步设置过期时间，目标为该本地时间段最后一次结束后的一秒。
 * 活跃键会随取号定期补设或延长 TTL。若设置失败且此后未再到达补设时机，计数键可能留在 Redis 中。</p>
 *
 * @param redissonClient 保存共享计数器的 Redisson 客户端
 * @author wuxp
 * @date 2025-12-25 13:31
 **/
@Slf4j
public record RedissonTemporalSequenceSupport(RedissonClient redissonClient) {

    private static final int TTL_CHECK_INTERVAL = 512;

    public RedissonTemporalSequenceSupport {
        TemporalSequenceFactory.setCounter(this::next);
        log.info("TemporalSequenceFactory Redisson counter config successful");
    }

    private long next(SequenceTimeScopeType scope, WindSequenceType sequenceType) {
        return next(scope, sequenceType, ZonedDateTime.now());
    }

    long next(SequenceTimeScopeType scope, WindSequenceType sequenceType, ZonedDateTime now) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(scope.getPattern());
        String key = scope.name() + WindConstants.AT + now.format(formatter) + WindConstants.UNDERLINE + sequenceType.name();
        RAtomicLong counter = redissonClient.getAtomicLong(key);
        long sequence = counter.incrementAndGet();
        if (sequence <= 1 || sequence % TTL_CHECK_INTERVAL == 0) {
            try {
                counter.expireIfNotSetAsync(getExpireTime(scope, now));
            } catch (Exception exception) {
                log.warn("Failed to submit sequence TTL update for {}", counter.getName(), exception);
            }
        }
        return sequence;
    }

    private Duration getExpireTime(SequenceTimeScopeType scope, ZonedDateTime now) {
        LocalDateTime local = now.toLocalDateTime();
        LocalDateTime end = switch (scope) {
            case YEAR -> local.toLocalDate().withDayOfYear(1).plusYears(1).atStartOfDay();
            case MONTH -> local.toLocalDate().withDayOfMonth(1).plusMonths(1).atStartOfDay();
            case DAY -> local.toLocalDate().plusDays(1).atStartOfDay();
            case HOUR -> local.truncatedTo(ChronoUnit.HOURS).plusHours(1);
            case MINUTE -> local.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1);
            case SECONDS -> local.truncatedTo(ChronoUnit.SECONDS).plusSeconds(1);
        };
        // 以区间内的最后一刻判定偏移量，避免把相邻区间的时钟回拨算进来。
        ZonedDateTime last = end.minusNanos(1).atZone(now.getZone()).withLaterOffsetAtOverlap();
        return Duration.between(now, last).plusNanos(1).plusSeconds(1);
    }
}

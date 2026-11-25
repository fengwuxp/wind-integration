package com.wind.integration.infrastructure.redisson;

import com.wind.sequence.WindSequenceType;
import com.wind.sequence.time.SequenceTimeScopeType;
import com.wind.sequence.time.TemporalSequenceFactory;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentMatcher;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.redisson.misc.CompletableFutureWrapper;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;

/**
 * @author wuxp
 * @date 2023-11-29 14:53
 **/
@Isolated
class RedissonTemporalSequenceSupportTests {

    private final RedissonClient client = Mockito.mock(RedissonClient.class);

    private final RAtomicLong counter = Mockito.mock(RAtomicLong.class);

    private final InOrder commands = Mockito.inOrder(client, counter);

    private RedissonTemporalSequenceSupport support;

    private BiFunction<SequenceTimeScopeType, WindSequenceType, Long> previousCounter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void registerCounter() {
        var registration = (AtomicReference<BiFunction<SequenceTimeScopeType, WindSequenceType, Long>>)
                ReflectionTestUtils.getField(TemporalSequenceFactory.class, "COUNTER");
        previousCounter = registration.get();
        Mockito.when(client.getAtomicLong(anyString())).thenReturn(counter);
        Mockito.when(counter.incrementAndGet()).thenReturn(1L);
        Mockito.when(counter.expireIfNotSetAsync(any(Duration.class))).thenReturn(new CompletableFutureWrapper<>(false));
        support = new RedissonTemporalSequenceSupport(client);
    }

    @AfterEach
    void restoreCounter() {
        TemporalSequenceFactory.setCounter(previousCounter);
    }

    @Test
    void testFactoryReturnsFirstNumberWhileExpiryIsPending() throws Exception {
        CompletableFuture<Boolean> expiry = new CompletableFuture<>();
        Mockito.when(counter.expireIfNotSetAsync(any(Duration.class))).thenReturn(new CompletableFutureWrapper<>(expiry));

        CompletableFuture<String> issued = CompletableFuture.supplyAsync(() -> TemporalSequenceFactory.monthNext(ExampleType.SN));
        try {
            Assertions.assertTrue(issued.get(5, TimeUnit.SECONDS).matches("EXAMPLE\\d{6}000001"));
        } finally {
            expiry.complete(true);
        }
        Mockito.verify(counter).expireIfNotSetAsync(any(Duration.class));
        Mockito.verify(counter, Mockito.never()).expireIfGreaterAsync(any(Duration.class));
    }

    @Test
    void testFactoryPropagatesIncrementFailure() {
        RedisException failure = new RedisException("INCR failed");
        Mockito.when(counter.incrementAndGet()).thenThrow(failure);

        RedisException thrown = Assertions.assertThrows(RedisException.class, () -> TemporalSequenceFactory.dayNext(ExampleType.SN));

        Assertions.assertSame(failure, thrown);
    }

    @ParameterizedTest
    @ValueSource(longs = {2, 256, 511, 513, 1023, 1025})
    void testOrdinaryNumberUsesOnlyIncrement(long issued) {
        Mockito.when(counter.incrementAndGet()).thenReturn(issued);
        ZonedDateTime now = ZonedDateTime.parse("2024-02-29T12:00Z");

        Assertions.assertEquals(issued, support.next(SequenceTimeScopeType.DAY, ExampleType.SN, now));
        Mockito.verify(counter).incrementAndGet();
        Mockito.verifyNoMoreInteractions(counter);
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 0, 1, 512, 1024})
    void testExpiryRepairWithInitialOrPeriodicNumber(long issued) {
        Mockito.when(counter.incrementAndGet()).thenReturn(issued);
        ZonedDateTime now = ZonedDateTime.parse("2024-02-29T23:59:59Z");

        Assertions.assertEquals(issued, support.next(SequenceTimeScopeType.DAY, ExampleType.SN, now));
        Mockito.verify(counter).expireIfNotSetAsync(Duration.ofSeconds(2));
        Mockito.verify(counter, Mockito.never()).expireIfGreaterAsync(any(Duration.class));
    }

    @Test
    void testExpirySubmissionFailureStillReturnsIssuedNumber() {
        Mockito.when(counter.expireIfNotSetAsync(any(Duration.class))).thenThrow(new RedisException("PEXPIRE NX failed"));

        Assertions.assertTrue(TemporalSequenceFactory.dayNext(ExampleType.SN).endsWith("000001"));
        Mockito.verify(counter, Mockito.never()).expireIfGreaterAsync(any(Duration.class));
    }

    @Test
    void testFailedAsyncExpiryStillReturnsIssuedNumber() {
        Mockito.when(counter.expireIfNotSetAsync(any(Duration.class)))
                .thenReturn(new CompletableFutureWrapper<>(new RedisException("PEXPIRE NX failed")));

        Assertions.assertTrue(TemporalSequenceFactory.dayNext(ExampleType.SN).endsWith("000001"));
        Mockito.verify(counter).expireIfNotSetAsync(any(Duration.class));
    }

    @ParameterizedTest
    @CsvSource({
            "YEAR,    2024-01-01T00:00:00Z,      YEAR@2024_SN,              2025-01-01T00:00:00Z",
            "MONTH,   2024-02-01T00:00:00Z,      MONTH@202402_SN,           2024-03-01T00:00:00Z",
            "DAY,     2024-02-29T00:00:00Z,      DAY@20240229_SN,           2024-03-01T00:00:00Z",
            "HOUR,    2024-02-29T23:50:00Z,      HOUR@2024022923_SN,        2024-03-01T00:00:00Z",
            "MINUTE,  2024-02-29T23:59:10Z,      MINUTE@202402292359_SN,    2024-03-01T00:00:00Z",
            "SECONDS, 2024-02-29T23:59:59.9999Z, SECONDS@20240229235959_SN, 2024-03-01T00:00:00Z",
            "SECONDS, 2024-03-01T00:00:00Z,      SECONDS@20240301000000_SN, 2024-03-01T00:00:01Z"
    })
    void testKeyAndExpiryAtCalendarBoundaries(SequenceTimeScopeType scope, String timestamp, String key, String bucketEnd) {
        assertCounterRequest(scope, ExampleType.SN, ZonedDateTime.parse(timestamp), key, Instant.parse(bucketEnd));
    }

    /**
     * 纽约回拨时同一段本地时间出现两次，计数键相同，两次申请的过期时间都覆盖第二段。
     */
    @ParameterizedTest
    @CsvSource({
            "HOUR,    HOUR@2024110301_BATCH,        2024-11-03T07:00:00Z",
            "MINUTE,  MINUTE@202411030130_BATCH,    2024-11-03T06:31:00Z",
            "SECONDS, SECONDS@20241103013042_BATCH, 2024-11-03T06:30:43Z"
    })
    void testExpiryIncludesRepeatedLocalTime(SequenceTimeScopeType scope, String key, String lastBucketEnd) {
        ZonedDateTime earlier = ZonedDateTime.parse("2024-11-03T01:30:42.250-04:00[America/New_York]");
        ZonedDateTime later = earlier.withLaterOffsetAtOverlap();
        Instant end = Instant.parse(lastBucketEnd);

        assertCounterRequest(scope, ExampleType.BATCH, earlier, key, end);
        assertCounterRequest(scope, ExampleType.BATCH, later, key, end);
    }

    private void assertCounterRequest(SequenceTimeScopeType scope, ExampleType sequenceType, ZonedDateTime now, String key, Instant end) {
        long minimumTtlMillis = Duration.between(now.toInstant(), end).plusSeconds(1).toMillis();
        ArgumentMatcher<Duration> expiresAfterBucket = ttl -> ttl.toMillis() == minimumTtlMillis;

        Assertions.assertEquals(1L, support.next(scope, sequenceType, now));
        commands.verify(client).getAtomicLong(key);
        commands.verify(counter).incrementAndGet();
        commands.verify(counter).expireIfNotSetAsync(argThat(expiresAfterBucket));
    }

    @AllArgsConstructor
    @Getter
    enum ExampleType implements WindSequenceType {

        SN("EXAMPLE", 6),
        BATCH("BATCH", 6);

        private final String prefix;

        private final int length;


        @Override
        public int length() {
            return length;
        }
    }
}

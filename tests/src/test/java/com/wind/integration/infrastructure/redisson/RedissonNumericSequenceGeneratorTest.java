package com.wind.integration.infrastructure.redisson;

import com.wind.sequence.WindSequenceType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;

import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.anyString;

/**
 * @author wuxp
 * @date 2023-11-29 14:53
 **/
class RedissonNumericSequenceGeneratorTest {

    private RedissonNumericSequenceGenerator generator;

    @BeforeEach
    void setup() {
        generator = mock();
    }

    @Test
    void testNext() {
        String seq = generator.dayNext(ExampleType.SN);
        Assertions.assertTrue(seq.endsWith("000001"));
    }

    private RedissonNumericSequenceGenerator mock() {
        AtomicLong counter = new AtomicLong(0);
        RedissonClient redissonClient = Mockito.mock(RedissonClient.class);
        RAtomicLong atomicLong = Mockito.mock(RAtomicLong.class);
        Mockito.when(atomicLong.incrementAndGet()).thenAnswer(invocation -> counter.incrementAndGet());
        Mockito.when(atomicLong.get()).thenAnswer(invocation -> counter.get());
        Mockito.when(redissonClient.getAtomicLong(anyString())).thenReturn(atomicLong);
        return new RedissonNumericSequenceGenerator(redissonClient);
    }

    @AllArgsConstructor
    @Getter
    enum ExampleType implements WindSequenceType {

        SN("EXAMPLE", 6);

        private final String prefix;

        private final int length;


        @Override
        public int defaultLength() {
            return length;
        }
    }
}

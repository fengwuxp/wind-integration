package com.wind.integration.infrastructure.redisson;

import com.wind.sequence.WindSequenceType;
import com.wind.sequence.time.TemporalSequenceFactory;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;

import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.anyString;

/**
 * @author wuxp
 * @date 2023-11-29 14:53
 **/
class RedissonTemporalSequenceSupportTests {


    @BeforeEach
    void setup() {
        AtomicLong atomicLong = new AtomicLong(0);
        RedissonClient client = Mockito.mock(RedissonClient.class);
        RAtomicLong counter = Mockito.mock(RAtomicLong.class);
        Mockito.when(counter.incrementAndGet()).thenAnswer((Answer<Long>) invocation -> atomicLong.incrementAndGet());
        Mockito.when(client.getAtomicLong(anyString())).thenReturn(counter);
        new RedissonTemporalSequenceSupport(client);
    }

    @Test
    void testNext() {
        String seq = TemporalSequenceFactory.dayNext(ExampleType.SN);
        Assertions.assertTrue(seq.endsWith("000001"));
    }


    @AllArgsConstructor
    @Getter
    enum ExampleType implements WindSequenceType {

        SN("EXAMPLE", 6);

        private final String prefix;

        private final int length;


        @Override
        public int length() {
            return length;
        }
    }
}

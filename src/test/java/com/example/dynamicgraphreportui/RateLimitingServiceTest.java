package com.example.dynamicgraphreportui;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.github.bucket4j.Bucket;

class RateLimitingServiceTest {

    private RateLimitingService rateLimitingService;

    @BeforeEach
    void setUp() {
        rateLimitingService = new RateLimitingService();
        ReflectionTestUtils.setField(rateLimitingService, "capacity", 10L);
        ReflectionTestUtils.setField(rateLimitingService, "duration", Duration.ofMinutes(1));
    }

    @Test
    void resolveBucket_CreatesNewBucketForNewIp() {
        Bucket bucket = rateLimitingService.resolveBucket("127.0.0.1");
        assertNotNull(bucket);
        assertEquals(10, bucket.getAvailableTokens());
    }

    @Test
    void resolveBucket_ReturnsSameBucketForSameIp() {
        Bucket bucket1 = rateLimitingService.resolveBucket("127.0.0.1");
        Bucket bucket2 = rateLimitingService.resolveBucket("127.0.0.1");
        
        // Consume one from bucket1
        bucket1.tryConsume(1);
        
        // bucket2 should reflect the consumption
        assertEquals(9, bucket2.getAvailableTokens());
    }

    @Test
    void resolveBucket_ReturnsDifferentBucketsForDifferentIps() {
        Bucket bucket1 = rateLimitingService.resolveBucket("127.0.0.1");
        Bucket bucket2 = rateLimitingService.resolveBucket("192.168.1.1");
        
        assertNotSame(bucket1, bucket2);
        
        bucket1.tryConsume(1);
        
        assertEquals(9, bucket1.getAvailableTokens());
        assertEquals(10, bucket2.getAvailableTokens());
    }
}

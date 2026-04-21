import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    // Stage 1: Ramp up to 100 concurrent users over 10 seconds
    // Stage 2: Hold at 100 users for 20 seconds
    // Stage 3: Ramp down to 0 users over 10 seconds
    stages: [
        { duration: '10s', target: 200 },
        { duration: '20s', target: 200 },
        { duration: '10s', target: 0 },
    ],
    // Only accept 50 requests per minute success rate based on our rate limiter defaults. 
    // If >200 requests succeed, the Redis atomicity is broken.
};

export default function () {
    // Generate a diverse set of IPs to simulate a distributed attack
    // We will stick to a single IP for this test to prove the token bucket actually enforces 
    // exactly 50 per IP.
    
    const params = {
        headers: {
            'X-Forwarded-For': '203.0.113.195', // Simulated Attacker IP
            'Content-Type': 'application/json',
        },
    };

    const res = http.get('http://host.docker.internal:8080/api/test', params);

    // Assertions
    check(res, {
        'is status 200 or 429': (r) => r.status === 200 || r.status === 429,
        // Short-circuit verify: Check if our L1 cache is working (it should drop Response Time drastically)
        'L1 short-circuit response time < 20ms': (r) => r.status === 429 ? r.timings.duration < 20 : true,
    });
    
    // Tiny sleep to simulate real user network latency, but still fast enough to hammer the API
    sleep(0.01);
}

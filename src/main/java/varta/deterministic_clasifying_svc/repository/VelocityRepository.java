package varta.deterministic_clasifying_svc.repository;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

@Repository
public class VelocityRepository {

    private static final String MERCHANT_KEY_PREFIX = "velocity:merchants:";
    private static final String TX_KEY_PREFIX = "velocity:tx:";
    private static final long ONE_HOUR_SECONDS = 3600L;
    private static final long THIRTY_DAYS_SECONDS = 30L * 24 * 3600;

    private final StringRedisTemplate redisTemplate;

    public VelocityRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Records a transaction in Redis for use by stateful velocity rules (V03, V05).
     * Must be called before querying counts so the current transaction is included.
     *
     * @param sourceCard      card that initiated the transaction
     * @param merchantAcquirer merchant on the transaction (may be null for transfers)
     * @param txId            unique transaction ID used as value in the tx sorted set
     * @param epochSecond     current epoch second (score for sorted set entries)
     */
    public void recordTransaction(Long sourceCard, Long merchantAcquirer, Long txId, long epochSecond) {
        if (sourceCard == null || txId == null) {
            return;
        }

        // Track distinct merchants per card in a 1H sliding window (V03)
        if (merchantAcquirer != null) {
            String merchantKey = MERCHANT_KEY_PREFIX + sourceCard;
            // ZADD updates the score if the merchant already exists, keeping the latest timestamp
            redisTemplate.opsForZSet().add(merchantKey, merchantAcquirer.toString(), epochSecond);
            // Expire slightly beyond 1H so stale entries are eventually cleaned up
            redisTemplate.expire(merchantKey, Duration.ofSeconds(ONE_HOUR_SECONDS + 120));
        }

        // Track all transactions per card in a 30D sliding window (V05)
        String txKey = TX_KEY_PREFIX + sourceCard;
        redisTemplate.opsForZSet().add(txKey, txId.toString(), epochSecond);
        redisTemplate.expire(txKey, Duration.ofDays(31));
    }

    /**
     * Returns the number of distinct merchants transacted with by sourceCard in the last 1 hour.
     * Removes entries outside the window before counting.
     */
    public long getDistinctMerchantCount1H(Long sourceCard) {
        if (sourceCard == null) {
            return 0;
        }
        String key = MERCHANT_KEY_PREFIX + sourceCard;
        long nowEpoch = System.currentTimeMillis() / 1000;
        long oneHourAgo = nowEpoch - ONE_HOUR_SECONDS;

        // Prune entries older than 1H
        redisTemplate.opsForZSet().removeRangeByScore(key, Double.NEGATIVE_INFINITY, oneHourAgo - 1);

        Long count = redisTemplate.opsForZSet().zCard(key);
        return count != null ? count : 0;
    }

    /**
     * Returns the number of transactions recorded for sourceCard in the last 30 days.
     * This count is maintained by this service itself and grows as transactions are processed.
     */
    public long getTransactionCount30D(Long sourceCard) {
        if (sourceCard == null) {
            return 0;
        }
        String key = TX_KEY_PREFIX + sourceCard;
        long nowEpoch = System.currentTimeMillis() / 1000;
        long thirtyDaysAgo = nowEpoch - THIRTY_DAYS_SECONDS;

        Long count = redisTemplate.opsForZSet().count(key, thirtyDaysAgo, nowEpoch);
        return count != null ? count : 0;
    }
}

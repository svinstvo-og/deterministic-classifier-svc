package varta.deterministic_clasifying_svc.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RuleConfig {

    // V01 — High Velocity 1H
    @Value("${rules.velocity.high-1h-threshold:5}")
    private int highVelocity1HThreshold;

    // V02 — High Velocity 24H
    @Value("${rules.velocity.high-24h-threshold:15}")
    private int highVelocity24HThreshold;

    // V03 — High Distinct Merchant Spread 1H
    @Value("${rules.velocity.merchant-spread-1h-threshold:4}")
    private int highMerchantSpread1HThreshold;

    // V04 — Rapid Sequential Transactions
    @Value("${rules.velocity.rapid-sequential-seconds:60}")
    private long rapidSequentialSeconds;

    @Value("${rules.velocity.rapid-sequential-min-velocity:2}")
    private int rapidSequentialMinVelocity;

    // V05 — Velocity Spike vs Historical Baseline
    @Value("${rules.velocity.spike-multiplier:10.0}")
    private double velocitySpikeMultiplier;

    @Value("${rules.velocity.spike-min-30d-transactions:10}")
    private long velocitySpikeMin30DTransactions;

    public int getHighVelocity1HThreshold() { return highVelocity1HThreshold; }
    public int getHighVelocity24HThreshold() { return highVelocity24HThreshold; }
    public int getHighMerchantSpread1HThreshold() { return highMerchantSpread1HThreshold; }
    public long getRapidSequentialSeconds() { return rapidSequentialSeconds; }
    public int getRapidSequentialMinVelocity() { return rapidSequentialMinVelocity; }
    public double getVelocitySpikeMultiplier() { return velocitySpikeMultiplier; }
    public long getVelocitySpikeMin30DTransactions() { return velocitySpikeMin30DTransactions; }
}

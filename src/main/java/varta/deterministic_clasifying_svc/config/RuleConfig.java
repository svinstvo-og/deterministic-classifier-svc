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

    // T01 — Night Transaction (fallback when isNight is null)
    @Value("${rules.time.night-start-hour:1}")
    private int nightStartHour;

    @Value("${rules.time.night-end-hour:5}")
    private int nightEndHour;

    // T02 — Rapid Sequential Transactions
    @Value("${rules.time.rapid-sequential-high-seconds:30}")
    private long rapidSequentialHighSeconds;

    @Value("${rules.time.rapid-sequential-moderate-seconds:120}")
    private long rapidSequentialModerateSeconds;

    // T03 — Dormant Card Reactivation
    @Value("${rules.time.dormant-days-high:90}")
    private long dormantDaysHigh;

    @Value("${rules.time.dormant-amount-threshold:300}")
    private int dormantAmountThreshold;

    @Value("${rules.time.dormant-days-moderate:180}")
    private long dormantDaysModerate;

    // A04 — Round Number Amount
    @Value("${rules.amount.round-number-min:200}")
    private int roundNumberMin;

    @Value("${rules.amount.round-number-high-min:500}")
    private int roundNumberHighMin;

    // A05 — Micro-Charge Card Testing
    @Value("${rules.amount.micro-charge-max:1.00}")
    private double microChargeMax;

    // A06 — Amount Just Below Authentication Threshold
    @Value("${rules.amount.auth-threshold-low-min:45.00}")
    private double authThresholdLowMin;

    @Value("${rules.amount.auth-threshold-low-max:49.99}")
    private double authThresholdLowMax;

    @Value("${rules.amount.auth-threshold-high-min:95.00}")
    private double authThresholdHighMin;

    @Value("${rules.amount.auth-threshold-high-max:99.99}")
    private double authThresholdHighMax;

    public int getHighVelocity1HThreshold() { return highVelocity1HThreshold; }
    public int getHighVelocity24HThreshold() { return highVelocity24HThreshold; }
    public int getHighMerchantSpread1HThreshold() { return highMerchantSpread1HThreshold; }
    public long getRapidSequentialSeconds() { return rapidSequentialSeconds; }
    public int getRapidSequentialMinVelocity() { return rapidSequentialMinVelocity; }
    public double getVelocitySpikeMultiplier() { return velocitySpikeMultiplier; }
    public long getVelocitySpikeMin30DTransactions() { return velocitySpikeMin30DTransactions; }
    public int getNightStartHour() { return nightStartHour; }
    public int getNightEndHour() { return nightEndHour; }
    public long getRapidSequentialHighSeconds() { return rapidSequentialHighSeconds; }
    public long getRapidSequentialModerateSeconds() { return rapidSequentialModerateSeconds; }
    public long getDormantDaysHigh() { return dormantDaysHigh; }
    public int getDormantAmountThreshold() { return dormantAmountThreshold; }
    public long getDormantDaysModerate() { return dormantDaysModerate; }
    public int getRoundNumberMin() { return roundNumberMin; }
    public int getRoundNumberHighMin() { return roundNumberHighMin; }
    public double getMicroChargeMax() { return microChargeMax; }
    public double getAuthThresholdLowMin() { return authThresholdLowMin; }
    public double getAuthThresholdLowMax() { return authThresholdLowMax; }
    public double getAuthThresholdHighMin() { return authThresholdHighMin; }
    public double getAuthThresholdHighMax() { return authThresholdHighMax; }
}

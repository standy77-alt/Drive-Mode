package com.haval.akppmodes.trip;

public final class TripAccumulator {
    private static final double KILOMETRES_PER_CONSUMPTION_UNIT = 100.0;

    private final long startedAtMs;
    private long lastUpdatedAtMs;
    private double distanceKm;
    private double lastJourneyOdometerKm;
    private boolean lastJourneyOdometerValid;
    private boolean distanceValid;
    private float lastAverageFuelConsumption;
    private boolean lastAverageFuelConsumptionValid;

    private TripAccumulator(long startedAtMs) {
        this.startedAtMs = startedAtMs;
        this.lastUpdatedAtMs = startedAtMs;
    }

    public static TripAccumulator start(long startedAtMs) {
        return new TripAccumulator(startedAtMs);
    }

    public void update(TripTelemetry telemetry) {
        if (telemetry.capturedAtMs < lastUpdatedAtMs) return;

        if (telemetry.journeyOdometerValid) {
            if (lastJourneyOdometerValid) {
                double candidateDelta = telemetry.journeyOdometerKm - lastJourneyOdometerKm;
                if (candidateDelta >= 0.0 && Double.isFinite(candidateDelta)) {
                    distanceKm += candidateDelta;
                    distanceValid = true;
                }
            }
            lastJourneyOdometerKm = telemetry.journeyOdometerKm;
            lastJourneyOdometerValid = true;
        }

        lastUpdatedAtMs = telemetry.capturedAtMs;
        if (telemetry.averageFuelConsumptionValid) {
            lastAverageFuelConsumption = telemetry.averageFuelConsumption;
            lastAverageFuelConsumptionValid = true;
        }
    }

    public TripSummary finish(long stoppedAtMs) {
        boolean durationValid = stoppedAtMs >= startedAtMs && startedAtMs >= 0L;
        long durationMs = durationValid ? stoppedAtMs - startedAtMs : 0L;
        
        boolean consumptionValid = distanceValid && distanceKm > 0.0 
                && lastAverageFuelConsumptionValid && Float.isFinite(lastAverageFuelConsumption) 
                && lastAverageFuelConsumption > 0.0f;
                
        double averageConsumption = consumptionValid ? lastAverageFuelConsumption : Double.NaN;
        double fuelLiters = consumptionValid ? distanceKm * lastAverageFuelConsumption / KILOMETRES_PER_CONSUMPTION_UNIT : Double.NaN;
        
        return new TripSummary(distanceKm, distanceValid, averageConsumption, consumptionValid, 
                               durationMs, durationValid, fuelLiters, consumptionValid);
    }
}
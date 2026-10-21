package com.haval.akppmodes.trip;

public final class TripTelemetry {
    public final long capturedAtMs;
    public final double journeyOdometerKm;
    public final boolean journeyOdometerValid;
    public final float averageFuelConsumption;
    public final boolean averageFuelConsumptionValid;

    public TripTelemetry(long capturedAtMs, double journeyOdometerKm, boolean journeyOdometerValid,
                         float averageFuelConsumption, boolean averageFuelConsumptionValid) {
        this.capturedAtMs = capturedAtMs;
        this.journeyOdometerKm = journeyOdometerKm;
        this.journeyOdometerValid = journeyOdometerValid;
        this.averageFuelConsumption = averageFuelConsumption;
        this.averageFuelConsumptionValid = averageFuelConsumptionValid;
    }
}
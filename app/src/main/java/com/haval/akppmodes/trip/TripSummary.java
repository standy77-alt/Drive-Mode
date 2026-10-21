package com.haval.akppmodes.trip;

public final class TripSummary {
    public final double distanceKm;
    public final boolean distanceValid;
    public final double averageConsumptionLitersPer100Km;
    public final boolean consumptionValid;
    public final long durationMs;
    public final boolean durationValid;
    public final double fuelUsedLiters;
    public final boolean fuelUsedValid;

    public TripSummary(double distanceKm, boolean distanceValid, 
                       double averageConsumptionLitersPer100Km, boolean consumptionValid, 
                       long durationMs, boolean durationValid, 
                       double fuelUsedLiters, boolean fuelUsedValid) {
        this.distanceKm = distanceKm;
        this.distanceValid = distanceValid;
        this.averageConsumptionLitersPer100Km = averageConsumptionLitersPer100Km;
        this.consumptionValid = consumptionValid;
        this.durationMs = durationMs;
        this.durationValid = durationValid;
        this.fuelUsedLiters = fuelUsedLiters;
        this.fuelUsedValid = fuelUsedValid;
    }
}
package com.newrelic.videoagent.core;

import java.util.Locale;

public class NRVideoSetup {
    private String licenseKey;
    private int harvestCycleSeconds = 60;
    private int realTimeEventHarvestingSeconds = 0;
    private String region = "US";
    private String endpointUrl = "/v1/video/events";

    public NRVideoSetup withLicenseKey(String key) {
        this.licenseKey = key;
        return this;
    }

    public NRVideoSetup withHarvestingCycle(int seconds) {
        this.harvestCycleSeconds = seconds;
        return this;
    }

    public NRVideoSetup withRealTimeEventHarvesting(int seconds) {
        this.realTimeEventHarvestingSeconds = seconds;
        return this;
    }

    public NRVideoSetup withRegion(String region) {
        this.region = region;
        return this;
    }

    public NRVideoSetup withEndpointUrl(String url) {
        this.endpointUrl = url;
        return this;
    }

    private String detectRegion() {
        String country = Locale.getDefault().getCountry();
        if (country.equalsIgnoreCase("US")) return "US";
        if (country.equalsIgnoreCase("GB") || country.equalsIgnoreCase("FR") || country.equalsIgnoreCase("DE") || country.equalsIgnoreCase("IT") || country.equalsIgnoreCase("ES") || country.equalsIgnoreCase("NL") || country.equalsIgnoreCase("BE") || country.equalsIgnoreCase("SE") || country.equalsIgnoreCase("DK") || country.equalsIgnoreCase("FI") || country.equalsIgnoreCase("NO") || country.equalsIgnoreCase("IE") || country.equalsIgnoreCase("PT") || country.equalsIgnoreCase("GR") || country.equalsIgnoreCase("AT") || country.equalsIgnoreCase("CH")) return "EU";
        return "US"; // Default fallback
    }

    public NRVideo build() {
        String regionToUse = (region == null || region.isEmpty()) ? detectRegion() : region;
        com.newrelic.videoagent.core.harvest.HarvestManager.Config config = new com.newrelic.videoagent.core.harvest.HarvestManager.Config(licenseKey, endpointUrl)
            .setHarvestCycleSeconds(harvestCycleSeconds)
            .setRegion(regionToUse);
        com.newrelic.videoagent.core.harvest.HarvestManager.getInstance().configure(config);
        return new NRVideo();
    }
}

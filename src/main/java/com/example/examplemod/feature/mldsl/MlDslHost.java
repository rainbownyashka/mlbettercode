package com.example.examplemod.feature.mldsl;

public interface MlDslHost
{
    void setActionBar(boolean ok, String text, long timeMs);

    String resolveHubBaseUrl();
}

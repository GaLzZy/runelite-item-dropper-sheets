package com.galzzz;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(GoogleSheetsDropsExporterConfig.GROUP)
public interface GoogleSheetsDropsExporterConfig extends Config
{
    String GROUP = "googlesheetsdropsexporter";

    @ConfigItem(
            keyName = "endpointUrl",
            name = "Endpoint URL",
            description = "Apps Script endpoint used for fetching the whitelist and receiving drop notifications."
    )
    default String endpointUrl()
    {
        return "";
    }
}

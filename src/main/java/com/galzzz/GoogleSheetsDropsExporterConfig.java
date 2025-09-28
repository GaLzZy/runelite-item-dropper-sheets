package com.galzzz;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(GoogleSheetsDropsExporterConfig.GROUP)
public interface GoogleSheetsDropsExporterConfig extends Config
{
    String GROUP = "googlesheetsdropsexporter";

    @ConfigItem(
            keyName = "filteredItems",
            name = "Filtered items",
            description = "Comma-separated list of item names to send to Google Sheets."
    )
    default String filteredItems()
    {
        return "";
    }

    @ConfigItem(
            keyName = "googleSheetId",
            name = "Google Sheet ID",
            description = "The ID of the Google Sheet that should receive the drops."
    )
    default String googleSheetId()
    {
        return "";
    }

    @ConfigItem(
            keyName = "webhookUrl",
            name = "Webhook URL",
            description = "Apps Script endpoint that receives filtered drop notifications."
    )
    default String webhookUrl()
    {
        return "";
    }
}

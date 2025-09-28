package com.galzzz;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class PluginLauncher
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(GoogleSheetsDropsExporter.class);
        RuneLite.main(args);
    }
}

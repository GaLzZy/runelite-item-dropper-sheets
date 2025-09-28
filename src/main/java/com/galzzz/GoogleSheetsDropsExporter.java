package com.galzzz;

import com.google.common.base.Strings;
import com.google.inject.Provides;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
        name = "Google Sheets Drops Exporter"
)
public class GoogleSheetsDropsExporter extends Plugin
{
    @Inject
    private Client client;

    @Inject
    private GoogleSheetsDropsExporterConfig config;

    @Override
    protected void startUp()
    {
        log.info("Google Sheets Drops Exporter started");
    }

    @Override
    protected void shutDown()
    {
        log.info("Google Sheets Drops Exporter stopped");
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged gameStateChanged)
    {
        if (gameStateChanged.getGameState() == GameState.LOGGED_IN)
        {
            final List<String> filteredItems = getFilteredItems();
            final String googleSheetId = getGoogleSheetId();

            final StringBuilder message = new StringBuilder();
            message.append("Item Dropper filter ready for ")
                    .append(filteredItems.size())
                    .append(filteredItems.size() == 1 ? " item." : " items.");

            if (!googleSheetId.isEmpty())
            {
                message.append(" Sheet ID: ").append(googleSheetId);
            }

            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message.toString(), null);
        }
    }

    @Provides
    GoogleSheetsDropsExporterConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(GoogleSheetsDropsExporterConfig.class);
    }

    private List<String> getFilteredItems()
    {
        return Arrays.stream(Strings.nullToEmpty(config.filteredItems()).split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private String getGoogleSheetId()
    {
        return Strings.nullToEmpty(config.googleSheetId()).trim();
    }
}

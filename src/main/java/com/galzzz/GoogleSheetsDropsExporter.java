package com.galzzz;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.inject.Provides;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.TileItem;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemSpawned;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

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

    @Inject
    private ItemManager itemManager;

    @Inject
    private OkHttpClient okHttpClient;

    private static final MediaType JSON_MEDIA_TYPE = Objects.requireNonNull(MediaType.parse("application/json; charset=utf-8"));

    private final Gson gson = new Gson();

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

    @Subscribe
    public void onItemSpawned(ItemSpawned itemSpawned)
    {
        final Set<String> filteredItems = getFilteredItemsLowercase();

        if (filteredItems.isEmpty())
        {
            return;
        }

        final TileItem tileItem = itemSpawned.getItem();
        final ItemComposition composition = itemManager.getItemComposition(tileItem.getId());
        final String itemName = composition.getName();

        if (filteredItems.contains(itemName.toLowerCase()))
        {
            final String message = String.format("Filtered drop detected: %s x%d", itemName, tileItem.getQuantity());
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null);
            sendWebhook(itemName, tileItem.getQuantity());
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

    private Set<String> getFilteredItemsLowercase()
    {
        return getFilteredItems().stream()
                .map(String::toLowerCase)
                .collect(Collectors.toCollection(HashSet::new));
    }

    private String getGoogleSheetId()
    {
        return Strings.nullToEmpty(config.googleSheetId()).trim();
    }

    private void sendWebhook(String itemName, int quantity)
    {
        final String webhookUrl = Strings.nullToEmpty(config.webhookUrl()).trim();

        if (webhookUrl.isEmpty())
        {
            return;
        }

        final Request request = buildRequest(webhookUrl, itemName, quantity);

        okHttpClient.newCall(request).enqueue(new Callback()
        {
            @Override
            public void onFailure(Call call, IOException e)
            {
                log.warn("Failed to post drop for {} x{}", itemName, quantity, e);
            }

            @Override
            public void onResponse(Call call, Response response)
            {
                try (Response res = response)
                {
                    if (!res.isSuccessful())
                    {
                        log.warn("Webhook responded with {} for {} x{}", res.code(), itemName, quantity);
                    }
                }
            }
        });
    }

    private Request buildRequest(String webhookUrl, String itemName, int quantity)
    {
        final DropPayload payload = new DropPayload(itemName, quantity);
        final String json = gson.toJson(payload);
        final RequestBody body = RequestBody.create(JSON_MEDIA_TYPE, json);

        return new Request.Builder()
                .url(webhookUrl)
                .post(body)
                .build();
    }

    private static class DropPayload
    {
        private final String item;
        private final int quantity;

        private DropPayload(String item, int quantity)
        {
            this.item = item;
            this.quantity = quantity;
        }
    }
}

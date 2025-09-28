package com.galzzz;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.TileItem;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemSpawned;
import net.runelite.client.callback.ClientThread;
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

    @Inject
    private ClientThread clientThread;

    private static final MediaType JSON_MEDIA_TYPE = Objects.requireNonNull(MediaType.parse("application/json; charset=utf-8"));

    private final Gson gson = new Gson();

    private volatile List<String> filteredItems = Collections.emptyList();
    private volatile Set<String> filteredItemsLowercase = Collections.emptySet();

    @Override
    protected void startUp()
    {
        log.info("Google Sheets Drops Exporter started");
        refreshFilteredItems(false);
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
            refreshFilteredItems(true);
        }
    }

    @Subscribe
    public void onItemSpawned(ItemSpawned itemSpawned)
    {
        final Set<String> filteredItems = filteredItemsLowercase;

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

    private void sendWebhook(String itemName, int quantity)
    {
        final String webhookUrl = getEndpointUrl();

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

    private String getEndpointUrl()
    {
        final String endpointUrl = config.endpointUrl();
        return endpointUrl == null ? "" : endpointUrl.trim();
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

    private void refreshFilteredItems(boolean announceResult)
    {
        final String endpointUrl = getEndpointUrl();

        if (endpointUrl.isEmpty())
        {
            updateFilteredItems(Collections.emptyList());

            if (announceResult)
            {
                postChatMessage("Item Dropper endpoint not configured.");
            }

            return;
        }

        final Request request = new Request.Builder()
                .url(endpointUrl)
                .get()
                .build();

        okHttpClient.newCall(request).enqueue(new Callback()
        {
            @Override
            public void onFailure(Call call, IOException e)
            {
                log.warn("Failed to refresh filtered items", e);

                if (announceResult)
                {
                    postChatMessage("Item Dropper failed to refresh filtered items. Check logs for details.");
                }
            }

            @Override
            public void onResponse(Call call, Response response)
            {
                try (Response res = response)
                {
                    if (!res.isSuccessful())
                    {
                        log.warn("Filtered items endpoint responded with {}", res.code());

                        if (announceResult)
                        {
                            postChatMessage("Item Dropper endpoint responded with an error while refreshing the whitelist.");
                        }

                        return;
                    }

                    final String body = res.body() != null ? res.body().string() : "";

                    final FilteredItemsResponse payload = gson.fromJson(body, FilteredItemsResponse.class);

                    if (payload == null)
                    {
                        log.warn("Filtered items endpoint returned an empty payload");

                        if (announceResult)
                        {
                            postChatMessage("Item Dropper received an empty whitelist payload.");
                        }

                        return;
                    }

                    final List<String> items = payload.items != null ? payload.items : Collections.emptyList();
                    updateFilteredItems(items);

                    if (announceResult)
                    {
                        final int size = filteredItems.size();
                        final String suffix = payload.updatedAt != null && !payload.updatedAt.isEmpty()
                                ? " (updated " + payload.updatedAt + ")"
                                : "";
                        postChatMessage(String.format("Item Dropper loaded %d filtered %s%s.",
                                size,
                                size == 1 ? "item" : "items",
                                suffix));
                    }
                }
                catch (IOException e)
                {
                    log.warn("Failed to parse filtered items response", e);

                    if (announceResult)
                    {
                        postChatMessage("Item Dropper failed to parse the whitelist response.");
                    }
                }
            }
        });
    }

    private void updateFilteredItems(List<String> items)
    {
        final List<String> normalizedItems = new ArrayList<>(items.size());
        final Set<String> lowercase = new HashSet<>(items.size());

        for (String item : items)
        {
            if (item == null)
            {
                continue;
            }

            final String trimmed = item.trim();

            if (!trimmed.isEmpty())
            {
                normalizedItems.add(trimmed);
                lowercase.add(trimmed.toLowerCase());
            }
        }

        this.filteredItems = Collections.unmodifiableList(normalizedItems);
        this.filteredItemsLowercase = Collections.unmodifiableSet(lowercase);
    }

    private void postChatMessage(String message)
    {
        clientThread.invokeLater(() -> client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null));
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

    private static class FilteredItemsResponse
    {
        private List<String> items;
        private Integer count;
        private String updatedAt;
    }
}

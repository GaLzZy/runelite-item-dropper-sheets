# Google Sheets Item Drops

A RuneLite plugin that filters item drops by name and forwards matching drops to a Google Sheet.

## Configuration

1. **Endpoint URL** – Apps Script endpoint that returns the whitelist via `GET` and receives drop notifications via `POST`.

   The plugin expects the `GET` response to look like:

   ```json
   {
     "items": ["Bones"],
     "count": 1,
     "updatedAt": "2025-09-28T22:46:37.836Z"
   }
   ```

   The whitelist is refreshed every time the player logs in (and once when the plugin starts).

   For each matching drop, the plugin sends a POST request with the payload:

   ```json
   {
     "item": "Bones",
     "quantity": 5
   }
   ```

The plugin also announces matching drops in chat for quick verification.

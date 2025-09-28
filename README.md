# Google Sheets Item Drops

A RuneLite plugin that filters item drops by name and forwards matching drops to a Google Sheet.

## Configuration

1. **Filtered items** – comma-separated list of item names to watch for.
2. **Google Sheet ID** – optional reference for the destination sheet (if your Apps Script uses it).
3. **Webhook URL** – Apps Script endpoint that receives JSON payloads in the shape:

   ```json
   {
     "item": "Bones",
     "quantity": 5
   }
   ```

The plugin sends a POST request with that payload for every matching drop and also announces the drop in chat for quick verification.

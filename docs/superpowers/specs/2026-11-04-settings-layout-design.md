# Settings layout design

The Settings tab groups controls by the outcome users expect:

1. **Models** contains matching disclosure rows for Speech model and Notes AI model. Each row shows its model name and a short status. Speech opens the existing choice picker. Notes AI opens a sheet with its download state, progress, download/cancel/remove action, and Wi-Fi-only switch. The different model lifecycles remain explicit inside the sheets.
2. **Transcription** contains Language, Dictionary, and Detect speakers.
3. **New notes** contains Analyze new notes automatically and Imported files' day.
4. **Appearance** contains Theme with System, Light, and Dark choices. The persisted choice applies to the whole app immediately; Dark is the default.
5. **Storage & data** contains On this device and Delete all data. Debug actions and version text remain at the end.

Remove the disabled Haptics and Reduce motion switches. Android system settings still control both behaviors. No new controls are added for unsupported features.

Use the existing Echo tokens, row component, and sheet style. The main page stays compact; model actions remain available one tap away. Verify theme persistence, theme resolution, relevant unit tests, and a debug build.

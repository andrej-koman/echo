# echo

- [ ] Device verification: Test editing, interrupted transcription recovery, and deletion during analysis on the phone.
- [ ] Recording shortcuts: Home-screen widget and Quick Settings tile.
- [ ] Wear OS: Recording, transfer to phone, tile, and complication.
- [ ] Per-recording language selection: Override the global transcription language.
- [ ] Automatic model updates.
- [ ] Tablet/foldable layouts and broader device testing.
- [ ] Billing (RevenueCat): Purchases, restoration, paywall, and free-tier limits; pending Play device verification.
- [ ] Encrypted backups: Still an open product decision, separate from Android’s automatic backup.
- [x] Onboarding: Improve to include model downloads, more transparency

To prepare a signed Google Play upload bundle, run the [upload wizard](scripts/play-upload.sh):

```bash
rtk bash scripts/play-upload.sh
```

The wizard creates or reuses your upload key, builds and signs the release bundle, and guides the
Play Console upload. It remembers the keystore path and alias in `~/.android/echo-upload.env`;
passwords are entered privately and never stored. Keep the keystore outside the repository.

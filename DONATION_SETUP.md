# Optional donations — Google Play setup

The app uses **Google Play Billing Library 8.0.0** for optional support. There are no ads, no
subscriptions and no tracking. Donations support the developer (Patrick James) and are **not**
donations to the Wikimedia Foundation.

## Product IDs

Create these **one-time products** in Play Console → Monetise → Products → In-app products.
The IDs must match exactly (they are compiled into the app):

| Product ID        | Type          | Suggested price | Behaviour                              |
|-------------------|---------------|-----------------|----------------------------------------|
| `donation_small`  | Consumable    | €1.99 / $1.99   | Can be bought again and again          |
| `donation_medium` | Consumable    | €4.99 / $4.99   | Can be bought again and again          |
| `donation_large`  | Consumable    | €9.99 / $9.99   | Can be bought again and again          |
| `supporter`       | Non-consumable| €9.99 / $9.99   | Acknowledged once, unlocks the badge   |

The consumable products are consumed immediately after purchase so the same tier can be bought
again later. The `supporter` product is acknowledged but never consumed, so it stays owned.

## Steps in Play Console

1. Upload a signed build to **Internal testing** first. Billing products are only queryable from
   an installed build whose application ID and signing key match the Play listing.
2. Go to **Monetise → Products → In-app products** and create the four products above.
3. Give each a title and description. The app displays the Play-provided title and formatted
   price, so localisation is automatic.
4. Activate every product. Inactive products do not appear in `queryProductDetailsAsync`.
5. Under **Settings → License testing**, add the Google accounts that will test purchases.
   License testers are not charged.
6. Install the build from the internal testing link on the test device, open **About → Support
   the app**, and complete a test purchase.
7. Verify the supporter badge appears, then use the hidden developer menu
   (About header, ten taps in five seconds) → **Reset donation state** if you need to re-test.

## How the app behaves

- `DonationBilling.start()` connects lazily when the donation UI opens. Nothing connects at
  launch.
- `queryProductDetailsAsync` requests `donation_small`, `donation_medium`, `donation_large` and
  `supporter` in one call. Only active products are returned.
- If Play Billing is unavailable (sideloaded APK, emulator, unsupported country, no Play Store),
  the dialog shows a short reason and a **Support on GitHub** fallback. The app never crashes.
- Purchases of a consumable are consumed; `supporter` is acknowledged (if needed) and then
  stored locally as a boolean in the private `donations` preferences file.
- The optional milestone prompt is **off by default**. When the user enables
  **Settings → About → Donation prompts**, the app may show a small prompt after 100, 300, 600,
  1000 and 2000 opened articles. Supporters never see it.
- Users can hide the prompt permanently by leaving the setting off or by donating.

## Code map

- `app/src/main/java/com/wikirandomizerp/app/data/billing/DonationBilling.kt` — billing client,
  product query, purchase/consume/acknowledge, supporter flag, debug helpers.
- `app/src/main/java/com/wikirandomizerp/app/ui/donation/DonationDialog.kt` — support dialog.
- `app/src/main/java/com/wikirandomizerp/app/ui/aboutScreen/AboutScreen.kt` — About entry.
- `app/src/main/java/com/wikirandomizerp/app/ui/settingsScreen/SettingsScreen.kt` — support row
  and the donation-prompt switch.
- `app/src/main/java/com/wikirandomizerp/app/ui/debugScreen/DebugScreen.kt` — hidden test menu.
- `app/src/main/java/com/wikirandomizerp/app/ui/homeScreen/viewModel/HomeScreenViewModel.kt` —
  article-open counting and milestone prompt.

## Policy notes

- Donations must remain **optional** and must never gate content or features.
- Do not describe donations as charitable or as supporting Wikipedia/Wikimedia.
- Do not use Google Play's donation features for a charity unless you are a registered charity.
  This is a developer-support product, not a charity.
- Keep prices inclusive of tax as configured by Play. Google handles tax and receipts.

# All-villages and ranked attacks widgets

## Next 5 Upgrades widget
Shows the five upgrades that finish soonest across every saved village.

## Ranked Attacks widget
Lists every saved village with its ranked attack status. Villages with attacks still to do are shown first.

Attacks are counted from the battleTimestamp field in each village's battle log. The window starts at the latest 05:00 UTC reset, or Monday 05:00 UTC when the weekly option is on.

## Linking the Clash of Clans API
1. Tap the gear on the Ranked Attacks widget.
2. Paste an API token from developer.clashofclans.com.
3. Set how many ranked attacks you expect per day or week, then tap Save and fetch now.

Official API keys only work from the IP addresses listed on the key. If the official server rejects the key, use a proxy and enter its address as the base URL.

Only player tags are sent to the API. The token is stored on this device.

## Signing builds
The GitHub build signs the debug APK with the key stored in the DEBUG_KEYSTORE_BASE64 repository secret, so updates install over the top of an earlier build signed with the same key. Keep a backup of the keystore file. Without the secret, the build falls back to a new random key and Android will not install it as an update.

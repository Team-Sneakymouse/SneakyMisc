# Chat migration from DippGen

Deploy the updated SneakyMisc and DippGen jars together, then restart the server. The old DippGen jar still registers its chat listeners and `/kobold`; running it alongside the new SneakyMisc jar would process chat twice. A restart also lets SneakyMisc register the WorldGuard flag during plugin loading.

## Chat

The `chat` mechanic requires WorldGuard and PlaceholderAPI. It owns the existing WorldGuard `chat` state flag, which defaults to allow and retains existing region settings.

- Normal chat reaches accounts within 12 blocks in the same world.
- `!message` shouts within 36 blocks with `dipp.chat.shout`. Shouting costs 10 health in survival and requires more than 10 health. An account too weak to shout sends normal chat and receives a warning.
- `!!message` sends global chat with `dipp.chat.shout.global`.
- `dipp.chatspy` receives out-of-range chat with the existing coordinate colors and teleport action.
- Character names and voice-chat status still use the existing PlaceholderAPI placeholders.
- `dipp.spambypass` keeps the existing spam-kick exemption.

## Kobold

The separate `kobold` mechanic requires only PlaceholderAPI. `/kobold <message>` and `/ko <message>` send chat whose words become "kobold" for recipients without the `kobold` character tag or `dipp.koboldspy` permission. Console recipients retain the original message.

Kobold wraps the current chat renderer. It works independently of the chat mechanic, and registers after chat when both are available so proximity filtering and formatting still apply.

## Server verification

After restarting, check normal chat across the 12-block boundary, shouting across the 36-block boundary, low-health shouting, global chat across worlds, and chat-spy delivery. Check a region with `chat: deny` and confirm it cancels both ordinary and kobold chat. Compare `/kobold` and `/ko` output for a kobold character, an ordinary character, and a kobold spy. Confirm shouting deducts health only once.

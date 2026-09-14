# Crates

A crate is an uncolored `minecraft:shulker_box` with these string PDC entries:

- `magicspells:magicitem`: the crate's registered MagicItem ID.
- `magicspells:magicspellpermanentdata_crate_item`: the registered MagicItem ID allowed inside.

Both IDs must resolve in the loaded MagicSpells registry. Inventory clicks, shift transfers, hotbar and offhand swaps, drags, and packing match the contained item's `magicspells:magicitem` exactly. Material, display name, and numeric custom model data do not determine its identity.

Resolution returns either a definition or a failure reason. When placement, opening, GUI interaction, or packing fails to resolve a crate, the player involved receives the reason in chat if they have `dipp.debug`. For `/cratecheck <player>`, this is the named player whose crate is checked, including when the command runs from the console. Internal reads without a player context remain silent.

The GUI has nine storage slots. A full crate contains nine stacks at the contained MagicItem's configured `max-stack-size`, using the item's normal limit when the config does not override it. The GUI also uses this limit. Definitions are resolved from the current registry; reopening a GUI updates its inventory limit after a MagicSpells reload.

The crate ItemStack must have an explicit `minecraft:item_model`. Placement preserves it, and the GUI background copies it with custom model data strings set to `["background"]`. The model is not derived from the contained ID or copied from a registry template, so crates holding the same item can have different appearances. A missing model causes a resolution failure. GUI decorations carry `sneakymisc:crate_decoration` and are excluded from saved contents.

`/cratecheck <player>` derives the success spell from the crate's own MagicItem ID, removes a trailing `WIP`, and appends `-success`. For example:

```text
Crate ID:       item-crate-pearlRawWIP
Contained ID:   item-pearl-raw
Item model:     lom:crates/pearl-raw
Success spell:  item-crate-pearlRaw-success
```

Packing retains the shared failure spell `item-crate-cratepack-fail`. The success spell must exist before the crate is consumed. Packing rejects stacked crate items and crates with an open CMI backpack GUI.

If a shulker box of any color fails normal resolution, the mechanic attempts migration when it is placed, opened as a CMI backpack, checked for packing, or opened or picked up as an existing placed crate. It reads `magicspells:magicitem`, removes an optional `magicitem:` prefix, and searches registered MagicItem IDs case-insensitively. For example, `magicitem:item-crate-spellthreadwip` can match `item-crate-spellThreadWIP`. It does not remove `WIP` during this search.

Migration requires exactly one match, and that registered template must resolve as a valid crate. It replaces the old item with a fresh copy of the template, preserving the original item count, every inventory slot, and the CMI backpack ID. The stored items themselves are copied unchanged, including their metadata and counts. Old crate appearance and other metadata are replaced by the template's values. Already-valid crates are left unchanged.

Missing or ambiguous matches, invalid templates, and unreadable or differently sized inventories leave the original item untouched. Migration does not replace a crate with an open GUI. Failures are reported through the existing `dipp.debug` chat diagnostics. This is migration on interaction, not a scan of all inventories or worlds.

Shulker boxes that still cannot resolve or migrate cannot be placed or opened as crates. Vanilla shulker block inventories are also blocked. The former admin placement bypass remains removed. Existing placed crates can still be picked up when migration fails.

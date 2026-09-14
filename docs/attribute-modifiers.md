# Attribute modifier removal

SneakyMisc extends the vanilla command with a literal `*` for removing all modifiers:

```text
/attribute <target> <attribute> modifier remove <id|*>
```

Using `*` removes every modifier currently attached to that attribute and
reports the number removed. The command result is that count, including zero when
there were no modifiers. The attribute's base value is unchanged.

```text
/attribute Alex minecraft:movement_speed modifier remove *
/execute as Alex run attribute @s minecraft:movement_speed modifier remove *
```

Supplying an ID retains vanilla behavior. Omitting it remains a syntax error.
The extension keeps the existing
permission checks and single-entity target syntax, including selectors. It also
extends `/minecraft:attribute`. Offline players cannot be targeted.

Equipment, effects, or plugins can reapply their modifiers afterward. This command
does not alter items or remove the source of a modifier.

The extension runs during Paper's command lifecycle, including command reloads.
It merges a branch through the dispatcher root because Paper hides vanilla node
children behind `ShadowBrigNode`. It depends on the vanilla
`target / attribute / modifier / remove` command tree. Existing nodes retain their
parsers and permissions. Missing nodes deny execution, and a missing root command
produces a warning.

## Server verification

On the supported Paper build, add two temporary modifiers to a test player's
movement speed. Remove one by ID and confirm the other remains. Add it again,
then use `*` and confirm both are gone and the base value is unchanged.
Repeat using `/minecraft:attribute` and `/execute as <player> run attribute @s`.
Check that an unprivileged player cannot run either removal form, and repeat the
wildcard form after a command reload.

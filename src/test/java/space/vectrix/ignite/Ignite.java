package space.vectrix.ignite;

import space.vectrix.ignite.mod.Mods;

/** Test stand-in for Ignite 1.2.1's public entry point, used by IgniteModsTest. */
public final class Ignite {
    public static volatile Mods mods;

    private Ignite() {}

    public static Mods mods() {
        if (mods == null) throw new IllegalStateException("Ignite has not been initialized yet!");
        return mods;
    }
}

package com.kasper.vcdistance.server;

import net.minecraft.commands.CommandSourceStack;

/** Who may use {@code /vcd} (NeoForge 1.21 - 1.21.1): the console and command blocks, and operators of level 2 or more. */
public final class AdminPermission {

    private AdminPermission() {
    }

    public static boolean isAdmin(CommandSourceStack source) {
        return source.getPlayer() == null || source.hasPermission(2);
    }
}

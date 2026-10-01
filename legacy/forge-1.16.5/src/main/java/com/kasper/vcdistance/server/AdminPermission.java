package com.kasper.vcdistance.server;

import com.kasper.vcdistance.compat.Mc;
import net.minecraft.commands.CommandSourceStack;

/** Who may use {@code /vcd} (Forge 1.16.5): the console and command blocks, and operators of level 2 or more. */
public final class AdminPermission {

    private AdminPermission() {
    }

    public static boolean isAdmin(CommandSourceStack source) {
        return Mc.player(source) == null || source.hasPermission(2);
    }
}

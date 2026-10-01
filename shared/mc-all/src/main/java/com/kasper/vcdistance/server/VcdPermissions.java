package com.kasper.vcdistance.server;

import com.kasper.vcdistance.compat.Mc;
import com.kasper.vcdistance.AdminCommands;
import net.minecraft.commands.CommandSourceStack;

import java.lang.reflect.Method;

/**
 * Who may use which part of {@code /vcd} on Fabric, Forge and NeoForge. The console and command
 * blocks always may. With a permissions mod that implements fabric-permissions-api (LuckPerms on
 * Fabric), each part has its own node ({@link AdminCommands#PERM_STATUS} and the others, or
 * {@link AdminCommands#PERM_ADMIN} for all); without one, operators of level 2 or more may use all
 * of it, as before.
 */
public final class VcdPermissions {

    private static volatile Method check;
    private static volatile boolean looked;

    private VcdPermissions() {
    }

    public static boolean allows(CommandSourceStack source, String permission) {
        if (Mc.player(source) == null) {
            return true;
        }
        Method m = check();
        if (m != null) {
            try {
                return (Boolean) m.invoke(null, source, permission, 2) || (Boolean) m.invoke(null, source, AdminCommands.PERM_ADMIN, 2);
            } catch (Throwable ignored) {
                // falls back to the operator level
            }
        }
        return AdminPermission.isAdmin(source);
    }

    /** Whether the source may use any part of {@code /vcd} (and so gets the command and the Server tab). */
    public static boolean any(CommandSourceStack source) {
        for (String p : AdminCommands.PERMISSIONS) {
            if (allows(source, p)) {
                return true;
            }
        }
        return false;
    }

    /** {@code Permissions.check(source, permission, defaultLevel)} of fabric-permissions-api, when it is installed. */
    private static Method check() {
        if (!looked) {
            try {
                Class<?> api = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions");
                for (Method m : api.getMethods()) {
                    Class<?>[] types = m.getParameterTypes();
                    if (m.getName().equals("check") && types.length == 3 && types[0].isAssignableFrom(CommandSourceStack.class)
                            && types[1] == String.class && types[2] == int.class && m.getReturnType() == boolean.class) {
                        check = m;
                        break;
                    }
                }
            } catch (Throwable ignored) {
                // not installed
            }
            looked = true;
        }
        return check;
    }
}

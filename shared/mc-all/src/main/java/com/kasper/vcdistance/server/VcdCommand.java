package com.kasper.vcdistance.server;

import com.kasper.vcdistance.compat.Txt;

import com.kasper.vcdistance.AdminCommands;
import com.kasper.vcdistance.AudioDistancePlugin;
import com.kasper.vcdistance.CommandReply;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

/**
 * {@code /vcd} on a Fabric, Forge or NeoForge server. The text after the command goes to
 * {@link AdminCommands} as is, so every version registers the same small tree; replies come back as
 * coloured chat lines with buttons, and suggestions carry a short explanation.
 */
public final class VcdCommand {

    /** What the version's server entrypoint provides. */
    public interface Server {
        void resendProfiles(MinecraftServer server);
    }

    private VcdCommand() {
    }

    /**
     * @param admin kept for the entrypoints; who may use which part is decided by {@link VcdPermissions}
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, Predicate<CommandSourceStack> admin, Server hooks) {
        dispatcher.register(Commands.literal(AdminCommands.NAME)
                .requires(VcdPermissions::any)
                .executes(ctx -> run(ctx, "", hooks))
                .then(Commands.argument("args", StringArgumentType.greedyString())
                        .suggests((ctx, builder) -> suggest(ctx, builder, hooks))
                        .executes(ctx -> run(ctx, StringArgumentType.getString(ctx, "args"), hooks))));
    }

    private static int run(CommandContext<CommandSourceStack> ctx, String args, Server hooks) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player != null) {
            // Commands like zone pos1 need where the admin stands right now
            AudioDistancePlugin.PLAYERS.update(ServerBridge.info(player));
        }
        CommandReply reply = AdminCommands.execute(args, AudioDistancePlugin.SERVER_SETTINGS, ServerBridge.context(source, hooks));
        for (CommandReply.Line line : reply.lines()) {
            Component text = ReplyComponents.of(line);
            source.sendSuccess(() -> text, false);
        }
        return 1;
    }

    private static CompletableFuture<Suggestions> suggest(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder, Server hooks) {
        String typed = builder.getRemaining();
        int lastSpace = typed.lastIndexOf(' ');
        SuggestionsBuilder word = builder.createOffset(builder.getStart() + lastSpace + 1);
        AdminCommands.Context context = ServerBridge.context(ctx.getSource(), hooks);
        for (AdminCommands.Suggestion s : AdminCommands.suggestions(typed, AudioDistancePlugin.SERVER_SETTINGS, context)) {
            if (s.tooltip() == null) {
                word.suggest(s.text());
            } else {
                word.suggest(s.text(), Txt.literal(s.tooltip()));
            }
        }
        return word.buildFuture();
    }
}

package com.kasper.vcdistance.server;

import com.kasper.vcdistance.compat.Txt;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chat text whose links open in the browser when clicked, text that runs a command, and the click
 * and hover events of command replies.
 * <p>
 * Both events changed shape in 1.21.5 (a class with an action and a value became a record per
 * action), and one jar serves both sides of that change under obfuscated names, so the events are
 * made by shape: the nested record holding a {@link URI}, a text or a {@link Component}, or the
 * constructor taking an action and a value with the action whose name is "open_url", "show_text"...
 * When neither works the text stays plain.
 */
public final class ChatLink {

    private static final Pattern URL = Pattern.compile("https?://\\S+[^\\s.,;:!?)\\]]");
    private static volatile boolean failed;
    private static volatile boolean hoverFailed;

    private ChatLink() {
    }

    public static Component of(String text) {
        Matcher m = URL.matcher(text);
        if (failed || !m.find()) {
            return Txt.literal(text);
        }
        MutableComponent out = Txt.literal("");
        int last = 0;
        do {
            out.append(Txt.literal(text.substring(last, m.start())));
            String url = m.group();
            ClickEvent click = openUrl(url);
            MutableComponent link = Txt.literal(url);
            if (click != null) {
                link = link.withStyle(Style.EMPTY.withClickEvent(click).withUnderlined(true));
            }
            out.append(link);
            last = m.end();
        } while (m.find());
        out.append(Txt.literal(text.substring(last)));
        return out;
    }

    /**
     * {@code text} that runs {@code command} (with its slash) when clicked, or {@code text} unchanged
     * when this version's click event could not be made.
     */
    public static Component command(Component text, String command) {
        ClickEvent click = failed ? null : make("run_command", command);
        if (click == null) {
            return text;
        }
        return Txt.empty().append(text).withStyle(Style.EMPTY.withClickEvent(click).withUnderlined(true));
    }

    private static ClickEvent openUrl(String url) {
        ClickEvent click = make("open_url", url);
        if (click == null) {
            failed = true;
        }
        return click;
    }

    /**
     * A click event of the action serialized as {@code action} ("open_url", "run_command",
     * "suggest_command", "copy_to_clipboard") with {@code value}, or {@code null} when this version's
     * event could not be made.
     */
    public static ClickEvent click(String action, String value) {
        return failed ? null : make(action, value);
    }

    /** A hover event that shows {@code text}, or {@code null} when this version's event could not be made. */
    public static HoverEvent hover(Component text) {
        if (hoverFailed) {
            return null;
        }
        try {
            if (HoverEvent.class.isInterface()) {
                // 1.21.5 and newer: HoverEvent.ShowText(Component)
                for (Class<?> nested : HoverEvent.class.getDeclaredClasses()) {
                    Class<?>[] parts = com.kasper.vcdistance.Jv.recordTypes(nested);
                    if (parts != null && parts.length == 1 && parts[0] == Component.class) {
                        Constructor<?> c = nested.getDeclaredConstructor(Component.class);
                        c.setAccessible(true);
                        return (HoverEvent) c.newInstance(text);
                    }
                }
            } else {
                // Before 1.21.5: new HoverEvent(HoverEvent.Action.SHOW_TEXT, text), the action a constant of a class
                for (Class<?> nested : HoverEvent.class.getDeclaredClasses()) {
                    Object showText = constantNamed(nested, "show_text");
                    if (showText == null) {
                        continue;
                    }
                    for (Constructor<?> c : HoverEvent.class.getDeclaredConstructors()) {
                        Class<?>[] types = c.getParameterTypes();
                        if (types.length == 2 && types[0] == nested) {
                            c.setAccessible(true);
                            return (HoverEvent) c.newInstance(showText, text);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            // falls through to no hover
        }
        hoverFailed = true;
        return null;
    }

    /** The static field of {@code type}'s own type whose name (any no-argument String method) is {@code name}. */
    private static Object constantNamed(Class<?> type, String name) {
        for (java.lang.reflect.Field f : type.getDeclaredFields()) {
            if (!java.lang.reflect.Modifier.isStatic(f.getModifiers()) || f.getType() != type) {
                continue;
            }
            try {
                f.setAccessible(true);
                Object constant = f.get(null);
                for (Method method : type.getMethods()) {
                    if (method.getParameterCount() == 0 && method.getReturnType() == String.class
                            && !java.lang.reflect.Modifier.isStatic(method.getModifiers()) && name.equals(method.invoke(constant))) {
                        return constant;
                    }
                }
            } catch (Throwable ignored) {
                // not this one
            }
        }
        return null;
    }

    /** A click event of the action serialized as {@code action} ("open_url", "run_command") with {@code value}. */
    private static ClickEvent make(String action, String value) {
        try {
            if (ClickEvent.class.isInterface()) {
                // 1.21.5 and newer: a record per action, e.g. ClickEvent.OpenUrl(URI), ClickEvent.RunCommand(String)
                for (Class<?> nested : ClickEvent.class.getDeclaredClasses()) {
                    Class<?>[] parts = com.kasper.vcdistance.Jv.recordTypes(nested);
                    if (parts == null || parts.length != 1) {
                        continue;
                    }
                    Object arg = parts[0] == URI.class ? URI.create(value)
                            : parts[0] == String.class ? value : null;
                    if (arg == null) {
                        continue;
                    }
                    Constructor<?> c = nested.getDeclaredConstructor(parts[0]);
                    c.setAccessible(true);
                    Object event = c.newInstance(arg);
                    if (hasAction(event, action)) {
                        return (ClickEvent) event;
                    }
                }
            } else {
                // Before 1.21.5: new ClickEvent(ClickEvent.Action.OPEN_URL, url)
                for (Constructor<?> c : ClickEvent.class.getDeclaredConstructors()) {
                    Class<?>[] types = c.getParameterTypes();
                    if (types.length == 2 && types[0].isEnum() && types[1] == String.class) {
                        Object constant = actionNamed(types[0], action);
                        if (constant != null) {
                            c.setAccessible(true);
                            return (ClickEvent) c.newInstance(constant, value);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            // falls through to plain text
        }
        return null;
    }

    /** Whether the event's action (the enum any no-argument method returns) is {@code action}. */
    private static boolean hasAction(Object event, String action) {
        for (Method method : event.getClass().getMethods()) {
            if (method.getParameterCount() == 0 && method.getReturnType().isEnum()) {
                try {
                    Object constant = method.invoke(event);
                    if (constant instanceof Enum<?> e && actionNamed(e.getDeclaringClass(), action) == constant) {
                        return true;
                    }
                } catch (Throwable ignored) {
                    // not this one
                }
            }
        }
        return false;
    }

    /** The enum constant whose serialized name (any no-argument String method) is {@code name}. */
    private static Object actionNamed(Class<?> type, String name) {
        for (Object constant : type.getEnumConstants()) {
            if (((Enum<?>) constant).name().equalsIgnoreCase(name)) {
                return constant;
            }
            for (Method method : type.getMethods()) {
                if (method.getParameterCount() == 0 && method.getReturnType() == String.class) {
                    try {
                        if (name.equals(method.invoke(constant))) {
                            return constant;
                        }
                    } catch (Throwable ignored) {
                        // not this one
                    }
                }
            }
        }
        return null;
    }
}

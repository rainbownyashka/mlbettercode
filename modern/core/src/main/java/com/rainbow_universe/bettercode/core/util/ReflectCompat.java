package com.rainbow_universe.bettercode.core.util;

import java.util.function.Function;

public final class ReflectCompat {
    private ReflectCompat() {
    }

    public static String readSignLineReflect(Object be, int line, Function<Object, String> textToString) {
        if (be == null || textToString == null) {
            return "";
        }
        try {
            Object text = be.getClass()
                .getMethod("getTextOnRow", int.class, boolean.class)
                .invoke(be, Integer.valueOf(line), Boolean.FALSE);
            String s = safe(textToString.apply(text)).trim();
            if (!s.isEmpty()) {
                return s;
            }
        } catch (Exception ignore) {
        }
        try {
            Object front = be.getClass().getMethod("getFrontText").invoke(be);
            Object msg = front.getClass()
                .getMethod("getMessage", int.class, boolean.class)
                .invoke(front, Integer.valueOf(line), Boolean.FALSE);
            String s = safe(textToString.apply(msg)).trim();
            if (!s.isEmpty()) {
                return s;
            }
        } catch (Exception ignore) {
        }
        String[] fields = new String[] {"text", "texts", "messages"};
        for (String f : fields) {
            String v = readSignLineFromField(be, f, line, textToString);
            if (!v.isEmpty()) {
                return v;
            }
        }
        try {
            java.lang.reflect.Field[] all = be.getClass().getDeclaredFields();
            for (java.lang.reflect.Field f : all) {
                if (f == null || f.getType() == null || !f.getType().isArray()) {
                    continue;
                }
                Class<?> c = f.getType().getComponentType();
                if (c == null || !c.getName().toLowerCase().contains("text")) {
                    continue;
                }
                f.setAccessible(true);
                Object raw = f.get(be);
                if (!(raw instanceof Object[])) {
                    continue;
                }
                Object[] arr = (Object[]) raw;
                if (line < 0 || line >= arr.length) {
                    continue;
                }
                String s = safe(textToString.apply(arr[line])).trim();
                if (!s.isEmpty()) {
                    return s;
                }
            }
        } catch (Exception ignore) {
        }
        return "";
    }

    public static void sendLookPacketReflect(Object networkHandler, float yaw, float pitch, boolean onGround) {
        if (networkHandler == null) {
            return;
        }
        Object packet = null;
        String[] candidates = new String[] {
            "net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket$LookAndOnGround",
            "net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket$LookOnly"
        };
        for (String cn : candidates) {
            try {
                Class<?> c = Class.forName(cn);
                java.lang.reflect.Constructor<?> ctor = c.getDeclaredConstructor(float.class, float.class, boolean.class);
                ctor.setAccessible(true);
                packet = ctor.newInstance(Float.valueOf(yaw), Float.valueOf(pitch), Boolean.valueOf(onGround));
                if (packet != null) {
                    break;
                }
            } catch (Exception ignore) {
            }
        }
        if (packet == null) {
            return;
        }
        try {
            for (java.lang.reflect.Method m : networkHandler.getClass().getMethods()) {
                if (!"sendPacket".equals(m.getName()) || m.getParameterCount() != 1) {
                    continue;
                }
                m.invoke(networkHandler, packet);
                return;
            }
        } catch (Exception ignore) {
        }
    }

    private static String readSignLineFromField(Object be, String fieldName, int line, Function<Object, String> textToString) {
        if (be == null || fieldName == null || fieldName.isEmpty()) {
            return "";
        }
        try {
            java.lang.reflect.Field f = be.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            Object raw = f.get(be);
            if (!(raw instanceof Object[])) {
                return "";
            }
            Object[] arr = (Object[]) raw;
            if (line < 0 || line >= arr.length) {
                return "";
            }
            return safe(textToString.apply(arr[line])).trim();
        } catch (Exception ignore) {
            return "";
        }
    }

    private static String safe(String v) {
        return v == null ? "" : v;
    }
}

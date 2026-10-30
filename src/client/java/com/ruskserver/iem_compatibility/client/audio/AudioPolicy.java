package com.ruskserver.iem_compatibility.client.audio;

/** 音の用途と、既にサーバーで加工された音を区別する。Minecraft/OpenAL 非依存。 */
public final class AudioPolicy {
    private AudioPolicy() {}
    public static boolean positional(String category, String namespace, String path, boolean relative, boolean attenuated) {
        if (relative || !attenuated) return false;
        if (switch (category) { case "music", "record", "records", "weather", "ambient", "voice", "ui" -> true; default -> false; }) return false;
        if (namespace.equals("iem")) {
            for (String prefix : new String[]{"radio.", "raid_end.", "ambient.", "pda.", "result.", "loot.reveal.", "med.", "backpack."})
                if (path.startsWith(prefix)) return false;
        }
        return true;
    }
    public static boolean serverOccluded(String namespace, String path) {
        return namespace.equals("iem") && path.startsWith("scav.voice.") && (path.endsWith(".muffled") || path.endsWith(".deep"));
    }
    public record Response(float gain, float highFrequency) {
        public static final Response CLEAR = new Response(1, 1);
    }
    /** 減衰距離そのものはバニラに任せ、高域と実遮蔽だけを追加する。 */
    public static Response response(double distance, double barriers, boolean alreadyOccluded) {
        if (alreadyOccluded) return Response.CLEAR;
        double obstruction = Math.max(0, Math.min(2, barriers));
        return new Response((float)Math.exp(-.48 * obstruction),
                (float)Math.max(.16, Math.exp(-.75 * obstruction - Math.max(0, distance - 8) / 180)));
    }
    public static float mixGain(int channels, boolean positional) { return positional && channels == 2 ? .5f : 1f; }
}

package com.ruskserver.iem_compatibility.client.audio;

public final class AudioPolicyTest {
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) {
        for (String path : new String[]{"radio.quest_item_found", "radio.high_threat", "result.level_up", "loot.reveal.5", "raid_end.voice.client_warning", "pda.click"})
            check(!AudioPolicy.positional("master", "iem", path, false, true), "presentation changed: " + path);
        for (String path : new String[]{"scav.voice.spotted.a1", "bullet.impact", "bullet.whizz", "step.stone.indoor"})
            check(AudioPolicy.positional("master", "iem", path, false, true), "world effect excluded: " + path);
        check(!AudioPolicy.positional("blocks", "minecraft", "block.stone.break", true, true), "relative source");
        check(!AudioPolicy.positional("blocks", "minecraft", "block.stone.break", false, false), "unattenuated source");
        check(!AudioPolicy.positional("music", "anothermod", "song", false, true), "music");
        check(AudioPolicy.positional("players", "toisarm", "fire.usp", false, true), "datapack source");
        check(AudioPolicy.mixGain(2,true)==.5f && AudioPolicy.mixGain(1,true)==1 && AudioPolicy.mixGain(2,false)==1, "downmix headroom");
        check(AudioPolicy.serverOccluded("iem", "scav.voice.spotted.a1.deep"), "server occlusion missed");
        check(!AudioPolicy.serverOccluded("other", "scav.voice.spotted.a1.deep"), "foreign event");
        check(AudioPolicy.response(30,2,true).equals(AudioPolicy.Response.CLEAR), "double occlusion");
        var clear=AudioPolicy.response(8,0,false); var wall=AudioPolicy.response(8,1,false); var wool=AudioPolicy.response(8,1.5,false);
        check(clear.gain()==1 && clear.highFrequency()==1, "near clear path");
        check(wool.gain()<wall.gain() && wall.gain()<clear.gain(), "barrier transmission");
        check(wall.highFrequency()>.16f, "high frequencies removed entirely");
        check(AudioPolicy.response(100,0,false).gain()==1, "distance applied twice");
        check(AudioPolicy.response(100,0,false).highFrequency()<clear.highFrequency(), "air absorption");
        check(AcousticMath.loaded(-16,-16,-16,-16,(x,z)->x==-1 && z==-1), "negative zero-length");
        check(!AcousticMath.loaded(15.9,15.8,16.1,16.2,(x,z)->!(x==0 && z==1)), "short diagonal unloaded crossing");
        check(!AcousticMath.loaded(1,1,31,31,(x,z)->!(x==1 && z==0)), "corner side chunk");
        check(AcousticMath.loaded(-31,-31,31,31,(x,z)->true), "loaded diagonal");
        check(!AcousticMath.loaded(0,0,100000,0,(x,z)->true), "unbounded chunk walk");
        System.out.println("AudioPolicyTest passed: routing, double filtering, downmix gain, attenuation, chunk boundaries");
    }
}

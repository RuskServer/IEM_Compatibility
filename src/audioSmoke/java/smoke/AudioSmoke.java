package smoke;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** 専用テストMODからpreLaunchで実行する。通常の配布jarには含めない。 */
public final class AudioSmoke implements PreLaunchEntrypoint {
    private Class<?> type(String name) throws Exception { return Class.forName(name, false, Thread.currentThread().getContextClassLoader()); }
    private Object call(Class<?> owner, Object object, String name, Class<?>[] params, Object... values) throws Exception {
        return owner.getMethod(name, params).invoke(object, values);
    }
    private void configModel() throws Exception {
        Class<?> settingsClass=type("com.ruskserver.iem_compatibility.client.audio.AudioSettings");
        Object settings=settingsClass.getField("INSTANCE").get(null);
        boolean original=settingsClass.getField("enabled").getBoolean(settings);
        java.util.concurrent.atomic.AtomicInteger saves=new java.util.concurrent.atomic.AtomicInteger();
        Class<?> screen=type("com.ruskserver.iem_compatibility.client.IemConfigScreen");
        Object model=call(screen,null,"model",new Class<?>[]{boolean.class,Runnable.class},false,(Runnable)saves::incrementAndGet);
        Class<?> yacl=type("dev.isxander.yacl3.api.YetAnotherConfigLib");
        Class<?> option=type("dev.isxander.yacl3.api.Option");
        Class<?> state=type("dev.isxander.yacl3.api.StateManager");
        Class<?> component=type("net.minecraft.network.chat.Component");
        Class<?> translatable=type("net.minecraft.network.chat.contents.TranslatableContents");
        Object stream=call(type("dev.isxander.yacl3.api.utils.OptionUtils"),null,"getFlatOptions",new Class<?>[]{yacl},model);
        java.util.Map<String,Object> options=new java.util.HashMap<>();
        for(Object o:((java.util.stream.Stream<?>)stream).toList()) {
            Object name=call(option,o,"name",new Class<?>[]{});
            Object contents=call(component,name,"getContents",new Class<?>[]{});
            if(translatable.isInstance(contents))options.put((String)call(translatable,contents,"getKey",new Class<?>[]{}),o);
        }
        Object enabled=call(option,options.get("iem.config.enabled"),"stateManager",new Class<?>[]{});
        call(state,enabled,"set",new Class<?>[]{Object.class},!original);
        if(settingsClass.getField("enabled").getBoolean(settings)!=original)throw new AssertionError("UI changed audio before saving");
        call(state,enabled,"sync",new Class<?>[]{});
        if(!call(state,enabled,"get",new Class<?>[]{}).equals(original))throw new AssertionError("cancel did not restore applied value");
        Runnable save=(Runnable)call(yacl,model,"saveFunction",new Class<?>[]{});
        call(state,enabled,"set",new Class<?>[]{Object.class},!original);call(state,enabled,"apply",new Class<?>[]{});save.run();
        if(saves.get()!=1)throw new AssertionError("first audio save");
        save.run();if(saves.get()!=1)throw new AssertionError("unchanged settings reloaded audio");
        call(state,enabled,"set",new Class<?>[]{Object.class},original);call(state,enabled,"apply",new Class<?>[]{});save.run();
        if(saves.get()!=2)throw new AssertionError("second save must compare against previous applied state");
        for(String button:new String[]{"test_mono","test_stereo","test_stop"}) {
            if((boolean)call(option,options.get("iem.config."+button),"available",new Class<?>[]{}))throw new AssertionError("test allowed outside world");
        }
        Object gpu=call(option,options.get("iem.config.gpu"),"stateManager",new Class<?>[]{});
        call(state,gpu,"set",new Class<?>[]{Object.class},true);call(state,gpu,"apply",new Class<?>[]{});save.run();
        if(saves.get()!=2)throw new AssertionError("GPU setting restarted audio");
        call(state,gpu,"set",new Class<?>[]{Object.class},false);call(state,gpu,"apply",new Class<?>[]{});
        try { type("com.ruskserver.iem_compatibility.client.CoreSwitches");throw new AssertionError("shader bypass class still present"); }
        catch(ClassNotFoundException expected) { /* 診断用の描画無効化機構は配布しない。 */ }
        if(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("modmenu")) {
            Class<?> bridge=type("com.ruskserver.iem_compatibility.client.IemModMenu");
            if(call(bridge,bridge.getConstructor().newInstance(),"getModConfigScreenFactory",new Class<?>[]{})==null)throw new AssertionError("Mod Menu factory missing");
            System.out.println("MOD_MENU_FACTORY_PASSED");
        } else System.out.println("CONFIG_WITHOUT_MOD_MENU_PASSED");
        System.out.println("YACL_CONFIG_MODEL_PASSED: pending/cancel, repeated saves, no-op save, GPU independence, world-only tests");
    }
    public void onPreLaunch() {
        try {
            Class<?> library = type("com.mojang.blaze3d.audio.Library");
            Class<?> channel = type("com.mojang.blaze3d.audio.Channel");
            Class<?> engine = type("net.minecraft.client.sounds.SoundEngine");
            for (Class<?> c : new Class<?>[]{library,channel,engine}) { c.getDeclaredMethods(); System.out.println("AUDIO_MIXIN_TRANSFORM_OK " + c.getName()); }
            Class<?> duck = type("com.ruskserver.iem_compatibility.client.audio.AudioChannel");
            if (!duck.isAssignableFrom(channel)) throw new AssertionError("AudioChannel interface missing");
            Class<?> devices = type("com.mojang.blaze3d.audio.DeviceList");
            Object lib = library.getConstructor().newInstance();
            call(library,lib,"init",new Class<?>[]{String.class,devices,boolean.class},null,devices.getField("EMPTY").get(null),false);
            Class<?> spatial = type("com.ruskserver.iem_compatibility.client.audio.SpatialAudio");
            Object device = spatial.getField("device").get(null);
            int status = (int)device.getClass().getMethod("status").invoke(device);
            if (status != 1 && status != 3 && status != 4) throw new AssertionError("HRTF request did not reach device: " + status);
            Class<?> pool = type("com.mojang.blaze3d.audio.Library$Pool");
            Object ch = call(library,lib,"acquireChannel",new Class<?>[]{pool},pool.getField("STATIC").get(null));
            Class<?> al = type("org.lwjgl.openal.AL10");
            int buffer = (int)call(al,null,"alGenBuffers",new Class<?>[]{});
            ByteBuffer pcm = ByteBuffer.allocateDirect(4800 * 4).order(ByteOrder.nativeOrder());
            for (int i=0;i<4800;i++) { short s=(short)(Math.sin(i*.1)*2000);pcm.putShort(s).putShort(s); } pcm.flip();
            call(al,null,"alBufferData",new Class<?>[]{int.class,int.class,ByteBuffer.class,int.class},buffer,0x1103,pcm,48000);
            Field sourceField = channel.getDeclaredField("source"); sourceField.setAccessible(true);int source = sourceField.getInt(ch);
            call(al,null,"alSourcei",new Class<?>[]{int.class,int.class,int.class},source,0x1009,buffer);
            Class<?> vec = type("net.minecraft.world.phys.Vec3");
            call(channel,ch,"setSelfPosition",new Class<?>[]{vec},vec.getConstructor(double.class,double.class,double.class).newInstance(0.,2.,-6.));
            call(channel,ch,"setVolume",new Class<?>[]{float.class},.8f);
            Class<?> sound = type("net.minecraft.client.resources.sounds.SoundInstance");
            Class<?> identifier = type("net.minecraft.resources.Identifier");
            Object id = call(identifier,null,"fromNamespaceAndPath",new Class<?>[]{String.class,String.class},"iem_compatibility","audio_probe.stereo");
            Object proxy = Proxy.newProxyInstance(sound.getClassLoader(),new Class<?>[]{sound},(o,m,a)-> {
                if (m.getName().equals("getIdentifier")) return id;
                throw new UnsupportedOperationException(m.getName());
            });
            Class<?> entry = type("com.ruskserver.iem_compatibility.client.audio.AcousticScene$Entry");
            Constructor<?> ctor=entry.getDeclaredConstructors()[0];ctor.setAccessible(true);Object e=ctor.newInstance(proxy,null);
            Class<?> response=type("com.ruskserver.iem_compatibility.client.audio.AudioPolicy$Response");
            entry.getField("target").set(e,response.getConstructor(float.class,float.class).newInstance(.62f,.47f));
            call(duck,ch,"iem$configure",new Class<?>[]{boolean.class,entry},true,e);
            call(channel,ch,"play",new Class<?>[]{});
            float gain=(float)call(al,null,"alGetSourcef",new Class<?>[]{int.class,int.class},source,0x100A);
            if(Math.abs(gain-.8f*.5f*.62f)>1e-5)throw new AssertionError("initial mix/occlusion gain: "+gain);
            call(channel,ch,"setVolume",new Class<?>[]{float.class},.6f);
            gain=(float)call(al,null,"alGetSourcef",new Class<?>[]{int.class,int.class},source,0x100A);
            if(Math.abs(gain-.6f*.5f*.62f)>1e-5)throw new AssertionError("volume update: "+gain);
            entry.getField("target").set(e,response.getConstructor(float.class,float.class).newInstance(1f,1f));
            call(channel,ch,"updateStream",new Class<?>[]{});
            gain=(float)call(al,null,"alGetSourcef",new Class<?>[]{int.class,int.class},source,0x100A);
            if(!(gain>.6f*.5f*.62f && gain<.6f*.5f))throw new AssertionError("occlusion must interpolate: "+gain);
            call(channel,ch,"setSelfPosition",new Class<?>[]{vec},vec.getConstructor(double.class,double.class,double.class).newInstance(0.,0.,0.));
            entry.getField("target").set(e,response.getConstructor(float.class,float.class).newInstance(1f,1f));
            for(int i=0;i<50;i++)call(channel,ch,"updateStream",new Class<?>[]{});
            gain=(float)call(al,null,"alGetSourcef",new Class<?>[]{int.class,int.class},source,0x100A);
            if(Math.abs(gain-.6f)>1e-5)throw new AssertionError("coincident stereo gain: "+gain);
            call(library,lib,"releaseChannel",new Class<?>[]{channel},ch);
            call(al,null,"alDeleteBuffers",new Class<?>[]{int.class},buffer);
            int error=(int)call(al,null,"alGetError",new Class<?>[]{});
            if(error!=0)throw new AssertionError("OpenAL error "+Integer.toHexString(error));
            call(library,lib,"cleanup",new Class<?>[]{});
            Class<?> settings=type("com.ruskserver.iem_compatibility.client.audio.AudioSettings");
            settings.getField("enabled").setBoolean(settings.getField("INSTANCE").get(null),false);
            call(library,lib,"init",new Class<?>[]{String.class,devices,boolean.class},null,devices.getField("EMPTY").get(null),false);
            device=spatial.getField("device").get(null);
            status=(int)device.getClass().getMethod("status").invoke(device);
            if(status!=0)throw new AssertionError("OFF did not restore vanilla disabled HRTF: "+status);
            call(library,lib,"cleanup",new Class<?>[]{});
            configModel();
            System.out.println("AUDIO_MIXIN_SMOKE_PASSED: transformed targets, HRTF ON/OFF, stereo gain, EFX, smoothing, volume updates, cleanup");
            System.exit(0);
        } catch (Throwable error) { error.printStackTrace();System.exit(1); }
    }
}

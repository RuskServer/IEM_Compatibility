"""OpenAL Softのループバックによる回帰テスト。音声デバイス・OS設定は変更しない。
Usage: python tools/check_audio_native.py /path/to/libopenal.so
Requires numpy. Tests native semantics; does not launch Minecraft/Mixin.
"""
import ctypes as c
import sys
import numpy as np

lib = c.CDLL(sys.argv[1])
P,I,U,F = c.c_void_p,c.c_int,c.c_uint,c.c_float
def bind(name,result,args):
    fn = getattr(lib,name); fn.restype=result; fn.argtypes=args; return fn
open_device=bind('alcLoopbackOpenDeviceSOFT',P,[c.c_char_p])
context=bind('alcCreateContext',P,[P,c.POINTER(I)])
current=bind('alcMakeContextCurrent',c.c_char,[P])
destroy=bind('alcDestroyContext',None,[P]);close=bind('alcCloseDevice',c.c_char,[P])
render=bind('alcRenderSamplesSOFT',None,[P,P,I])
gen_source=bind('alGenSources',None,[I,c.POINTER(U)]);gen_buffer=bind('alGenBuffers',None,[I,c.POINTER(U)])
delete_source=bind('alDeleteSources',None,[I,c.POINTER(U)]);delete_buffer=bind('alDeleteBuffers',None,[I,c.POINTER(U)])
data=bind('alBufferData',None,[U,I,P,I,I]);source_i=bind('alSourcei',None,[U,I,I]);source_f=bind('alSourcef',None,[U,I,F])
position=bind('alSource3f',None,[U,I,F,F,F]);play=bind('alSourcePlay',None,[U]);error=bind('alGetError',I,[])
gen_filter=bind('alGenFilters',None,[I,c.POINTER(U)]);delete_filter=bind('alDeleteFilters',None,[I,c.POINTER(U)])
filter_i=bind('alFilteri',None,[U,I,I]);filter_f=bind('alFilterf',None,[U,I,F])

def run(samples,stereo=False,spatial=False,angle=45,gain=1,hf=1,occlusion_gain=1):
    device=open_device(None)
    attrs=(I*11)(0x1990,0x1501,0x1991,0x1406,0x1007,48000,0x1992,1,0x1996,0,0)
    ctx=context(device,attrs);assert ctx;current(ctx)
    source=U();buffer=U();flt=U()
    gen_source(1,c.byref(source));gen_buffer(1,c.byref(buffer))
    pcm=(samples*20000).astype(np.int16)
    if stereo: pcm=np.repeat(pcm[:,None],2,axis=1)
    data(buffer,0x1103 if stereo else 0x1101,pcm.ctypes.data,pcm.nbytes,48000)
    source_i(source,0x1009,buffer.value);source_f(source,0x1021,0)
    if spatial:source_i(source,0x1214,1)
    source_f(source,0x100A,gain*occlusion_gain)
    angle=np.deg2rad(angle);position(source,0x1004,0,6*np.sin(angle),-6*np.cos(angle))
    gen_filter(1,c.byref(flt));filter_i(flt,0x8001,1);filter_f(flt,1,1);filter_f(flt,2,hf)
    source_i(source,0x20005,flt.value)
    assert error()==0,'native setup failed'
    play(source)
    out=np.zeros((len(samples)+4800,2),np.float32);render(device,out.ctypes.data,len(out))
    source_i(source,0x20005,0);delete_filter(1,c.byref(flt))
    delete_source(1,c.byref(source));delete_buffer(1,c.byref(buffer))
    assert error()==0,'render/cleanup failed'
    current(None);destroy(ctx);close(device)
    return out

noise=np.random.default_rng(5313).normal(0,.1,48000)
vanilla_up=run(noise,True);vanilla_down=run(noise,True,angle=-45)
assert np.array_equal(vanilla_up,vanilla_down),'stereo baseline changed'
up=run(noise,True,True,gain=.5);down=run(noise,True,True,angle=-45,gain=.5)
assert not np.allclose(up,down),'positional stereo lost height'
mono=run(noise)
assert np.allclose(up,mono,atol=2e-6),'stereo correction does not match correlated mono peak'
near=run(noise,True,True,gain=.5,hf=1)
wall=run(noise,True,True,gain=.5,hf=.47,occlusion_gain=.62)
assert np.linalg.norm(wall)<np.linalg.norm(near),'occlusion volume did not decrease'
f=np.fft.rfftfreq(len(near),1/48000)
def band(x):return float((np.abs(np.fft.rfft(x[:,0]))**2)[f>4000].sum())
assert 0<band(wall)<band(near),'filter must attenuate without erasing high frequencies'
print('Native audio checks passed: stereo height, mono headroom, EFX transmission, cleanup')

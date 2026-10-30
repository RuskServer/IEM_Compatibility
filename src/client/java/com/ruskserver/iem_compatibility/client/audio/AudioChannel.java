package com.ruskserver.iem_compatibility.client.audio;

/** メソッドの呼出しは必ず ChannelHandle.execute の音声スレッドから行う。 */
public interface AudioChannel {
    void iem$configure(boolean positional, AcousticScene.Entry entry);
}

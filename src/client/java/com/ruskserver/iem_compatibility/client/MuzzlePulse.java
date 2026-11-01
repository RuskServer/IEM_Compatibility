package com.ruskserver.iem_compatibility.client;

/** 点灯・消灯が描画前にまとめて届いても、発射を次の描画まで保持する。 */
final class MuzzlePulse {
    static final long VISIBLE_NANOS = 50_000_000L;
    static final long QUEUE_NANOS = 250_000_000L;
    private int pending, active;
    private long receivedAt, activeUntil;
    private long packets, shots, sampled;

    synchronized void receive(int mode, long now) {
        packets++;
        if (mode < 1 || mode > 3) return;
        shots++;
        inject(mode, now);
        // 0はサーバーの1tick終了通知。未描画の点灯を消さず、消灯は描画側の期限で行う。
    }

    synchronized void inject(int mode, long now) {
        if (mode < 1 || mode > 3) return;
        pending = mode;
        receivedAt = now;
    }

    synchronized int sample(long now) {
        if (pending != 0) {
            if (now - receivedAt <= QUEUE_NANOS) {
                active = pending;
                activeUntil = now + VISIBLE_NANOS;
                sampled++;
            }
            pending = 0;
        }
        if (active != 0 && now >= activeUntil) active = 0;
        return active;
    }

    synchronized void clear() {
        pending = active = 0;
        receivedAt = activeUntil = packets = shots = sampled = 0;
    }

    synchronized String status(long now) {
        return "受信=" + packets + " 発射=" + shots + " 描画用に採用=" + sampled
                + " 待機=" + pending + " 表示=" + (now < activeUntil ? active : 0);
    }
}

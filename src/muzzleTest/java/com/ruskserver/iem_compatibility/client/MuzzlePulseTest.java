package com.ruskserver.iem_compatibility.client;

public final class MuzzlePulseTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        var pulse = new MuzzlePulse();
        long ms = 1_000_000L;
        pulse.receive(1, 0);
        pulse.receive(0, 50 * ms);
        check(pulse.sample(80 * ms) == 1, "on/off batched before rendering must still draw");
        check(pulse.sample(129 * ms) == 1, "pulse lasts one tick from rendering");
        check(pulse.sample(130 * ms) == 0, "pulse expires without server reset");
        pulse.receive(2, 140 * ms);
        check(pulse.sample(141 * ms) == 2, "ADS replaces expired pulse");
        pulse.receive(0, 142 * ms);
        check(pulse.sample(143 * ms) == 2, "off cannot erase the render pulse");
        pulse.receive(3, 150 * ms);
        check(pulse.sample(151 * ms) == 3, "new shot replaces active pulse with correct mode");
        pulse.receive(999, 152 * ms);
        check(pulse.sample(153 * ms) == 3, "invalid mode cannot activate or replace a shot");
        pulse.clear();
        check(pulse.sample(154 * ms) == 0, "disconnect clears active and pending data");
        pulse.receive(1, 200 * ms);
        check(pulse.sample(451 * ms) == 0, "ancient queued shots are discarded after a stall");
        for (int i = 0; i < 20; i++) {
            long now = (500 + i * 100) * ms;
            pulse.receive(1, now); pulse.receive(0, now + 50 * ms);
            check(pulse.sample(now + 60 * ms) == 1, "batched full-auto shot remains visible");
            check(pulse.sample(now + 110 * ms) == 0, "full-auto has bounded pulse duration");
        }
        pulse.clear();
        pulse.inject(1, 3000 * ms);
        check(pulse.status(3000 * ms).contains("受信=0 発射=0"), "local test cannot masquerade as received packets");
        check(pulse.sample(3001 * ms) == 1, "local injection still renders");
        System.out.println("MuzzlePulseTest passed: batched packets, low FPS, expiration, modes, repeated fire and disconnect");
    }
}

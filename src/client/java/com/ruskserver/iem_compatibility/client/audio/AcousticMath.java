package com.ruskserver.iem_compatibility.client.audio;

import java.util.function.BiPredicate;

/** 16ブロック格子のDDA。角をかすめる場合も隣接チャンクを確認する。 */
public final class AcousticMath {
    private AcousticMath() {}
    public static boolean loaded(double ax, double az, double bx, double bz, BiPredicate<Integer,Integer> loaded) {
        int x = (int)Math.floor(ax / 16), z = (int)Math.floor(az / 16);
        int endX = (int)Math.floor(bx / 16), endZ = (int)Math.floor(bz / 16);
        double dx = bx - ax, dz = bz - az;
        int sx = Double.compare(dx, 0), sz = Double.compare(dz, 0);
        double tx = sx == 0 ? Double.POSITIVE_INFINITY : ((sx > 0 ? (x+1)*16. : x*16.) - ax) / dx;
        double tz = sz == 0 ? Double.POSITIVE_INFINITY : ((sz > 0 ? (z+1)*16. : z*16.) - az) / dz;
        double stepX = sx == 0 ? Double.POSITIVE_INFINITY : 16 / Math.abs(dx);
        double stepZ = sz == 0 ? Double.POSITIVE_INFINITY : 16 / Math.abs(dz);
        for (int i = 0; i < 32; i++) {
            if (!loaded.test(x, z)) return false;
            if (x == endX && z == endZ) return true;
            if (Math.abs(tx - tz) < 1e-10) {
                if (!loaded.test(x+sx,z) || !loaded.test(x,z+sz)) return false;
                x += sx; z += sz; tx += stepX; tz += stepZ;
            } else if (tx < tz) { x += sx; tx += stepX; }
            else { z += sz; tz += stepZ; }
        }
        return false;
    }
}

package net.phoenixvine.solaris.client.render;

public final class TextureAddressing {

    private TextureAddressing() {}

    public static int properMod(int v, int m) {
        return ((v % m) + m) % m;
    }

    public static float properMod(float v, int m) {
        return ((v % m) + m) % m;
    }
}

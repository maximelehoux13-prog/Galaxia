package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

public final class RenderTickState {

    private static float lastPartialTicks = 0.0F;

    private RenderTickState() {}

    public static float getLastPartialTicks() {
        return lastPartialTicks;
    }

    public static void setLastPartialTicks(float partialTicks) {
        lastPartialTicks = partialTicks;
    }
}

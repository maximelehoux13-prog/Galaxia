package com.gtnewhorizons.galaxia.core;

import com.gtnewhorizons.galaxia.core.oxygen.api.OxygenTankWailaProvider;
import com.gtnewhorizons.galaxia.core.oxygen.tile.TileEntityOxygenStorage;
import cpw.mods.fml.common.event.FMLInterModComms;
import mcp.mobius.waila.Waila;
import mcp.mobius.waila.api.IWailaRegistrar;

public class WailaRegistar {
    public static void callbackRegister(IWailaRegistrar registrar) {
        OxygenTankWailaProvider provider = new OxygenTankWailaProvider();
        registrar.registerNBTProvider(provider, TileEntityOxygenStorage.class);
        registrar.registerBodyProvider(provider, TileEntityOxygenStorage.class);
    }
    public static void init() {
        FMLInterModComms.sendMessage("Waila", "register", WailaRegistar.class.getName() + ".callbackRegister");
    }
}

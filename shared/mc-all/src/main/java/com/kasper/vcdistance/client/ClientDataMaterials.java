package com.kasper.vcdistance.client;

import com.kasper.vcdistance.BlockDataRules;
import com.kasper.vcdistance.server.DataMaterials;
import net.minecraft.client.Minecraft;

/** Block materials from resource packs and mod jars, read on joining a world. */
final class ClientDataMaterials {

    private ClientDataMaterials() {
    }

    static void load() {
        Minecraft mc = Minecraft.getInstance();
        BlockDataRules.setAssets(DataMaterials.read(mc.getResourceManager(), "resource pack"));
        if (mc.getSingleplayerServer() == null) {
            // The data packs of a single-player world left behind: a remote server's are not known here
            BlockDataRules.setData(null);
        }
    }
}

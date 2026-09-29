package com.example.visuals;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod("visuals")
public class VisualsMod {
    public VisualsMod() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::clientSetup);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> MinecraftForge.EVENT_BUS.register(new ClientEvents()));
    }

    private void clientSetup(FMLClientSetupEvent e) {
        ClientRegistry.registerKeyBinding(ClientEvents.ZOOM);
    }
}

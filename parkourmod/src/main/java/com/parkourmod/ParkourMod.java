package com.parkourmod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;

public final class ParkourMod implements ClientModInitializer {
    public static final String MOD_ID = "parkourmod";

    @Override
    public void onInitializeClient() {
        ParkourConfig.load();

        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(MOD_ID, "main"));
        ParkourKeys.register(category);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            LocalPlayer player = client.player;
            if (player == null) {
                while (ParkourKeys.toggle.consumeClick()) { /* сбрасываем накопленные нажатия */ }
                while (ParkourKeys.burst.consumeClick()) { /* сбрасываем накопленные нажатия */ }
                ParkourKeys.drainHeldKeys();
                return;
            }
            while (ParkourKeys.toggle.consumeClick()) {
                ParkourController.toggle(player);
            }
            while (ParkourKeys.burst.consumeClick()) {
                ParkourController.requestBurst(player);
            }
            ParkourKeys.drainHeldKeys();
            ParkourController.showHud(player);
        });
    }
}

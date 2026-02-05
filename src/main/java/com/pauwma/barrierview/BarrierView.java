package com.pauwma.barrierview;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.pauwma.barrierview.commands.BarrierColorCommand;
import com.pauwma.barrierview.commands.BarrierModeCommand;
import com.pauwma.barrierview.commands.ShowBarrierCommand;

import javax.annotation.Nonnull;

public class BarrierView extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public BarrierView(@Nonnull JavaPluginInit init) {
        super(init);
        LOGGER.atInfo().log("[BarrierView] Loading version " + this.getManifest().getVersion().toString());
    }

    @Override
    protected void setup() {
        // Register commands
        this.getCommandRegistry().registerCommand(new ShowBarrierCommand());
        this.getCommandRegistry().registerCommand(new BarrierModeCommand());
        this.getCommandRegistry().registerCommand(new BarrierColorCommand());

        // Start the barrier indicator manager
        BarrierViewManager.start();

        // Cleanup on player disconnect
        this.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, event -> {
            BarrierViewManager.removePlayer(event.getPlayerRef().getUuid());
        });

        LOGGER.atInfo().log("[BarrierView] Ready! Use /showbarrier to toggle, /barriermode for display mode");
    }

    @Override
    protected void shutdown() {
        BarrierViewManager.stop();
        LOGGER.atInfo().log("[BarrierView] Shutdown complete");
    }
}

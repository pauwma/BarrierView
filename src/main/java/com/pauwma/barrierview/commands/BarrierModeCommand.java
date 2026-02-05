package com.pauwma.barrierview.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.pauwma.barrierview.BarrierViewManager;

import javax.annotation.Nonnull;
import java.awt.Color;
import java.util.UUID;

public class BarrierModeCommand extends AbstractPlayerCommand {

    public BarrierModeCommand() {
        super("barriermode", "Toggle barrier display mode (individual/grouped)", false);
    }

    @Override
    protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
    ) {
        UUID playerUuid = playerRef.getUuid();
        BarrierViewManager.DisplayMode newMode = BarrierViewManager.cycleDisplayMode(playerUuid);

        String modeName = newMode == BarrierViewManager.DisplayMode.INDIVIDUAL ? "Individual" : "Grouped";
        context.sendMessage(Message.join(
                Message.raw("[BarrierView] ").color(Color.RED).bold(true),
                Message.raw("Display mode: ").color(Color.WHITE),
                Message.raw(modeName).color(Color.YELLOW)
        ));
    }
}

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

public class ShowBarrierCommand extends AbstractPlayerCommand {

    public ShowBarrierCommand() {
        super("showbarrier", "Toggle barrier block wireframe visibility", false);
        this.addAliases("barrierview");
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
        boolean enabled = BarrierViewManager.toggle(playerUuid);

        if (enabled) {
            BarrierViewManager.registerWorld(world);
            context.sendMessage(Message.join(
                    Message.raw("[BarrierView] ").color(Color.RED).bold(true),
                    Message.raw("Barrier outlines ").color(Color.WHITE),
                    Message.raw("ENABLED").color(Color.GREEN)
            ));
        } else {
            BarrierViewManager.clearDebugShapes(playerRef);
            context.sendMessage(Message.join(
                    Message.raw("[BarrierView] ").color(Color.RED).bold(true),
                    Message.raw("Barrier outlines ").color(Color.WHITE),
                    Message.raw("DISABLED").color(Color.YELLOW)
            ));
        }
    }
}

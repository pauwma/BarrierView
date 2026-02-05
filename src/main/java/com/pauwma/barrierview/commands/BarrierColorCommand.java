package com.pauwma.barrierview.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgumentType;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.pauwma.barrierview.BarrierViewManager;

import javax.annotation.Nonnull;
import java.awt.Color;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

public class BarrierColorCommand extends AbstractPlayerCommand {

    private final RequiredArg<String> colorArg = this.withRequiredArg("color", "Color preset or hex value (e.g., red, #FF0000)", (ArgumentType) ArgTypes.STRING);

    public BarrierColorCommand() {
        super("barriercolor", "Change barrier wireframe color", false);
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

        if (!context.provided(this.colorArg)) {
            // Show current color and available presets
            Vector3f currentColor = BarrierViewManager.getColor(playerUuid);
            String hexColor = BarrierViewManager.colorToHex(currentColor);

            String presets = Arrays.stream(BarrierViewManager.ColorPreset.values())
                    .map(p -> p.name().toLowerCase())
                    .collect(Collectors.joining(", "));

            context.sendMessage(Message.join(
                    Message.raw("[BarrierView] ").color(Color.RED).bold(true),
                    Message.raw("Current color: ").color(Color.WHITE),
                    Message.raw(hexColor).color(new Color(
                            Math.round(currentColor.x * 255),
                            Math.round(currentColor.y * 255),
                            Math.round(currentColor.z * 255)
                    ))
            ));
            context.sendMessage(Message.join(
                    Message.raw("[BarrierView] ").color(Color.RED).bold(true),
                    Message.raw("Usage: /barriercolor <preset|#hex>").color(Color.GRAY)
            ));
            context.sendMessage(Message.join(
                    Message.raw("[BarrierView] ").color(Color.RED).bold(true),
                    Message.raw("Presets: ").color(Color.WHITE),
                    Message.raw(presets).color(Color.YELLOW)
            ));
            return;
        }

        String colorInput = ((String) context.get(this.colorArg)).toUpperCase();

        // Try to parse as preset
        for (BarrierViewManager.ColorPreset preset : BarrierViewManager.ColorPreset.values()) {
            if (preset.name().equals(colorInput)) {
                BarrierViewManager.setColor(playerUuid, preset);
                context.sendMessage(Message.join(
                        Message.raw("[BarrierView] ").color(Color.RED).bold(true),
                        Message.raw("Color set to: ").color(Color.WHITE),
                        Message.raw(preset.name().toLowerCase()).color(new Color(
                                Math.round(preset.r * 255),
                                Math.round(preset.g * 255),
                                Math.round(preset.b * 255)
                        ))
                ));
                return;
            }
        }

        // Try to parse as hex
        Vector3f hexColor = BarrierViewManager.parseHexColor(colorInput);
        if (hexColor != null) {
            BarrierViewManager.setColor(playerUuid, hexColor);
            String hexStr = BarrierViewManager.colorToHex(hexColor);
            context.sendMessage(Message.join(
                    Message.raw("[BarrierView] ").color(Color.RED).bold(true),
                    Message.raw("Color set to: ").color(Color.WHITE),
                    Message.raw(hexStr).color(new Color(
                            Math.round(hexColor.x * 255),
                            Math.round(hexColor.y * 255),
                            Math.round(hexColor.z * 255)
                    ))
            ));
            return;
        }

        // Invalid input
        context.sendMessage(Message.join(
                Message.raw("[BarrierView] ").color(Color.RED).bold(true),
                Message.raw("Invalid color! Use a preset name or hex value (e.g., #FF0000)").color(Color.YELLOW)
        ));
    }
}

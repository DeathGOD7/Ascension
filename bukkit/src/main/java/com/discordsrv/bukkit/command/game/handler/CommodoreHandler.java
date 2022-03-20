/*
 * This file is part of DiscordSRV, licensed under the GPLv3 License
 * Copyright (c) 2016-2022 Austin "Scarsz" Shapiro, Henri "Vankka" Schubin and DiscordSRV contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.discordsrv.bukkit.command.game.handler;

import com.discordsrv.bukkit.BukkitDiscordSRV;
import com.discordsrv.common.command.game.abstraction.GameCommand;
import com.discordsrv.common.command.game.handler.util.BrigadierUtil;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import me.lucko.commodore.Commodore;
import me.lucko.commodore.CommodoreProvider;
import org.bukkit.command.PluginCommand;

/**
 * No avoiding basic handler on bukkit. Unfortunately it isn't possible to use brigadier for executing.
 */
public class CommodoreHandler extends AbstractBukkitCommandExecutor {

    private final Commodore commodore;

    public CommodoreHandler(BukkitDiscordSRV discordSRV) {
        super(discordSRV);
        this.commodore = CommodoreProvider.getCommodore(discordSRV.plugin());
    }

    @Override
    public void registerCommand(GameCommand command) {
        logger.debug("Registering command " + command.getLabel() + " with commodore");

        LiteralCommandNode<?> argumentBuilder = BrigadierUtil.convertToBrigadier(
                command,
                wrapper -> sender(commodore.getBukkitSender(wrapper))
        );
        CommandNode<?> redirection = argumentBuilder.getRedirect();
        if (redirection != null) {
            // Commodore handles the label being different fine
            argumentBuilder = (LiteralCommandNode<?>) redirection;
        }

        PluginCommand pluginCommand = command(command);
        if (pluginCommand == null) {
            logger.error("Failed to create command " + command.getLabel());
            return;
        }

        handler.registerCommand(command);
        pluginCommand.setExecutor(this);

        String requiredPermission = command.getRequiredPermission();
        LiteralCommandNode<?> finalArgumentBuilder = argumentBuilder;
        discordSRV.scheduler().runOnMainThread(() -> commodore.register(
                pluginCommand,
                finalArgumentBuilder,
                player -> requiredPermission == null || player.hasPermission(requiredPermission)
        ));
    }
}

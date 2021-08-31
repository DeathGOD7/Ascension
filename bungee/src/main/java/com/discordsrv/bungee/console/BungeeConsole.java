/*
 * This file is part of DiscordSRV, licensed under the GPLv3 License
 * Copyright (c) 2016-2021 Austin "Scarsz" Shapiro, Henri "Vankka" Schubin and DiscordSRV contributors
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

package com.discordsrv.bungee.console;

import com.discordsrv.bungee.BungeeDiscordSRV;
import com.discordsrv.common.console.Console;
import com.discordsrv.common.logging.logger.backend.LoggingBackend;
import com.discordsrv.common.logging.logger.impl.JavaLoggerImpl;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

public class BungeeConsole implements Console {

    private final BungeeDiscordSRV discordSRV;
    private final LoggingBackend loggingBackend;

    public BungeeConsole(BungeeDiscordSRV discordSRV) {
        this.discordSRV = discordSRV;
        this.loggingBackend = JavaLoggerImpl.getRoot();
    }

    @Override
    public void sendMessage(Identity identity, @NotNull Component message) {
        discordSRV.audiences().console().sendMessage(identity, message);
    }

    @Override
    public void runCommand(String command) {
        discordSRV.proxy().getPluginManager().dispatchCommand(
                discordSRV.proxy().getConsole(), command);
    }

    @Override
    public LoggingBackend loggingBackend() {
        return loggingBackend;
    }
}

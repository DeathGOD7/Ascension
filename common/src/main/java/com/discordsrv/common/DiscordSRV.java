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

package com.discordsrv.common;

import com.discordsrv.api.DiscordSRVApi;
import com.discordsrv.api.module.type.Module;
import com.discordsrv.common.channel.ChannelConfigHelper;
import com.discordsrv.common.component.ComponentFactory;
import com.discordsrv.common.config.connection.ConnectionConfig;
import com.discordsrv.common.config.main.MainConfig;
import com.discordsrv.common.config.manager.ConnectionConfigManager;
import com.discordsrv.common.config.manager.MainConfigManager;
import com.discordsrv.common.console.Console;
import com.discordsrv.common.debug.data.OnlineMode;
import com.discordsrv.common.discord.api.DiscordAPIImpl;
import com.discordsrv.common.discord.connection.DiscordConnectionManager;
import com.discordsrv.common.linking.LinkProvider;
import com.discordsrv.common.logging.Logger;
import com.discordsrv.common.logging.impl.DiscordSRVLogger;
import com.discordsrv.common.module.type.AbstractModule;
import com.discordsrv.common.placeholder.PlaceholderServiceImpl;
import com.discordsrv.common.player.provider.AbstractPlayerProvider;
import com.discordsrv.common.plugin.PluginManager;
import com.discordsrv.common.scheduler.Scheduler;
import com.discordsrv.common.storage.Storage;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.vankka.dependencydownload.classpath.ClasspathAppender;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public interface DiscordSRV extends DiscordSRVApi {

    // Platform
    Logger platformLogger();
    Path dataDirectory();
    Scheduler scheduler();
    Console console();
    String version();
    PluginManager pluginManager();
    OnlineMode onlineMode();
    ClasspathAppender classpathAppender();

    // DiscordSRVApi
    @Override
    @NotNull
    ComponentFactory componentFactory();

    @Override
    @NotNull
    PlaceholderServiceImpl placeholderService();

    @Override
    @NotNull
    AbstractPlayerProvider<?, ?> playerProvider();

    @Override
    @NotNull
    DiscordAPIImpl discordAPI();

    // Logger
    DiscordSRVLogger logger();

    // Storage
    Storage storage();

    // Link Provider
    LinkProvider linkProvider();

    // Config
    ConnectionConfigManager<? extends ConnectionConfig> connectionConfigManager();
    ConnectionConfig connectionConfig();
    MainConfigManager<? extends MainConfig> configManager();
    MainConfig config();
    // Config helper
    ChannelConfigHelper channelConfig();

    // Internal
    DiscordConnectionManager discordConnectionManager();

    // Modules
    @Nullable
    <T extends Module> T getModule(Class<T> moduleType);
    void registerModule(AbstractModule<?> module);
    void unregisterModule(AbstractModule<?> module);

    Locale locale();

    // Status
    void setStatus(Status status);
    default void waitForStatus(Status status) throws InterruptedException {
        waitForStatus(status, -1, TimeUnit.MILLISECONDS);
    }
    void waitForStatus(Status status, long time, TimeUnit unit) throws InterruptedException;

    @SuppressWarnings("unchecked")
    @ApiStatus.NonExtendable
    default <K, V> Caffeine<K, V> caffeineBuilder() {
        return (Caffeine<K, V>) Caffeine.newBuilder()
                .executor(scheduler().forkJoinPool());
    }

    // Lifecycle
    CompletableFuture<Void> invokeEnable();
    CompletableFuture<Void> invokeDisable();
    CompletableFuture<Void> invokeReload();

}

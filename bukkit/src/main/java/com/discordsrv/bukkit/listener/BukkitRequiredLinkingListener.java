/*
 * This file is part of DiscordSRV, licensed under the GPLv3 License
 * Copyright (c) 2016-2023 Austin "Scarsz" Shapiro, Henri "Vankka" Schubin and DiscordSRV contributors
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

package com.discordsrv.bukkit.listener;

import com.discordsrv.bukkit.BukkitDiscordSRV;
import com.discordsrv.bukkit.config.main.BukkitRequiredLinkingConfig;
import com.discordsrv.bukkit.requiredlinking.BukkitRequiredLinkingModule;
import com.discordsrv.common.DiscordSRV;
import com.discordsrv.common.player.IPlayer;
import net.kyori.adventure.platform.bukkit.BukkitComponentSerializer;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class BukkitRequiredLinkingListener implements Listener {

    private final BukkitDiscordSRV discordSRV;

    public BukkitRequiredLinkingListener(BukkitDiscordSRV discordSRV) {
        this.discordSRV = discordSRV;

        register(PlayerLoginEvent.class, this::handle);
        register(AsyncPlayerPreLoginEvent.class, this::handle);
        discordSRV.server().getPluginManager().registerEvents(this, discordSRV.plugin());
    }

    @SuppressWarnings("unchecked")
    private <T extends Event> void register(Class<T> eventType, BiConsumer<T, EventPriority> eventConsumer) {
        for (EventPriority priority : EventPriority.values()) {
            if (priority == EventPriority.MONITOR) {
                continue;
            }

            discordSRV.server().getPluginManager().registerEvent(
                    eventType,
                    this,
                    priority,
                    (listener, event) -> eventConsumer.accept((T) event, priority),
                    discordSRV.plugin(),
                    true
            );
        }
    }

    public void disable() {
        HandlerList.unregisterAll(this);
    }

    private BukkitRequiredLinkingModule getModule() {
        int tries = 0;

        BukkitRequiredLinkingModule module;
        do {
            module = discordSRV.getModule(BukkitRequiredLinkingModule.class);

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            tries++;
        } while (module == null || tries >= 50);

        return module;
    }

    private CompletableFuture<Component> getBlockReason(UUID playerUUID) {
        BukkitRequiredLinkingModule module = getModule();
        if (module == null) {
            return CompletableFuture.completedFuture(Component.text("Discord unavailable, please try again later"));
        }

        return module.getBlockReason(playerUUID);
    }

    //
    // Kick
    //

    private void handle(AsyncPlayerPreLoginEvent event, EventPriority priority) {
        handle(
                "AsyncPlayerPreLoginEvent",
                priority,
                event.getUniqueId(),
                () -> event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED ? event.getLoginResult().name() : null,
                text -> event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, text)
        );
    }

    private void handle(PlayerLoginEvent event, EventPriority priority) {
        handle(
                "PlayerLoginEvent",
                priority,
                event.getPlayer().getUniqueId(),
                () -> event.getResult() != PlayerLoginEvent.Result.ALLOWED ? event.getResult().name() : null,
                text -> event.disallow(PlayerLoginEvent.Result.KICK_OTHER, text)
        );
    }

    private void handle(
            String eventType,
            EventPriority priority,
            UUID playerUUID,
            Supplier<String> alreadyBlocked,
            Consumer<String> disallow
    ) {
        BukkitRequiredLinkingConfig config = discordSRV.config().requiredLinking;
        if (!config.enabled || !config.action.equalsIgnoreCase("KICK")
                || !eventType.equals(config.kick.event) || !priority.name().equals(config.kick.priority)) {
            return;
        }

        String blockType = alreadyBlocked.get();
        if (blockType != null) {
            discordSRV.logger().debug(playerUUID + " is already blocked for " + eventType + "/" + priority + " (" + blockType + ")");
            return;
        }

        Component kickReason = getBlockReason(playerUUID).join();
        if (kickReason != null) {
            disallow.accept(BukkitComponentSerializer.legacy().serialize(kickReason));
        }
    }

    //
    // Freeze
    //

    private final Map<UUID, Component> frozen = new ConcurrentHashMap<>();

    private boolean isFrozen(Player player) {
        return frozen.containsKey(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }

        if (discordSRV.isShutdown()) {
            return;
        } else if (!discordSRV.isReady()) {
            try {
                discordSRV.waitForStatus(DiscordSRV.Status.CONNECTED);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        BukkitRequiredLinkingConfig config = discordSRV.config().requiredLinking;
        if (!config.enabled || !config.action.equalsIgnoreCase("FREEZE")) {
            return;
        }

        Component blockReason = getBlockReason(event.getUniqueId()).join();
        if (blockReason != null) {
            frozen.put(event.getUniqueId(), blockReason);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLogin(PlayerLoginEvent event) {
        if (event.getResult() != PlayerLoginEvent.Result.ALLOWED) {
            frozen.remove(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        Component blockReason = frozen.get(uuid);
        if (blockReason == null) {
            return;
        }

        IPlayer player = discordSRV.playerProvider().player(uuid);
        if (player == null) {
            throw new IllegalStateException("Player not available: " + uuid);
        }

        player.sendMessage(blockReason);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Component freezeReason = frozen.get(event.getPlayer().getUniqueId());
        if (freezeReason == null) {
            return;
        }

        Location from = event.getFrom(), to = event.getTo();
        if (from.getWorld().getName().equals(to.getWorld().getName())
                && from.getBlockX() == to.getBlockX()
                && from.getBlockZ() == to.getBlockZ()
                && from.getBlockY() >= to.getBlockY()) {
            return;
        }

        event.setTo(
                new Location(
                        from.getWorld(),
                        from.getBlockX() + 0.5,
                        from.getBlockY(),
                        from.getBlockZ() + 0.5,
                        from.getYaw(),
                        from.getPitch()
                )
        );

        IPlayer player = discordSRV.playerProvider().player(event.getPlayer());
        player.sendMessage(freezeReason);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Component freezeReason = frozen.get(event.getPlayer().getUniqueId());
        if (freezeReason == null) {
            event.getRecipients().removeIf(this::isFrozen);
            return;
        }

        event.setCancelled(true);

        IPlayer player = discordSRV.playerProvider().player(event.getPlayer());
        player.sendMessage(freezeReason);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!isFrozen(event.getPlayer())) {
            return;
        }

        event.setCancelled(true);

        String message = event.getMessage();
        if (message.startsWith("/")) message = message.substring(1);
        if (message.equals("linked")) {
            IPlayer player = discordSRV.playerProvider().player(event.getPlayer());
            player.sendMessage(Component.text("Checking..."));

            UUID uuid = player.uniqueId();
            getBlockReason(uuid).whenComplete((reason, t) -> {
                if (t != null) {
                    return;
                }

                if (reason == null) {
                    frozen.remove(uuid);
                } else {
                    frozen.put(uuid, reason);
                    player.sendMessage(reason);
                }
            });
        }
    }
}

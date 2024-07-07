/*
 * This file is part of DiscordSRV, licensed under the GPLv3 License
 * Copyright (c) 2016-2024 Austin "Scarsz" Shapiro, Henri "Vankka" Schubin and DiscordSRV contributors
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

package com.discordsrv.bukkit.requiredlinking;

import com.discordsrv.bukkit.BukkitDiscordSRV;
import com.discordsrv.bukkit.config.main.BukkitRequiredLinkingConfig;
import com.discordsrv.common.DiscordSRV;
import com.discordsrv.common.config.main.linking.ServerRequiredLinkingConfig;
import com.discordsrv.common.linking.LinkStore;
import com.discordsrv.common.linking.requirelinking.ServerRequireLinkingModule;
import com.discordsrv.common.player.IPlayer;
import com.github.benmanes.caffeine.cache.Cache;
import net.kyori.adventure.platform.bukkit.BukkitComponentSerializer;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class BukkitRequiredLinkingModule extends ServerRequireLinkingModule<BukkitDiscordSRV> implements Listener {

    private final Cache<UUID, Boolean> linkCheckRateLimit;

    public BukkitRequiredLinkingModule(BukkitDiscordSRV discordSRV) {
        super(discordSRV);
        this.linkCheckRateLimit = discordSRV.caffeineBuilder()
                .expireAfterWrite(LinkStore.LINKING_CODE_RATE_LIMIT)
                .build();
    }

    @Override
    public BukkitRequiredLinkingConfig config() {
        return discordSRV.config().requiredLinking;
    }

    @Override
    public void enable() {
        super.enable();

        register(AsyncPlayerPreLoginEvent.class, this::handle);
        register(PlayerLoginEvent.class, this::handle);
        register(PlayerJoinEvent.class, this::handle);
        discordSRV.server().getPluginManager().registerEvents(this, discordSRV.plugin());
    }

    public void disable() {
        HandlerList.unregisterAll(this);
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

    @Override
    public void recheck(IPlayer player) {
        getBlockReason(player.uniqueId(), player.username(), false).whenComplete((component, throwable) -> {
            if (component != null) {
                switch (action()) {
                    case KICK:
                        player.kick(component);
                        break;
                    case FREEZE:
                        freeze(player, component);
                        break;
                }
            } else if (action() == ServerRequiredLinkingConfig.Action.FREEZE) {
                frozen.remove(player.uniqueId());
            }
        });
    }

    public ServerRequiredLinkingConfig.Action action() {
        return config().action;
    }

    //
    // Kick
    //

    private void handle(AsyncPlayerPreLoginEvent event, EventPriority priority) {
        handle(
                "AsyncPlayerPreLoginEvent",
                priority,
                event.getUniqueId(),
                event.getName(),
                () -> event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED ? event.getLoginResult().name() : null,
                text -> event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, text)
        );
    }

    private void handle(PlayerLoginEvent event, EventPriority priority) {
        Player player = event.getPlayer();
        handle(
                "PlayerLoginEvent",
                priority,
                player.getUniqueId(),
                player.getName(),
                () -> event.getResult() != PlayerLoginEvent.Result.ALLOWED ? event.getResult().name() : null,
                text -> event.disallow(PlayerLoginEvent.Result.KICK_OTHER, text)
        );
    }

    private void handle(PlayerJoinEvent event, EventPriority priority) {
        Player player = event.getPlayer();
        handle(
                "PlayerJoinEvent",
                priority,
                player.getUniqueId(),
                player.getName(),
                () -> null,
                player::kickPlayer
        );
    }

    private void handle(
            String eventType,
            EventPriority priority,
            UUID playerUUID,
            String playerName,
            Supplier<String> alreadyBlocked,
            Consumer<String> disallow
    ) {
        BukkitRequiredLinkingConfig config = config();
        if (!config.enabled || config.action != ServerRequiredLinkingConfig.Action.KICK
                || !eventType.equals(config.kick.event) || !priority.name().equals(config.kick.priority)) {
            return;
        }

        String blockType = alreadyBlocked.get();
        if (blockType != null) {
            discordSRV.logger().debug(playerName + " is already blocked for " + eventType + "/" + priority + " (" + blockType + ")");
            return;
        }

        Component kickReason = getBlockReason(playerUUID, playerName, true).join();
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

    private void freeze(IPlayer player, Component blockReason) {
        frozen.put(player.uniqueId(), blockReason);
        player.sendMessage(blockReason);
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

        BukkitRequiredLinkingConfig config = config();
        if (!config.enabled || config.action != ServerRequiredLinkingConfig.Action.FREEZE) {
            return;
        }

        Component blockReason = getBlockReason(event.getUniqueId(), event.getName(), false).join();
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
        if (message.equals("discord link") || message.equals("link")) {
            IPlayer player = discordSRV.playerProvider().player(event.getPlayer());

            if (linkCheckRateLimit.getIfPresent(player.uniqueId()) != null) {
                player.sendMessage(discordSRV.messagesConfig(player).pleaseWaitBeforeRunningThatCommandAgain.asComponent());
                return;
            }
            linkCheckRateLimit.put(player.uniqueId(), true);

            player.sendMessage(Component.text("Checking..."));

            UUID uuid = player.uniqueId();
            getBlockReason(uuid, player.username(), false).whenComplete((reason, t) -> {
                if (t != null) {
                    return;
                }

                if (reason == null) {
                    frozen.remove(uuid);
                } else {
                    freeze(player, reason);
                }
            });
        }
    }
}

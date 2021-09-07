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

package com.discordsrv.common.discord.api.channel;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.receive.ReadonlyMessage;
import club.minnced.discord.webhook.send.WebhookMessage;
import com.discordsrv.api.discord.api.entity.channel.DiscordTextChannel;
import com.discordsrv.api.discord.api.entity.guild.DiscordGuild;
import com.discordsrv.api.discord.api.entity.message.ReceivedDiscordMessage;
import com.discordsrv.api.discord.api.entity.message.SendableDiscordMessage;
import com.discordsrv.api.discord.api.exception.NotReadyException;
import com.discordsrv.api.discord.api.exception.UnknownChannelException;
import com.discordsrv.common.DiscordSRV;
import com.discordsrv.common.discord.api.guild.DiscordGuildImpl;
import com.discordsrv.common.discord.api.message.ReceivedDiscordMessageImpl;
import com.discordsrv.common.discord.api.message.util.SendableDiscordMessageUtil;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.requests.restaction.MessageAction;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

public class DiscordTextChannelImpl extends DiscordMessageChannelImpl implements DiscordTextChannel {

    private final DiscordSRV discordSRV;
    private final long id;
    private final String name;
    private final String topic;
    private final DiscordGuild guild;

    public DiscordTextChannelImpl(DiscordSRV discordSRV, TextChannel textChannel) {
        this.discordSRV = discordSRV;
        this.id = textChannel.getIdLong();
        this.name = textChannel.getName();
        this.topic = textChannel.getTopic();
        this.guild = new DiscordGuildImpl(discordSRV, textChannel.getGuild());
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public @NotNull String getName() {
        return name;
    }

    @Override
    public @NotNull String getTopic() {
        return topic;
    }

    @Override
    public @NotNull DiscordGuild getGuild() {
        return guild;
    }

    @Override
    public @NotNull CompletableFuture<ReceivedDiscordMessage> sendMessage(SendableDiscordMessage message) {
        return message(message, WebhookClient::send, MessageChannel::sendMessage);
    }

    @Override
    public CompletableFuture<Void> deleteMessageById(long id) {
        return null; // TODO
    }

    @Override
    public @NotNull CompletableFuture<ReceivedDiscordMessage> editMessageById(long id, SendableDiscordMessage message) {
        return message(
                message,
                (client, msg) -> client.edit(id, msg),
                (textChannel, msg) -> textChannel.editMessageById(id, msg)
        );
    }

    private CompletableFuture<ReceivedDiscordMessage> message(
            SendableDiscordMessage message,
            BiFunction<WebhookClient, WebhookMessage, CompletableFuture<ReadonlyMessage>> webhookFunction,
            BiFunction<TextChannel, Message, MessageAction> jdaFunction) {
        CompletableFuture<ReceivedDiscordMessage> future;
        if (message.isWebhookMessage()) {
            future = discordSRV.discordAPI().queryWebhookClient(getId())
                    .thenCompose(client -> webhookFunction.apply(
                            client, SendableDiscordMessageUtil.toWebhook(message)))
                    .thenApply(msg -> ReceivedDiscordMessageImpl.fromWebhook(discordSRV, msg));
        } else {
            JDA jda = discordSRV.jda().orElse(null);
            if (jda == null) {
                throw new NotReadyException();
            }

            TextChannel textChannel = jda.getTextChannelById(getId());
            if (textChannel == null) {
                future = new CompletableFuture<>();
                future.completeExceptionally(new UnknownChannelException());
                return future;
            }

            future = jdaFunction
                    .apply(textChannel, SendableDiscordMessageUtil.toJDA(message))
                    .submit()
                    .thenApply(msg -> ReceivedDiscordMessageImpl.fromJDA(discordSRV, msg));
        }

        return mapExceptions(future);
    }
}

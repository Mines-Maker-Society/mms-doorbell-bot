package edu.mines.mmsbot.bot.listeners;

import edu.mines.mmsbot.MMSContext;
import net.dv8tion.jda.api.events.message.MessageBulkDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveAllEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEmojiEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class CatListener extends ListenerAdapter implements MMSContext {
    @Override
    public void onMessageReactionAdd(MessageReactionAddEvent event) {
        log().info("New reaction on message {}",event.getMessageId());
        stats().getCatStats().updateMessage(event.getMessageId());
    }

    @Override
    public void onMessageReactionRemove(MessageReactionRemoveEvent event) {
        log().info("Reaction Removed From message {}",event.getMessageId());
        stats().getCatStats().updateMessage(event.getMessageId());
    }

    @Override
    public void onMessageReactionRemoveAll(MessageReactionRemoveAllEvent event) {
        log().info("All reactions removed from message {}",event.getMessageId());
        stats().getCatStats().updateMessage(event.getMessageId());
    }

    @Override
    public void onMessageReactionRemoveEmoji(MessageReactionRemoveEmojiEvent event) {
        log().info("Moderator removed emoji from {}",event.getMessageId());
        stats().getCatStats().updateMessage(event.getMessageId());
    }

    @Override
    public void onMessageDelete(MessageDeleteEvent event) {
        log().info("Message Deleted");
        stats().getCatStats().deleteMessage(event.getMessageIdLong());
    }

    @Override
    public void onMessageBulkDelete(MessageBulkDeleteEvent event) {
        log().info("Messages bulk deleted...");
        event.getMessageIds().forEach(message -> {
            long id = Long.parseLong(message);
            stats().getCatStats().deleteMessage(id);
        });
    }
}

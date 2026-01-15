package edu.mines.mmsbot.bot.listeners;

import edu.mines.mmsbot.MMSContext;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.ArrayList;
import java.util.List;

public class LockChannelListener extends ListenerAdapter implements MMSContext {

    private final List<Long> alreadyWarned = new ArrayList<>();

    @Override // If more than 10 messages are in front of a button, it will not be cleaned up properly. It's best to just not allow messaging in the channel for anyone.
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getChannel().getIdLong() != runtime().getServer().getLockChannel().getIdLong()) return;

        User author = event.getAuthor();
        long authorId = author.getIdLong();
        if (authorId == runtime().getJda().getSelfUser().getIdLong()) return;

        event.getMessage().delete().queue();

        if (author.isBot() || alreadyWarned.contains(authorId)) return;

        alreadyWarned.add(authorId);
        event.getMessage().getAuthor().openPrivateChannel()
                .flatMap(dm -> dm.sendMessage("Please try not to message in the notification channel. It may cause problems with the claiming feature..."))
                .queue();
    }
}

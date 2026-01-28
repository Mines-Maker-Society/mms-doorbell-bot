package edu.mines.mmsbot.bot.listeners;

import edu.mines.mmsbot.MMSContext;
import edu.mines.mmsbot.bot.commands.StatsCommand;
import edu.mines.mmsbot.data.util.StatsReport.StatType;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;

public class StatsListener extends ListenerAdapter implements MMSContext {

    @Override
    public void onStringSelectInteraction(@NotNull StringSelectInteractionEvent event) {
        if (!event.getComponentId().equals("stat-selector")) return;

        event.deferEdit().queue();

        long startTime = Instant.from(event.getTimeCreated()).toEpochMilli();
        StatType selected = StatType.valueOf(event.getSelectedOptions().getFirst().getValue());

        MessageEmbed embed = runtime()
                .getCommand(StatsCommand.class)
                .getCachedEmbed(selected, startTime)
                .build();

        event.getHook().editOriginalEmbeds(embed).queue();
    }
}
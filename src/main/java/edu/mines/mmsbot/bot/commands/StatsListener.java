package edu.mines.mmsbot.bot.commands;

import edu.mines.mmsbot.MMSContext;
import edu.mines.mmsbot.util.TimeUtils;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

public class StatsListener extends ListenerAdapter implements MMSContext {

    @Override
    public void onStringSelectInteraction(@NotNull StringSelectInteractionEvent event) {
        if (!event.getComponentId().equals("stat-selector"))
            return;

        event.deferEdit().queue();

        long startTime = System.currentTimeMillis();
        StatsCommand.StatType selected =
                StatsCommand.StatType.valueOf(event.getSelectedOptions().getFirst().getValue());

        MessageEmbed embed = runtime()
                .getCommand(StatsCommand.class)
                .generateStatistics(selected)
                .setFooter("Retrieved in " + TimeUtils.formatDuration(System.currentTimeMillis() - startTime,true))
                .build();

        event.getHook().editOriginalEmbeds(embed).queue();
    }

}

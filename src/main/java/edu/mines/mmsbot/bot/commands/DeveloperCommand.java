package edu.mines.mmsbot.bot.commands;

import edu.mines.mmsbot.bot.framework.AbstractCommand;
import edu.mines.mmsbot.util.EmbedUtils;
import edu.mines.mmsbot.util.TimeUtils;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

public class DeveloperCommand extends AbstractCommand {

    public DeveloperCommand() {
        super(Commands.slash("dev","Random developer stuff.")
                .setContexts(InteractionContextType.GUILD)
                .addOption(OptionType.BOOLEAN,"cats-populate","Populates the cat database with all prior bot messages.",false)
                .addOption(OptionType.BOOLEAN, "cats-update", "Starts a cat table update."));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (serverIncorrect(event)) return;
        boolean isDeveloper = config().developerIDs.contains(event.getUser().getIdLong());
        boolean isAdmin = event.getMember() != null
                && event.getMember().hasPermission(Permission.ADMINISTRATOR);

        if (!(isDeveloper || isAdmin)) {
            event.replyEmbeds(EmbedUtils.defaultEmbed()
                    .setTitle("No Permission")
                    .setDescription("Only the developer and server admins can use this command. This isn't an arbitrary restriction, these commands put a lot of strain on both the bot and the discord API, and running them frequently can lead the bot being rate limited. That is generally not very ideal.")
                    .build()).queue();
            return;
        }

        event.replyEmbeds(EmbedUtils.defaultEmbed()
                        .setDescription("Processing Request...")
                .build()).queue();
        long stamp = System.currentTimeMillis();
        
        OptionMapping populate = event.getOption("cats-populate");
        if (populate != null && populate.getAsBoolean()) {
            stats().getCatStats().populateTable().thenAccept(count -> {
                event.getHook().editOriginalEmbeds(
                        EmbedUtils.defaultEmbed()
                                .setTitle("Database Table Populated")
                                .setDescription("A total of " + count + " messages were found. Took " + TimeUtils.formatDuration(System.currentTimeMillis() - stamp,true) + ".")
                                .build()
                ).queue();
            });
        }

        OptionMapping update = event.getOption("cats-update");
        if (update != null && update.getAsBoolean()) {
            stats().getCatStats().updateTable().thenAccept(count -> {
                event.getHook().editOriginalEmbeds(
                        EmbedUtils.defaultEmbed()
                                .setTitle("Database Table Updated")
                                .setDescription("A total of " + count + " messages were refreshed. Took " + TimeUtils.formatDuration(System.currentTimeMillis() - stamp,true) + ".")
                                .build()
                ).queue();
            });
        }
    }
}

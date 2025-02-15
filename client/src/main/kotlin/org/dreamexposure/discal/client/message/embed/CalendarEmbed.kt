package org.dreamexposure.discal.client.message.embed

import discord4j.core.`object`.entity.Guild
import discord4j.core.spec.EmbedCreateSpec
import org.dreamexposure.discal.core.entities.Calendar
import org.dreamexposure.discal.core.enums.time.DiscordTimestampFormat
import org.dreamexposure.discal.core.enums.time.TimeFormat
import org.dreamexposure.discal.core.extensions.*
import org.dreamexposure.discal.core.extensions.discord4j.getCalendar
import org.dreamexposure.discal.core.`object`.GuildSettings
import org.dreamexposure.discal.core.`object`.calendar.PreCalendar
import org.dreamexposure.discal.core.utils.GlobalVal.discalColor
import org.dreamexposure.discal.core.utils.getCommonMsg
import reactor.core.publisher.Mono
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object CalendarEmbed : EmbedMaker {
    fun link(guild: Guild, settings: GuildSettings, calNumber: Int, overview: Boolean): Mono<EmbedCreateSpec> {
        return guild.getCalendar(calNumber).flatMap {
            if (overview) overview(guild, settings, it, false)
            else Mono.just(link(guild, settings, it))
        }
    }

    fun link(guild: Guild, settings: GuildSettings, calendar: Calendar): EmbedCreateSpec {
        val builder = defaultBuilder(guild, settings)
        //Handle optional fields
        if (calendar.name.isNotBlank())
            builder.title(calendar.name.toMarkdown().embedTitleSafe())
        if (calendar.description.isNotBlank())
            builder.description(calendar.description.toMarkdown().embedDescriptionSafe())

        return builder.addField(getMessage("calendar", "link.field.timezone", settings), calendar.zoneName, false)
            .addField(getMessage("calendar", "link.field.host", settings), calendar.calendarData.host.name, true)
            .addField(getMessage("calendar", "link.field.number", settings), "${calendar.calendarNumber}", true)
            .addField(getMessage("calendar", "link.field.id", settings), calendar.calendarId, false)
            .url(calendar.link)
            .footer(getMessage("calendar", "link.footer.default", settings), null)
            .color(discalColor)
            .build()
    }

    fun overview(guild: Guild, settings: GuildSettings, calendar: Calendar, showUpdate: Boolean): Mono<EmbedCreateSpec> {
        return calendar.getUpcomingEvents(15).collectList().map { it.groupByDateMulti() }.map { events ->
            val builder = defaultBuilder(guild, settings)

            //Handle optional fields
            if (calendar.name.isNotBlank())
                builder.title(calendar.name.toMarkdown().embedTitleSafe())
            if (calendar.description.isNotBlank())
                builder.description(calendar.description.toMarkdown().embedDescriptionSafe())

            // Show events
            events.forEach { date ->

                val title = date.key.toInstant().humanReadableDate(calendar.timezone, settings.timeFormat, longDay = true)

                // sort events
                val sortedEvents = date.value.sortedBy { it.start }

                val content = StringBuilder()

                sortedEvents.forEach {
                    // Start event
                    content.append("```\n")


                    // determine time length
                    val timeDisplayLen = ("${it.start.humanReadableTime(it.timezone, settings.timeFormat)} -" +
                        " ${it.end.humanReadableTime(it.timezone,settings.timeFormat)} ").length

                    // Displaying time
                   if (it.isAllDay()) {
                       content.append(getCommonMsg("generic.time.allDay", settings).padCenter(timeDisplayLen))
                           .append("| ")
                   } else {
                       // Add start text
                       var str = if (it.start.isBefore(date.key.toInstant())) {
                           "${getCommonMsg("generic.time.continued", settings)} - "
                       } else {
                           "${it.start.humanReadableTime(it.timezone, settings.timeFormat)} - "
                       }
                       // Add end text
                       str += if (it.end.isAfter(date.key.toInstant().plus(1, ChronoUnit.DAYS))) {
                           getCommonMsg("generic.time.continued", settings)
                       } else {
                           "${it.end.humanReadableTime(it.timezone, settings.timeFormat)} "
                       }
                       content.append(str.padCenter(timeDisplayLen))
                           .append("| ")
                   }
                    // Display name or ID if not set
                    if (it.name.isNotBlank()) content.append(it.name)
                    else content.append(getMessage("calendar", "link.field.id", settings)).append(" ${it.eventId}")
                    content.append("\n")
                    if (it.location.isNotBlank()) content.append("    Location: ")
                        .append(it.location.embedFieldSafe())
                        .append("\n")

                    // Finish event
                    content.append("```\n")
                }

                if (content.isNotBlank())
                    builder.addField(title, content.toString(), false)
            }


            // set footer
            if (showUpdate) {
                val lastUpdate = Instant.now().asDiscordTimestamp(DiscordTimestampFormat.RELATIVE_TIME)
                builder.footer(getMessage("calendar", "link.footer.update", settings, lastUpdate), null)
                    .timestamp(Instant.now())
            } else builder.footer(getMessage("calendar", "link.footer.default", settings), null)

            // finish and return
            builder.addField(getMessage("calendar", "link.field.timezone", settings), calendar.zoneName, true)
                .addField(getMessage("calendar", "link.field.number", settings), "${calendar.calendarNumber}", true)
                .url(calendar.link)
                .color(discalColor)
                .build()
        }
    }


    fun time(guild: Guild, settings: GuildSettings, calNumber: Int): Mono<EmbedCreateSpec> {
        return guild.getCalendar(calNumber).map { cal ->
            val ldt = LocalDateTime.now(cal.timezone)

            val fmt: DateTimeFormatter =
                if (settings.timeFormat == TimeFormat.TWELVE_HOUR)
                    DateTimeFormatter.ofPattern("yyyy/MM/dd hh:mm:ss a")
                else
                    DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")


            val correctTime = fmt.format(ldt)
            val builder = defaultBuilder(guild, settings)

            builder.title(getMessage("time", "embed.title", settings))
            builder.addField(getMessage("time", "embed.field.current", settings), correctTime, false)
            builder.addField(getMessage("time", "embed.field.timezone", settings), cal.zoneName, false)
            builder.footer(getMessage("time", "embed.footer", settings), null)
            builder.url(cal.link)

            builder.color(discalColor)

            builder.build()
        }
    }

    fun pre(guild: Guild, settings: GuildSettings, preCal: PreCalendar): EmbedCreateSpec {
        val builder = defaultBuilder(guild, settings)
            .title(getMessage("calendar", "wizard.title", settings))
            .addField(getMessage(
                "calendar", "wizard.field.name", settings),
                preCal.name.toMarkdown().embedFieldSafe(),
                false
            ).addField(
                getMessage("calendar", "wizard.field.description", settings),
                preCal.description?.ifEmpty { getCommonMsg("embed.unset", settings) }?.toMarkdown()?.embedFieldSafe()
                    ?: getCommonMsg("embed.unset", settings),
                false
            ).addField(getMessage("calendar", "wizard.field.timezone", settings),
                preCal.timezone?.id ?: getCommonMsg("embed.unset", settings),
                true
            ).addField(getMessage("calendar", "wizard.field.host", settings), preCal.host.name, true)
            .footer(getMessage("calendar", "wizard.footer", settings), null)

        if (preCal.editing)
            builder.addField(getMessage("calendar", "wizard.field.id", settings), preCal.calendar!!.calendarId, false)

        return builder.build()
    }
}

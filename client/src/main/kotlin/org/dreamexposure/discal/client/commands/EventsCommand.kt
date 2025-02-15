package org.dreamexposure.discal.client.commands

import discord4j.core.`object`.command.ApplicationCommandInteractionOption
import discord4j.core.`object`.command.ApplicationCommandInteractionOptionValue
import discord4j.core.`object`.entity.Message
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent
import org.dreamexposure.discal.client.message.embed.EventEmbed
import org.dreamexposure.discal.core.`object`.GuildSettings
import org.dreamexposure.discal.core.extensions.discord4j.followup
import org.dreamexposure.discal.core.extensions.discord4j.getCalendar
import org.dreamexposure.discal.core.utils.getCommonMsg
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.function.TupleUtils
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@Component
class EventsCommand : SlashCommand {
    override val name = "events"
    override val ephemeral = false

    override fun handle(event: ChatInputInteractionEvent, settings: GuildSettings): Mono<Message> {
        return when (event.options[0].name) {
            "upcoming" -> upcomingEventsSubcommand(event, settings)
            "ongoing" -> ongoingEventsSubcommand(event, settings)
            "today" -> eventsTodaySubcommand(event, settings)
            "range" -> eventsRangeSubcommand(event, settings)
            else -> Mono.empty() //Never can reach this, makes compiler happy.
        }
    }

    private fun upcomingEventsSubcommand(event: ChatInputInteractionEvent, settings: GuildSettings): Mono<Message> {
        val calendarNumber = event.options[0].getOption("calendar")
            .flatMap(ApplicationCommandInteractionOption::getValue)
            .map(ApplicationCommandInteractionOptionValue::asLong)
            .map(Long::toInt)
            .orElse(1)

        val amount = event.options[0].getOption("amount")
            .flatMap(ApplicationCommandInteractionOption::getValue)
            .map(ApplicationCommandInteractionOptionValue::asLong)
            .map(Long::toInt)
            .orElse(1)


        if (amount < 1 || amount > 15) {
            return event.followup(getMessage("upcoming.failure.outOfRange", settings))
        }

        return event.interaction.guild.flatMap { guild ->
            guild.getCalendar(calendarNumber).flatMap { cal ->
                cal.getUpcomingEvents(amount).collectList().flatMap { events ->
                    if (events.isEmpty()) {
                        event.followup(getMessage("upcoming.success.none", settings))
                    } else if (events.size == 1) {
                        event.followup(
                            getMessage("upcoming.success.one", settings),
                            EventEmbed.getFull(guild, settings, events[0])
                        )
                    } else {
                        event.followup(
                            getMessage("upcoming.success.many", settings, "${events.size}")
                        ).flatMapMany {
                            Flux.fromIterable(events)
                        }.concatMap {
                            event.followup(EventEmbed.getCondensed(guild, settings, it))
                        }.last()
                    }
                }
            }.switchIfEmpty(event.followup(getCommonMsg("error.notFound.calendar", settings)))
        }
    }

    private fun ongoingEventsSubcommand(event: ChatInputInteractionEvent, settings: GuildSettings): Mono<Message> {
        val calendarNumber = event.options[0].getOption("calendar")
            .flatMap(ApplicationCommandInteractionOption::getValue)
            .map(ApplicationCommandInteractionOptionValue::asLong)
            .map(Long::toInt)
            .orElse(1)

        return event.interaction.guild.flatMap { guild ->
            guild.getCalendar(calendarNumber).flatMap { cal ->
                cal.getOngoingEvents().collectList().flatMap { events ->
                    if (events.isEmpty()) {
                        event.followup(getMessage("ongoing.success.none", settings))
                    } else if (events.size == 1) {
                        event.followup(
                            getMessage("ongoing.success.one", settings),
                            EventEmbed.getFull(guild, settings, events[0])
                        )
                    } else {
                        event.followup(
                            getMessage("ongoing.success.many", settings, "${events.size}")
                        ).flatMapMany {
                            Flux.fromIterable(events)
                        }.concatMap {
                            event.followup(EventEmbed.getCondensed(guild, settings, it))
                        }.last()
                    }
                }
            }.switchIfEmpty(event.followup(getCommonMsg("error.notFound.calendar", settings)))
        }
    }

    private fun eventsTodaySubcommand(event: ChatInputInteractionEvent, settings: GuildSettings): Mono<Message> {
        val calendarNumber = event.options[0].getOption("calendar")
            .flatMap(ApplicationCommandInteractionOption::getValue)
            .map(ApplicationCommandInteractionOptionValue::asLong)
            .map(Long::toInt)
            .orElse(1)

        return event.interaction.guild.flatMap { guild ->
            guild.getCalendar(calendarNumber).flatMap { cal ->
                cal.getEventsInNext24HourPeriod(Instant.now()).collectList().flatMap { events ->
                    if (events.isEmpty()) {
                        event.followup(getMessage("today.success.none", settings))
                    } else if (events.size == 1) {
                        event.followup(
                            getMessage("today.success.one", settings),
                            EventEmbed.getFull(guild, settings, events[0])
                        )
                    } else {
                        event.followup(
                            getMessage("today.success.many", settings, "${events.size}")
                        ).flatMapMany {
                            Flux.fromIterable(events)
                        }.concatMap {
                            event.followup(EventEmbed.getCondensed(guild, settings, it))
                        }.last()
                    }
                }
            }.switchIfEmpty(event.followup(getCommonMsg("error.notFound.calendar", settings)))
        }
    }

    private fun eventsRangeSubcommand(event: ChatInputInteractionEvent, settings: GuildSettings): Mono<Message> {
        val gMono = event.interaction.guild.cache()

        val calMono = Mono.justOrEmpty(event.options[0].getOption("calendar").flatMap { it.value })
            .map { it.asLong().toInt() }
            .defaultIfEmpty(1)
            .flatMap { num ->
                gMono.flatMap {
                    it.getCalendar(num)
                }
            }.cache()

        val sMono = Mono.justOrEmpty(event.options[0].getOption("start").flatMap { it.value })
            .map { it.asString() }
            .flatMap { value ->
                calMono.map {
                    val formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")

                    LocalDate.parse(value, formatter).atStartOfDay(it.timezone)
                }
            }.map(ZonedDateTime::toInstant)

        val eMono = Mono.justOrEmpty(event.options[0].getOption("end").flatMap { it.value })
            .map { it.asString() }
            .flatMap { value ->
                calMono.map {
                    val formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")

                    //At end of day
                    LocalDate.parse(value, formatter).plusDays(1).atStartOfDay(it.timezone)
                }
            }.map(ZonedDateTime::toInstant)

        return Mono.zip(gMono, calMono, sMono, eMono).flatMap(
            TupleUtils.function { guild, cal, start, end ->
                cal.getEventsInTimeRange(start, end).collectList().flatMap { events ->
                    if (events.isEmpty()) {
                        event.followup(getMessage("range.success.none", settings))
                    } else if (events.size == 1) {
                        event.followup(
                            getMessage("range.success.one", settings),
                            EventEmbed.getFull(guild, settings, events[0])
                        )
                    } else if (events.size > 15) {
                        event.followup(getMessage("range.success.tooMany", settings, "${events.size}", cal.link))
                    } else {
                        event.followup(
                            getMessage("range.success.many", settings, "${events.size}")
                        ).flatMapMany {
                            Flux.fromIterable(events)
                        }.concatMap {
                            event.followup(EventEmbed.getCondensed(guild, settings, it))
                        }.last()
                    }
                }
            }).switchIfEmpty(event.followup(getCommonMsg("error.notFound.calendar", settings)))
            .onErrorResume(DateTimeParseException::class.java) {
                event.followup(getCommonMsg("error.format.date", settings))
            }
    }
}

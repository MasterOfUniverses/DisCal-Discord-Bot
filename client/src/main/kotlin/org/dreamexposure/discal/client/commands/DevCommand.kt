package org.dreamexposure.discal.client.commands

import discord4j.common.util.Snowflake
import discord4j.core.`object`.command.ApplicationCommandInteractionOptionValue
import discord4j.core.event.domain.interaction.SlashCommandEvent
import org.dreamexposure.discal.client.message.Responder
import org.dreamexposure.discal.core.`object`.GuildSettings
import org.dreamexposure.discal.core.`object`.web.UserAPIAccount
import org.dreamexposure.discal.core.crypto.KeyGenerator.csRandomAlphaNumericString
import org.dreamexposure.discal.core.database.DatabaseManager
import org.dreamexposure.discal.core.logger.LOGGER
import org.dreamexposure.discal.core.utils.GlobalVal
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class DevCommand : SlashCommand {
    override val name = "dev"
    override val ephemeral = true

    override fun handle(event: SlashCommandEvent, settings: GuildSettings): Mono<Void> {
        if (!GlobalVal.devUserIds.contains(event.interaction.user.id)) {
            return Responder.followupEphemeral(event, getMessage("error.notDeveloper", settings)).then()
        }

        return when (event.options[0].name) {
            "patron" -> patronSubcommand(event, settings)
            "dev" -> devSubcommand(event, settings)
            "maxcal" -> maxCalSubcommand(event, settings)
            "api-register" -> apiRegisterSubcommand(event, settings)
            "api-block" -> apiBlockSubcommand(event, settings)
            else -> Mono.empty() //Never can reach this, makes compiler happy.
        }
    }

    private fun patronSubcommand(event: SlashCommandEvent, settings: GuildSettings): Mono<Void> {
        return Mono.justOrEmpty(event.options[0].getOption("guild").flatMap { it.value })
              .map(ApplicationCommandInteractionOptionValue::asString)
              .map(Snowflake::of)
              .flatMap { DatabaseManager.getSettings(it) }
              .doOnNext { settings.patronGuild = !settings.patronGuild }
              .flatMap {
                  DatabaseManager.updateSettings(it).then(Responder.followupEphemeral(
                        event,
                        getMessage("patron.success", settings, settings.patronGuild.toString())
                  ))
              }.doOnError { LOGGER.error("[cmd] patron failure", it) }
              .onErrorResume { Responder.followupEphemeral(event, "patron.failure.badId") }
              .then()
    }

    private fun devSubcommand(event: SlashCommandEvent, settings: GuildSettings): Mono<Void> {
        return Mono.justOrEmpty(event.options[0].getOption("guild").flatMap { it.value })
              .map(ApplicationCommandInteractionOptionValue::asString)
              .map(Snowflake::of)
              .flatMap { DatabaseManager.getSettings(it) }
              .doOnNext { settings.devGuild = !settings.devGuild }
              .flatMap {
                  DatabaseManager.updateSettings(it).then(Responder.followupEphemeral(
                        event,
                        getMessage("dev.success", settings, settings.devGuild.toString())
                  ))
              }.doOnError { LOGGER.error("[cmd] dev failure", it) }
              .onErrorResume { Responder.followupEphemeral(event, "dev.failure.badId") }
              .then()
    }

    private fun maxCalSubcommand(event: SlashCommandEvent, settings: GuildSettings): Mono<Void> {
        return Mono.justOrEmpty(event.options[0].getOption("guild").flatMap { it.value })
              .map(ApplicationCommandInteractionOptionValue::asString)
              .map(Snowflake::of)
              .flatMap { DatabaseManager.getSettings(it) }
              .doOnNext {
                  val amount = event.options[0].getOption("amount").get().value.get().asLong().toInt()
                  it.maxCalendars = amount
              }.flatMap {
                  DatabaseManager.updateSettings(it).then(Responder.followupEphemeral(
                        event,
                        getMessage("maxcal.success", settings, settings.maxCalendars.toString())
                  ))
              }
              .onErrorResume {
                  Responder.followupEphemeral(event, "maxcal.failure.badInput")
              }.then()
    }

    private fun apiRegisterSubcommand(event: SlashCommandEvent, settings: GuildSettings): Mono<Void> {
        return Mono.justOrEmpty(event.options[0].getOption("user").flatMap { it.value })
              .flatMap(ApplicationCommandInteractionOptionValue::asUser)
              .flatMap { user ->
                  val acc = UserAPIAccount(
                        user.id.asString(),
                        csRandomAlphaNumericString(64),
                        false,
                        System.currentTimeMillis()
                  )

                  DatabaseManager.updateAPIAccount(acc).flatMap { success ->
                      if (success) {
                          Responder.followupEphemeral(
                                event,
                                getMessage("apiRegister.success", settings, acc.APIKey)
                          )
                      } else {
                          Responder.followupEphemeral(event, getMessage("apiRegister.failure.unable", settings))
                      }
                  }
              }.switchIfEmpty(Responder.followupEphemeral(event, getMessage("apiRegister.failure.empty", settings)))
              .then()
    }

    private fun apiBlockSubcommand(event: SlashCommandEvent, settings: GuildSettings): Mono<Void> {
        return Mono.justOrEmpty(event.options[0].getOption("key").flatMap { it.value })
              .map(ApplicationCommandInteractionOptionValue::asString)
              .flatMap(DatabaseManager::getAPIAccount)
              .map {
                  it.copy(blocked = true)
              }.flatMap(DatabaseManager::updateAPIAccount)
              .flatMap { Responder.followupEphemeral(event, getMessage("apiBlock.success", settings)) }
              .switchIfEmpty(Responder.followupEphemeral(event, getMessage("apiBlock.failure.notFound", settings)))
              .onErrorResume {
                  Responder.followupEphemeral(event, "apiBlock.failure.other")
              }.then()
    }
}

package org.dreamexposure.discal.server.endpoints.v2.calendar

import discord4j.common.util.Snowflake
import discord4j.core.DiscordClient
import kotlinx.serialization.encodeToString
import org.dreamexposure.discal.core.entities.Calendar
import org.dreamexposure.discal.core.extensions.discord4j.getCalendar
import org.dreamexposure.discal.core.logger.LOGGER
import org.dreamexposure.discal.core.utils.GlobalVal
import org.dreamexposure.discal.server.utils.Authentication
import org.dreamexposure.discal.server.utils.responseMessage
import org.json.JSONException
import org.json.JSONObject
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/v2/calendar")
class DeleteCalendarEndpoint(val client: DiscordClient) {
    @PostMapping("/delete", produces = ["application/json"])
    fun deleteCalendar(swe: ServerWebExchange, response: ServerHttpResponse, @RequestBody rBody: String): Mono<String> {
        return Authentication.authenticate(swe).flatMap { authState ->
            if (!authState.success) {
                response.rawStatusCode = authState.status
                return@flatMap Mono.just(GlobalVal.JSON_FORMAT.encodeToString(authState))
            } else if (authState.readOnly) {
                response.rawStatusCode = GlobalVal.STATUS_AUTHORIZATION_DENIED
                return@flatMap responseMessage("Read-Only key not allowed")
            }

            //Handle request
            val body = JSONObject(rBody)
            val guildId = Snowflake.of(body.getString("guild_id"))
            val calendarNumber = body.getInt("calendar_number")

            return@flatMap client.getGuildById(guildId).getCalendar(calendarNumber)
                    .flatMap(Calendar::delete)
                    .doOnNext { response.rawStatusCode = GlobalVal.STATUS_SUCCESS }
                    .then(responseMessage("Success"))
                    .switchIfEmpty(responseMessage("Calendar not found")
                            .doOnNext { response.rawStatusCode = GlobalVal.STATUS_NOT_FOUND }
                    )
        }.onErrorResume(JSONException::class.java) {
            LOGGER.trace("[API-v2] JSON error. Bad request?", it)

            response.rawStatusCode = GlobalVal.STATUS_BAD_REQUEST
            return@onErrorResume responseMessage("Bad Request")
        }.onErrorResume {
            LOGGER.error(GlobalVal.DEFAULT, "[API-v2] delete calendar error", it)

            response.rawStatusCode = GlobalVal.STATUS_INTERNAL_ERROR
            return@onErrorResume responseMessage("Internal Server Error")
        }
    }
}

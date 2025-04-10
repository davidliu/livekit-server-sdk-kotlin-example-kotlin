package io.livekit.server

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import livekit.LivekitModels.ParticipantPermission
import java.util.UUID

fun main() {
    println("Hello, server started")

    val roomName = "myroom"
    val apiKey = ""
    val secret = ""
    val roomServiceClient = RoomServiceClient.create(
        host = "http://localhost:7880",
        apiKey = apiKey,
        secret = secret,
        logging = true,
        okHttpConfigurator = { builder ->
        }
    )

    embeddedServer(Netty, port = 8000) {
        routing {
            get("/token") {
                println("token: ${call.request}")

                val token = AccessToken(apiKey, secret).apply {
                    name = "user"
                    addGrants(
                        RoomName(roomName),
                        RoomCreate(true),
                        RoomJoin(true),
                        RoomAdmin(true),
                        CanPublish(true),
                        CanPublishData(true),
                        CanSubscribe(true),
                    )
                    identity = "user-${UUID.randomUUID()}"
                }

                @Serializable
                data class TokenResponse(val token: String, val url: String)
                call.respond(HttpStatusCode.OK, Json.encodeToString(TokenResponse(url = "ws://192.168.11.2:7880", token = token.toJwt())))
            }
            get("/createStream") {
                println("createStream ${call.request}")

                println(
                    "serialization: ${
                        Json.encodeToString(
                            ParticipantMetadata(isCreator = true)
                        )
                    }"
                )

                val creatorName = (call.request.queryParameters["creatorName"] ?: "")
                    .takeIf { it.isNotBlank() }
                    ?: UUID.randomUUID().toString()

                roomServiceClient.createRoom(
                    name = roomName,
                    metadata = Json.encodeToString(
                        RoomMetadata(
                            LivestreamInfo(
                                code = "fdsa",
                                url = "http://example.com"
                            )
                        )
                    )
                ).execute()

                val token = AccessToken(apiKey, secret).apply {
                    name = creatorName
                    addGrants(
                        RoomName(roomName),
                        RoomCreate(true),
                        RoomJoin(true),
                        RoomAdmin(true),
                        CanPublish(true),
                        CanPublishData(true),
                        CanSubscribe(true),
                    )
                    identity = "owner-${UUID.randomUUID()}"
                }

                call.respond(HttpStatusCode.OK, Json.encodeToString(CreateStreamResponse("ws://192.168.11.5:7880", token.toJwt())))

                println("createStream ${call.request}, responded")

                runBlocking {
                    delay(2500)
                    roomServiceClient.updateParticipant(
                        roomName = roomName,
                        name = creatorName,
                        identity = token.identity!!,
                        metadata = Json.encodeToString(
                            ParticipantMetadata(isCreator = true)
                        )
                    ).execute()
                }
            }

            get("/joinStream") {

                println("joinStream ${call.request}")
                val creatorName = call.request.queryParameters["name"] ?: ""
                val token = AccessToken(apiKey, secret).apply {
                    name = creatorName
                    addGrants(
                        RoomName(roomName),
                        RoomCreate(true),
                        RoomJoin(true),
                        CanSubscribe(true),
                        CanPublishData(true),
                    )
                    identity = "owner-${UUID.randomUUID()}"
                }

                call.respond(HttpStatusCode.OK, Json.encodeToString(JoinStreamResponse("ws://192.168.11.5:7880", token.toJwt())))
                println("joinStream ${call.request}, responded")
            }

            get("/inviteToStage") {
                println("inviteToStage ${call.request}")
                val identity = call.request.queryParameters["name"]
                if (identity == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@get
                }

                roomServiceClient.updateParticipant(
                    roomName = roomName,
                    identity = identity,
                    name = null,
                    metadata = Json.encodeToString(
                        ParticipantMetadata(
                            isOnStage = true,
                        )
                    ),
                    participantPermission = with(ParticipantPermission.newBuilder()) {
                        canPublish = true
                        build()
                    },
                ).execute()
                call.respond(HttpStatusCode.OK, "")
                println("inviteToStage ${call.request}, responded")
            }
            get("/removeFromStage") {
                println("removeFromStage ${call.request}")
                val identity = call.request.queryParameters["name"]
                if (identity == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@get
                }

                roomServiceClient.updateParticipant(
                    roomName = roomName,
                    identity = identity,
                    name = null,
                    metadata = Json.encodeToString(
                        ParticipantMetadata()
                    ),
                    participantPermission = with(ParticipantPermission.newBuilder()) {
                        canPublish = false
                        build()
                    },
                ).execute()
                call.respond(HttpStatusCode.OK, "")
                println("removeFromStage ${call.request}, responded")
            }
            get("/requestToJoin") {
                println("requestToJoin ${call.request}")
                val identity = call.request.queryParameters["name"]
                if (identity == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@get
                }

                roomServiceClient.updateParticipant(
                    roomName = roomName,
                    identity = identity,
                    name = null,
                    metadata = Json.encodeToString(
                        ParticipantMetadata(requested = true)
                    ),
                    participantPermission = null,
                ).execute()
                call.respond(HttpStatusCode.OK, "")
                println("requestToJoin ${call.request}, responded")
            }


            get("/inviteToStage") {
                println("inviteToStage ${call.request}")
                val identity = call.request.queryParameters["name"]
                if (identity == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@get
                }

                roomServiceClient.updateParticipant(
                    roomName = roomName,
                    identity = identity,
                    name = null,
                    metadata = Json.encodeToString(
                        ParticipantMetadata(
                            isOnStage = true,
                        )
                    ),
                    participantPermission = with(ParticipantPermission.newBuilder()) {
                        canPublish = true
                        build()
                    },
                ).execute()
                call.respond(HttpStatusCode.OK, "")
                println("inviteToStage ${call.request}, responded")
            }
        }
    }.start(wait = true)

    println("Server ended.")
}

@Serializable
data class CreateStreamResponse(val livekitUrl: String, val token: String)

@Serializable
data class JoinStreamResponse(val livekitUrl: String, val token: String)

@Serializable
data class RoomMetadata(
    val livestream: LivestreamInfo
)

@Serializable
data class LivestreamInfo(
    // Name of the room
    val code: String,
    // Url to join the room
    val url: String,
)

@Serializable
data class ParticipantMetadata(
    // true if participant requested to join stage
    val requested: Boolean = false,
    // true if participant has been invited to stage and accepted
    val isOnStage: Boolean = false,
    // true if room creator
    val isCreator: Boolean = false,
    // url of avatar
    val avatarUrl: String = "",
)
package com.oiw.camera.control

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.net.InetAddress
import java.net.ServerSocket
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Local-only external control socket (docs/API_PLUGIN_FRAMEWORK.md #4). DISABLED BY DEFAULT —
 * constructed/started only when the user enables it in Settings. Newline-delimited JSON per
 * configs/schema/control_message.schema.json. First message of each connection MUST carry the
 * pairing token or the connection is closed immediately. Binds loopback only unless the user
 * explicitly opts into LAN exposure.
 */
class ExternalControlServer(
    private val port: Int,
    private val bindLanInterface: Boolean, // false => 127.0.0.1 only (default)
    private val handler: CommandHandler,
) {
    interface CommandHandler {
        /** @return JSON-serializable reply object. Runs on the connection thread — keep it fast. */
        fun handle(cmd: ControlMessage): Map<String, Any?>
    }

    data class ControlMessage(val cmd: String?, val value: Any? = null, val token: String? = null)

    private val gson = Gson()
    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null

    /** Random pairing token, regenerated per enable; shown as QR/text in Settings for the client. */
    val pairingToken: String = ByteArray(24).let {
        SecureRandom().nextBytes(it)
        it.joinToString("") { b -> "%02x".format(b) }
    }

    fun start(onError: (String) -> Unit) {
        if (running.getAndSet(true)) return
        try {
            val bindAddr = if (bindLanInterface) null else InetAddress.getLoopbackAddress()
            serverSocket = ServerSocket(port, 2, bindAddr)
        } catch (e: Exception) {
            onError("External control: could not bind port $port (${e.message}).")
            running.set(false)
            return
        }
        acceptThread = Thread({
            while (running.get()) {
                val client = runCatching { serverSocket?.accept() }.getOrNull() ?: continue
                Thread({ serveClient(client) }, "OIWControlClient").start()
            }
        }, "OIWControlAccept").also { it.start() }
    }

    private fun serveClient(socket: java.net.Socket) {
        socket.use { s ->
            s.soTimeout = 30_000
            val reader = s.getInputStream().bufferedReader()
            val writer = s.getOutputStream().bufferedWriter()
            var authenticated = false
            while (running.get()) {
                val line = runCatching { reader.readLine() }.getOrNull() ?: return
                val msg = try {
                    gson.fromJson(line, ControlMessage::class.java)
                } catch (e: JsonSyntaxException) {
                    writer.write(gson.toJson(mapOf("error" to "malformed JSON"))); writer.newLine(); writer.flush()
                    continue
                }
                if (!authenticated) {
                    if (msg?.token == pairingToken) {
                        authenticated = true
                        writer.write(gson.toJson(mapOf("ok" to "paired"))); writer.newLine(); writer.flush()
                        if (msg.cmd == null) continue
                    } else {
                        // Wrong/missing token on first message: close immediately, no retry on this connection.
                        writer.write(gson.toJson(mapOf("error" to "pairing token required"))); writer.newLine(); writer.flush()
                        return
                    }
                }
                if (msg?.cmd == null) continue
                val reply = try {
                    handler.handle(msg)
                } catch (e: Exception) {
                    mapOf("error" to "command failed: ${e.message}")
                }
                writer.write(gson.toJson(reply)); writer.newLine(); writer.flush()
            }
        }
    }

    fun stop() {
        running.set(false)
        runCatching { serverSocket?.close() }
        acceptThread?.join(1_000)
    }

    companion object {
        val KNOWN_COMMANDS = setOf(
            "record_start", "record_stop", "profile_switch", "set_exposure", "set_iso",
            "set_white_balance", "set_focus", "slate_metadata", "status_query",
        )
    }
}

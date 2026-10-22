package io.github.clfundev.glink.discovery

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

/**
 * 局域网服务发现能力
 */
object LocalServerDiscovery {
    private const val DISCOVERY_MESSAGE = "Who is GlinkServer?"
    private const val DISCOVERY_PORT = 7420
    private const val DISCOVERY_RECEIVE_BUFFER = 1024 // bytes
    private const val DISCOVERY_TIMEOUT = 500 // ms
    private const val DISCOVERY_MAX_SERVERS = 5
    private const val TAG = "LocalServerDiscovery"

    /**
     * 通过 [socket] 向广播地址255.255.255.255发送消息
     */
    private fun send(socket: DatagramSocket) {
        val address = InetAddress.getByAddress(
            byteArrayOf(255.toByte(), 255.toByte(), 255.toByte(), 255.toByte())
        )
        try {
            val message = DISCOVERY_MESSAGE.toByteArray()
            val packet = DatagramPacket(message, message.size, address, DISCOVERY_PORT)
            socket.send(packet)

            Log.d(TAG, "Discovering via $address")
        } catch (err: IOException) {
            Log.e(TAG, "Unable to send discovery message to $address", err)
        }
    }

    /**
     * 从 [socket] 接收消息并解析得到服务器信息
     */
    private fun receive(socket: DatagramSocket): ServerInfo? {
        Log.d(TAG, "Receiving message")

        val buffer = ByteArray(DISCOVERY_RECEIVE_BUFFER)
        val packet = DatagramPacket(buffer, buffer.size)

        return try {
            socket.receive(packet)

            // ByteArray转String
            val message = String(packet.data, 0, packet.length)
            Log.d(TAG, "Received message: \"$message\"")

            // 解析JSON得到ServiceInfo
            Json.decodeFromString<ServerInfo>(message)
        } catch (_: SocketTimeoutException) {
            // 超时异常，忽略
            null
        } catch (e: IOException) {
            // 消息接收异常
            Log.e(TAG, "Unable to receive message", e)
            null
        } catch (e: SerializationException) {
            // JSON解析异常
            Log.e(TAG, "Unable to deserialize message", e)
            null
        }
    }

    /**
     * 发现服务器，[timeout] 毫秒超时，最多发现 [maxServers] 个
     */
    fun discover(
        timeout: Int = DISCOVERY_TIMEOUT,
        maxServers: Int = DISCOVERY_MAX_SERVERS
    ): Flow<ServerInfo> = flow {
        Log.i(TAG, "discover start: timeout(ms) = $timeout, maxServers = $maxServers")

        // 发送广播
        val socket = DatagramSocket().apply {
            soTimeout = timeout
        }
        send(socket)
        Log.d(TAG, "Finished sending broadcast, listening for responses")

        // 等待服务器响应
        val foundServers = mutableSetOf<String>()
        for (i in 0..maxServers) {
            if (socket.isClosed || !currentCoroutineContext().isActive) break
            val info = receive(socket) ?: continue
            if (info.id in foundServers) continue
            foundServers += info.id
            emit(info)
        }

        // 结束
        socket.close()
        Log.d(TAG, "discover end")
    }.flowOn(Dispatchers.IO)
}
package com.my.kizzy.rpc

import android.util.ArrayMap
import com.android.girish.vlog.Vlog
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.my.kizzy.domain.repository.KizzyRepository
import com.my.kizzy.rpc.Constants.APPLICATION
import com.my.kizzy.rpc.Constants.LARGE_IMAGE
import com.my.kizzy.rpc.Constants.SMALL_IMAGE
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.net.URISyntaxException
import javax.inject.Inject
import javax.net.ssl.SSLParameters

class KizzyRPC @Inject constructor(
    private val token: String,
    private val kizzyRepository: KizzyRepository,
    private val vlog: Vlog
) {

    private var activityName: String? = null
    private var details: String? = null
    private var state: String? = null
    private var largeImage: RpcImage? = null
    private var smallImage: RpcImage? = null
    private var status:String? = null
    private var startTimestamps: Long? = null
    private var stopTimestamps: Long? = null
    private var type:Int = 0
    var rpc = ArrayMap<String, Any>()
    var webSocketClient: WebSocketClient? = null
    var gson: Gson = GsonBuilder().setPrettyPrinting().serializeNulls().create()
    var heartbeatRunnable: Runnable
    var heartbeatThr: Thread? = null
    var heartbeatInterval = 0
    var seq = 0
    private var sessionId: String? = null
    private var reconnectSession = false
    private var buttons = ArrayList<String>()
    private var buttonUrl = ArrayList<String>()


    fun closeRPC() {
        if (heartbeatThr != null) {
            if (!heartbeatThr!!.isInterrupted) heartbeatThr!!.interrupt()
        }
        if (webSocketClient != null) webSocketClient!!.close(1000)
    }

    fun isRpcRunning(): Boolean{
        return webSocketClient?.isOpen == true
    }

    /**
     * #Todo add a better token checking function
     * @return true if token is valid else false
     */
    fun isUserTokenValid(): Boolean{
        return token.isNotEmpty()
    }
    /**
     * Activity Name of Rpc
     *
     * @param activity_name
     * @return
     */
    fun setName(activity_name: String?): KizzyRPC {
        this.activityName = activity_name
        return this
    }

    /**
     * Details of Rpc
     *
     * @param details
     * @return
     */
    fun setDetails(details: String?): KizzyRPC {
        this.details = details
        return this
    }

    /**
     * Rpc State
     *
     * @param state
     * @return
     */
    fun setState(state: String?): KizzyRPC {
        this.state = state
        return this
    }

    /**
     * Large image on rpc
     * How to get Image ?
     * Upload image to any discord chat and copy its media link it should look like "https://media.discordapp.net/attachments/90202992002/xyz.png" now just use the image link from attachments part
     * so it would look like: .setLargeImage("attachments/90202992002/xyz.png")
     * @param large_image
     * @return
     */
    fun setLargeImage(large_image: RpcImage?): KizzyRPC {
        this.largeImage = large_image
        return this
    }

    /**
     * Small image on Rpc
     *
     * @param small_image
     * @return
     */
    fun setSmallImage(small_image: RpcImage?): KizzyRPC {
        this.smallImage = small_image
        return this
    }

    /**
     * start timestamps
     *
     * @param start_timestamps
     * @return
     */
    fun setStartTimestamps(start_timestamps: Long?): KizzyRPC {
        this.startTimestamps = start_timestamps
        return this
    }

    /**
     * stop timestamps
     *
     * @param stop_timestamps
     * @return
     */
    fun setStopTimestamps(stop_timestamps: Long?): KizzyRPC {
        this.stopTimestamps = stop_timestamps
        return this
    }

    /**
     * Activity Types
     * 0: Playing
     * 1: Streaming
     * 2: Listening
     * 3: Watching
     * 5: Competing
     *
     * @param type
     * @return
     */

    fun setType(type: Int): KizzyRPC {
        if (type in 0..5)
            this.type = type
        else this.type = 0
        return this
    }

    /** Status type for profile online,idle,dnd
    *
    * @param status
    * @return
    */
    fun setStatus(status: String?): KizzyRPC {
        this.status = status
        return this
    }

    /**
     * Button1 text
     * @param button1_Text
     * @return
     */
    fun setButton1(button1_Text: String?): KizzyRPC {
        button1_Text?.let { buttons.add(it) }
        return this
    }

    /**
     * Button2 text
     * @param button2_text
     * @return
     */
    fun setButton2(button2_text: String?): KizzyRPC {
        button2_text?.let { buttons.add(it) }
        return this
    }

    /**
     * Button1 url
     * @param url
     * @return
     */
    fun setButton1URL(url: String?): KizzyRPC {
        url?.let { buttonUrl.add(it) }
        return this
    }

    /**
     * Button2 url
     * @param url
     * @return
     */
    fun setButton2URL(url: String?): KizzyRPC {
        url?.let { buttonUrl.add(it) }
        return this
    }

    suspend fun build() {
        val presence = ArrayMap<String, Any?>()
        val activity = ArrayMap<String, Any?>()
        activity[Constants.NAME] = activityName
        activity[Constants.DETAILS] = details
        activity[Constants.STATE] = state
        activity[Constants.TYPE] = type
        val timestamps = ArrayMap<String, Any?>()
        timestamps[Constants.START_TIMESTAMPS] = startTimestamps
        timestamps[Constants.STOP_TIMESTAMPS] = stopTimestamps
        activity[Constants.TIMESTAMPS] = timestamps
        val assets = ArrayMap<String, Any?>()
        assets[LARGE_IMAGE] = largeImage?.resolveImage(kizzyRepository)
        assets[SMALL_IMAGE] = smallImage?.resolveImage(kizzyRepository)
        activity[Constants.ASSETS] = assets
        if (buttons.size > 0) {
            activity[APPLICATION] = Constants.APPLICATION_ID
            activity[Constants.BUTTONS] = buttons
            val metadata = ArrayMap<String, Any>()
            metadata[Constants.BUTTON_LINK] = buttonUrl
            activity[Constants.METADATA] = metadata
        }
        presence[Constants.ACTIVITIES] = arrayOf<Any>(activity)
        presence[Constants.AFK] = true
        presence[Constants.SINCE] = startTimestamps
        presence[Constants.STATUS] = status
        rpc["op"] = 3
        rpc["d"] = presence
        createWebsocketClient()
    }

    fun sendIdentify() {
        val prop = ArrayMap<String, Any>()
        prop["os"] = "Windows"
        prop["browser"] = "Discord Client"
        prop["device"] = "disco"
        val data = ArrayMap<String, Any>()
        data["token"] = token
        data["properties"] = prop
        data["compress"] = false
        data["capabilities"] = 65
        data["largeThreshold"] = 100
        val identify = ArrayMap<String, Any>()
        identify["op"] = 2
        identify["d"] = data
        vlog.d(TAG, "sendIdentify() called")
        webSocketClient!!.send(gson.toJson(identify))
    }

    private fun createWebsocketClient() {
        val uri: URI = try {
            URI("wss://gateway.discord.gg/?encoding=json&v=10")
        } catch (e: URISyntaxException) {
            e.printStackTrace()
            return
        }
        val headerMap = ArrayMap<String, String>()
        webSocketClient = Websocket(uri,headerMap)
        (webSocketClient as Websocket).connect()
    }

    suspend fun updateRPC(
        name: String,
        details: String?,
        state: String?,
        large_image: RpcImage?,
        small_image: RpcImage?,
        enableTimestamps: Boolean,
        time: Long
    ) {
        if (!isRpcRunning()) return
        val presence = ArrayMap<String, Any>()
        val activity = ArrayMap<String, Any>()
        activity["name"] = name
        activity["details"] = details
        activity["state"] = state
        activity["type"] = 0
        if (enableTimestamps) {
            val timestamps = ArrayMap<String, Any>()
            timestamps["start"] = time
            activity["timestamps"] = timestamps
        }
        val assets = ArrayMap<String, String>()
        large_image?.let {
            assets[LARGE_IMAGE] = large_image.resolveImage(kizzyRepository)
        }
        small_image?.let {
            assets[SMALL_IMAGE] = small_image.resolveImage(kizzyRepository)
        }
        activity[Constants.ASSETS] = assets
        presence["activities"] = arrayOf<Any>(activity)
        presence["afk"] = true
        presence["since"] = time
        presence["status"] = "dnd"
        val arr = ArrayMap<String, Any>()
        arr["op"] = 3
        arr["d"] = presence
        webSocketClient?.send(gson.toJson(arr))
    }

    init {
        heartbeatRunnable = Runnable {
            try {
                if (heartbeatInterval < 10000) throw RuntimeException("invalid")
                Thread.sleep(heartbeatInterval.toLong())
                webSocketClient!!.send(
                    "{\"op\":1, \"d\":" + (if (seq == 0) "null" else seq.toString()) + "}"
                )
            } catch (_: InterruptedException) {
            }
        }
    }

    companion object{
        const val TAG = "Websocket"
    }

    inner class Websocket(uri: URI,map: ArrayMap<String,String>): WebSocketClient(uri,map) {
        override fun connect() {
            vlog.d(TAG, "connect() called")
            super.connect()
        }

        private var gatewayResume = ""
        override fun onOpen(handshakedata: ServerHandshake?) {
            vlog.i(TAG, "onOpen() called with: handshake-data = $handshakedata")
        }

        override fun onMessage(message: String) {
            vlog.i(TAG, "onMessage() called with: message = $message")
            val map = gson.fromJson<ArrayMap<String, Any>>(
                message, object : TypeToken<ArrayMap<String?, Any?>?>() {}.type
            )
            val o = map["s"]
            if (o != null) {
                seq = (o as Double).toInt()
            }
            when ((map["op"] as Double?)!!.toInt()) {
                0 -> if (map["t"] as String? == "READY") {
                    sessionId = (map["d"] as Map<*, *>?)!!["session_id"].toString()
                    gatewayResume = (map["d"] as Map<*, *>?)!!["resume_gateway_url"].toString()
                    vlog.d(TAG,gatewayResume)
                    vlog.i(TAG,"Connected")
                    send(gson.toJson(rpc))
                    return
                }
                10 -> if (!reconnectSession) {
                    val data = map["d"] as Map<*, *>?
                    heartbeatInterval = (data!!["heartbeat_interval"] as Double?)!!.toInt()
                    heartbeatThr = Thread(heartbeatRunnable)
                    heartbeatThr!!.start()
                    sendIdentify()
                } else {
                    vlog.d(TAG,"Sending Reconnect")
                    val data = map["d"] as Map<*, *>?
                    heartbeatInterval = (data!!["heartbeat_interval"] as Double?)!!.toInt()
                    heartbeatThr = Thread(heartbeatRunnable)
                    heartbeatThr!!.start()
                    reconnectSession = false
                    webSocketClient!!.send("{\"op\": 6,\"d\":{\"token\":\"$token\",\"session_id\":\"$sessionId\",\"seq\":$seq}}")
                }
                1 -> {
                    if (!Thread.interrupted()) {
                        heartbeatThr!!.interrupt()
                    }
                    webSocketClient!!.send(
                        "{\"op\":1, \"d\":" + (if (seq == 0) "null" else seq.toString()) + "}"
                    )
                }
                11 -> {
                    if (!Thread.interrupted()) {
                        heartbeatThr!!.interrupt()
                    }
                    heartbeatThr = Thread(heartbeatRunnable)
                    heartbeatThr!!.start()
                }
                7 -> {
                    reconnectSession = true
                    vlog.e(TAG, "Closing and Reconnecting Session")
                    webSocketClient!!.close(4000)
                }
                9 -> if (!heartbeatThr!!.isInterrupted) {
                    vlog.d(TAG, "Reconnect Failed")
                    heartbeatThr!!.interrupt()
                    heartbeatThr = Thread(heartbeatRunnable)
                    heartbeatThr!!.start()
                    sendIdentify()
                }
            }
        }

        override fun onClose(code: Int, reason: String, remote: Boolean) {
            vlog.d(TAG, "onClose() called with: code = $code, reason = $reason, remote = $remote")
            if (code == 4000) {
                reconnectSession = true
                heartbeatThr!!.interrupt()
                vlog.e(TAG, "Closed Socket")
                val newTh = Thread {
                    try {
                        Thread.sleep(200)
                        webSocketClient = Websocket(URI(gatewayResume),ArrayMap<String, String>())
                        (webSocketClient as Websocket).connect()
                    } catch (_: InterruptedException) {
                    }
                }
                newTh.start()
            } else throw RuntimeException("Invalid")
        }

        override fun onError(e: Exception) {
            vlog.e(TAG, "onError() called with: e = $e")
            if (e.message != "Interrupt") {
                closeRPC()
            }
        }
        override fun onSetSSLParameters(p: SSLParameters) {
            try {
                super.onSetSSLParameters(p)
            } catch (th: Throwable) {
                th.printStackTrace()
            }
        }
    }
}

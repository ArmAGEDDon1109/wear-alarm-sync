package com.wearalarmsync.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.tasks.Tasks
import com.wearalarmsync.common.WearSync

object PhoneCommand {
    private const val TAG = "PhoneCommand"

    fun send(context: Context, command: String): Boolean {
        val app = context.applicationContext
        return try {
            val nodes = Tasks.await(Wearable.getNodeClient(app).connectedNodes)
            if (nodes.isEmpty()) {
                Log.w(TAG, "No connected nodes")
                return false
            }
            val payload = command.toByteArray(Charsets.UTF_8)
            val client = Wearable.getMessageClient(app)
            for (node in nodes) {
                Tasks.await(client.sendMessage(node.id, WearSync.MESSAGE_PATH, payload))
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "sendMessage failed", e)
            false
        }
    }
}

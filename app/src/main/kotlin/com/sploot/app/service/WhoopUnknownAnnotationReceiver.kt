package com.sploot.app.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.sploot.data.repository.WhoopUnknownObservationRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class WhoopUnknownAnnotationReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: WhoopUnknownObservationRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ANNOTATE_UNKNOWN_WHOOP) return

        val pending = goAsync()
        val observationId = intent.getLongExtra(EXTRA_OBSERVATION_ID, -1L)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, UNKNOWN_PROMPT_NOTIFICATION_ID)
        val reply = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(KEY_TEXT_REPLY)
            ?.toString()
            ?.trim()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (observationId > 0 && !reply.isNullOrBlank()) {
                    repository.annotate(observationId, reply)
                }
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .cancel(notificationId)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_ANNOTATE_UNKNOWN_WHOOP = "com.sploot.app.ANNOTATE_UNKNOWN_WHOOP"
        const val EXTRA_OBSERVATION_ID = "extra_observation_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val KEY_TEXT_REPLY = "key_text_reply"
        const val UNKNOWN_PROMPT_NOTIFICATION_ID = 2001
    }
}

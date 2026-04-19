package uk.storitad.capture.ui.drafts

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import uk.storitad.capture.location.LocationProvider

object DraftLocationJob {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile private var job: Job? = null

    fun start(ctx: Context) {
        cancel()
        val appCtx = ctx.applicationContext
        job = scope.launch {
            val outcome = LocationProvider(appCtx).currentFix()
            if (outcome is LocationProvider.Outcome.Ok) {
                DraftHolder.setLocationFix(outcome.fix)
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }
}

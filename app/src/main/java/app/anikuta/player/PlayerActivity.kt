package app.anikuta.player

import android.app.Activity
import android.os.Bundle
import android.util.Log

/**
 * Minimal PlayerActivity stub.
 * TODO: Replace with full MPV player from aniyomi (Step 4.1).
 */
class PlayerActivity : Activity() {
    companion object {
        private const val TAG = "PlayerActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "PlayerActivity created — stub, no player yet")
        finish()  // Close immediately for now
    }
}

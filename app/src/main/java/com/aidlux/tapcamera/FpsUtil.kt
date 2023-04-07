package com.aidlux.tapcamera

import android.util.Log

class FpsUtil(val logName: String) {
    var frames = 0
    var startTime: Long = 0

    fun timing() {
        val nowTime = System.currentTimeMillis()

        if (startTime == 0L) {
            startTime = nowTime
        } else {

            ++frames
            val deltaTime: Long = nowTime - startTime
            if (deltaTime > 1000) {
                val secs = deltaTime / 1000f
                val fpsValue: Float = frames / secs
                val fpsString = "fps: $fpsValue"
                Log.d(logName, fpsString)
                startTime = nowTime
                frames = 0
            }


        }

    }

    @Throws(Throwable::class)
    protected fun finalize() {

    }

}
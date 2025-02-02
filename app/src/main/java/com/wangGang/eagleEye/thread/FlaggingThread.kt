package com.wangGang.eagleEye.thread

import java.util.concurrent.Semaphore

abstract class FlaggingThread(protected var semaphore: Semaphore) : Thread() {
    /*
        * Starts the work of the thread while acquiring the semaphore permit
        */
    fun startWork() {
        try {
            semaphore.acquire()
            this.start()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    /*
     * Stops the work of the thread. IMPORTANT. This must be called at the end of the overrided run() function to ensure that the semaphore permit is released.
     */
    fun finishWork() {
        semaphore.release()
    }

    companion object {
        private const val TAG = "FlaggingThread"
    }
}
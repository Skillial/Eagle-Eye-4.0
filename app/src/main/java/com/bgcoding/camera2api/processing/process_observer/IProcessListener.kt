package com.bgcoding.camera2api.processing.process_observer

interface IProcessListener {
    fun onProcessCompleted()
    fun onProducedInitialHR()
}
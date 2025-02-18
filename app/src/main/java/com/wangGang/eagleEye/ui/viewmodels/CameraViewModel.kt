package com.wangGang.eagleEye.ui.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import android.net.Uri

class CameraViewModel : ViewModel() {
    private val _loadingText = MutableLiveData<String>()
    val loadingText: LiveData<String> get() = _loadingText

    private val _thumbnailUri = MutableLiveData<Uri?>()
    val thumbnailUri: LiveData<Uri?> get() = _thumbnailUri

    private val _imageInputMap = MutableLiveData<MutableList<String>>()
    val imageInputMap: LiveData<MutableList<String>> get() = _imageInputMap

    private val _loadingBoxVisible = MutableLiveData<Boolean>()
    val loadingBoxVisible: LiveData<Boolean> get() = _loadingBoxVisible

    init {
        _imageInputMap.value = mutableListOf()
        _loadingBoxVisible.value = false
    }

    fun updateLoadingText(text: String) {
        _loadingText.postValue(text)
    }

    fun updateThumbnailUri(uri: Uri?) {
        _thumbnailUri.postValue(uri)
    }

    fun addImageInput(imagePath: String) {
        _imageInputMap.value?.add(imagePath)
        _imageInputMap.postValue(_imageInputMap.value) // Trigger LiveData update
    }

    fun clearImageInputMap() {
        _imageInputMap.value?.clear()
        _imageInputMap.postValue(_imageInputMap.value) // Trigger LiveData update
    }

    fun getImageInputMap(): MutableList<String>? {
        return _imageInputMap.value
    }

    fun setLoadingBoxVisible(visible: Boolean) {
        _loadingBoxVisible.postValue(visible)
    }
}
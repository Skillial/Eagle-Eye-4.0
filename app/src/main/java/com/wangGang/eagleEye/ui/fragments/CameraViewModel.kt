import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class CameraViewModel : ViewModel() {
    private val _loadingText = MutableLiveData<String>()
    val loadingText: LiveData<String> get() = _loadingText

    fun updateLoadingText(text: String) {
        _loadingText.value = text
    }
}
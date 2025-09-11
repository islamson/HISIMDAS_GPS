package fitech.tutorials.rsmgraphlast.data.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class LocationVMFactory(private val homeVM: HomeViewModel) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(LocationViewModel::class.java))
        return LocationViewModel(homeVM) as T
    }
}

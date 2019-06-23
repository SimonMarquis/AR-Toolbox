package fr.smarquis.ar_toolbox

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.preference.PreferenceManager
import kotlin.reflect.KClass

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferenceManager.getDefaultSharedPreferences(application)

    val selection = MutableLiveData<KClass<out Nodes>>(Sphere::class)

    var sessionInitializationFailed: Boolean = false

    var installRequested: Boolean = false

    val sfbUri: MutableLiveData<String?> = object : MutableLiveData<String?>() {

        val KEY_URI = "KEY_URI"

        init {
            value = prefs.getString(KEY_URI, null).orDefault()
        }

        private fun String?.orDefault(): String =
            if (this.isNullOrBlank()) application.getString(R.string.model_link_default_uri) else this

        override fun postValue(value: String?) {
            value.orDefault().let {
                updateValue(it)
                super.postValue(it)
            }
        }

        override fun setValue(value: String?) {
            value.orDefault().let {
                updateValue(it)
                super.setValue(it)
            }
        }

        private fun updateValue(value: String) {
            prefs.edit().putString(KEY_URI, value).apply()
        }

    }

}
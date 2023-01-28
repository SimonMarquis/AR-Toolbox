package fr.smarquis.ar_toolbox

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.preference.PreferenceManager
import kotlin.reflect.KClass

class SceneViewModel(application: Application) : AndroidViewModel(application) {

    val selection = MutableLiveData<KClass<out Nodes>>(Sphere::class)

    val externalModelUri: MutableLiveData<String?> = object : MutableLiveData<String?>() {

        private val prefs = PreferenceManager.getDefaultSharedPreferences(application)

        private val KEY_URI = "KEY_URI"

        init {
            value = prefs.getString(KEY_URI, null)
        }

        override fun setValue(value: String?) {
            super.setValue(value)
            prefs.edit().putString(KEY_URI, value).apply()
        }
    }
}

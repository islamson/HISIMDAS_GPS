package fitech.tutorials.rsmgraphlast

import android.app.Application
import android.content.Context

class MyApplication : Application() {
    init {
        instance = this
    }
    companion object {
        private var instance : MyApplication? = null
        val appContext : Context
            get() = instance!!.applicationContext
    }
}
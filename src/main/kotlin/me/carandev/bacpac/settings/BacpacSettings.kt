package me.carandev.bacpac.settings

import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
    name = "me.carandev.bacpac.BacpacSettings",
    storages = [Storage("bacpac-settings.xml")]
)
@Service
class BacpacSettings : PersistentStateComponent<BacpacSettings.State> {
    
    private var myState = State()
    
    companion object {
        fun getInstance(): BacpacSettings = service()
    }
    
    class State {
        var sqlPackagePath: String? = null
        var lastExportDirectory: String? = null
        var lastImportDirectory: String? = null
        var commandTimeout: Int = 120
    }
    
    override fun getState(): State = myState
    
    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, myState)
    }
}

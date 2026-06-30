package spacewaiker.helixjet.utils

import com.akuleshov7.ktoml.Toml
import com.akuleshov7.ktoml.TomlInputConfig
import com.akuleshov7.ktoml.tree.nodes.TomlFile
import com.akuleshov7.ktoml.tree.nodes.TomlKeyValuePrimitive
import com.akuleshov7.ktoml.tree.nodes.TomlNode

@Suppress("UNCHECKED_CAST")
fun <T> TomlNode.getTomlValue(key: String) = this[key]?.let { it as? T }

@Suppress("UNCHECKED_CAST")
fun <T> TomlNode.getPrimitiveValue(key: String) = getTomlValue<TomlKeyValuePrimitive>(key)
    ?.let { it.value.content as? T }

operator fun TomlNode.get(key: String) = children.find { it.name == key }

fun readToml(contents: String): TomlFile? {
    return try {
        Toml(inputConfig = TomlInputConfig(ignoreUnknownNames = true)).tomlParser.parseString(contents)
    } catch (e: Throwable) {
        e.printStackTrace()
        null
    }
}
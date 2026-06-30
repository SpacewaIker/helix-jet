package spacewaiker.helixjet.utils

class Lazy<T>(private val f: () -> T) {
    private var value: T? = null

    operator fun invoke() = value ?: f().also { value = it }
}
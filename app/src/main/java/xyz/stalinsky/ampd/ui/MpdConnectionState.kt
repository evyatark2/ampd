package xyz.stalinsky.ampd.ui

sealed interface MpdConnectionState<T> {
    class Loading<T> : MpdConnectionState<T>
    class Error<T>(val err: Throwable) : MpdConnectionState<T>
    class Ok<T>(val res: T) : MpdConnectionState<T>
}
package xyz.stalinsky.ampd.model

interface State<T> {
    class Loading<T> : State<T>
    class Error<T> : State<T>
    class Success<T>(val value: T) : State<T>
}
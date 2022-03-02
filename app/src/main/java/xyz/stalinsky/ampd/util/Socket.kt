package xyz.stalinsky.ampd.util

import java.net.SocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class Socket {
    private val socket = AsynchronousSocketChannel.open()

    suspend fun connect(remote: SocketAddress) {
        doWait<Void> { continuation, completionHandler ->
            socket.connect(remote, continuation, completionHandler)
        }
    }

    suspend fun write(src: ByteBuffer): Int {
        return doWait { continuation, completionHandler ->
            socket.write(src, continuation, completionHandler)
        }
    }

    suspend fun read(src: ByteBuffer): Int {
        return doWait { continuation, completionHandler ->
            socket.read(src, continuation, completionHandler)
        }
    }

    private suspend fun <O> doWait(waiter: (Continuation<O>, CompletionHandler<O, Continuation<O>>) -> Unit): O {
        return suspendCoroutine {
            waiter(it, object : CompletionHandler<O, Continuation<O>> {
                override fun completed(result: O, attachment: Continuation<O>) {
                    attachment.resume(result)
                }

                override fun failed(exc: Throwable, attachment: Continuation<O>) {
                    attachment.resumeWithException(exc)
                }

            })
        }
    }
}
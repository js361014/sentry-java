package io.sentry

import io.sentry.protocol.SentryTransaction
import org.mockito.Mockito
import org.mockito.kotlin.internal.createInstance

/**
 * Verifies is [SentryEnvelope] contains first event matching a predicate.
 */
fun checkEvent(predicate: (SentryEvent) -> Unit): SentryEnvelope {
    return check {
        val event: SentryEvent? = it.items.first().getEvent(JsonSerializer(SentryOptions.empty()))
        if (event != null) {
            predicate(event)
        } else {
            throw SkipError("event is null")
        }
    }
}

fun checkTransaction(predicate: (SentryTransaction) -> Unit): SentryEnvelope {
    return check {
        val transaction = it.items.first().getTransaction(JsonSerializer(SentryOptions.empty()))
        if (transaction != null) {
            predicate(transaction)
        } else {
            throw SkipError("transaction is null")
        }
    }
}

/**
 * Asserts an envelope item of [T] exists in [items] and returns the first one. Otherwise it throws an [AssertionError].
 */
inline fun <reified T> assertEnvelopeItem(items: List<SentryEnvelopeItem>, predicate: (index: Int, item: T) -> Unit): T {
    val item = items.mapIndexedNotNull { index, it ->
        val deserialized = JsonSerializer(SentryOptions()).deserialize(it.data.inputStream().reader(), T::class.java)
        deserialized?.let { Pair(index, it) }
    }.firstOrNull()
        ?: throw AssertionError("No item found of type: ${T::class.java.name}")
    predicate(item.first, item.second)
    return item.second
}

/**
 * Modified version of check from mockito-kotlin Verification.kt, that does not print errors of type `SkipError`.
 */
private inline fun <reified T : Any> check(noinline predicate: (T) -> Unit): T {
    return Mockito.argThat { arg: T? ->
        if (arg == null) {
            error(
                """The argument passed to the predicate was null.

If you are trying to verify an argument to be null, use `isNull()`.
If you are using `check` as part of a stubbing, use `argThat` or `argForWhich` instead.
                """.trimIndent()
            )
        }

        try {
            predicate(arg)
            true
        } catch (e: SkipError) {
            false
        } catch (e: Error) {
            e.printStackTrace()
            false
        }
    } ?: createInstance(T::class)
}

class SkipError(message: String) : Error(message)

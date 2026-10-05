package io.github.javiernarvaezz.shura.core.stream

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VisitorDataTest {
    private var fetches = 0

    @Test
    fun fetchesWhenTheSessionHasNoVisitorData() =
        runTest {
            ensureVisitorData(current = { null }, fetch = { fetches++ })
            ensureVisitorData(current = { "" }, fetch = { fetches++ })

            assertEquals(2, fetches)
        }

    @Test
    fun skipsTheFetchWhenTheSessionAlreadyHasVisitorData() =
        runTest {
            ensureVisitorData(current = { "present" }, fetch = { fetches++ })

            assertEquals(0, fetches)
        }

    @Test
    fun aFailedFetchIsIgnored() =
        runTest {
            ensureVisitorData(current = { null }, fetch = { error("offline") })
        }

    @Test
    fun cancellationIsNotSwallowed() =
        runTest {
            assertFailsWith<CancellationException> {
                ensureVisitorData(current = { null }, fetch = { throw CancellationException("cancelled") })
            }
        }
}

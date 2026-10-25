package com.skillverse.app.core.time

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Source of "now". The daily boundary is the device's local calendar date, evaluated on every
 * call with the current system zone, so timezone changes take effect immediately.
 */
interface TimeProvider {
    fun now(): Instant
    fun zone(): ZoneId
    fun today(): LocalDate = now().atZone(zone()).toLocalDate()

    /** Emits today's date and again whenever the local date changes (midnight, timezone change). */
    fun observeToday(pollMillis: Long = 30_000): Flow<LocalDate> = flow {
        while (true) {
            emit(today())
            delay(pollMillis)
        }
    }.distinctUntilChanged()
}

object SystemTimeProvider : TimeProvider {
    override fun now(): Instant = Instant.now()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}

/** Adjustable clock for tests. */
class FakeTimeProvider(var instant: Instant, var zoneId: ZoneId = ZoneId.of("UTC")) : TimeProvider {
    override fun now(): Instant = instant
    override fun zone(): ZoneId = zoneId
    fun setDate(date: LocalDate, hour: Int = 12) {
        instant = date.atTime(hour, 0).atZone(zoneId).toInstant()
    }
}

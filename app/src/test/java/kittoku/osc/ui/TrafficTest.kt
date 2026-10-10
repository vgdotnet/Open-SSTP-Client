package kittoku.osc.ui

import kittoku.osc.control.TrafficCounter
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale


class TrafficTest {
    private val russian = Locale.forLanguageTag("ru-RU")

    @Test
    fun firstSampleHasTotalsButNoRate() {
        val snapshot = TrafficSampler().sample(1_000, 5_000, 700)

        assertEquals(TrafficSnapshot(5_000, 700, 0, 0), snapshot)
    }

    @Test
    fun rateIsTheDeltaPerSecond() {
        val sampler = TrafficSampler()
        sampler.sample(1_000, 0, 0)

        val snapshot = sampler.sample(3_000, 2_000_000, 100_000)

        assertEquals(1_000_000, snapshot.receiveRate)
        assertEquals(50_000, snapshot.sendRate)
    }

    @Test
    fun counterResetGivesZeroRateInsteadOfANegativeOne() {
        val sampler = TrafficSampler()
        sampler.sample(1_000, 9_000_000, 9_000_000)

        val snapshot = sampler.sample(2_000, 1_000, 500)

        assertEquals(TrafficSnapshot(1_000, 500, 0, 0), snapshot)
    }

    @Test
    fun sameTimestampDoesNotDivideByZero() {
        val sampler = TrafficSampler()
        sampler.sample(1_000, 0, 0)

        assertEquals(0, sampler.sample(1_000, 10, 10).receiveRate)
    }

    @Test
    fun sizesPickTheUnitAndPrecision() {
        assertEquals(SizeText("512", SizeUnit.B), formatSize(512, Locale.US))
        assertEquals(SizeText("1.5", SizeUnit.KB), formatSize(1_536, Locale.US))
        assertEquals(SizeText("12.3", SizeUnit.MB), formatSize((12.3 * 1024 * 1024).toLong(), Locale.US))
        assertEquals(SizeText("250", SizeUnit.MB), formatSize(250L * 1024 * 1024, Locale.US))
        assertEquals(SizeText("2.0", SizeUnit.GB), formatSize(2L * 1024 * 1024 * 1024, Locale.US))
        assertEquals(SizeText("4096", SizeUnit.GB).unit, formatSize(4096L * 1024 * 1024 * 1024, Locale.US).unit)
    }

    @Test
    fun russianLocaleUsesADecimalComma() {
        assertEquals(SizeText("1,5", SizeUnit.MB), formatSize(1_572_864, russian))
    }

    @Test
    fun counterAccumulatesAndResets() {
        val counter = TrafficCounter()
        counter.addReceived(1_500)
        counter.addReceived(500)
        counter.addSent(40)

        assertEquals(2_000, counter.receivedBytes)
        assertEquals(40, counter.sentBytes)

        counter.reset()
        assertEquals(0, counter.receivedBytes)
        assertEquals(0, counter.sentBytes)
    }
}

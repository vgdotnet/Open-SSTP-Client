package kittoku.osc.control

import java.net.InetAddress


internal const val DNS_TYPE_A = 1
internal const val DNS_TYPE_AAAA = 28

internal data class DnsAnswer(val name: String, val type: Int, val address: ByteArray)

internal data class DnsResponse(val questions: List<String>, val answers: List<DnsAnswer>)

// Locates the DNS payload inside an IPv4/UDP packet whose source port is 53; null for anything else
internal fun dnsPayloadRange(packet: ByteArray, start: Int, size: Int): IntRange? {
    if (size < 20 + 8 || start + size > packet.size) return null
    if (packet[start].toInt() ushr 4 != 4) return null
    if (packet[start + 9].toInt() and 0xFF != 17) return null

    val flagsAndOffset = readShort(packet, start + 6)
    if (flagsAndOffset and 0x1FFF != 0 || flagsAndOffset and 0x2000 != 0) return null // fragmented

    val headerLength = (packet[start].toInt() and 0x0F) * 4
    val udpStart = start + headerLength
    if (headerLength < 20 || udpStart + 8 > start + size) return null
    if (readShort(packet, udpStart) != 53) return null

    val udpLength = readShort(packet, udpStart + 4)
    val payloadStart = udpStart + 8
    val payloadEnd = minOf(udpStart + udpLength, start + size)
    if (udpLength < 8 || payloadEnd - payloadStart < 12) return null

    return payloadStart until payloadEnd
}

// RFC 1035 response parser: questions and the A/AAAA records of the answer section, with name compression
internal fun parseDnsResponse(data: ByteArray, range: IntRange): DnsResponse? {
    val start = range.first
    val end = range.last + 1
    if (end - start < 12) return null

    val flags = readShort(data, start + 2)
    if (flags and 0x8000 == 0 || flags and 0x000F != 0) return null // not a response, or an error

    val questionCount = readShort(data, start + 4)
    val answerCount = readShort(data, start + 6)
    if (questionCount == 0 || answerCount > 64) return null

    var position = start + 12
    val questions = mutableListOf<String>()
    repeat(questionCount) {
        val (name, next) = readName(data, position, start, end) ?: return null
        if (next + 4 > end) return null
        questions.add(name)
        position = next + 4
    }

    val answers = mutableListOf<DnsAnswer>()
    repeat(answerCount) {
        val (name, next) = readName(data, position, start, end) ?: return null
        if (next + 10 > end) return null
        val type = readShort(data, next)
        val dataLength = readShort(data, next + 8)
        val dataStart = next + 10
        if (dataStart + dataLength > end) return null

        if ((type == DNS_TYPE_A && dataLength == 4) || (type == DNS_TYPE_AAAA && dataLength == 16)) {
            answers.add(DnsAnswer(name, type, data.copyOfRange(dataStart, dataStart + dataLength)))
        }

        position = dataStart + dataLength
    }

    return DnsResponse(questions, answers)
}

private fun readShort(data: ByteArray, offset: Int): Int {
    return (data[offset].toInt() and 0xFF shl 8) or (data[offset + 1].toInt() and 0xFF)
}

// Returns the lower-cased name and the position after it in the un-compressed stream
private fun readName(data: ByteArray, position: Int, messageStart: Int, end: Int): Pair<String, Int>? {
    val labels = mutableListOf<String>()
    var cursor = position
    var after = -1
    var jumps = 0

    while (true) {
        if (cursor >= end) return null
        val length = data[cursor].toInt() and 0xFF

        when {
            length == 0 -> {
                if (after < 0) after = cursor + 1
                return labels.joinToString(".") to after
            }

            length and 0xC0 == 0xC0 -> {
                if (cursor + 1 >= end || ++jumps > 32) return null
                if (after < 0) after = cursor + 2
                cursor = messageStart + (readShort(data, cursor) and 0x3FFF)
            }

            length and 0xC0 != 0 -> return null

            else -> {
                if (cursor + 1 + length > end || labels.size >= 127) return null
                labels.add(String(data, cursor + 1, length, Charsets.US_ASCII).lowercase())
                cursor += 1 + length
            }
        }
    }
}

// "ozon.ru" matches ozon.ru and any subdomain; a leading "*." or "." in the rule is tolerated
internal fun normalizeDomain(rule: String): String {
    return rule.trim().lowercase().removePrefix("*.").trimStart('.').trimEnd('.')
}

private val DOMAIN_LABEL = Regex("""[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?""")

internal fun isValidDomain(domain: String): Boolean {
    val labels = domain.split('.')
    return domain.length <= 253 && labels.size >= 2 && labels.all { DOMAIN_LABEL.matches(it) }
}

internal fun normalizeDomains(text: String): String {
    return text.lines().map { normalizeDomain(it) }.filter { it.isNotEmpty() }.distinct().joinToString("\n")
}

internal fun areValidDomains(text: String): Boolean {
    val domains = normalizeDomains(text).lines().filter { it.isNotEmpty() }
    return domains.isNotEmpty() && domains.all { isValidDomain(it) }
}

internal fun matchesDomain(name: String, domains: Collection<String>): String? {
    return domains.firstOrNull { name == it || name.endsWith(".$it") }
}

internal data class BypassDecision(val matchedDomain: String, val addresses: List<InetAddress>)

// Addresses the response resolves a bypassed domain to, minus the ones already excluded; null when nothing to do
internal fun decideBypass(response: DnsResponse, domains: Collection<String>, isExcluded: (InetAddress) -> Boolean): BypassDecision? {
    val matched = response.questions.firstNotNullOfOrNull { matchesDomain(it, domains) } ?: return null
    if (response.answers.isEmpty()) return null

    val fresh = response.answers
        .map { InetAddress.getByAddress(it.address) }
        .distinct()
        .filterNot(isExcluded)

    return if (fresh.isEmpty()) null else BypassDecision(matched, fresh)
}

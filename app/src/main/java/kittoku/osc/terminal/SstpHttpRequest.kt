package kittoku.osc.terminal


private const val SSTP_PATH = "/sra_{BA195980-CD49-458b-9E23-C84EE0ADCD75}/"

// Keenetic camouflage mode: the server only speaks SSTP when the request path carries the secret
internal fun buildSstpDuplexPostRequest(hostname: String, correlationId: String, camouflageSecret: String): String {
    val path = if (camouflageSecret.isEmpty()) SSTP_PATH else "$SSTP_PATH?$camouflageSecret"

    return arrayOf(
        "SSTP_DUPLEX_POST $path HTTP/1.1",
        "Content-Length: 18446744073709551615",
        "Host: $hostname",
        "SSTPCORRELATIONID: {$correlationId}"
    ).joinToString(separator = "\r\n", postfix = "\r\n\r\n")
}

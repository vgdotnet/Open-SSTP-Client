package kittoku.osc.ui


internal enum class ErrorKind {
    CREDENTIALS,
    AUTH_PROTOCOL,
    CERTIFICATE,
    HOST_UNREACHABLE,
    NOT_SSTP_SERVER,
    PROXY,
    SERVER_CLOSED,
    TIMEOUT,
    UNKNOWN,
}

private val AUTH_STAGES = setOf("PAP", "CHAP", "MSCHAPV2", "EAP")
private val AUTH_FAILURES = setOf("ERR_AUTHENTICATION_FAILED", "ERR_VERIFICATION_FAILED")

// The code is "<Where>: <Result>" as Controller reports it, e.g. "MSCHAPV2: ERR_AUTHENTICATION_FAILED"
internal fun classifyError(code: String): ErrorKind {
    val parts = code.split(":", limit = 2).map { it.trim() }
    if (parts.size != 2) return ErrorKind.UNKNOWN
    val (where, result) = parts

    return when {
        where in AUTH_STAGES && result in AUTH_FAILURES -> ErrorKind.CREDENTIALS
        where == "LCP_AUTH" -> ErrorKind.AUTH_PROTOCOL
        where == "CERT" || where == "CERT_PATH" -> ErrorKind.CERTIFICATE
        where == "SSL" && result == "ERR_VERIFICATION_FAILED" -> ErrorKind.CERTIFICATE
        where == "SSL" && result == "ERR_TIMEOUT" -> ErrorKind.HOST_UNREACHABLE
        where == "SSL" && result == "ERR_UNEXPECTED_MESSAGE" -> ErrorKind.NOT_SSTP_SERVER
        where == "SSTP_REQUEST" && result == "ERR_COUNT_EXHAUSTED" -> ErrorKind.NOT_SSTP_SERVER
        where == "PROXY" -> ErrorKind.PROXY
        result == "ERR_DISCONNECT_REQUESTED" || result == "ERR_ABORT_REQUESTED" -> ErrorKind.SERVER_CLOSED
        result == "ERR_TERMINATE_REQUESTED" -> ErrorKind.SERVER_CLOSED
        result == "ERR_TIMEOUT" -> ErrorKind.TIMEOUT
        else -> ErrorKind.UNKNOWN
    }
}

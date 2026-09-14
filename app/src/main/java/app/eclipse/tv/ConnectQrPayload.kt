package app.eclipse.tv

import java.util.UUID

object ConnectQrPayload {
    /** Local pairing identity. The payload deliberately contains no account credentials. */
    fun create(): String {
        val nonce = UUID.randomUUID().toString().replace("-", "")
        return "eclipsetv://connect?device=Eclipse%20TV&nonce=$nonce"
    }
}

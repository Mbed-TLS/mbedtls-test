# Readme

## The time and date in the TLS handshake

As the part of the random nonce the peers send to each other and use to produce the master secret, the peers send each other their current time and date. This part will be removed and replaced by random bytes in protocol version TLS 1.3. Most implementations send random bytes instead of the date and time with earlier protocol versions too. Mbed TLS conforms to the standards and sends the date and time in earlier protocol versions as prescribed.

## How to extract time and date with Mbed TLS?

Extracting the time and date is achievable with the current Mbed TLS interface. To do so, we need to manually step through the execution of the internal SSL state machine. When the next state is `MBEDTLS_SSL_SERVER_HELLO_DONE`, the ServerHello message has already been successfully parsed. Therefore, we can access the internal TLS handshake struct and read out the first four bytes of the ServerHello.random array which correspond to the g`mt_unix_time` value in RFC 5246 Section A.4.1. Once the value is read, continue the TLS handshake as normal.

**WARNING!** In some cases this value is not authenticated at this point yet and cannot be trusted. Only use it after the handshake has been successfully completed and discard it if the handshake fails to complete for any reason.

The sample program `ssl_client2_date_time_extract.c` uses the method explained above to extract the time and the date from a TLS handshake.

## Security considerations

The time and date in the TLS protocols serves as a part of a random nonce and is not interpreted or used in any way. The TLS protocol wasn't designed to be used as a secure time synchronisation protocol and does not provide any security guarantees for that functionality. Also, this field won't be present in protocol version TLS 1.3.

That being said, after the handshake completes, the authenticity and the integrity of the handshake messages is guaranteed (assuming that the peers were configured properly and the TLS version and ciphersuites used are secure). Therefore, after the handshake successfully completes the time and date field can be trusted as well.

If the time and date value is used too early, then an adversary can for example set back the clock of the device and prevent the system from recovering from private key exposures or can use old key material to launch a man-in-the-middle attack.

/*
 *  Fuzz target for SSL/TLS handshake
 *
 *  Copyright (C) 2017, ARM Limited, All Rights Reserved
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may
 *  not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *  This file is part of mbed TLS (https://tls.mbed.org)
 */
#include <stdio.h>
#include <string.h>

#include <mbedtls/ctr_drbg.h>
#include <mbedtls/entropy.h>
#include <mbedtls/ssl.h>
#include <mbedtls/debug.h>
#include <mbedtls/ssl_internal.h>

#define BUFFER_SIZE 16384
typedef struct
{
    unsigned char buf[BUFFER_SIZE];
    size_t avail;
} message_buffer;

static message_buffer c2s; /* Client-to-Server */
static message_buffer s2c; /* Server-to-Client */

/*
 * Zero-initialise message buffers
 */
void buffer_init( message_buffer *mbuf )
{
    memset( (unsigned char*) mbuf, 0, sizeof( message_buffer ) );
}

/*
 * Read size bytes from mbuf into dst
 */
int buffer_read( message_buffer* const mbuf, unsigned char *dst, unsigned long size )
{
    size_t remaining;

    if( mbuf->avail == 0 )
        return( MBEDTLS_ERR_SSL_WANT_READ );

    if( size > mbuf->avail )
    {
        size      = mbuf->avail;
        remaining = 0;
    }
    else
    {
        remaining = mbuf->avail - size;
    }

    memcpy( dst, mbuf->buf, size );
    memmove( mbuf->buf, mbuf->buf + size, remaining );

    mbuf->avail = remaining;

    return( size );
}

/*
 * Write size bytes from src into mbuf
 */
int buffer_write( message_buffer* const mbuf, const unsigned char *src, size_t size )
{
    const size_t remaining = sizeof( mbuf->buf ) - mbuf->avail;

    if( remaining == 0 )
        return( MBEDTLS_ERR_SSL_WANT_WRITE );

    if( size > remaining )
        size = remaining;

    memcpy( mbuf->buf + mbuf->avail, src, size );
    mbuf->avail += size;

    return( size );
}

/*
 * Write size bytes from src into the server to client buffer
 */
int buffer_write_srv( void *data, const unsigned char *src, size_t size )
{
    return buffer_write( &s2c, src, size );
}

/*
 * Write size bytes from src into the client to server buffer
 */
int buffer_write_cli( void *data, const unsigned char *src, size_t size )
{
    return buffer_write( &c2s, src, size );
}

/*
 * Read size bytes from the client to server buffer into dst
 */
int buffer_read_srv( void *data, unsigned char *dst, unsigned long size )
{
    return buffer_read( &c2s, dst, size );
}

/*
 * Read size bytes from the server to client buffer into dst
 */
int buffer_read_cli( void *data, unsigned char *dst, unsigned long size )
{
    return buffer_read( &s2c, dst, size );
}

/*
 * Send debug output straight (FILE *) ctx
 */
static void my_debug( void *ctx, int level,
                      const char *file, int line,
                      const char *str )
{
    ((void) level);

    fprintf( (FILE *) ctx, "%s:%04d: %s", file, line, str );
    fflush(  (FILE *) ctx  );
}

/* enum to keep track of which side of the handshake we're fuzzing */

enum
{
    FUZZ_CLIENT_OUTPUT,
    FUZZ_SERVER_OUTPUT,
};

int LLVMFuzzerTestOneInput( const uint8_t *Data, size_t Size )
{
    int ret;
    int written;
    int keep_fuzzing = -1;

    mbedtls_ssl_context ssl_srv,  ssl_cli;
    mbedtls_ssl_config  conf_srv, conf_cli;

    mbedtls_entropy_context  entropy;
    mbedtls_ctr_drbg_context ctr_drbg;

    int fuzzing_state;

    if( Size <= sizeof( int ) )
        return 0;

    /* use the first int of Data to determine which part of the handshake to fuzz */
    fuzzing_state = *( int * ) Data;
    Data += sizeof( int );
    Size -= sizeof( int );

    /* don't bother if we're not going to be fuzzing anything */
    if( fuzzing_state > MBEDTLS_SSL_SERVER_HELLO_VERIFY_REQUEST_SENT )
        return 0;

    mbedtls_ssl_init( &ssl_srv );
    mbedtls_ssl_init( &ssl_cli );
    mbedtls_ssl_config_init( &conf_cli );
    mbedtls_ssl_config_init( &conf_srv );
    mbedtls_ctr_drbg_init( &ctr_drbg );

    buffer_init( &c2s );
    buffer_init( &s2c );

    mbedtls_entropy_init( &entropy );
    if( ( ret = mbedtls_ctr_drbg_seed( &ctr_drbg, mbedtls_entropy_func,
                                       &entropy, (unsigned char*) "test",
                                       4 ) ) != 0 )
    {
        printf( "mbedtls_crt_drbg_seed failed\n" );
        goto exit;
    }

    /*
     * Setup client config
     */

    if( ( ret = mbedtls_ssl_config_defaults( &conf_cli,
                 MBEDTLS_SSL_IS_CLIENT, MBEDTLS_SSL_TRANSPORT_STREAM,
                 MBEDTLS_SSL_PRESET_DEFAULT ) ) != 0 )
    {
        printf( "client config failed\n" );
        goto exit;
    }

    mbedtls_ssl_conf_authmode( &conf_cli, MBEDTLS_SSL_VERIFY_NONE );
    mbedtls_ssl_conf_psk( &conf_cli, (const unsigned char*) "42", 2,
                          (const unsigned char*) "galaxy", 6 );
    mbedtls_ssl_conf_dbg( &conf_cli, my_debug, stdout );
    mbedtls_ssl_conf_rng( &conf_cli, mbedtls_ctr_drbg_random, &ctr_drbg );

    /*
     * Setup server config
     */

    if( ( mbedtls_ssl_config_defaults( &conf_srv,
                 MBEDTLS_SSL_IS_SERVER, MBEDTLS_SSL_TRANSPORT_STREAM,
                 MBEDTLS_SSL_PRESET_DEFAULT ) ) != 0 )
    {
        printf( "server config failed\n" );
        goto exit;
    }

    mbedtls_ssl_conf_authmode( &conf_srv, MBEDTLS_SSL_VERIFY_NONE );
    mbedtls_ssl_conf_psk( &conf_srv, (const unsigned char*) "42", 2,
                          (const unsigned char*) "galaxy", 6 );
    mbedtls_ssl_conf_dbg( &conf_srv, my_debug, stdout );
    mbedtls_ssl_conf_rng( &conf_srv, mbedtls_ctr_drbg_random, &ctr_drbg );

    if( ( ret = mbedtls_ssl_setup( &ssl_srv, &conf_srv ) ) != 0 )
    {
        printf( "mbedtls_ssl_setup for server failed\n" );
        goto exit;
    }

    if( ( ret = mbedtls_ssl_setup( &ssl_cli, &conf_cli ) ) != 0 )
    {
        printf( "mbedtls_ssl_setup for client failed\n" );
        goto exit;
    }

    mbedtls_ssl_set_bio( &ssl_srv, NULL, buffer_write_srv, buffer_read_srv, NULL );
    mbedtls_ssl_set_bio( &ssl_cli, NULL, buffer_write_cli, buffer_read_cli, NULL );

    /* Run client and server; when the client matches the fuzzing state, start
     * using the fuzzer data for the client's responses. Similarly for the
     * server. Use `mbedtls_ssl_handshake_step` to advance through the
     * handshake protocol.
     */

    while( ssl_srv.state != MBEDTLS_SSL_HANDSHAKE_OVER && ssl_cli.state != MBEDTLS_SSL_HANDSHAKE_OVER )
    {
        if ( Size <= 0 )
        {
            ret = 0;
            goto exit;
        }

        if( ssl_cli.state == fuzzing_state || keep_fuzzing == FUZZ_CLIENT_OUTPUT )
        {
            written = buffer_write_cli( NULL, Data, Size );
            Data += written;
            Size -= written;
            keep_fuzzing = FUZZ_CLIENT_OUTPUT;
            fuzzing_state = -1;
        }
        else
        {
            ret = mbedtls_ssl_handshake_step( &ssl_cli );
            if( ret != 0 && ret != MBEDTLS_ERR_SSL_WANT_READ && ret != MBEDTLS_ERR_SSL_WANT_WRITE )
            {
                ret = 0;
                goto exit;
            }
        }

        if( ssl_srv.state == fuzzing_state || keep_fuzzing == FUZZ_SERVER_OUTPUT )
        {
            written = buffer_write_srv( NULL, Data, Size );
            Data += written;
            Size -= written;
            keep_fuzzing = FUZZ_SERVER_OUTPUT;
            fuzzing_state = -1;
        }
        else
        {
            ret = mbedtls_ssl_handshake_step( &ssl_srv );
            if( ret != 0 && ret != MBEDTLS_ERR_SSL_WANT_READ && ret != MBEDTLS_ERR_SSL_WANT_WRITE )
            {
                ret = 0;
                goto exit;
            }
        }
    }

exit:

    mbedtls_ssl_config_free( &conf_srv );
    mbedtls_ssl_config_free( &conf_cli );
    mbedtls_ssl_free( &ssl_srv );
    mbedtls_ssl_free( &ssl_cli );
    mbedtls_ctr_drbg_free( &ctr_drbg );
    mbedtls_entropy_free( &entropy );

    return ret;
}

/*
 *  Fuzz target for certificate verification
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
#if !defined(MBEDTLS_CONFIG_FILE)
#include "mbedtls/config.h"
#else
#include MBEDTLS_CONFIG_FILE
#endif

#include <stdlib.h>
#include <string.h>
#include <assert.h>

#include <mbedtls/x509.h>
#include <mbedtls/x509_crt.h>

#include "config.h"

#if defined(AB_TESTING)
int b_mbedtls_x509_crt_verify( mbedtls_x509_crt *,
                     mbedtls_x509_crt *,
                     mbedtls_x509_crl *,
                     const char *, uint32_t *,
                     int (*)(void *, mbedtls_x509_crt *, int, uint32_t *),
                     void *);
#endif

/*
 * Load root_ca.crt as the trusted root certificate
 */
int load_root_cert( mbedtls_x509_crt *root_ca )
{
#if defined(MBEDTLS_FS_IO) && defined(MBEDTLS_X509_CRT_PARSE_C)
    mbedtls_x509_crt_init( root_ca );

    if ( mbedtls_x509_crt_parse_file( root_ca, "/fuzzing/root_ca.crt" ) != 0 )
        goto fail;

    return 0;

fail:
    mbedtls_x509_crt_free( root_ca );
#endif

    return -1;
}

/*
 * Rewrite the issuer of child to match the subject of parent
 */
static int rewrite_issuer( mbedtls_x509_crt *child, const mbedtls_x509_crt *parent )
{
#if defined(MBEDTLS_X509_USE_C)
    unsigned char *p;
    mbedtls_x509_name *name_cur, *name_prv;
    size_t len;

    child->issuer_raw.p = parent->subject_raw.p;
    child->issuer_raw.len = parent->subject_raw.len;

    p = child->issuer_raw.p;

    /* free issuer names */
    name_cur = child->issuer.next;
    while( name_cur != NULL )
    {
        name_prv = name_cur;
        name_cur = name_cur->next;
        free( name_prv );
    }

    if( mbedtls_asn1_get_tag( &p, p + child->issuer_raw.len, &len,
                MBEDTLS_ASN1_CONSTRUCTED | MBEDTLS_ASN1_SEQUENCE ) != 0 )
    {
        printf("tag mismatch\n");
        return -1;
    }

    if( len && mbedtls_x509_get_name( &p, p + len, &child->issuer ) != 0 )
        return -1;

    return 0;
#else
    return -1;
#endif
}

/*
 * Rewrites issuer fields in chain such that
 * - the issuer of certificate i matches the subject of certificate i+1
 * - the issuer of the final certificate matches the subject of root_ca
 */
static int rewrite_chain( mbedtls_x509_crt *chain, const mbedtls_x509_crt *root_ca )
{
#if defined(MBEDTLS_X509_CRT_PARSE_C)
    mbedtls_x509_crt *child, *parent;
    child = chain;

    while( ( parent = child->next ) )
    {
        if( rewrite_issuer( child, parent ) != 0)
        {
            printf( "failed to rewrite issuer\n" );
            return -1;
        }

        child = parent;
    }

    if( rewrite_issuer( child, root_ca ) != 0)
    {
        printf( "failed to rewrite issuer\n" );
        return -1;
    }

    return 0;
#else
    return -1;
#endif
}

/*
 * Allocate a new buffer containing data with a terminating null
 */
static uint8_t *alloc_with_terminating_null( const uint8_t *data, size_t *size )
{
    uint8_t *ret = malloc( *size + 1 );
    if( !ret )
    {
        printf( "allocation failed\n" );
        return NULL;
    }
    memcpy( ret, data, *size );
    ret[ *size ] = '\0';

    *size += 1;
    return ret;
}

/*
 * Fuzz target entry into mbedtls_x509_crt_verify
 */
int LLVMFuzzerTestOneInput( const uint8_t *data, size_t size )
{
#if defined(MBEDTLS_X509_CRT_PARSE_C) && defined(MBEDTLS_PEM_PARSE_C) && defined(MBEDTLS_FS_IO)
    static mbedtls_x509_crt root_ca;

    mbedtls_x509_crt crt;
    int result_a, result_b;
    uint32_t flags_a, flags_b;
    uint8_t *modified_data;
    size_t modified_size = size;

    /* PEM files need to end in '\0' :( */
    modified_data = alloc_with_terminating_null( data, &modified_size );
    if( !modified_data )
        return 1;

    /* read root cert only once */
    static int init = 1;
    if( init == 1 )
        init = load_root_cert( &root_ca );

    if( init != 0 )
    {
        printf( "load_root_cert failed.\n" );
        return 1;
    }

    /* parse Data, might be PEM or DER encoded */
    mbedtls_x509_crt_init( &crt);
    if( mbedtls_x509_crt_parse( &crt, modified_data, modified_size ) != 0 )
        goto exit;

    if( rewrite_chain( &crt, &root_ca ) != 0 )
        goto exit;

    mbedtls_x509_crl *ca_crl = NULL;
    const char *cn = NULL;
    int (*f_vrfy)(void *, mbedtls_x509_crt *, int, uint32_t *) = NULL;
    void *p_vrfy = NULL;

    result_a = mbedtls_x509_crt_verify( &crt, &root_ca, ca_crl, cn, &flags_a, f_vrfy, p_vrfy );

#ifdef AB_TESTING
    result_b = b_mbedtls_x509_crt_verify( &crt, &root_ca, ca_crl, cn, &flags_b, f_vrfy, p_vrfy );

    if( result_a != result_b )
    {
        printf( "%i != %i\n" , result_a, result_b );
        assert( 0 );
    }

    if ( result_a == MBEDTLS_ERR_X509_CERT_VERIFY_FAILED )
    {
        if( flags_a != flags_b )
        {
            char buf[1024];
            mbedtls_x509_crt_verify_info( buf, 1024, "a: ", flags_a );
            printf( "%s", buf );
            mbedtls_x509_crt_verify_info( buf, 1024, "b: ", flags_b );
            printf( "%s", buf );
            assert( 0 );
        }
    }
#endif

exit:
    /* We can safely cast away const here, since Data has been reallocated and now belongs to us */
    free( modified_data );
    mbedtls_x509_crt_free( &crt );
    return 0;
#else
    return 1;
#endif
}


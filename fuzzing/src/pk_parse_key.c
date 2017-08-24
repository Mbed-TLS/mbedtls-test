/*
 *  Fuzz target for private key parsing
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

#include <stdint.h>
#include <stddef.h>

#include "mbedtls/pk.h"

/*
 * Fuzz target entry into mbedtls_pk_parse_key
 */
int LLVMFuzzerTestOneInput( const uint8_t *Data, size_t Size )
{
#if defined(MBEDTLS_PK_PARSE_C)
    mbedtls_pk_context ctx;
    mbedtls_pk_init( &ctx );

    mbedtls_pk_parse_key( &ctx, Data, Size, NULL, 0 );

    mbedtls_pk_free( &ctx );
    return( 0 );
#else
    return( 1 );
#endif
}

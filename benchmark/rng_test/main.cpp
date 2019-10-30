#include "mbed.h"

#include "inc/ecc.h"
#include "inc/ecc_dh.h"
#include "inc/ecc_dsa.h"

#include <stdlib.h>

Timer t;

int rng_function(uint8_t *dest, unsigned int size)
{
    unsigned i;

    for (i = 0; i < size; i++)
        dest[i] = rand();

    return 1;
}

int main()
{
    printf("RNG test\n");
	uint8_t private1[NUM_ECC_BYTES] = {0};
	uint8_t public1[2*NUM_ECC_BYTES] = {0};
    uint8_t private2[NUM_ECC_BYTES] = {0};
    uint8_t public2[2*NUM_ECC_BYTES] = {0};
    uint8_t secret[NUM_ECC_BYTES] = {0};
	uint8_t hash[NUM_ECC_BYTES];
	unsigned int hash_words[NUM_ECC_WORDS];
    uint8_t sig[2*NUM_ECC_BYTES];

    const struct uECC_Curve_t * curve = uECC_secp256r1();
    uECC_set_rng(&rng_function);
    printf("Key generation\n");
    t.start();

    if (!uECC_make_key(public1, private1, curve)) {
        printf("uECC_make_key() failed\n");
    }
    t.stop();
    printf("Time taken: %d milliseconds\n", t.read_ms());
    printf("\n");

    t.reset();
    printf("Signing\n");

    //Generate message to sign
    uECC_generate_random_int(hash_words, curve->n, BITS_TO_WORDS(curve->num_n_bits));
    uECC_vli_nativeToBytes(hash, NUM_ECC_BYTES, hash_words);

    t.start();

    if(!uECC_sign(private1, hash, sizeof(hash), sig, curve)) {
        printf("uECC_sign() failed\n");
    }

    t.stop();
    printf("Time taken: %d milliseconds\n", t.read_ms());

    printf("\n");

    t.reset();
    printf("Verify signature\n");

    t.start();

    if(!uECC_verify(public1, hash, sizeof(hash), sig, curve)) {
        printf("uECC_verify() failed\n");
    }

    t.stop();
    printf("Time taken: %d milliseconds\n", t.read_ms());
    printf("\n");

    t.reset();
    printf("Generate a second keypair (for shared secret)\n");
    t.start();

    if (!uECC_make_key(public2, private2, curve)) {
        printf("uECC_make_key() failed\n");
    }
    t.stop();
    printf("Time taken: %d milliseconds\n", t.read_ms());
    printf("\n");

    t.reset();
    printf("Generate a shared secret\n");
    t.start();

    if (!uECC_shared_secret(public2, private1, secret, curve)) {
        printf("uECC_shared_secret() failed\n");
    }
    t.stop();
    printf("Time taken: %d milliseconds\n", t.read_ms());
    printf("\n");


}


/**
 *
 *      File Name -     RSAKeyPairGenerator.java
 *      Created By -    Pujie Wang
 *      Brief -
 *
 *          Generate public key and private key using RSA algorithm
 *
 */

package server;

import java.security.*;

public class RSAKeyPairGenerator {

    /** The private key  */
    private PrivateKey privateKey;
    /** The public key */
    private PublicKey publicKey;

    public RSAKeyPairGenerator() throws NoSuchAlgorithmException {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(1024);
        KeyPair pair = keyGen.generateKeyPair();
        this.privateKey = pair.getPrivate();
        this.publicKey = pair.getPublic();
    }
    /** Get the private key from the key pair */
    public PrivateKey getPrivateKey() {
        return privateKey;
    }
    /** Get the public key from the key pair */
    public PublicKey getPublicKey() {
        return publicKey;
    }

}

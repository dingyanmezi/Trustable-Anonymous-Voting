/**
 *
 *      File Name -     RSAUtil.java
 *      Created By -    Pujie Wang
 *      Brief -
 *
 *          Encryption and Decryption mechanism used in the Proof-of-Authority phase
 *
 */

package lib;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.security.*;
import java.util.Base64;

public class RSAUtil {
    /** encrypt data using RSA */
    public static byte[] encrypt(String data, Key publicKey) throws
            BadPaddingException, IllegalBlockSizeException, InvalidKeyException,
            NoSuchPaddingException, NoSuchAlgorithmException {
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        return cipher.doFinal(data.getBytes());
    }
    /** decrypt the data using RSA - HELPER FUNCTION */
    public static String decrypt(byte[] data, Key privateKey) throws NoSuchPaddingException,
            NoSuchAlgorithmException, InvalidKeyException,
            BadPaddingException, IllegalBlockSizeException {
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        return new String(cipher.doFinal(data));
    }
    /** decrypt the data using RSA*/
    public static String decrypt(String data, Key base64PrivateKey) throws IllegalBlockSizeException,
            InvalidKeyException, BadPaddingException, NoSuchAlgorithmException, NoSuchPaddingException {
        return decrypt(Base64.getDecoder().decode(data), base64PrivateKey);
    }
    /** encrypt the data using AES */
    public static byte[] encrypt_for_AES(String data, Key sessionKey)throws BadPaddingException,
            IllegalBlockSizeException, InvalidKeyException, NoSuchPaddingException, NoSuchAlgorithmException {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, sessionKey);
        return cipher.doFinal(data.getBytes());
    }
    /** decrypt the data using AES */
    public static String decrypt_for_AES(byte[] data, Key sessionKey) throws IllegalBlockSizeException,
            InvalidKeyException, BadPaddingException, NoSuchAlgorithmException, NoSuchPaddingException {
        Cipher cipher = Cipher.getInstance("AES");   //
        cipher.init(Cipher.DECRYPT_MODE, sessionKey);
        return new String(cipher.doFinal(data));
    }
    /** decrypt the data using AES - HELPER FUNCTION */
    public static String decrypt_for_AES(String data, Key sessionKey) throws IllegalBlockSizeException,
            InvalidKeyException, BadPaddingException, NoSuchAlgorithmException, NoSuchPaddingException {
        return decrypt_for_AES(Base64.getDecoder().decode(data), sessionKey);
    }

}
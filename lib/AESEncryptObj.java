/**
 *
 *      File Name -     AESEncryptObj.java
 *      Created By -    Pujie Wang
 *      Brief -
 *
 *          The object created is used to extract fields of the json object related to AES encryption information.
 *
 */

package lib;

public class AESEncryptObj {
    /** chain id of the blockchain  */
    private int chain_id;
    /** which voter */
    private String user_name;
    /** candidate name the voter votes for in an encrypted manner */
    private String encrypted_vote;

    public AESEncryptObj(int id, String username, String key){
        this.chain_id = id;
        this.user_name = username;
        this.encrypted_vote = key;
    }
    /** get the chain id of the blockchain */
    public int getChainId(){
        return this.chain_id;
    }
    /** get voter name of the vote */
    public String getUserName(){
        return this.user_name;
    }
    /** get the candidate name of the vote */
    public String getEncryptedVote(){
        return this.encrypted_vote;
    }


}

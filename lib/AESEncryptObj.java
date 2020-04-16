package lib;

public class AESEncryptObj {
    private int chain_id;
    private String user_name;
    private String encrypted_vote;

    public AESEncryptObj(int id, String username, String key){
        this.chain_id = id;
        this.user_name = username;
        this.encrypted_vote = key;
    }

    public int getChainId(){
        return this.chain_id;

    }

    public String getUserName(){
        return this.user_name;
    }

    public String getEncryptedVote(){
        return this.encrypted_vote;
    }


}

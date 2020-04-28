/**
 *
 *      File Name -     Blockchain.java
 *      Created By -    Pujie Wang
 *      Brief -
 *
 *          The blockchain structure that stores blocks. Contains a genesis block by default
 *
 */

package blockchain;

import message.Block;
import java.util.ArrayList;
import java.util.List;

public class Blockchain {
    /** The initial block that starts the blockchain*/
    private static Block genesis = new Block();
    /** determins the blockchain type - Identity or voter */
    private int chain_id;
    /** the list that stores the blocks as a chain */
    private List<Block> blocks;

    public Blockchain(int chain_id){
        this.chain_id = chain_id;
        this.blocks = new ArrayList<>();
        this.blocks.add(genesis);

    }
    /** get how many blocks are there in the chain */
    public int getLength(){
        return blocks.size();
    }
    /** get the blocks */
    public List<Block> getBlocks(){
        return this.blocks;
    }
    /** get the previous hash of the "About-to-add" block. What it really gets is the "previousHash"
     *  value of the CURRENT last block.
     * */
    public String getPrevHash(){
        return this.blocks.get(blocks.size()-1).getHash();
    }
    /** As the name suggests it gets the previousHash value of the last block currently */
    public String prevHashOfLastBlock(){
        return this.blocks.get(blocks.size()-1).getPreviousHash();
    }
    /** add a new block to the end of the list */
    public void addBlock(Block blk){
        this.blocks.add(blk);
    }
    /** set the blocks  */
    public void setBlocks(List<Block> list){
        this.blocks = list;
    }

}

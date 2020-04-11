package blockchain;

import message.Block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;

public class Blockchain {
    private static Block genesis = new Block();
    private int chain_id;
    private List<Block> blocks;

    public Blockchain(int chain_id){
        this.chain_id = chain_id;
        this.blocks = new ArrayList<>();
        this.blocks.add(genesis);

    }

    public int getLength(){
        return blocks.size();
    }

    public List<Block> getBlocks(){
        return this.blocks;
    }

    public String getPrevHash(){
        return this.blocks.get(blocks.size()-1).getHash();
    }

    public String prevHashOfLastBlock(){
        return this.blocks.get(blocks.size()-1).getPreviousHash();
    }

    public void addBlock(Block blk){
        this.blocks.add(blk);
    }

    public void setBlocks(List<Block> list){
        this.blocks = list;
    }

    public static void main(String[] args) {

    }
}
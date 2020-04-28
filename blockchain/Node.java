/**
 *
 *      File Name -     Node.java
 *      Created By -    Pujie Wang
 *      Brief -
 *
 *          The blockchain system consists of multiple nodes and serves as a decentralized,
 *          distributed and public ledger of transactions.
 *          Each node has a local copy of the blockchains, which are growing lists of records, called blocks.
 *          Each node is able to get blockchain that it holds, mine a new block and add a new block, broadcast
 *          other node peers and make self go to sleep.
 *
 */

package blockchain;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import message.*;
import test.Config;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Node{
    // APIs
    protected static final String GET_CHAIN_URI = "/getchain";
    protected static final String MINE_BLOCK_URI = "/mineblock";
    protected static final String ADD_BLOCK_URI = "/addblock";
    protected static final String BROADCAST_URI = "/broadcast";
    protected static final String SLEEP_URI = "/sleep";
    // IDs
    protected static final int IDENTITY_ID = 1;
    protected static final int VOTECHAIN_ID = 2;
    // Broadcast types
    protected static final String BROADCAST_PRE = "PRECOMMIT";
    protected static final String BROADCAST_COMMIT = "COMMIT";
<<<<<<< HEAD

=======
    /** the index in the port list  */
>>>>>>> more comments added
    private static int NODE_ID;
    /** ports of the nodes  */
    private static String[] PORT_LIST;
    /** host name of the port */
    private static final String HOSTNAME = "127.0.0.1";
    /** all the node ports */
    private static int[] NODE_PORTS = Config.node_ports;
    /** node that are not into sleep*/
    public static List<Integer> live_ports = new ArrayList<>();
    /** the server that this node runs */
    protected HttpServer server;
    /** the gson object to parse the json */
    protected Gson gson;
    /** identity chain for authentication*/
    private Blockchain firstBlockchain;
    /** votes chain */
    private Blockchain secondBlockchain;
    /** the actual port that this node runs on */
    private int port;

    public Node() throws IOException {
        // get the actual port that his node runs on
        this.port = Integer.parseInt(PORT_LIST[NODE_ID]);
        this.server =  HttpServer.create(new InetSocketAddress(this.port), 0);
        this.server.setExecutor(Executors.newCachedThreadPool());
        this.gson = new Gson();
        this.firstBlockchain = new Blockchain(IDENTITY_ID);
        this.secondBlockchain = new Blockchain(VOTECHAIN_ID);
    }

    /** add nodes to live nodes list and start the server*/
    void start(){
        for (int each : NODE_PORTS){
            live_ports.add(each);
        }
        this.startSkeletons();
        this.server.start();

    }
    /** contains all the API handlers */
    private void startSkeletons(){
        this.getBlockChain();
        this.mineBlock();
        this.addBlock();
        this.broadcastBlock();
        this.sleep();
    }

    /** This call is sent from users/peers to request a copy of the blockchain */
    private void getBlockChain(){
        this.server.createContext(GET_CHAIN_URI, (exchange -> {
            String respText = "";
            int returnCode = 200;
            if ("POST".equals(exchange.getRequestMethod())) {
                InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "utf-8");
                GetChainRequest gcr = gson.fromJson(isr, GetChainRequest.class);
                /** 1 for identity chain, 2 for vote chain */
                int id = gcr.getChainId();
                Blockchain candi = null;
                switch (id){
                    case IDENTITY_ID:
                        candi = this.firstBlockchain;
                        break;
                    case VOTECHAIN_ID:
                        candi = this.secondBlockchain;
                        break;
                    default:
                }
                // get information regarding chain and return 
                int length = candi.getLength();
                List<Block> blocks = candi.getBlocks();
                GetChainReply reply = new GetChainReply(id, length, blocks);
                respText = gson.toJson(reply);
                this.generateResponseAndClose(exchange, respText, returnCode);
            }
        }));
    }
    /** create a new block given the provided information. */
    private void mineBlock(){
        this.server.createContext(MINE_BLOCK_URI, (exchange -> {
            String respText = "";
            int returnCode = 200;
            if ("POST".equals(exchange.getRequestMethod())) {
                InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "utf-8");
                MineBlockRequest mbr = gson.fromJson(isr, MineBlockRequest.class);
                int chainId = mbr.getChainId();
                Blockchain candi = null;
                switch (chainId){
                    case IDENTITY_ID:
                        candi = this.firstBlockchain;
                        break;
                    case VOTECHAIN_ID:
                        candi = this.secondBlockchain;
                        break;
                    default:
                }
                // sync the chain for this node before mining
                try {
                    syncChain(chainId, candi);
                } catch (Exception e) {
                    e.printStackTrace();
                }
<<<<<<< HEAD
=======
                // get the data preparing for the mining process 
>>>>>>> more comments added
                Map<String, String> data = mbr.getData();
                String prev_hash = candi.getPrevHash();
                long timestamp = System.currentTimeMillis();
                long nonce = 0;
                Block block = new Block(candi.getLength(), data, timestamp, nonce, prev_hash, null);
                String hash = Block.computeHash(block);
                // the 5 difficulty only applies to Identity chain. Voter chain does not any diff!!!
                if (chainId == IDENTITY_ID){
                    while (!hash.startsWith("00000")){
                        // change the nonce and update the current time
                        nonce++;
                        timestamp = System.currentTimeMillis();
                        block = new Block(candi.getLength(), data, timestamp, nonce, prev_hash, null);
                        hash = Block.computeHash(block);
                    }
                }
                // The valid hash is formed. Set it up.
                block.setHash(hash);
                BlockReply br = new BlockReply(chainId, block);
                respText = gson.toJson(br);
                this.generateResponseAndClose(exchange, respText, returnCode);
            }
        }));
    }
    /** Add a new block to specific blockchain and broadcast to peers */
    private void addBlock(){
        this.server.createContext(ADD_BLOCK_URI, (exchange -> {
            String respText = "";
            int returnCode = 200;
            if ("POST".equals(exchange.getRequestMethod())) {
                StatusReply sr = null;
                InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "utf-8");
                AddBlockRequest abr = gson.fromJson(isr, AddBlockRequest.class);
                // 如果sleep了说明port已经从live_ports中移除了 所以直接返回false！
                if (!live_ports.contains(port)){
                    sr = new StatusReply(false, "already slept !");
                    respText = gson.toJson(sr);
                    this.generateResponseAndClose(exchange, respText, returnCode);
                    return;
                }
                int chainId = abr.getChainId();
                Blockchain candi = null;
                switch (chainId){
                    case IDENTITY_ID:
                        candi = this.firstBlockchain;
                        break;
                    case VOTECHAIN_ID:
                        candi = this.secondBlockchain;
                        break;
                    default:
                }
                Block blk = abr.getBlock();
                // looking for duplication - bug solved !
                if (candi.prevHashOfLastBlock().equals(blk.getPreviousHash())){
                    sr = new StatusReply(false, "same blk already existed");
                    respText = gson.toJson(sr);
                    this.generateResponseAndClose(exchange, respText, returnCode);
                    return;
                }
                // count to see if the success are up to 2/3
                List<Future<Boolean>> allFutures = new ArrayList<>();
                int successCount = 0;
                // make the broadcast go parallel
                ExecutorService executor = Executors.newFixedThreadPool(NODE_PORTS.length);
                for (int each : NODE_PORTS){
                    // making sure the current node does not broadcast to ITSELF!!
                    if (each != this.port){
                        Future<Boolean> future = executor.submit(() -> {
                            HttpResponse<String> response = null;
                            try {
                                response = getResponse(BROADCAST_URI,
                                        new BroadcastRequest(chainId, BROADCAST_PRE, blk), each);
                            } catch (Exception e){
                                e.printStackTrace();
                            }
                            boolean success = gson.fromJson(response.body(), StatusReply.class).getSuccess();
                            return success;
                        });
                        allFutures.add(future);
                    }
                }
                // count the success
                for (Future<Boolean> each : allFutures){
                    try {
                        if (each.get().equals(true)) successCount++;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                executor.shutdown();

                // check whether larger than 2/3 for consensus
                executor = Executors.newFixedThreadPool(NODE_PORTS.length);
<<<<<<< HEAD
                // if up to 2/3, BROADCAST
=======
                // if up to 2/3, add the block and BROADCAST other nodes to commit
>>>>>>> more comments added
                if (successCount >= (int) Math.ceil((NODE_PORTS.length - 1) * 2.0 / 3)){
                    candi.addBlock(blk);
                    for (int each : NODE_PORTS){
                        if (each != this.port){
                            executor.submit(() -> {
                                try {
                                    HttpResponse<String> response = getResponse(BROADCAST_URI,
                                            new BroadcastRequest(chainId, BROADCAST_COMMIT, blk), each);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            });
                        }
                    }
                    sr = new StatusReply(true, "approval by 2/3");
                }else{
                    sr = new StatusReply(false, "not up to 2/3 approval");
                }
                executor.shutdown();
                respText = gson.toJson(sr);
                this.generateResponseAndClose(exchange, respText, returnCode);
            }
        }));
    }
    /** broadcast the blockchain to all other nodes. Two phases : PRECOMMIT and COMMIT */
    private void broadcastBlock(){
        this.server.createContext(BROADCAST_URI, (exchange -> {
            String respText = "";
            int returnCode = 200;
            if ("POST".equals(exchange.getRequestMethod())) {
                BroadcastRequest br = null;
                StatusReply sr = null;
                InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "utf-8");
                br = gson.fromJson(isr, BroadcastRequest.class);
                // if a node is already slept, return false
                if (!live_ports.contains(port)){
                    sr = new StatusReply(false, "already slept !");
                    respText = gson.toJson(sr);
                    this.generateResponseAndClose(exchange, respText, returnCode);
                    return;
                }
                int chainId = br.getChainId();
                Blockchain candi = null;
                switch (chainId){
                    case IDENTITY_ID:
                        candi = this.firstBlockchain;
                        break;
                    case VOTECHAIN_ID:
                        candi = this.secondBlockchain;
                        break;
                    default:
                }
                String requestType = br.getRequestType();
                Block blk = br.getBlock();
                String hash = blk.getHash();
                String prevHash = blk.getPreviousHash();
                // respond in terms of the Broadcast types
                switch (requestType){
                    case BROADCAST_COMMIT:
                        candi.addBlock(blk);
                        sr = new StatusReply(true, "commit - able ");
                        break;
                    case BROADCAST_PRE:
                        // check if hash is correct based on block information and check if previous hash is indeed
                        if (Block.computeHash(blk).equals(hash)
                                && candi.getPrevHash().equals(prevHash)){
                            sr = new StatusReply(true, "precommit conditions met");
                        }else{
                            sr = new StatusReply(false, "one of the condition not met during precommit");
                        }
                        break;
                    default:
                }
                respText = gson.toJson(sr);
                this.generateResponseAndClose(exchange ,respText, returnCode);
            }
        }));
    }

    /** put a node into sleep and awake after timeout. Sync after being awake */
    private void sleep(){
        this.server.createContext(SLEEP_URI, (exchange -> {
            String respText = "";
            int returnCode = 200;
            if ("POST".equals(exchange.getRequestMethod())) {
                InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "utf-8");
                SleepRequest sr = gson.fromJson(isr, SleepRequest.class);
                int timeout = sr.getTimeout() * 1000;
                // When received, the node should reply immediately and then go to sleep.
                try {
                    StatusReply reply = new StatusReply(true, "");
                    respText = gson.toJson(reply);
                    this.generateResponseAndClose(exchange ,respText, returnCode);
                    live_ports.remove((Integer) port);
                    Thread.sleep(timeout);
                    // after timeout you need to add it back FOR SURE LOL
                    live_ports.add(port);
                    /** nodes need to sync blockchain after waking up*/
                     syncChain(IDENTITY_ID, this.firstBlockchain);
                     syncChain(VOTECHAIN_ID, this.secondBlockchain);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }));
    }

    /** respond with response text and return code for each HTTP request */
    private void generateResponseAndClose(HttpExchange exchange, String respText, int returnCode) throws IOException {
        exchange.sendResponseHeaders(returnCode, respText.getBytes().length);
        OutputStream output = exchange.getResponseBody();
        output.write(respText.getBytes());
        output.flush();
        exchange.close();
    }

    /** get response from the request
     *
     * @param api specific request
     * @param obj the request object
     * @param port port number for the node or server
     *
     * */
    private HttpResponse<String> getResponse(String api, Object obj, int port) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://" + HOSTNAME + ":" + port + api))
                .setHeader("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(obj)))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /** synchronize the chain to get the most up to date chain given what chain it is.
     *
     * @param  chainId the chain ID of the given chain. Could be 1 or 2.
     * @param  candi   the specific blockchain the node asks to sync
     *
     * */
    private void syncChain(int chainId, Blockchain candi){
        ExecutorService executor = Executors.newFixedThreadPool(live_ports.size());
        for (int each : live_ports){
            if (each != this.port){
                executor.submit(() -> {
                    HttpResponse<String> response = null;
                    try {
                        response = getResponse(GET_CHAIN_URI, new GetChainRequest(chainId), each);
                    } catch (Exception e){
                        e.printStackTrace();
                    }
                    int length = gson.fromJson(response.body(), GetChainReply.class).getChainLength();
                    List<Block> blks = gson.fromJson(response.body(), GetChainReply.class).getBlocks();
                    // updating the length by setting the blocks list with the larger length
                    if (length > candi.getLength()){
                        candi.setBlocks(blks);
                    }
                });
            }
        }
        executor.shutdown();
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2){
            System.out.println("Proper Usage is: java MyClass 0 7001,7002,7003");
            System.exit(0);
        }
        // Get the node id and list of nodes to get the exact node
        NODE_ID = Integer.parseInt(args[0]);
        PORT_LIST = args[1].split(",");

        // debugging purpose
        PrintStream debug_file = new PrintStream(new FileOutputStream("debug_storage.txt", true));
        System.setOut(debug_file);

        Node node = new Node();
        node.start();
    }
}
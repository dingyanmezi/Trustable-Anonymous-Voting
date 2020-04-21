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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Node{

    private static int NODE_ID;
    private static String[] PORT_LIST;
    private static final String HOSTNAME = "127.0.0.1";
    private static int[] NODE_PORTS = Config.node_ports;
    public static List<Integer> live_ports = new ArrayList<>();
    protected HttpServer server;
    protected Gson gson;
    /** identity chain for authentication*/
    private Blockchain firstBlockchain;
    /** votes chain */
    private Blockchain secondBlockchain;
    private int port;

    public Node() throws IOException {
        this.port = Integer.parseInt(PORT_LIST[NODE_ID]);
        this.server =  HttpServer.create(new InetSocketAddress(this.port), 0);
        this.server.setExecutor(Executors.newCachedThreadPool());
        this.gson = new Gson();
        this.firstBlockchain = new Blockchain(1);
        this.secondBlockchain = new Blockchain(2);
    }


    void start(){
        for (int each : NODE_PORTS){
            live_ports.add(each);
        }
        this.startSkeletons();
        this.server.start();

    }

    private void startSkeletons(){
        this.getBlockChain();
        this.mineBlock();
        this.addBlock();
        this.broadcastBlock();
        this.sleep();
    }
    /** This call is sent from users/peers to request a copy of the blockchain */
    private void getBlockChain(){
        this.server.createContext("/getchain", (exchange -> {
            String respText = "";
            int returnCode = 200;
            if ("POST".equals(exchange.getRequestMethod())) {
                GetChainRequest gcr = null;
                GetChainReply reply = null;

                InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "utf-8");
                gcr = gson.fromJson(isr, GetChainRequest.class);
                /** 1 for identity chain, 2 for vote chain */
                int id = gcr.getChainId();
                Blockchain candi = null;
                switch (id){
                    case 1:
                        candi = this.firstBlockchain;
                        break;
                    case 2:
                        candi = this.secondBlockchain;
                        break;
                    default:
                }
                int length = candi.getLength();
                List<Block> blocks = candi.getBlocks();
                reply = new GetChainReply(id, length, blocks);
                respText = gson.toJson(reply);
                this.generateResponseAndClose(exchange, respText, returnCode);
            }
        }));
    }

    private void mineBlock(){
        this.server.createContext("/mineblock", (exchange -> {
            String respText = "";
            int returnCode = 200;
            if ("POST".equals(exchange.getRequestMethod())) {
                MineBlockRequest mbr = null;
                BlockReply br = null;
                InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "utf-8");
                mbr = gson.fromJson(isr, MineBlockRequest.class);

                int chainId = mbr.getChainId();

                Blockchain candi = null;
                switch (chainId){
                    case 1:
                        candi = this.firstBlockchain;
                        break;
                    case 2:
                        candi = this.secondBlockchain;
                        break;
                    default:
                }

                try {
                    syncChain(chainId, candi);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                Map<String, String> data = mbr.getData();
                Random rand = new Random();

                String prev_hash = candi.getPrevHash();
                long timestamp = System.currentTimeMillis();
                long nonce = 0;
                Block block = new Block((long) candi.getLength(), data, timestamp, nonce, prev_hash, null);
                String hash = Block.computeHash(block);
                if (chainId == 1){
                    while (!hash.startsWith("00000")){
                        nonce++;
                        timestamp = System.currentTimeMillis();
                        block = new Block((long) candi.getLength(), data, timestamp, nonce, prev_hash, null);
                        hash = Block.computeHash(block);
                    }
                }
                block.setHash(hash);
                br = new BlockReply(chainId, block);
                respText = gson.toJson(br);
                this.generateResponseAndClose(exchange, respText, returnCode);
            }
        }));
    }

    private void addBlock(){
        this.server.createContext("/addblock", (exchange -> {
            String respText = "";
            int returnCode = 200;
            if ("POST".equals(exchange.getRequestMethod())) {
                AddBlockRequest abr = null;
                BroadcastRequest br = null;
                StatusReply sr = null;
                InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "utf-8");
                abr = gson.fromJson(isr, AddBlockRequest.class);
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
                    case 1:
                        candi = this.firstBlockchain;
                        break;
                    case 2:
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
                br = new BroadcastRequest(chainId, "PRECOMMIT", blk);

                List<Future<Boolean>> allFutures = new ArrayList<>();
                int successCount = 0;
                ExecutorService executor = Executors.newFixedThreadPool(NODE_PORTS.length);
                for (int each : NODE_PORTS){
                    if (each != this.port){
                        Future<Boolean> future = executor.submit(() -> {
                            HttpResponse<String> response = null;
                            try {
                                response = getResponse("/broadcast",
                                        new BroadcastRequest(chainId, "PRECOMMIT", blk), each);
                            } catch (Exception e){
                                e.printStackTrace();
                            }
                            boolean success = gson.fromJson(response.body(), StatusReply.class).getSuccess();
                            System.out.println(gson.fromJson(response.body(), StatusReply.class).getInfo());
                            return success;
                        });
                        allFutures.add(future);
                    }
                }
                for (Future<Boolean> each : allFutures){
                    try {
                        if (each.get().equals(true)) successCount++;
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } catch (ExecutionException e) {
                        e.printStackTrace();
                    }
                }
                executor.shutdown();

                // check whether larger than 2/3 for consensus
                executor = Executors.newFixedThreadPool(NODE_PORTS.length);
                if (successCount >= (int) Math.ceil((NODE_PORTS.length - 1) * 2.0 / 3)){
                    candi.addBlock(blk);
                    br = new BroadcastRequest(chainId, "COMMIT", blk);
                    for (int each : NODE_PORTS){
                        if (each != this.port){
                            executor.submit(() -> {
                                try {
                                    HttpResponse<String> response = getResponse("/broadcast",
                                            new BroadcastRequest(chainId, "COMMIT", blk), each);
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

    private void broadcastBlock(){
        this.server.createContext("/broadcast", (exchange -> {
            String respText = "";
            int returnCode = 200;
            if ("POST".equals(exchange.getRequestMethod())) {
                BroadcastRequest br = null;
                StatusReply sr = null;
                InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "utf-8");
                br = gson.fromJson(isr, BroadcastRequest.class);
                if (!live_ports.contains(port)){
                    sr = new StatusReply(false, "already slept !");
                    respText = gson.toJson(sr);
                    this.generateResponseAndClose(exchange, respText, returnCode);
                    return;
                }
                int chainId = br.getChainId();
                Blockchain candi = null;
                switch (chainId){
                    case 1:
                        candi = this.firstBlockchain;
                        break;
                    case 2:
                        candi = this.secondBlockchain;
                        break;
                    default:
                }
                String requestType = br.getRequestType();
                Block blk = br.getBlock();
                String hash = blk.getHash();
                String prevHash = blk.getPreviousHash();
                switch (requestType){
                    case "COMMIT":
                        candi.addBlock(blk);
                        sr = new StatusReply(true, "commit - able ");
                        break;
                    case "PRECOMMIT":
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

    private void sleep(){
        this.server.createContext("/sleep", (exchange -> {
            String respText = "";
            int returnCode = 200;
            if ("POST".equals(exchange.getRequestMethod())) {
                SleepRequest sr = null;
                StatusReply reply = null;
                InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "utf-8");
                sr = gson.fromJson(isr, SleepRequest.class);

                int timeout = sr.getTimeout() * 1000;
                // When received, the node should reply immediately and then go to sleep.
                try {
                    reply = new StatusReply(true, "");
                    respText = gson.toJson(reply);
                    this.generateResponseAndClose(exchange ,respText, returnCode);
                    live_ports.remove((Integer) port);
                    Thread.sleep(timeout);
                    // after timeout you need to add it back FOR SURE LOL
                    live_ports.add(port);
                    /** nodes need to sync blockchain after waking up*/
                     syncChain(1, this.firstBlockchain);
                     syncChain(2, this.secondBlockchain);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }));
    }

    private void generateResponseAndClose(HttpExchange exchange, String respText, int returnCode) throws IOException {
        exchange.sendResponseHeaders(returnCode, respText.getBytes().length);
        OutputStream output = exchange.getResponseBody();
        output.write(respText.getBytes());
        output.flush();
        exchange.close();
    }

    private HttpResponse<String> getResponse(String api, Object obj, int port) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://" + HOSTNAME + ":" + port + api))
                .setHeader("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(obj)))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private void syncChain(int chainId, Blockchain candi) throws IOException, InterruptedException {
        GetChainRequest gcr = null;
        ExecutorService executor = Executors.newFixedThreadPool(live_ports.size());
        for (int each : live_ports){
            if (each != this.port){
                executor.submit(() -> {
                    HttpResponse<String> response = null;
                    try {
                        response = getResponse("/getchain", new GetChainRequest(chainId), each);
                    } catch (Exception e){
                        e.printStackTrace();
                    }
                    int length = gson.fromJson(response.body(), GetChainReply.class).getChainLength();
                    List<Block> blks = gson.fromJson(response.body(), GetChainReply.class).getBlocks();
                    if (length > candi.getLength()){
                        candi.setBlocks(blks);
                    }
//                    try {
//                        response = getResponse("/getchain", new GetChainRequest(2), each);
//                    } catch (Exception e){
//                        e.printStackTrace();
//                    }
//                    length = gson.fromJson(response.body(), GetChainReply.class).getChainLength();
//                    blks = gson.fromJson(response.body(), GetChainReply.class).getBlocks();
//                    if (length > this.secondBlockchain.getLength()) {
//                        this.secondBlockchain.setBlocks(blks);
//                    }
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
        NODE_ID = Integer.parseInt(args[0]);
        PORT_LIST = args[1].split(",");

        PrintStream debug_file = new PrintStream(new FileOutputStream("debug_storage.txt", true));
        System.setOut(debug_file);

        Node node = new Node();
        node.start();
    }
}
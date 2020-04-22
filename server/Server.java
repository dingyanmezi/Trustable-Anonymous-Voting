/**
 *
 *      File Name -     Server.java
 *      Created By -    Pujie Wang
 *      Brief -
 *
 *          The role of the voting server is to hold the election and collect votes
 *
 */

package server;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import lib.AESEncryptObj;
import lib.RSAUtil;
import message.*;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import java.util.concurrent.Executors;

public class Server {

    // blockchain apis
    protected static final String GET_CHAIN_URI = "/getchain";
    protected static final String MINE_BLOCK_URI = "/mineblock";
    protected static final String ADD_BLOCK_URI = "/addblock";

    // server apis
    protected static final String SERVER_STATUS_URI = "/checkserver";
    protected static final String BECOME_CANDIDATE_URI = "/becomecandidate";
    protected static final String GET_CANDIDATES_URI = "/getcandidates";
    protected static final String CAST_VOTE_URI = "/castvote";
    protected static final String COUNT_VOTES_URI = "/countvotes";
    // IDs
    protected static final int IDENTITY_ID = 1;
    protected static final int VOTECHAIN_ID = 2;

    private static final String HOSTNAME = "localhost";
    /** port for the voting server */
    private static int VOTING_SERVER_PORT;
    /** port for the blockchain node */
    private static int BLOCKCHAIN_PORT;
    /** gson object for parsing the json */
    protected Gson gson;
    /** the server that the voting server runs*/
    protected HttpServer server;
    /** the generator to generate public and private keys for */
    public RSAKeyPairGenerator generator;
    /** list for holding candidates among clients */
    private List<String> candidates;

    public Server() throws NoSuchAlgorithmException {
        this.candidates = new ArrayList<>();
        this.gson = new Gson();
        this.generator = new RSAKeyPairGenerator();
    }
    /** start the voting server process */
    private void start() throws IOException, InterruptedException {
        this.register();
        this.startSkeletons();
        this.server.start();
    }
    /** register public key and username information of the voting server to the identity chain */
    private void register() throws IOException, InterruptedException {

        Map<String, String> data = new TreeMap<>();
        data.put("public_key", Base64.getEncoder().encodeToString(this.generator.getPublicKey().getEncoded()));
        data.put("user_name", "server");
        MineBlockRequest mbr = new MineBlockRequest(IDENTITY_ID, data);
        boolean success = false;
        while (!success){
            // first mine and then add.
            HttpResponse<String> response = getResponse(MINE_BLOCK_URI, mbr, BLOCKCHAIN_PORT);
            Block block = gson.fromJson(response.body(), BlockReply.class).getBlock();
            AddBlockRequest abr = new AddBlockRequest(IDENTITY_ID, block);
            response = getResponse(ADD_BLOCK_URI, abr, BLOCKCHAIN_PORT);
            success = gson.fromJson(response.body(), StatusReply.class).getSuccess();
        }

    }
    /** create the server and set up the API handlers */
    private void startSkeletons() throws IOException {
        this.server =  HttpServer.create(new InetSocketAddress(VOTING_SERVER_PORT), 0);
        this.server.setExecutor(Executors.newCachedThreadPool());
        this.checkServer();
        this.becomeCandidate();
        this.getCandidates();
        this.castVote();
        this.countVotes();
    }
    /** make sure the server is functioning normally or crash */
    private void checkServer() {
        this.server.createContext(SERVER_STATUS_URI, (exchange -> {
            String respText = "";
            int returnCode = 200;
            if ("POST".equals(exchange.getRequestMethod())) {
                StatusReply sr = new StatusReply(true, "");
                respText = gson.toJson(sr);
                this.generateResponseAndClose(exchange, respText, returnCode);
            }
        }));
    }
    /** check if the candidate is valid and add the candidate upon success */
    private void becomeCandidate() {
        this.server.createContext(BECOME_CANDIDATE_URI, (exchange -> {
            String respText = "";
            int returnCode = 200;
            if ("POST".equals(exchange.getRequestMethod())) {
                BecomeCandidateRequest bcr = null;
                StatusReply sr = null;
                InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "utf-8");
                bcr = gson.fromJson(isr, BecomeCandidateRequest.class);
                String candidateName = bcr.getCandidateName();
                // check if the node is already a candidate. return false if yes
                if (candidates.contains(candidateName)){
                    sr = new StatusReply(false, "NodeAlreadyCandidate");
                    respText = gson.toJson(sr);
                    returnCode = 409;
                    this.generateResponseAndClose(exchange ,respText, returnCode);
                    return;
                }
                GetChainRequest gcr = new GetChainRequest(IDENTITY_ID);
                HttpResponse<String> response = null;
                try {
                    response = getResponse(GET_CHAIN_URI, gcr, BLOCKCHAIN_PORT);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                int length = gson.fromJson(response.body(), GetChainReply.class).getChainLength();
                List<Block> blocks =  gson.fromJson(response.body(), GetChainReply.class).getBlocks();
                int count = 0;
                // the following loop is to check whether the given candidate name exists.
                for (Block each : blocks){
                    if (each.getData().containsKey("user_name") &&
                            !each.getData().get("user_name").equals(candidateName)){
                        count++;
                    }
                }
                // cuz you need to remove genesis when considering count ~
               if (count == length - 1){
                    sr = new StatusReply(false, "CandidatePublicKeyUnknown");
                    respText = gson.toJson(sr);
                    returnCode = 422;
                    this.generateResponseAndClose(exchange ,respText, returnCode);
                    return;
                }
                // after all the check. Add the candidate to the list.
                candidates.add(candidateName);
                sr = new StatusReply(true, "add successfully");
                respText = gson.toJson(sr);
                this.generateResponseAndClose(exchange ,respText, returnCode);
            }
        }));
    }
    /** obtain a list of candidates and return*/
    private void getCandidates() {
        this.server.createContext(GET_CANDIDATES_URI, (exchange -> {
            String respText = "";
            int returnCode = 200;
            if ("POST".equals(exchange.getRequestMethod())) {
                GetCandidatesReply gcr = new GetCandidatesReply(candidates);
                respText = gson.toJson(gcr);
                this.generateResponseAndClose(exchange ,respText, returnCode);
            }
        }));
    }
    /** the server receives the request from the client and then
     *
     *  0. check for malformed/unencrpted vote
     *  1. Decrypt the encrypted AES session key with server private key
     *  2. Decrypt the encrypted vote contents with AES session key
     *  3. Decrypt the encrypted vote with client public key
     *  4. add a node : A Encrypt voter name with client public key + B add a block
     *
     * */
    private void castVote() {
        this.server.createContext(CAST_VOTE_URI, (exchange -> {
            String respText = "";
            int returnCode = 200;
            if ("POST".equals(exchange.getRequestMethod())) {
                CastVoteRequest cvr = null;
                StatusReply sr = null;
                synchronized(this){
                    InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "utf-8");
                    cvr = gson.fromJson(isr, CastVoteRequest.class);
                    String encrypted_vote_contents = cvr.getEncryptedVotes();
                    String encrypted_session_key = cvr.getEncryptedSessionKey();

                    /** 0. check for malformed/unencrpted vote */
                    for (String candidate : candidates){
                        if (encrypted_session_key.equals(candidate) || encrypted_vote_contents.equals(candidate)){
                            sr = new StatusReply(false, "malformed");
                            respText = gson.toJson(sr);
                            returnCode = 409;
                            this.generateResponseAndClose(exchange, respText, returnCode);
                            return;
                        }
                    }

                    /** 1. Decrypt the encrypted AES session key with server private key  */
                    PrivateKey server_private_key = this.generator.getPrivateKey();
                    String sessionKey = "";
                    try {
                        sessionKey = RSAUtil.decrypt(encrypted_session_key, server_private_key);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    /** 2. Decrypt the encrypted vote contents with AES session key */
                    String json = "";
                    byte[] decodedKey  = Base64.getDecoder().decode(sessionKey);
                    SecretKey originalKey = new SecretKeySpec(decodedKey,"AES");
                    try {
                        json = RSAUtil.decrypt_for_AES(encrypted_vote_contents, originalKey);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    // get the json object and extract the username and encrypted vote out.
                    AESEncryptObj obj = gson.fromJson(json, AESEncryptObj.class);
                    String username = obj.getUserName();
                    String encryptedVote = obj.getEncryptedVote();

                    /** 3. Decrypt the encrypted vote with client public key */
                    HttpResponse<String> response = null;
                    try {
                        response = getResponse(GET_CHAIN_URI, new GetChainRequest(IDENTITY_ID), BLOCKCHAIN_PORT);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    List<Block> blocks = gson.fromJson(response.body(), GetChainReply.class).getBlocks();
                    String client_public_key = "";
                    // search the client public key.
                    for (Block each : blocks){
                        if (each.getData().containsKey("user_name") &&
                                each.getData().get("user_name").equals(username)){
                                client_public_key = each.getData().get("public_key");
                                break;
                        }
                    }
                    byte[] decodeKey_public_key_client = Base64.getDecoder().decode(client_public_key);
                    X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decodeKey_public_key_client);
                    KeyFactory keyFactory = null;
                    try {
                        keyFactory = KeyFactory.getInstance("RSA");
                    } catch (NoSuchAlgorithmException e) {
                        e.printStackTrace();
                    }
                    PublicKey the_final_client_public_key = null;
                    try {
                        the_final_client_public_key  = keyFactory.generatePublic(keySpec);
                    } catch (InvalidKeySpecException e) {
                        e.printStackTrace();
                    }
                    String candidateName = "";
                    try {
                        candidateName = RSAUtil.decrypt(encryptedVote, the_final_client_public_key);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    /** 4. add a node : A Encrypt voter name with client public key + B add a block */
                    String vote_credential = "";
                    try {
                        vote_credential = Base64.getEncoder().encodeToString(RSAUtil.encrypt(username,
                                the_final_client_public_key));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    // put the candidate name and voter into credential package
                    Map<String, String> map = new TreeMap<>();
                    map.put("vote", candidateName);
                    map.put("vote_credential", vote_credential);
                    MineBlockRequest mbr = new MineBlockRequest(VOTECHAIN_ID, map);

                    boolean success = false;
                    while (!success){
                        try {
                            response = getResponse(MINE_BLOCK_URI, mbr, BLOCKCHAIN_PORT);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        Block block = gson.fromJson(response.body(), BlockReply.class).getBlock();
                        AddBlockRequest abr = new AddBlockRequest(VOTECHAIN_ID, block);
                        try {
                            response = getResponse(ADD_BLOCK_URI, abr, BLOCKCHAIN_PORT);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        success = gson.fromJson(response.body(), StatusReply.class).getSuccess();
                    }
                    // return the success
                    sr = new StatusReply(true, "finish everything about castVote");
                    respText = gson.toJson(sr);
                    this.generateResponseAndClose(exchange ,respText, returnCode);
                }
            }
        }));
    }
    /** count the number of votes for a candidate */
    private void countVotes() {
        this.server.createContext(COUNT_VOTES_URI, (exchange -> {
            String respText = "";
            int returnCode = 200;
            if ("POST".equals(exchange.getRequestMethod())) {
                CountVotesRequest cvr = null;
                CountVotesReply reply = null;
                InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "utf-8");
                cvr = gson.fromJson(isr, CountVotesRequest.class);

                String return_votes_for = cvr.getCountVotesFor();
                int vote_count = 0;
                int missed = 0;
                HttpResponse<String> response = null;

                try {
                    response = getResponse(GET_CHAIN_URI, new GetChainRequest(IDENTITY_ID), BLOCKCHAIN_PORT);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                List<Block> blocks = gson.fromJson(response.body(), GetChainReply.class).getBlocks();
                for (Block each : blocks){
                    if (each.getData().containsKey("user_name") && !each.getData().get("user_name").equals(return_votes_for)){
                        missed++;
                    }
                }
                // when all missed that means no candidate is found return false
                if (missed == blocks.size() - 1){
                    reply = new CountVotesReply(false, missed);
                    respText = gson.toJson(reply);
                    returnCode = 409;
                    this.generateResponseAndClose(exchange ,respText, returnCode);
                    return;
                }

                try {
                    response = getResponse(GET_CHAIN_URI, new GetChainRequest(VOTECHAIN_ID), BLOCKCHAIN_PORT);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                blocks = gson.fromJson(response.body(), GetChainReply.class).getBlocks();
                // count the actual number of votes
                for (Block each : blocks){
                    if (each.getData().containsKey("vote") && each.getData().get("vote").equals(return_votes_for)){
                        vote_count++;
                    }
                }
                // return the vote count number
                reply = new CountVotesReply(true, vote_count);
                respText = gson.toJson(reply);
                this.generateResponseAndClose(exchange, respText, returnCode);
            }
        }));
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

    /** respond with response text and return code for each HTTP request */
    private void generateResponseAndClose(HttpExchange exchange, String respText, int returnCode) throws IOException {
        exchange.sendResponseHeaders(returnCode, respText.getBytes().length);
        OutputStream output = exchange.getResponseBody();
        output.write(respText.getBytes());
        output.flush();
        exchange.close();
    }

    public static void main(String[] args) throws IOException, NoSuchAlgorithmException, InterruptedException {
        if (args.length != 2){
            System.out.println("Proper Usage is: java MyClass 88 88");
            System.exit(0);
        }
        // get the ports
        VOTING_SERVER_PORT = Integer.parseInt(args[0]);
        BLOCKCHAIN_PORT = Integer.parseInt(args[1]);
        // debugging purpose
        PrintStream debug_file = new PrintStream(new FileOutputStream("debug_storage.txt", true));
        System.setOut(debug_file);

        Server server = new Server();
        server.start();
    }
}

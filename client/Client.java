/**
 *
 *      File Name -     Client.java
 *      Created By -    Pujie Wang
 *      Brief -
 *
 *          The clients are participants within the voting systems. Accepting API requests from test suites.
 *
 */

package client;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import lib.AESEncryptObj;
import lib.RSAUtil;
import message.*;
import server.RSAKeyPairGenerator;

import javax.crypto.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executors;

public class Client {

    // APIs
    protected static final String GET_CHAIN_URI = "/getchain";
    protected static final String MINE_BLOCK_URI = "/mineblock";
    protected static final String ADD_BLOCK_URI = "/addblock";
    protected static final String CAST_VOTE_URI = "/castvote";
    protected static final String GET_CANDIDATES_URI = "/getcandidates";
    protected static final String START_VOTE_URI = "/startvote";

    protected static final int IDENTITY_ID = 1;

    /** port for this client */
    private static int CLIENT_PORT;
    /** port for the server it sends request to */
    private static int SERVER_PORT;
    /** port for the blockchain node */
    private static int BLOCKCHAIN_PORT;
    /** Host name for all */
    private static final String HOSTNAME = "localhost";
    /** Object for parsing json */
    private Gson gson;
    /** the actual server that this client runs  */
    protected HttpServer client_server;
    /** the generator used to generate public and private keys for the client*/
    private RSAKeyPairGenerator generator;
    /** the name of this client*/
    private String username;
    /** document whether this client has voted or not */
    private boolean hasVoted;

    public Client() throws NoSuchAlgorithmException {
        this.gson = new Gson();
        this.generator = new RSAKeyPairGenerator();
        this.username = "client" + CLIENT_PORT;
        this.hasVoted = false;
    }

    /**
     * YOU NEED TO REGISTER BEFORE BEFORE BEFORE YOU CREATE AND START THE SERVER !!!
     *
     * register the client's public keys to identity chain
     *
     * */
    private void start() throws IOException, InterruptedException {
        // I CANNOT believe that I made this bug again
        this.register();
        this.startSkeletons();
        this.client_server.start();
    }

    /** create the server and set up API handler */
    private void startSkeletons() throws IOException {
        this.client_server =  HttpServer.create(new InetSocketAddress(CLIENT_PORT), 0);
        this.client_server.setExecutor(Executors.newCachedThreadPool());
        this.startVote();
    }

    /** generate public key for this client, mine and add the block to the identity chain */
    private void register() throws IOException, InterruptedException {
        AddBlockRequest abr = null;

        Map<String, String> data = new TreeMap<>();
        data.put("public_key", Base64.getEncoder().encodeToString(this.generator.getPublicKey().getEncoded()));
        data.put("user_name", this.username);
        MineBlockRequest mbr = new MineBlockRequest(IDENTITY_ID, data);

        HttpResponse<String> response = getResponse(MINE_BLOCK_URI, mbr, BLOCKCHAIN_PORT);
        Block block = gson.fromJson(response.body(), BlockReply.class).getBlock();
        boolean success = false;
        while (!success){
            abr = new AddBlockRequest(IDENTITY_ID, block);
            response = getResponse(ADD_BLOCK_URI, abr, BLOCKCHAIN_PORT);
            success = gson.fromJson(response.body(), StatusReply.class).getSuccess();
        }
    }
    /**
     *  StartVote process includes such :
     *  0. check for improper candidate name
     *  1. encrypt with Client Private Key
     *  2. Encrypt 3 fields of information with AES session key
     *  3. Encrypt the session key with server public key
     *  4. send message
     *
     *
     * */
    private void startVote() {
        this.client_server.createContext(START_VOTE_URI, (exchange -> {
            String respText = "";
            int returnCode = 200;
            if ("POST".equals(exchange.getRequestMethod())) {
                StartVoteRequest svr = null;
                StatusReply sr = null;
                InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "utf-8");
                svr = gson.fromJson(isr, StartVoteRequest.class);
                int chainId = svr.getChainId();
                String vote_for = svr.getVoteFor();

                /** 0. check for improper candidate name */
                HttpResponse<String> response = null;
                try {
                    response = getResponse(GET_CANDIDATES_URI, null, SERVER_PORT);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                List<String> candidates = gson.fromJson(response.body(), GetCandidatesReply.class).getCandidates();
                // return false for not being a candidate
                if (!candidates.contains(vote_for))
                {
                    sr = new StatusReply(false, "covid - 19 ");
                    respText = gson.toJson(sr);
                    returnCode = 409;
                    this.generateResponseAndClose(exchange, respText, returnCode);
                    return;
                }
                // return false for already voted
                if (this.hasVoted){
                    sr = new StatusReply(false, "has already voted ");
                    respText = gson.toJson(sr);
                    returnCode = 409;
                    this.generateResponseAndClose(exchange, respText, returnCode);
                    return;
                }

                // generate session key
                KeyGenerator keyGen = null;
                try {
                    keyGen = KeyGenerator.getInstance("AES");
                } catch (NoSuchAlgorithmException e) {
                    e.printStackTrace();
                }
                keyGen.init(128);
                SecretKey sessionKey = keyGen.generateKey();
                /** 1. encrypt with Client Private Key */
                String encryptedString = null;
                try {
                    encryptedString = Base64.getEncoder().encodeToString(RSAUtil.encrypt(vote_for,
                            this.generator.getPrivateKey()));
                } catch (Exception e){
                    e.printStackTrace();
                }

                /** 2. Encrypt 3 fields of information with AES session key */
                // put them into an obj form and them make it gson to become a string for further ops
                AESEncryptObj obj = new AESEncryptObj(chainId, username, encryptedString);
                String obj_to_json = gson.toJson(obj);

                String encrypted_vote_contents = "";
                try {
                    // Act on the actual data and make it to String form
                    encrypted_vote_contents = Base64.getEncoder().encodeToString(
                            RSAUtil.encrypt_for_AES(obj_to_json, sessionKey));
                } catch (Exception e){
                    e.printStackTrace();
                }

                /** 3. Encrypt the session key with server public key */
                String encrypted_session_key = "";
                try {
                    response = getResponse(GET_CHAIN_URI, new GetChainRequest(IDENTITY_ID), BLOCKCHAIN_PORT);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                List<Block> blocks = gson.fromJson(response.body(), GetChainReply.class).getBlocks();
                String server_public_key = "";
                for (Block each : blocks){
                    // NOTE: IT NEEDS TO CHECK WHETHER IT CONTAINS "user_name" FIELD TO GET AWAY WITH GENESIS BLOCK!!!
                    if (each.getData().containsKey("user_name") &&
                            each.getData().get("user_name").equals("server")) {
                        server_public_key = each.getData().get("public_key");
                        break;
                    }
                }
                byte[] publicBytes = Base64.getDecoder().decode(server_public_key);
                X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicBytes);
                KeyFactory keyFactory = null;
                try {
                    keyFactory = KeyFactory.getInstance("RSA");
                } catch (NoSuchAlgorithmException e) {
                    e.printStackTrace();
                }
                PublicKey pubKey = null;
                try {
                    pubKey = keyFactory.generatePublic(keySpec);
                } catch (InvalidKeySpecException e) {
                    e.printStackTrace();
                }
                try {
                    encrypted_session_key = Base64.getEncoder().encodeToString(RSAUtil.encrypt(
                            Base64.getEncoder().encodeToString(sessionKey.getEncoded()),
                            pubKey));
                } catch (Exception e){
                    e.printStackTrace();
                }

                /** 4. send message */
                CastVoteRequest cvr = new CastVoteRequest(encrypted_vote_contents, encrypted_session_key);
                try {
                  response  = getResponse(CAST_VOTE_URI, cvr, SERVER_PORT);
                  boolean success = gson.fromJson(response.body(), StatusReply.class).getSuccess();
                  System.out.println(this.username + " -------- " + success);
                  String info = gson.fromJson(response.body(), StatusReply.class).getInfo();
                  System.out.println(this.username + " -------- " + info);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                /** return */
                this.hasVoted = true; // this client has voted then
                sr = new StatusReply(true, "message sent from the voting client");
                respText = gson.toJson(sr);
                this.generateResponseAndClose(exchange, respText, returnCode);
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

    public static void main(String[] args) throws IOException, NoSuchAlgorithmException, InterruptedException {
        if (args.length != 3){
            System.out.println("Proper Usage is: java MyClass 88 88 88");
            System.exit(0);
        }
        // take out the ports respectively
        CLIENT_PORT = Integer.parseInt(args[0]);
        SERVER_PORT = Integer.parseInt(args[1]);
        BLOCKCHAIN_PORT = Integer.parseInt(args[2]);

        // used for debugging purpose
        PrintStream debug_file = new PrintStream(new FileOutputStream("debug_storage.txt", true));
        System.setOut(debug_file);

        Client client = new Client();
        client.start();
    }


}

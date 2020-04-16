package server;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jdk.jshell.Snippet;
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

    private static final String HOSTNAME = "localhost";
    private static int VOTING_SERVER_PORT;
    private static int BLOCKCHAIN_PORT;
    protected Gson gson;

    protected HttpServer server;
    /** need to be accessed by others clients*/
    public RSAKeyPairGenerator generator;
    private List<String> candidates;

    public Server() throws IOException, NoSuchAlgorithmException {



//        this.generator = new RSAKeyPairGenerator();
        this.candidates = new ArrayList<>();
        this.gson = new Gson();
        this.generator = new RSAKeyPairGenerator();
    }

    private void start() throws IOException, InterruptedException {
        this.register();
        this.startSkeletons();
        this.server.start();

    }

    private void register() throws IOException, InterruptedException {
        MineBlockRequest mbr = null;
        AddBlockRequest abr = null;

        Map<String, String> data = new TreeMap<>();
        data.put("public_key", Base64.getEncoder().encodeToString(this.generator.getPublicKey().getEncoded()));
        data.put("user_name", "server");
        mbr = new MineBlockRequest(1, data);
        int chain_id = 1;

        HttpResponse<String> response = getResponse("/mineblock", mbr, BLOCKCHAIN_PORT);
        Block block = gson.fromJson(response.body(), BlockReply.class).getBlock();

        abr = new AddBlockRequest(1, block);
        response = getResponse("/addblock", abr, BLOCKCHAIN_PORT);
        boolean success = gson.fromJson(response.body(), StatusReply.class).getSuccess();
        String info = gson.fromJson(response.body(), StatusReply.class).getInfo();

    }

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
        this.server.createContext("/checkserver", (exchange -> {
            String respText = "";
            int returnCode = 200;
            if ("POST".equals(exchange.getRequestMethod())) {
                StatusReply sr = null;

                sr = new StatusReply(true, "");
                respText = gson.toJson(sr);
                this.generateResponseAndClose(exchange, respText, returnCode);
            }
        }));
    }

    private void becomeCandidate() {
        this.server.createContext("/becomecandidate", (exchange -> {
            String respText = "";
            int returnCode = 200;
            if ("POST".equals(exchange.getRequestMethod())) {
                BecomeCandidateRequest bcr = null;
                StatusReply sr = null;

                InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "utf-8");
                bcr = gson.fromJson(isr, BecomeCandidateRequest.class);
                String candidateName = bcr.getCandidateName();

                if (candidates.contains(candidateName)){
                    sr = new StatusReply(false, "NodeAlreadyCandidate");
                    respText = gson.toJson(sr);
                    returnCode = 409;
                    this.generateResponseAndClose(exchange ,respText, returnCode);
                    return;
                }
                GetChainRequest gcr = new GetChainRequest(1);
                HttpResponse<String> response = null;
                try {
                    response = getResponse("/getchain", gcr, BLOCKCHAIN_PORT);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                int length = gson.fromJson(response.body(), GetChainReply.class).getChainLength();
                List<Block> blocks =  gson.fromJson(response.body(), GetChainReply.class).getBlocks();
                int count = 0;
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

                candidates.add(candidateName);
                sr = new StatusReply(true, "add successfully");
                respText = gson.toJson(sr);
                this.generateResponseAndClose(exchange ,respText, returnCode);
            }
        }));
    }

    private void getCandidates() {
        this.server.createContext("/getcandidates", (exchange -> {
            String respText = "";
            int returnCode = 200;
            if ("POST".equals(exchange.getRequestMethod())) {
                GetCandidatesReply gcr = new GetCandidatesReply(candidates);
                respText = gson.toJson(gcr);
                this.generateResponseAndClose(exchange ,respText, returnCode);
            }
        }));
    }

    private void castVote() {
        this.server.createContext("/castvote", (exchange -> {
            String respText = "";
            int returnCode = 200;
            if ("POST".equals(exchange.getRequestMethod())) {
                CastVoteRequest cvr = null;
                StatusReply sr = null;
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
                PrivateKey server_private_key = null;
                server_private_key = this.generator.getPrivateKey();
                String sessionKey = "";
                try {
                    sessionKey = RSAUtil.decrypt(encrypted_session_key, server_private_key);
                } catch (IllegalBlockSizeException e) {
                    e.printStackTrace();
                } catch (InvalidKeyException e) {
                    e.printStackTrace();
                } catch (BadPaddingException e) {
                    e.printStackTrace();
                } catch (NoSuchAlgorithmException e) {
                    e.printStackTrace();
                } catch (NoSuchPaddingException e) {
                    e.printStackTrace();
                }

                /** 2. Decrypt the encrypted vote contents with AES session key */
                String json = "";
                byte[] decodedKey  = Base64.getDecoder().decode(sessionKey);
                SecretKey originalKey = new SecretKeySpec(decodedKey,"AES");
                try {
                    json = RSAUtil.decrypt_for_AES(encrypted_vote_contents, originalKey);
                } catch (IllegalBlockSizeException e) {
                    e.printStackTrace();
                } catch (InvalidKeyException e) {
                    e.printStackTrace();
                } catch (BadPaddingException e) {
                    e.printStackTrace();
                } catch (NoSuchAlgorithmException e) {
                    e.printStackTrace();
                } catch (NoSuchPaddingException e) {
                    e.printStackTrace();
                }

                AESEncryptObj obj = gson.fromJson(json, AESEncryptObj.class);
                int chainId = obj.getChainId();
                String username = obj.getUserName();
                String encryptedVote = obj.getEncryptedVote();


                /** 3. Decrypt the encrypted vote with client public key */
                HttpResponse<String> response = null;
                try {
                    response = getResponse("/getchain", new GetChainRequest(1), BLOCKCHAIN_PORT);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                List<Block> blocks = gson.fromJson(response.body(), GetChainReply.class).getBlocks();
                String client_public_key = "";
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
                } catch (IllegalBlockSizeException e) {
                    e.printStackTrace();
                } catch (InvalidKeyException e) {
                    e.printStackTrace();
                } catch (BadPaddingException e) {
                    e.printStackTrace();
                } catch (NoSuchAlgorithmException e) {
                    e.printStackTrace();
                } catch (NoSuchPaddingException e) {
                    e.printStackTrace();
                }

                /** 4. add a node : A Encrypt voter name with client public key + B add a block */

                String vote_credential = "";
                try {
                    vote_credential = Base64.getEncoder().encodeToString(RSAUtil.encrypt(username,
                            the_final_client_public_key));
                } catch (BadPaddingException e) {
                    e.printStackTrace();
                } catch (IllegalBlockSizeException e) {
                    e.printStackTrace();
                } catch (InvalidKeyException e) {
                    e.printStackTrace();
                } catch (NoSuchPaddingException e) {
                    e.printStackTrace();
                } catch (NoSuchAlgorithmException e) {
                    e.printStackTrace();
                }
                Map<String, String> map = new TreeMap<>();
                map.put("vote", candidateName);
                map.put("vote_credential", vote_credential);
                MineBlockRequest mbr = new MineBlockRequest(2, map);

                try {
                    response = getResponse("/mineblock", mbr, BLOCKCHAIN_PORT);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                Block block = gson.fromJson(response.body(), BlockReply.class).getBlock();

                AddBlockRequest abr = new AddBlockRequest(2, block);
                try {
                    response = getResponse("/addblock", abr, BLOCKCHAIN_PORT);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                boolean success = gson.fromJson(response.body(), StatusReply.class).getSuccess();
                String info = gson.fromJson(response.body(), StatusReply.class).getInfo();

                sr = new StatusReply(true, "finish everything about castVote");
                respText = gson.toJson(sr);
                this.generateResponseAndClose(exchange ,respText, returnCode);
            }
        }));
    }

    private void countVotes() {
        this.server.createContext("/countvotes", (exchange -> {
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
                    response = getResponse("/getchain", new GetChainRequest(1), BLOCKCHAIN_PORT);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                List<Block> blocks = gson.fromJson(response.body(), GetChainReply.class).getBlocks();
                for (Block each : blocks){
                    if (each.getData().containsKey("user_name") && !each.getData().get("user_name").equals(return_votes_for)){
                        missed++;
                    }
                }
                if (missed == blocks.size() - 1){
                    reply = new CountVotesReply(false, missed);
                    respText = gson.toJson(reply);
                    returnCode = 409;
                    this.generateResponseAndClose(exchange ,respText, returnCode);
                    return;
                }

                try {
                    response = getResponse("/getchain", new GetChainRequest(2), BLOCKCHAIN_PORT);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                blocks = gson.fromJson(response.body(), GetChainReply.class).getBlocks();
                for (Block each : blocks){
                    if (each.getData().containsKey("vote") && each.getData().get("vote").equals(return_votes_for)){
                        vote_count++;
                    }
                }

                reply = new CountVotesReply(true, vote_count);
                respText = gson.toJson(reply);
                this.generateResponseAndClose(exchange, respText, returnCode);

            }
        }));
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
        VOTING_SERVER_PORT = Integer.parseInt(args[0]);
        BLOCKCHAIN_PORT = Integer.parseInt(args[1]);

        PrintStream debug_file = new PrintStream(new FileOutputStream("debug_storage.txt", true));
        System.setOut(debug_file);


        Server server = new Server();
        server.start();

    }
}

package client;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import lib.AESEncryptObj;
import lib.RSAUtil;
import message.*;
import server.RSAKeyPairGenerator;
import server.Server;

import javax.crypto.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.InvalidKeyException;
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

    private static int CLIENT_PORT;
    private static int SERVER_PORT;
    private static int BLOCKCHAIN_PORT;
    private static final String HOSTNAME = "localhost";
    private Gson gson;
    protected HttpServer client_server;
    private RSAKeyPairGenerator generator;
    private String username;
    private boolean hasVoted;

    public Client() throws IOException, NoSuchAlgorithmException {
        this.gson = new Gson();

        this.generator = new RSAKeyPairGenerator();
        this.username = "client" + CLIENT_PORT;
        this.hasVoted = false;
    }

    /** YOU NEED TO REGISTER BEFORE BEFORE BEFORE YOU CREATE AND START THE SERVER !!! */
    private void start() throws IOException, InterruptedException {
        // I CANNOT believe that I made this bug again
        this.register();
        this.startSkeletons();
        this.client_server.start();

    }

    private void startSkeletons() throws IOException {
        this.client_server =  HttpServer.create(new InetSocketAddress(CLIENT_PORT), 0);
        this.client_server.setExecutor(Executors.newCachedThreadPool());
        this.startVote();
    }



    private void register() throws IOException, InterruptedException {
        MineBlockRequest mbr = null;
        AddBlockRequest abr = null;

        Map<String, String> data = new TreeMap<>();
        data.put("public_key", Base64.getEncoder().encodeToString(this.generator.getPublicKey().getEncoded()));
        data.put("user_name", this.username);
        mbr = new MineBlockRequest(1, data);

        HttpResponse<String> response = getResponse("/mineblock", mbr, BLOCKCHAIN_PORT);
        Block block = gson.fromJson(response.body(), BlockReply.class).getBlock();

        abr = new AddBlockRequest(1, block);
        response = getResponse("/addblock", abr, BLOCKCHAIN_PORT);
        boolean success = gson.fromJson(response.body(), StatusReply.class).getSuccess();
        String info = gson.fromJson(response.body(), StatusReply.class).getInfo();


    }

    private void startVote() {
        this.client_server.createContext("/startvote", (exchange -> {
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
                    response = getResponse("/getcandidates", null, SERVER_PORT);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                List<String> candidates = gson.fromJson(response.body(), GetCandidatesReply.class).getCandidates();

                if (!candidates.contains(vote_for))
                {
                    sr = new StatusReply(false, "covid - 19 ");
                    respText = gson.toJson(sr);
                    returnCode = 409;
                    this.generateResponseAndClose(exchange, respText, returnCode);
                    return;
                }
                if (this.hasVoted){
                    sr = new StatusReply(false, "has already voted ");
                    respText = gson.toJson(sr);
                    returnCode = 409;
                    this.generateResponseAndClose(exchange, respText, returnCode);
                    return;
                }
                // TODO Implementation --------- POSSIBLE FALSE SYNTAX FOR ENCRYPTION
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

                /** 2. Encrypt 3 fields of information with AES session key */
                // put them into an obj form and them make it gson to become a string for further ops
                AESEncryptObj obj = new AESEncryptObj(chainId, username, encryptedString);
                String obj_to_json = gson.toJson(obj);

                String encrypted_vote_contents = "";
                try {
                    // Act on the actual data and make it to String form
                    encrypted_vote_contents = Base64.getEncoder().encodeToString(
                            RSAUtil.encrypt_for_AES(obj_to_json, sessionKey));
                } catch (IllegalBlockSizeException e) {
                    e.printStackTrace();
                } catch (BadPaddingException e) {
                    e.printStackTrace();
                } catch (NoSuchPaddingException e) {
                    e.printStackTrace();
                } catch (NoSuchAlgorithmException e) {
                    e.printStackTrace();
                } catch (InvalidKeyException e) {
                    e.printStackTrace();
                }

                /** 3. Encrypt the session key with server public key*/
                String encrypted_session_key = "";
                try {
                    response = getResponse("/getchain", new GetChainRequest(1), BLOCKCHAIN_PORT);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                List<Block> blocks = gson.fromJson(response.body(), GetChainReply.class).getBlocks();
//                int length = gson.fromJson(response.body(), GetChainReply.class).getChainLength();
                String server_public_key = "";
                for (Block each : blocks){
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


                /** 4. send message */
                CastVoteRequest cvr = new CastVoteRequest(encrypted_vote_contents, encrypted_session_key);
                try {
                  response  = getResponse("/castvote", cvr, SERVER_PORT);
                  boolean success = gson.fromJson(response.body(), StatusReply.class).getSuccess();
                  String info = gson.fromJson(response.body(), StatusReply.class).getInfo();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                /** return */
                this.hasVoted = true;
                sr = new StatusReply(true, "message sent from the voting client");
                respText = gson.toJson(sr);
                this.generateResponseAndClose(exchange, respText, returnCode);
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

    public static void main(String[] args) throws IOException, NoSuchAlgorithmException, InterruptedException {
        if (args.length != 3){
            System.out.println("Proper Usage is: java MyClass 88 88 88");
            System.exit(0);
        }

        String[] clientPorts = args[0].split(",");
        CLIENT_PORT = Integer.parseInt(args[0]);
//        for (int i = 0; i < clientPorts.length; i++) CLIENT_PORTS[i] = Integer.parseInt(clientPorts[i]);
        SERVER_PORT = Integer.parseInt(args[1]);
        BLOCKCHAIN_PORT = Integer.parseInt(args[2]);

        PrintStream debug_file = new PrintStream(new FileOutputStream("debug_storage.txt", true));
        System.setOut(debug_file);

        Client client = new Client();
        client.start();
    }


}

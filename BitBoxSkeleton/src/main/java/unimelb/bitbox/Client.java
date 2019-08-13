package unimelb.bitbox;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

import java.util.logging.Logger;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import unimelb.bitbox.util.Document;

public class Client {
	private static Logger log = Logger.getLogger(Client.class.getName());
	private static final String PKCS_1_PEM_HEADER = "-----BEGIN RSA PRIVATE KEY-----";
	private static final String PKCS_1_PEM_FOOTER = "-----END RSA PRIVATE KEY-----";
	private static final String PKCS_8_PEM_HEADER = "-----BEGIN PRIVATE KEY-----";
	private static final String PKCS_8_PEM_FOOTER = "-----END PRIVATE KEY-----";
	
	public static void main(String args[]) {
		
		System.setProperty("java.util.logging.SimpleFormatter.format",
                "[%1$tc] %2$s %4$s: %5$s%n");
		
		CmdLineArgs argsBean = new CmdLineArgs();

		CmdLineParser parser = new CmdLineParser(argsBean);
		try {
			
			parser.parseArgument(args);
			
			String command = argsBean.getCommand();
			String server  = argsBean.getServer();
			String identity = argsBean.getIdentity();
			System.out.println(command+" "+server+" "+identity);
			String[] serverHostPort = server.split(":");
			
			Socket socket = new Socket(serverHostPort[0], Integer.parseInt(serverHostPort[1]));
			BufferedWriter outputStream = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF8"));
			BufferedReader inputStream = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF8"));
			
			//auth_request
			PeerProtocolMessage peerProtocolMessage = new PeerProtocolMessage();
			outputStream.write(peerProtocolMessage.authRequest(identity));
			outputStream.newLine();
			outputStream.flush();
			log.info("sending message: " + peerProtocolMessage.authRequest(identity));
			
			String data = inputStream.readLine();
			log.info("received message: " + data);
			
			Document doc = Document.parse(data);
			boolean status = doc.getBoolean("status");
			
			if(status)
			{
				String message = doc.getString("AES128");
				String privateKeyOrigin = "bitboxclient_rsa/.ssh";
				
				PrivateKey privateKey;
				
					privateKey = loadKey(privateKeyOrigin);
				
				
					
				String secretKey = decryptAES128(privateKey, message);
				log.info("received message: " + secretKey);
				
				switch(command) {
				case "list_peers":
					
					log.info("preparing to send message: " + peerProtocolMessage.listPeersRequest());
					String list_peers_msg = sendEncrypted(secretKey, peerProtocolMessage.listPeersRequest());
					outputStream.write(peerProtocolMessage.payload(list_peers_msg));
					outputStream.newLine();
					outputStream.flush();
					log.info("sending message: " + peerProtocolMessage.payload(list_peers_msg));
					
					String data_list_JSON = inputStream.readLine();
					log.info("received payload: " + data_list_JSON);
					Document data_list_doc = Document.parse(data_list_JSON);
					String list_peers_encrypted = data_list_doc.getString("payload");
					String list_peers_list = decryptMessage(secretKey, list_peers_encrypted);
					log.info("received message: " + list_peers_list);
					
					break;
					
				case "connect_peer":
					
					String[] connect_peer = argsBean.getPeer().split(":");
					String connect_host = connect_peer[0];
					String connect_port = connect_peer[1];
					log.info("preparing to send message: " + peerProtocolMessage.connectPeerRequest(connect_host, connect_port));
					String connect_peer_msg = sendEncrypted(secretKey, peerProtocolMessage.connectPeerRequest(connect_host, connect_port));
					outputStream.write(peerProtocolMessage.payload(connect_peer_msg));
					outputStream.newLine();
					outputStream.flush();
					log.info("sending message: " + peerProtocolMessage.payload(connect_peer_msg));
					
					String connect_peer_JSON = inputStream.readLine();
					log.info("received payload: " + connect_peer_JSON);
					Document connect_peer_doc = Document.parse(connect_peer_JSON);
					String connect_peer_encrypted = connect_peer_doc.getString("payload");
					String connect_peer_list = decryptMessage(secretKey, connect_peer_encrypted);
					log.info("received message: " + connect_peer_list);
					
					break;
					
				case "disconnect_peer":
					
					String[] disconnect_peer = argsBean.getPeer().split(":");
					String disconnect_host = disconnect_peer[0];
					String disconnect_port = disconnect_peer[1];
					log.info("preparing to send message: " + peerProtocolMessage.disconnectPeerRequest(disconnect_host, disconnect_port));
					String disconnect_peer_msg = sendEncrypted(secretKey, peerProtocolMessage.disconnectPeerRequest(disconnect_host, disconnect_port));
					outputStream.write(peerProtocolMessage.payload(disconnect_peer_msg));
					outputStream.newLine();
					outputStream.flush();
					log.info("sending message: " + peerProtocolMessage.payload(disconnect_peer_msg));
					
					String disconnect_peer_JSON = inputStream.readLine();
					log.info("received payload: " + disconnect_peer_JSON);
					Document disconnect_peer_doc = Document.parse(disconnect_peer_JSON);
					String disconnect_peer_encrypted = disconnect_peer_doc.getString("payload");
					String disconnect_peer_list = decryptMessage(secretKey, disconnect_peer_encrypted);
					log.info("received message: " + disconnect_peer_list);
					socket.close();
					break;
				}
			}
		} catch (GeneralSecurityException e) {
			e.printStackTrace();	
		} catch (NumberFormatException | IOException e) {
			e.printStackTrace();
		} catch (CmdLineException e) {

			System.err.println(e.getMessage());

			parser.printUsage(System.err);
		}

	}
	
	private static String decryptAES128(PrivateKey privateKey, String message) {
		try {
			Cipher cipher = Cipher.getInstance("RSA");
			cipher.init(Cipher.DECRYPT_MODE, privateKey);
			message = new String(cipher.doFinal(Base64.getDecoder().decode(message.getBytes())));
			System.err.println("Decrypted message: "+message);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return message;
	}

	private static String sendEncrypted(String secretKey, String message){
		Key aesKey = new SecretKeySpec(secretKey.getBytes(), "AES");
		try {
			Cipher cipher = Cipher.getInstance("AES");
			cipher.init(Cipher.ENCRYPT_MODE, aesKey);
			byte[] encrypted = cipher.doFinal(new String(message+'\n').getBytes("UTF-8"));
			System.err.println("Encrypted text: "+new String(encrypted));
			message = Base64.getEncoder().encodeToString(encrypted);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return message;
	}

	private static String decryptMessage(String secretKey, String message){
		try {
    		Key aesKey = new SecretKeySpec(secretKey.getBytes(), "AES");
			Cipher cipher = Cipher.getInstance("AES");
			cipher.init(Cipher.DECRYPT_MODE, aesKey);
			message = new String(cipher.doFinal(Base64.getDecoder().decode(message.getBytes())));
			System.err.println("Decrypted message: "+message);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return message;
	}

	private static PrivateKey loadKey(String keyFilePath) throws GeneralSecurityException, IOException {
	    byte[] keyDataBytes = Files.readAllBytes(Paths.get(keyFilePath));
	    String keyDataString = new String(keyDataBytes, StandardCharsets.UTF_8);

	    if (keyDataString.contains(PKCS_1_PEM_HEADER)) {
	        // OpenSSL / PKCS#1 Base64 PEM encoded file
	        keyDataString = keyDataString.replace(PKCS_1_PEM_HEADER, "").replace("\n", "");
	        keyDataString = keyDataString.replace(PKCS_1_PEM_FOOTER, "").replace("\n", "");
	        return readPkcs1PrivateKey(Base64.getDecoder().decode(keyDataString));
	    }

	    if (keyDataString.contains(PKCS_8_PEM_HEADER)) {
	        // PKCS#8 Base64 PEM encoded file
	        keyDataString = keyDataString.replace(PKCS_8_PEM_HEADER, "").replace("\n", "");
	        keyDataString = keyDataString.replace(PKCS_8_PEM_FOOTER, "").replace("\n", "");
	        return readPkcs8PrivateKey(Base64.getDecoder().decode(keyDataString));
	    }

	    // We assume it's a PKCS#8 DER encoded binary file
	    return readPkcs8PrivateKey(Files.readAllBytes(Paths.get(keyFilePath)));
	}

	private static PrivateKey readPkcs8PrivateKey(byte[] pkcs8Bytes) throws GeneralSecurityException {
	    KeyFactory keyFactory = KeyFactory.getInstance("RSA", "SunRsaSign");
	    PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(pkcs8Bytes);
	    try {
	        return keyFactory.generatePrivate(keySpec);
	    } catch (InvalidKeySpecException e) {
	        throw new IllegalArgumentException("Unexpected key format!", e);
	    }
	}

	private static PrivateKey readPkcs1PrivateKey(byte[] pkcs1Bytes) throws GeneralSecurityException {
	    int pkcs1Length = pkcs1Bytes.length;
	    int totalLength = pkcs1Length + 22;
	    byte[] pkcs8Header = new byte[] {
	            0x30, (byte) 0x82, (byte) ((totalLength >> 8) & 0xff), (byte) (totalLength & 0xff), // Sequence + total length
	            0x2, 0x1, 0x0, // Integer (0)
	            0x30, 0xD, 0x6, 0x9, 0x2A, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xF7, 0xD, 0x1, 0x1, 0x1, 0x5, 0x0, // Sequence: 1.2.840.113549.1.1.1, NULL
	            0x4, (byte) 0x82, (byte) ((pkcs1Length >> 8) & 0xff), (byte) (pkcs1Length & 0xff) // Octet string + length
	    };
	    byte[] pkcs8bytes = join(pkcs8Header, pkcs1Bytes);
	    return readPkcs8PrivateKey(pkcs8bytes);
	}

	private static byte[] join(byte[] byteArray1, byte[] byteArray2){
	    byte[] bytes = new byte[byteArray1.length + byteArray2.length];
	    System.arraycopy(byteArray1, 0, bytes, 0, byteArray1.length);
	    System.arraycopy(byteArray2, 0, bytes, byteArray1.length, byteArray2.length);
	    return bytes;
	}
	
}

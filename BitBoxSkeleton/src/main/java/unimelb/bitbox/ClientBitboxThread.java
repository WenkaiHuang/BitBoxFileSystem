package unimelb.bitbox;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.security.Key;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.logging.Logger;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import unimelb.bitbox.util.Document;

public class ClientBitboxThread extends Thread{
	private static Logger log = Logger.getLogger(ServerMain.class.getName());
	private Thread t;
	private String host;
	private String port;
	private String secretKey;
	private Socket bitboxSocket;
	private BufferedReader inputStream;
	private BufferedWriter outputStream;
	protected static HashMap<String, String> connectedPeers = new LinkedHashMap<String, String>();
	
	public ClientBitboxThread(Socket bitboxSocket, String secretKey) {
		this.bitboxSocket = bitboxSocket;
		this.secretKey = secretKey;
	}
	
	public void start() {
		if (t == null) {
			t = new Thread(this);
			t.start();
		}
	}
	
	public void run() {
		
		try {
			inputStream = new BufferedReader(new InputStreamReader(bitboxSocket.getInputStream(), "UTF8"));
			outputStream = new BufferedWriter(new OutputStreamWriter(bitboxSocket.getOutputStream(), "UTF8"));
			
			String data_encrypted_payload = inputStream.readLine();
			Document doc_payload = Document.parse(data_encrypted_payload);
			String data_encrypted = doc_payload.getString("payload");
			
			String data_JSON = decryptMessage(secretKey, data_encrypted);
			log.info("received message: " + data_JSON);
			Document doc = Document.parse(data_JSON);
			String command = doc.getString("command");
			
			switch(command) {
			case "LIST_PEERS_REQUEST":
				
					for(String keySocket : ServerMain.otherHostPortSet.keySet())
					{
						connectedPeers.put(keySocket, ServerMain.otherHostPortSet.get(keySocket));
					}
				
				PeerProtocolMessage listPeersMessage = new PeerProtocolMessage(connectedPeers);
				String list_peers_msg = sendEncrypted(secretKey, listPeersMessage.listPeersResponse());
				log.info("preparing to send message: " + listPeersMessage.listPeersResponse());
				outputStream.write(listPeersMessage.payload(list_peers_msg));
				outputStream.newLine();
				outputStream.flush();
				log.info("sending encrypted message: " + listPeersMessage.payload(list_peers_msg));
				
				break;
			case "CONNECT_PEER_REQUEST":
				boolean connect_flag = false;
				this.host = doc.getString("host");
				this.port = String.valueOf(doc.getLong("port"));
				
				PeerThread peerThread = new PeerThread(host, port);
				peerThread.start();
				try {
					sleep(10000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				System.out.println(ServerMain.hostPortSet);
				for(String keySocket : ServerMain.hostPortSet.keySet())
				{
					if(port.equals(keySocket) && host.equals(ServerMain.hostPortSet.get(keySocket))) {
						connect_flag = true;
					}
				}
				
				if(connect_flag) {
				PeerProtocolMessage connectPeerMessage = new PeerProtocolMessage();
				log.info("preparing to send message: " + connectPeerMessage.connectPeerResponse(host, port, true));
				String connect_peer_msg = sendEncrypted(secretKey, connectPeerMessage.connectPeerResponse(host, port, true));
				outputStream.write(connectPeerMessage.payload(connect_peer_msg));
				outputStream.newLine();
				outputStream.flush();
				log.info("sending encrypted message: " + connectPeerMessage.payload(connect_peer_msg));
				}
				else{
					PeerProtocolMessage connectPeerMessage = new PeerProtocolMessage();
					log.info("preparing to send message: " + connectPeerMessage.connectPeerResponse(host, port, false));
					String connect_peer_msg = sendEncrypted(secretKey, connectPeerMessage.connectPeerResponse(host, port, false));
					outputStream.write(connectPeerMessage.payload(connect_peer_msg));
					outputStream.newLine();
					outputStream.flush();
					log.info("sending encrypted message: " + connectPeerMessage.payload(connect_peer_msg));
				}
				break;
				
			case "DISCONNECT_PEER_REQUEST":
				boolean disconnect_flag = false;
				int disconnect_count = 0;
				int socketNumber = -1;
				this.host = doc.getString("host");
				this.port = String.valueOf(doc.getLong("port"));
				
				for(String keySocket : ServerMain.otherHostPortSet.keySet())
				{
					if(port.equals(keySocket) && host.equals(ServerMain.otherHostPortSet.get(keySocket))) {
						disconnect_flag = true;
						socketNumber = disconnect_count;
					}
					disconnect_count++;
				}
				if(disconnect_flag) {
				ServerMain.clientSocketSet.get(socketNumber).close();
				PeerProtocolMessage disconnectPeerMessage = new PeerProtocolMessage();
				log.info("preparing to send message: " + disconnectPeerMessage.disconnectPeerResponse(host, port, true));
				String disconnect_peer_msg = sendEncrypted(secretKey, disconnectPeerMessage.disconnectPeerResponse(host, port, true));
				outputStream.write(disconnectPeerMessage.payload(disconnect_peer_msg));
				outputStream.newLine();
				outputStream.flush();
				log.info("sending encrypted message: " + disconnectPeerMessage.payload(disconnect_peer_msg));
				}
				else {
					PeerProtocolMessage disconnectPeerMessage = new PeerProtocolMessage();
					log.info("preparing to send message: " + disconnectPeerMessage.disconnectPeerResponse(host, port, false));
					String disconnect_peer_msg = sendEncrypted(secretKey, disconnectPeerMessage.disconnectPeerResponse(host, port, false));
					outputStream.write(disconnectPeerMessage.payload(disconnect_peer_msg));
					outputStream.newLine();
					outputStream.flush();
					log.info("sending encrypted message: " + disconnectPeerMessage.payload(disconnect_peer_msg));
				}
				break;
			}
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
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
	
	private static String sendEncrypted(String secretKey, String message){
		Key aesKey = new SecretKeySpec(secretKey.getBytes(), "AES");
		try {
			Cipher cipher = Cipher.getInstance("AES");
			// Perform encryption
			cipher.init(Cipher.ENCRYPT_MODE, aesKey);
			byte[] encrypted = cipher.doFinal(new String(message+'\n').getBytes("UTF-8"));
			System.err.println("Encrypted text: "+new String(encrypted));
			message = Base64.getEncoder().encodeToString(encrypted);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return message;
	}
}

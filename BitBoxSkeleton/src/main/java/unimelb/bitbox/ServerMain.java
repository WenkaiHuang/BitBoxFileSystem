package unimelb.bitbox;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigInteger;
import java.io.InputStreamReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.BufferedReader;
import java.security.Key;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.KeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Queue;
import java.util.logging.Logger;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import java.net.Socket;
import java.net.UnknownHostException;
import java.net.ServerSocket;

import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.FileSystemObserver;
import unimelb.bitbox.util.HostPort;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;

public class ServerMain implements FileSystemObserver {
	private static Logger log = Logger.getLogger(ServerMain.class.getName());
	protected static FileSystemManager fileSystemManager;

	protected static String serverPort = Configuration.getConfigurationValue("port");
	protected static String advertisedName = Configuration.getConfigurationValue("advertisedName");
	protected static String[] peers = Configuration.getConfigurationValue("peers").split(",");
	protected final static int MAXCONNECTIONS = Integer.parseInt(Configuration.getConfigurationValue("maximumIncommingConnections"));
	protected static int syncInterval = Integer.parseInt(Configuration.getConfigurationValue("syncInterval"));
	protected static String clientPort = Configuration.getConfigurationValue("clientPort");
	protected static String[] keyString = Configuration.getConfigurationValue("authorized_keys").split(",");

	protected static ArrayList<Socket> clientSocketSet = new ArrayList<Socket>();
	protected static ArrayList<String> otherServerSocketSet = new ArrayList<String>();
	protected static Queue<FileSystemEvent> pathevents = new LinkedList<FileSystemEvent>();
	protected static HashMap<String, String> connectedPorts = new LinkedHashMap<String, String>();
	protected static HashMap<String, String> hostPortSet = new LinkedHashMap<String, String>();
	protected static HashMap<String, String> otherHostPortSet = new LinkedHashMap<String, String>();

	public ServerMain() throws NumberFormatException, IOException, NoSuchAlgorithmException {

		fileSystemManager = new FileSystemManager(Configuration.getConfigurationValue("path"), this);

		ServerSocket clientListenSocket = new ServerSocket(Integer.parseInt(clientPort));
		ClientBitbox clientBitbox = new ClientBitbox(clientListenSocket);
		clientBitbox.start();

		ServerSocket listenSocket = new ServerSocket(Integer.parseInt(serverPort));

		int i = 0;
		log.info("start server");
		ServerThread serverThread = new ServerThread(listenSocket, i);
		serverThread.start();
		try {
			for (String hostPort : peers) {
				String[] peer = hostPort.split(":");

				Thread.sleep(15000);

				PeerThread peerThread = new PeerThread(peer);
				peerThread.start();

			}
			while (true) {
				if (pathevents.size() > 0) {
					int n = clientSocketSet.size();
					if (n > 0) {
						for (int m = 1; m < n; m++) {

							if (m == clientSocketSet.size() - 1) {
								FileRequestMessage fileRequestMessage = new FileRequestMessage(clientSocketSet.get(m),
										pathevents.poll());
								fileRequestMessage.start();
							} else {
								FileRequestMessage fileRequestMessage = new FileRequestMessage(clientSocketSet.get(m),
										pathevents.element());
								fileRequestMessage.start();
							}
						}
					}
				}
				Thread.sleep(15000);
			}

		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}
	}

	@Override
	public void processFileSystemEvent(FileSystemEvent fileSystemEvent) {
		pathevents.offer(fileSystemEvent);
		System.out.println(fileSystemEvent);
	}
}

class ClientBitbox extends Thread {
	private static Logger log = Logger.getLogger(ServerMain.class.getName());
	private Thread t;
	private ServerSocket clientListenSocket;
	private BufferedReader inputStream;
	private BufferedWriter outputStream;

	public ClientBitbox(ServerSocket clientListenSocket) {
		this.clientListenSocket = clientListenSocket;
	}

	public void start() {
		if (t == null) {
			t = new Thread(this);
			t.start();
		}
	}

	public void run() {
		try {
			while (true) {
				Socket bitboxSocket = clientListenSocket.accept();
				inputStream = new BufferedReader(new InputStreamReader(bitboxSocket.getInputStream(), "UTF8"));
				outputStream = new BufferedWriter(new OutputStreamWriter(bitboxSocket.getOutputStream(), "UTF8"));

				String data = inputStream.readLine();
				log.info("received message: " + data);

				Document doc = Document.parse(data);
				String command = doc.getString("command");

				if (command.equals("AUTH_REQUEST")) {
					String identity = doc.getString("identity");
					for (int a = 0; a < ServerMain.keyString.length; a++) {
						String[] keySplit = ServerMain.keyString[a].split(" ");
						if (keySplit[2].equals(identity)) {
							String keyContent = ServerMain.keyString[a];
							sendEncrSecretRSA(bitboxSocket, keyContent, outputStream);

						} else if (a + 1 > ServerMain.keyString.length) {
							PeerProtocolMessage authFalseResponse = new PeerProtocolMessage();
							outputStream.write(authFalseResponse.authResponse(false));
							log.info("sending message: " + authFalseResponse.authResponse(false));
						}

					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void sendEncrSecretRSA(Socket bitboxSocket, String keyContent, BufferedWriter outputStream) {
		String secretKey = "5v8y/B?D(G+KbPeS";
		Key aesKey = new SecretKeySpec(secretKey.getBytes(), "AES");
		try {
			Cipher cipher = Cipher.getInstance("RSA");
			PublicKey rsaPublicKey = readKey(new String(keyContent+'\n'));
			cipher.init(Cipher.ENCRYPT_MODE, rsaPublicKey);
			byte[] encrypted = cipher.doFinal(aesKey.getEncoded());
			System.err.println("Encrypted text: " + new String(encrypted));

			String Base64Encoded = Base64.getEncoder().encodeToString(encrypted);
			PeerProtocolMessage authTrueResponse = new PeerProtocolMessage(Base64Encoded);
			outputStream.write(authTrueResponse.authResponse(true));
			outputStream.newLine();
			outputStream.flush();
			log.info("sending message: " + authTrueResponse.authResponse(true));

			ClientBitboxThread clientBitboxThread = new ClientBitboxThread(bitboxSocket, secretKey);
			clientBitboxThread.start();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// Source:
	// https://github.com/ragnar-johannsson/CloudStack/blob/master/utils/src/com/cloud/utils/crypt/RSAHelper.java
	private static RSAPublicKey readKey(String key) throws Exception {
		// key = "ssh-rsa <myBase64key> <email>"
		byte[] encKey = Base64.getDecoder().decode(key.split(" ")[1]);
		DataInputStream dis = new DataInputStream(new ByteArrayInputStream(encKey));

		byte[] header = readElement(dis);
		String pubKeyFormat = new String(header);
		if (!pubKeyFormat.equals("ssh-rsa"))
			throw new RuntimeException("Unsupported format");

		byte[] publicExponent = readElement(dis);
		byte[] modulus = readElement(dis);

		KeySpec spec = new RSAPublicKeySpec(new BigInteger(modulus), new BigInteger(publicExponent));
		KeyFactory keyFactory = KeyFactory.getInstance("RSA");
		RSAPublicKey pubKey = (RSAPublicKey) keyFactory.generatePublic(spec);

		return pubKey;
	}

	private static byte[] readElement(DataInput dis) throws IOException {
		int len = dis.readInt();
		byte[] buf = new byte[len];
		dis.readFully(buf);
		return buf;
	}
}

class ServerThread extends Thread {
	private static Logger log = Logger.getLogger(ServerMain.class.getName());
	private int i;
	private Thread t;
	private String message;
	private ServerSocket listenSocket;
	private BufferedReader inputStream;
	private BufferedWriter outputStream;
	private PeerProtocolMessage peerProtocolMessage;

	public ServerThread(ServerSocket listenSocket, int i) {
		this.listenSocket = listenSocket;
		this.i = i;
	}

	public void start() {
		if (t == null) {
			t = new Thread(this);
			t.start();
		}
	}

	public void run() {

		try {
			while (true) {

				System.out.println("Server listening for a connection");
				Socket clientSocket = listenSocket.accept();

				if (!ServerMain.clientSocketSet.contains(clientSocket)) {

					ServerMain.clientSocketSet.add(clientSocket);

					i++;
					log.info("received connection " + i);

					inputStream = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), "UTF8"));
					System.out.println("server reading data");
					String data = inputStream.readLine();
					log.info("received message: " + data);

					Document doc = Document.parse(data);
					String command = doc.getString("command");
					Document hostPortDoc = (Document) doc.get("hostPort");
					HostPort hostPort = new HostPort(hostPortDoc);

					String host = ServerMain.advertisedName;
					String port = ServerMain.serverPort;

					String otherHost = hostPort.host;
					String otherPort = String.valueOf(hostPort.port);
					ServerMain.otherHostPortSet.put(otherPort, otherHost);

					if (i <= ServerMain.MAXCONNECTIONS) {

						if (command.equals("HANDSHAKE_REQUEST")) {
							peerProtocolMessage = new PeerProtocolMessage(host, port);
							message = peerProtocolMessage.handshakeResponse();

							System.out.println("server writing data");
							outputStream = new BufferedWriter(
									new OutputStreamWriter(clientSocket.getOutputStream(), "UTF8"));
							outputStream.write(message);
							outputStream.newLine();
							outputStream.flush();
							log.info("sending message: " + message);

							ArrayList<FileSystemEvent> systemEvents = ServerMain.fileSystemManager.generateSyncEvents();
							systemEvents.forEach((systemEvent) -> {
								FileRequestMessage fileRequestMessage = new FileRequestMessage(clientSocket,
										systemEvent);
								fileRequestMessage.start();
							});

							Thread.sleep(ServerMain.syncInterval);

							FileSystemProcess fileSystemProcess = new FileSystemProcess(otherHost, otherPort,
									clientSocket, ServerMain.fileSystemManager);
							fileSystemProcess.start();
						}
					} else {
						ServerMain.connectedPorts.put(port, host);
						ServerMain.connectedPorts.put(ServerMain.serverPort, ServerMain.advertisedName);
						outputStream = new BufferedWriter(
								new OutputStreamWriter(clientSocket.getOutputStream(), "UTF8"));
						peerProtocolMessage = new PeerProtocolMessage(ServerMain.connectedPorts);
						String refusedMessage = peerProtocolMessage.connectionRefused();
						log.info("sending message: " + refusedMessage);
						outputStream.write(refusedMessage);
						outputStream.newLine();
						outputStream.flush();
						clientSocket.close();
					}
				} else {
					outputStream = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream(), "UTF8"));
					peerProtocolMessage = new PeerProtocolMessage();
					String invalidMessage = peerProtocolMessage.invalidProtocol();
					log.info("sending message: " + invalidMessage);
					outputStream.write(invalidMessage);
					outputStream.newLine();
					outputStream.flush();
					clientSocket.close();
					clientSocket.close();
				}
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (EOFException e) {
			System.out.println("EOF:" + e.getMessage());
		} catch (IOException e) {
			System.out.println("readline:" + e.getMessage());
		} finally {
			try {
				listenSocket.close();
			} catch (IOException e) {
				/* close failed */}
		}
	}
}

class PeerThread extends Thread {
	private static Logger log = Logger.getLogger(ServerMain.class.getName());
	private Thread t;
	private String otherHost;
	private String otherPort;
	private String data;
	private BufferedReader inputStream;
	private BufferedWriter outputStream;
	private PeerProtocolMessage peerProtocolMessage;

	public PeerThread(String[] peer) {
		this.otherHost = peer[0];
		this.otherPort = peer[1];
	}
	
	public PeerThread(String otherHost, String otherPort) {
		this.otherHost = otherHost;
		this.otherPort = otherPort;
	}

	public void start() {
		if (t == null) {
			t = new Thread(this);
			t.start();
		}
	}

	public void run() {

		try {
			if (!ServerMain.otherHostPortSet.containsKey(otherPort)) {
				Socket socket = new Socket(otherHost, Integer.parseInt(otherPort));
				if (!ServerMain.clientSocketSet.contains(socket)) {

					String host = ServerMain.advertisedName;
					String port = ServerMain.serverPort;
					System.out.println("send to " + otherHost + ":" + otherPort);

					System.out.println("peer writing data");
					outputStream = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF8"));
					peerProtocolMessage = new PeerProtocolMessage(host, port);
					outputStream.write(peerProtocolMessage.handshakeRequest());
					outputStream.newLine();
					outputStream.flush();
					log.info("sending message: " + peerProtocolMessage.handshakeRequest());

					System.out.println("peer reading data");
					inputStream = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF8"));
					data = inputStream.readLine();

					Document doc = Document.parse(data);
					String command = doc.getString("command");
					Document hostPortDoc = (Document) doc.get("hostPort");
					HostPort hostPort = new HostPort(hostPortDoc);
					log.info("received command [" + command + "] from " + hostPort.host + ":" + hostPort.port);
					log.info("received message: " + data);

					switch (command) {
					case "INVALID_PROTOCOL":
						socket.close();
						break;
					case "CONNECTION_REFUSED":
						socket.close();
						break;
					case "HANDSHAKE_RESPONSE":
						FileSystemProcess fileSystemProcess = new FileSystemProcess(hostPort.host,
								String.valueOf(hostPort.port), socket, ServerMain.fileSystemManager);
						fileSystemProcess.start();

						Thread.sleep(ServerMain.syncInterval);
						
						ServerMain.clientSocketSet.add(socket);
						ServerMain.hostPortSet.put(otherPort, otherHost);
						ServerMain.otherHostPortSet.put(otherPort, otherHost);
						break;
					}
					ArrayList<FileSystemEvent> systemEvents = ServerMain.fileSystemManager.generateSyncEvents();
					systemEvents.forEach((systemEvent) -> {
						FileRequestMessage fileRequestMessage = new FileRequestMessage(socket, systemEvent);
						fileRequestMessage.start();
					});
				}
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
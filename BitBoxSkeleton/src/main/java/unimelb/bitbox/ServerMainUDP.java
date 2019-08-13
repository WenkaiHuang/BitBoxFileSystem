package unimelb.bitbox;

import java.io.IOException;
import java.io.EOFException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Queue;
import java.util.logging.Logger;
import java.net.*;

import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.FileSystemObserver;
import unimelb.bitbox.util.HostPort;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;

public class ServerMainUDP implements FileSystemObserver {
	private static Logger log = Logger.getLogger(ServerMain.class.getName());
	protected static FileSystemManager fileSystemManager;
	public static ServerSocket serverSocket = null;
	public static HashMap<String, String> hostPortSet = new HashMap<String, String>();
	public static HashMap<String, String> udpConnectedPorts = new LinkedHashMap<String, String>();
	public final static int MAXCONNECTIONS = Integer.parseInt(Configuration.getConfigurationValue("maximumIncommingConnections"));
	

	public static String udpServerPort = Configuration.getConfigurationValue("udpPort");
	public static String advertisedName = Configuration.getConfigurationValue("advertisedName");
	protected static ArrayList<String> udpClientSocketSet = new ArrayList<String>();
	public static Queue<FileSystemEvent> pathevents = new LinkedList<FileSystemEvent>();
	public static String[] peers = Configuration.getConfigurationValue("udpPeers").split(",");

	public ServerMainUDP() throws NumberFormatException, IOException, NoSuchAlgorithmException {
		
		
		fileSystemManager = new FileSystemManager(Configuration.getConfigurationValue("path"), this);

		int udpPort = Integer.parseInt(Configuration.getConfigurationValue("udpPort"));
		int syncInterval = Integer.parseInt(Configuration.getConfigurationValue("syncInterval"));

		DatagramSocket udpListenSocket = new DatagramSocket(udpPort); 
		int i = 0;
		log.info("start udp server");
		
		ServerThreadUDP serverThread = new ServerThreadUDP(syncInterval, udpListenSocket, i);
		serverThread.start();
		
		for (String hostPort : peers) {
			String[] peer = hostPort.split(":");

			try {
				Thread.sleep(5000);

				PeerThreadUDP peerThread = new PeerThreadUDP(peer);
				peerThread.start();

			} catch (InterruptedException e1) {
				e1.printStackTrace();
			}

		}
		try {

			while (true) {

				if (pathevents.size() > 0) {
					System.out.println("generating thread to process this event");
					System.out.println("event content: " + pathevents.element().toString() + "\n");
					
					System.out.println("udpConnectedPort size: "+udpClientSocketSet.size()+"\n");
						
					
					for(int j=0;j<udpClientSocketSet.size();j++)
						{
							if (udpClientSocketSet.get(i) != null) {
								if (i == udpClientSocketSet.size() - 1) {
									FileRequestMessageUDP client = new FileRequestMessageUDP(udpConnectedPorts.get(udpClientSocketSet.get(i)),
																		udpClientSocketSet.get(i), udpListenSocket, pathevents.poll());	//poll removes head of the queue
									client.start();
									
									System.out.println("connect to port: "+udpClientSocketSet.get(i));
									System.out.println("connect to host: "+udpConnectedPorts.get(udpClientSocketSet.get(i)));
									System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
								} else {
									FileRequestMessageUDP client = new FileRequestMessageUDP(udpConnectedPorts.get(udpClientSocketSet.get(i)),
																		udpClientSocketSet.get(i), udpListenSocket, pathevents.element()); //element just return the head of the queue
									client.start();
									System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
								}
								
							}
						}
				}
				 
				Thread.sleep(14000);
			}

		} catch (InterruptedException e) {
			e.printStackTrace();
		}

	}

	@Override
	public void processFileSystemEvent(FileSystemEvent fileSystemEvent) {
		pathevents.offer(fileSystemEvent);
		System.out.println(fileSystemEvent);

	}

}

class ServerThreadUDP extends Thread {
	private static Logger log = Logger.getLogger(ServerMain.class.getName());
	private DatagramSocket udpListenSocket;
	private String message;
	private Thread t;
	private int i;
	private PeerProtocolMessage peerProtocolMessage;
	private int syncInterval;
	private String host;
	private String port;

	public ServerThreadUDP(int syncInterval, DatagramSocket udpListenSocket, int i) {
		this.udpListenSocket = udpListenSocket;
		this.i = i;
		this.syncInterval = syncInterval;

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
				
				int bufferSize = Integer.parseInt(Configuration.getConfigurationValue("minBuffer"));
				byte[] buffer = new byte[bufferSize];
					
					DatagramPacket input = new DatagramPacket(buffer, buffer.length);
					
					System.out.println("server reading data");
				
					udpListenSocket.receive(input);
					
					String data = new String(input.getData()).trim();
					log.info("received message: " + data+"server side (main loop)");

					Document doc = Document.parse(data);
	
					String command = doc.getString("command");
					
					if (doc.containsKey("hostPort")) {
						Document hostPort = (Document) doc.get("hostPort");
						
						host = hostPort.getString("host");
						port = String.valueOf(hostPort.getLong("port"));
						
						ServerMainUDP.udpConnectedPorts.put(port, host);
						ServerMainUDP.udpClientSocketSet.add(port);
						i++;
						log.info("received connection " + i);
					}
					
						
						if (i <= ServerMain.MAXCONNECTIONS) {
							DatagramPacket output;
							
							
							if (command.equals("HANDSHAKE_REQUEST")) {
								peerProtocolMessage = new PeerProtocolMessage(Configuration.getConfigurationValue("advertisedName"), Configuration.getConfigurationValue("udpPort"));
								message = peerProtocolMessage.handshakeResponse();

								System.out.println("server writing data");
								output = new DatagramPacket(message.getBytes(), message.length(), InetAddress.getByName(host), Integer.valueOf(port));
								udpListenSocket.send(output);
								log.info("sending message: " + message+"server side (handshake block)");
								
								System.out.println("message sent: "+message);
								FileSystemProcessUDP fileSystemProcess = new FileSystemProcessUDP(host, port, udpListenSocket, ServerMainUDP.fileSystemManager);
								fileSystemProcess.start();
							}
							
							sleep(syncInterval*10);
							ArrayList<FileSystemEvent> systemEvents = ServerMainUDP.fileSystemManager.generateSyncEvents();
							
							
							systemEvents.forEach((systemEvent) -> {
								FileRequestMessageUDP fileRequestMessage = new FileRequestMessageUDP(host, port, udpListenSocket, systemEvent);
								fileRequestMessage.start();
								
							});
							
							
							FileSystemProcessUDP fileSystemProcess = new FileSystemProcessUDP(host, port, udpListenSocket, ServerMainUDP.fileSystemManager);
							fileSystemProcess.start();

							
						} else { 

							ServerMainUDP.udpConnectedPorts.put(port, host);
							ServerMainUDP.udpClientSocketSet.add(port);
			
							peerProtocolMessage = new PeerProtocolMessage(ServerMainUDP.udpConnectedPorts);
							
							String refusedMessage = peerProtocolMessage.connectionRefused();
							log.info("sending message: " + refusedMessage);
							
							DatagramPacket output = new DatagramPacket(refusedMessage.getBytes(), refusedMessage.length(), InetAddress.getByName(host), Integer.valueOf(port));
							udpListenSocket.send(output);
							udpListenSocket.close();
						}
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (EOFException e) {
			System.out.println("EOF:" + e.getMessage());
		} catch (IOException e) {
			System.out.println("readline:" + e.getMessage());
		} finally {
			udpListenSocket.close();	
		}
	}
}

class PeerThreadUDP extends Thread {
	private static Logger log = Logger.getLogger(ServerMain.class.getName());
	private String data;
	private PeerProtocolMessage peerProtocolMessage;
	private Thread t;
	private String otherHost;
	private String otherPort;

	public PeerThreadUDP(String[] peer) {
		this.otherHost = peer[0];
		this.otherPort = peer[1];
	}

	public void start() {
		if (t == null) {
			t = new Thread(this);
			t.start();
		}
	}

	public void run() {
		
		try {
			DatagramSocket udpSocket = new DatagramSocket();
			if (!ServerMainUDP.udpConnectedPorts.containsKey(otherPort)) {
			
				ServerMainUDP.udpConnectedPorts.put(otherPort, otherHost);
				ServerMainUDP.udpClientSocketSet.add(otherPort);
				
				String host = Configuration.getConfigurationValue("advertisedName");
				String port = Configuration.getConfigurationValue("udpPort");
				
				peerProtocolMessage = new PeerProtocolMessage(host, port);	
				System.out.println("peer writing data");
				
				String handshakeRequest = peerProtocolMessage.handshakeRequest()+'\n';				
				DatagramPacket output = new DatagramPacket(handshakeRequest.getBytes(), handshakeRequest.length(), InetAddress.getByName(otherHost), Integer.valueOf(otherPort));
				
				udpSocket.send(output);
				log.info("sending message: " + handshakeRequest);
	
				
				int bufferSize = Integer.parseInt(Configuration.getConfigurationValue("minBuffer"));
				byte[] buffer = new byte[bufferSize];
				DatagramPacket input = new DatagramPacket(buffer, buffer.length);
				
				System.out.println("peer reading data");
				udpSocket.receive(input);
				data = new String(input.getData()).trim();
				
				Document doc = Document.parse(data);
				String command = doc.getString("command");
				Document hostPortDoc = (Document) doc.get("hostPort");
				HostPort hostPort = new HostPort(hostPortDoc);
				log.info("received command [" + command + "] from " + hostPort.host + ":" + hostPort.port);
				log.info("received message: " + data+"peer side");
	
				switch (command) {
					case "INVALID_PROTOCOL":
						System.out.println("INVALID_PROTOCOL: closing socket from peer side");
						udpSocket.close();
						break;
					case "CONNECTION_REFUSED":
						udpSocket.close();
						break;
					default:
	
					ArrayList<FileSystemEvent> systemEvents = ServerMainUDP.fileSystemManager.generateSyncEvents();
					
					systemEvents.forEach((systemEvent) -> {
						FileRequestMessageUDP fileRequestMessage = new FileRequestMessageUDP(otherHost, otherPort, udpSocket, systemEvent);
						fileRequestMessage.start();
					});
	
					FileSystemProcessUDP fileSystemProcess = new FileSystemProcessUDP(otherHost, otherPort, udpSocket, ServerMainUDP.fileSystemManager);
					fileSystemProcess.start();
				}
			}
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}

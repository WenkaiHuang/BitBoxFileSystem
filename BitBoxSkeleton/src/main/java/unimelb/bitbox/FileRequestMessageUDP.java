package unimelb.bitbox;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.logging.Logger;
import java.net.*;

import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;

public class FileRequestMessageUDP extends Thread {
	private static Logger log = Logger.getLogger(ServerMain.class.getName());
	private Thread t;
	private String host;
	private String port;
	private String systemEvent;
	private DatagramSocket udpSocket;
	private FileSystemEvent fileSystemEvent;
	private PeerProtocolMessage peerProtocolMessage;

	public FileRequestMessageUDP(String host, String port, DatagramSocket udpSocket, FileSystemEvent fileSystemEvent) {
		this.host = host;
		this.port = port;
		this.udpSocket = udpSocket;
		this.fileSystemEvent = fileSystemEvent;
		this.systemEvent = fileSystemEvent.event.name();
	}

	public void start() {
		if (t == null) {
			t = new Thread(this);
			t.start();
		}
	}

	public void run() {
		try {
			DatagramPacket output;
			peerProtocolMessage = new PeerProtocolMessage(fileSystemEvent);
			switch (systemEvent) {
			
			case "FILE_CREATE":
				String fileCreateRequest = peerProtocolMessage.fileCreateRequest();
				output = new DatagramPacket(fileCreateRequest.getBytes(), fileCreateRequest.length(), InetAddress.getByName(host), Integer.valueOf(port));
				udpSocket.send(output);
				log.info("sending message: " + fileCreateRequest);
				break;
				
			case "FILE_DELETE":
				String fileDeleteRequest = peerProtocolMessage.fileDeleteRequest();
				output = new DatagramPacket(fileDeleteRequest.getBytes(), fileDeleteRequest.length(), InetAddress.getByName(host), Integer.valueOf(port));
				udpSocket.send(output);
				log.info("sending message: " + fileDeleteRequest);
				break;
				
			case "FILE_MODIFY":
				String fileModifyRequest = peerProtocolMessage.fileModifyRequest();
				output = new DatagramPacket(fileModifyRequest.getBytes(), fileModifyRequest.length(), InetAddress.getByName(host), Integer.valueOf(port));
				udpSocket.send(output);
				log.info("sending message: " + fileModifyRequest);
				break;
				
			case "DIRECTORY_CREATE":
				String directoryCreateRequest = peerProtocolMessage.directoryCreateRequest();
				output = new DatagramPacket(directoryCreateRequest.getBytes(), directoryCreateRequest.length(), InetAddress.getByName(host), Integer.valueOf(port));
				udpSocket.send(output);
				log.info("sending message: " + directoryCreateRequest);
				break;
				
			case "DIRECTORY_DELETE":
				String directoryDeleteRequest = peerProtocolMessage.directoryDeleteRequest();
				output = new DatagramPacket(directoryDeleteRequest.getBytes(), directoryDeleteRequest.length(), InetAddress.getByName(host), Integer.valueOf(port));
				udpSocket.send(output);
				log.info("sending message: " + directoryDeleteRequest);
				break;

			}
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}
}

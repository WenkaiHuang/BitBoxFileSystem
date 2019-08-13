package unimelb.bitbox;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.util.logging.Logger;

import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;

public class FileRequestMessage extends Thread {
	private static Logger log = Logger.getLogger(ServerMain.class.getName());
	private Thread t;
	private Socket socket;
	private String systemEvent;
	private BufferedWriter outputStream;
	private FileSystemEvent fileSystemEvent;
	private PeerProtocolMessage peerProtocolMessage;

	public FileRequestMessage(Socket socket, FileSystemEvent fileSystemEvent) {
		this.socket = socket;
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
			
			outputStream = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF8"));
			peerProtocolMessage = new PeerProtocolMessage(fileSystemEvent);
			
			switch (systemEvent) {
			case "FILE_CREATE":
				outputStream.write(peerProtocolMessage.fileCreateRequest());
				outputStream.newLine();
				outputStream.flush();
				log.info("sending message: " + peerProtocolMessage.fileCreateRequest());
				break;
				
			case "FILE_DELETE":
				outputStream.write(peerProtocolMessage.fileDeleteRequest());
				outputStream.newLine();
				outputStream.flush();
				log.info("sending message: " + peerProtocolMessage.fileDeleteRequest());
				break;
				
			case "FILE_MODIFY":
				outputStream.write(peerProtocolMessage.fileModifyRequest());
				outputStream.newLine();
				outputStream.flush();
				log.info("sending message: " + peerProtocolMessage.fileModifyRequest());
				break;
				
			case "DIRECTORY_CREATE":
				outputStream.write(peerProtocolMessage.directoryCreateRequest());
				outputStream.newLine();
				outputStream.flush();
				log.info("sending message: " + peerProtocolMessage.directoryCreateRequest());
				break;
				
			case "DIRECTORY_DELETE":
				outputStream.write(peerProtocolMessage.directoryDeleteRequest());
				outputStream.newLine();
				outputStream.flush();
				log.info("sending message: " + peerProtocolMessage.directoryDeleteRequest());
				break;
				
			}
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}
}

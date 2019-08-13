package unimelb.bitbox;

import java.util.HashMap;
import java.util.ArrayList;

import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;
import unimelb.bitbox.util.HostPort;

public class PeerProtocolMessage {
	private String host = null;
	private String port = null;
	private String encodeString = null;
	private FileSystemEvent fileSystemEvent = null;
	private HashMap<String, String> connectedPorts = null;
	
	public PeerProtocolMessage(){}

	public PeerProtocolMessage(String host, String port) {
		this.host = host;
		this.port = port;

	}
	
	public PeerProtocolMessage(String host, String port, FileSystemEvent fileSystemEvent) {
		this.host = host;
		this.port = port;
		this.fileSystemEvent = fileSystemEvent;

	}
	public PeerProtocolMessage(FileSystemEvent fileSystemEvent) {
		this.fileSystemEvent = fileSystemEvent;

	}

	public PeerProtocolMessage(HashMap<String, String> connectedPorts) {
		this.connectedPorts = connectedPorts;
	}
	
	public PeerProtocolMessage(String encodeString) {
		this.encodeString = encodeString;
	}
	
	public String invalidProtocol() {
		String message = null;

		Document invalidProtocol = new Document();
		invalidProtocol.append("command", "INVALID_PROTOCOL");
		invalidProtocol.append("message", "message must contain a command field as string");

		message = invalidProtocol.toJson();

		return message;
	}

	public String connectionRefused() {
		String message = null;

		Document connectionRefused = new Document();
		connectionRefused.append("command", "CONNECTION_REFUSED");
		connectionRefused.append("message", "connection limit reached");

		ArrayList<Document> hostPorts = new ArrayList<Document>();
		connectedPorts.forEach((port, host) -> {
			HostPort hostPort = new HostPort(host,Integer.parseInt(port));
			hostPorts.add(hostPort.toDoc());
		});
		connectionRefused.append("peers", hostPorts);

		message = connectionRefused.toJson();

		return message;
	}

	public String handshakeRequest() {
		String message = null;

		HostPort hostPort = new HostPort(host, Integer.parseInt(port));
		Document handshakeRequest = new Document();
		handshakeRequest.append("command", "HANDSHAKE_REQUEST");
		handshakeRequest.append("hostPort", hostPort.toDoc());

		message = handshakeRequest.toJson();

		return message;
	}

	public String handshakeResponse() {
		String message = null;

		HostPort hostPort = new HostPort(host, Integer.parseInt(port));

		Document handshakeResponse = new Document();
		handshakeResponse.append("command", "HANDSHAKE_RESPONSE");
		handshakeResponse.append("hostPort", hostPort.toDoc());

		message = handshakeResponse.toJson();

		return message;
	}

	public String fileCreateRequest() {
		String message = null;

		Document fileCreateRequest = new Document();
		fileCreateRequest.append("command", "FILE_CREATE_REQUEST");
		fileCreateRequest.append("fileDescriptor", fileSystemEvent.fileDescriptor.toDoc());
		fileCreateRequest.append("pathName", fileSystemEvent.pathName);

		message = fileCreateRequest.toJson();

		return message;
	}

	public String fileCreateResponse(Document fileDescriptor, String pathName, String message_debug, boolean status) {
		String message = null;

		Document fileCreateResponse = new Document();
		fileCreateResponse.append("command", "FILE_CREATE_RESPONSE");
		fileCreateResponse.append("fileDescriptor", fileDescriptor);
		fileCreateResponse.append("pathName", pathName);
		fileCreateResponse.append("message", message_debug);
			
				
		fileCreateResponse.append("status", status);

		message = fileCreateResponse.toJson();

		return message;
	}

	public String fileDeleteRequest() {
		String message = null;

		Document fileDeleteRequest = new Document();
		fileDeleteRequest.append("command", "FILE_DELETE_REQUEST");
		fileDeleteRequest.append("fileDescriptor", fileSystemEvent.fileDescriptor.toDoc());
		fileDeleteRequest.append("pathName", fileSystemEvent.pathName);

		message = fileDeleteRequest.toJson();

		return message;
	}

	public String fileDeleteResponse(Document fileDescriptor, String pathName, String message_debug, boolean status) {
		String message = null;

		Document fileDeleteResponse = new Document();
		fileDeleteResponse.append("command", "FILE_DELETE_RESPONSE");
		fileDeleteResponse.append("fileDescriptor", fileDescriptor);
		fileDeleteResponse.append("pathName", pathName);
		fileDeleteResponse.append("message", message_debug);
		fileDeleteResponse.append("status", status);

		message = fileDeleteResponse.toJson();

		return message;
	}

	public String fileModifyRequest() {
		String message = null;

		Document fileModifyRequest = new Document();
		fileModifyRequest.append("command", "FILE_MODIFY_REQUEST");
		fileModifyRequest.append("fileDescriptor", fileSystemEvent.fileDescriptor.toDoc());
		fileModifyRequest.append("pathName", fileSystemEvent.pathName);

		message = fileModifyRequest.toJson();

		return message;
	}

	public String fileModifyResponse(Document fileDescriptor, String pathName, String message_debug, boolean status) {
		String message = null;

		Document fileModifyResponse = new Document();
		fileModifyResponse.append("command", "FILE_MODIFY_RESPONSE");
		fileModifyResponse.append("fileDescriptor", fileDescriptor);
		fileModifyResponse.append("pathName", pathName);
		fileModifyResponse.append("message", message_debug);
		fileModifyResponse.append("status", status);

		message = fileModifyResponse.toJson();

		return message;
	}

	public String directoryCreateRequest() {
		String message = null;

		Document directoryCreateRequest = new Document();
		directoryCreateRequest.append("command", "DIRECTORY_CREATE_REQUEST");
		directoryCreateRequest.append("pathName", fileSystemEvent.pathName);

		message = directoryCreateRequest.toJson();

		return message;
	}

	public String directoryCreateResponse(String pathName, String message_debug, boolean status) {
		String message = null;

		Document directoryCreateResponse = new Document();
		directoryCreateResponse.append("command", "DIRECTORY_CREATE_RESPONSE");
		directoryCreateResponse.append("pathName", pathName);
		directoryCreateResponse.append("message", message_debug);
		directoryCreateResponse.append("status", status);

		message = directoryCreateResponse.toJson();

		return message;
	}

	public String directoryDeleteRequest() {
		String message = null;

		Document directoryDeleteRequest = new Document();
		directoryDeleteRequest.append("command", "DIRECTORY_DELETE_REQUEST");
		directoryDeleteRequest.append("pathName", fileSystemEvent.pathName);

		message = directoryDeleteRequest.toJson();

		return message;
	}

	public String directoryDeleteResponse(String pathName, String message_debug, boolean status) {
		String message = null;

		Document directoryDeleteResponse = new Document();
		directoryDeleteResponse.append("command", "DIRECTORY_DELETE_RESPONSE");
		directoryDeleteResponse.append("pathName", pathName);
		directoryDeleteResponse.append("message", message_debug);
		directoryDeleteResponse.append("status", status);

		message = directoryDeleteResponse.toJson();

		return message;
	}

	public String fileBytesRequest(Document fileDescriptor, String pathName, long position, long length) {
		String message = null;

		Document fileBytesRequest = new Document();
		fileBytesRequest.append("command", "FILE_BYTES_REQUEST");
		fileBytesRequest.append("fileDescriptor", fileDescriptor);
		fileBytesRequest.append("pathName", pathName);
		fileBytesRequest.append("position", String.valueOf(position));
		fileBytesRequest.append("length", String.valueOf(length));

		message = fileBytesRequest.toJson();

		return message;
	}

	public String fileBytesResponse(Document fileDescriptor, String pathName, boolean status, long position, long length, String content) {
		String message = null;

		Document fileBytesResponse = new Document();
		fileBytesResponse.append("command", "FILE_BYTES_RESPONSE");
		fileBytesResponse.append("fileDescriptor", fileDescriptor);
		fileBytesResponse.append("pathName", pathName);
		fileBytesResponse.append("position", String.valueOf(position));
		fileBytesResponse.append("length", String.valueOf(length));
		fileBytesResponse.append("content", content);

		if (status)
			fileBytesResponse.append("message", "successful read");
		else
			fileBytesResponse.append("message", "unsuccessful read");

		fileBytesResponse.append("status", String.valueOf(status));

		message = fileBytesResponse.toJson();

		return message;
	}
	
	public String authRequest(String identity)
	{
		String message = null;
		
		Document authRequest = new Document();
		authRequest.append("command", "AUTH_REQUEST");
		authRequest.append("identity", identity);
		message = authRequest.toJson();
		
		return message;
		
	}
	
	public String authResponse(boolean status)
	{
		String message = null;
		
		Document authResponse = new Document();
		authResponse.append("command", "AUTH_RESPONSE");
		if(status)
		{
			authResponse.append("AES128", encodeString);
			authResponse.append("status", status);
			authResponse.append("message", "public key found");
		}
		else
		{
			authResponse.append("status", status);
			authResponse.append("message", "public key not found");
		}
		message = authResponse.toJson();
		
		return message;
	}

	public String payload(String payloadString)
	{
		String message = null;
		
		Document payload = new Document();
		payload.append("payload",  payloadString);
		message = payload.toJson();
		return message;
	}
	
	
	
	
	public String listPeersRequest()
	{
		String message = null;
		
		Document listPeersRequest = new Document();
		listPeersRequest.append("command", "LIST_PEERS_REQUEST");
		message = listPeersRequest.toJson();
		
		return message;
	}
	
	public String listPeersResponse()
	{
		String message = null;
		
		Document listPeersResponse = new Document();
		listPeersResponse.append("command", "LIST_PEERS_RESPONSE");
		ArrayList<Document> hostPorts = new ArrayList<Document>();
		connectedPorts.forEach((port, host) -> {
			HostPort hostPort = new HostPort(host,Integer.parseInt(port));
			hostPorts.add(hostPort.toDoc());
		});
		listPeersResponse.append("peers", hostPorts);
		message = listPeersResponse.toJson();
		return message;
	}
	
	public String connectPeerRequest(String host, String port)
	{
		String message = null;
		
		Document connectPeerRequest = new Document();
		connectPeerRequest.append("command", "CONNECT_PEER_REQUEST");
		connectPeerRequest.append("host", host);
		connectPeerRequest.append("port", Integer.parseInt(port));
		message = connectPeerRequest.toJson();
		
		return message;
	}
	
	public String connectPeerResponse(String host, String port, boolean status)
	{
		String message = null;
		
		Document connectPeerResponse = new Document();
		connectPeerResponse.append("command", "CONNECT_PEER_RESPONSE");
		connectPeerResponse.append("host", host);
		connectPeerResponse.append("port", Integer.parseInt(port));
		connectPeerResponse.append("status", status);
		if(status)
			connectPeerResponse.append("message", "connected to peer");
		else
			connectPeerResponse.append("message", "connection failed");
		message = connectPeerResponse.toJson();
		
		return message;
	}
	
	public String disconnectPeerRequest(String host, String port)
	{
		String message = null;
		
		Document disconnectPeerRequest = new Document();
		disconnectPeerRequest.append("command", "DISCONNECT_PEER_REQUEST");
		disconnectPeerRequest.append("host", host);
		disconnectPeerRequest.append("port", Integer.parseInt(port));
		message = disconnectPeerRequest.toJson();
		
		return message;
	}
	
	public String disconnectPeerResponse(String host, String port, boolean status)
	{
		String message = null;
		
		Document disconnectPeerResponse = new Document();
		disconnectPeerResponse.append("command", "DISCONNECT_PEER_RESPONSE");
		disconnectPeerResponse.append("host", host);
		disconnectPeerResponse.append("port", Integer.parseInt(port));
		disconnectPeerResponse.append("status", status);
		if(status)
			disconnectPeerResponse.append("message", "disconnected from peer");
		else
			disconnectPeerResponse.append("message", "connection not active");
		message = disconnectPeerResponse.toJson();
		
		return message;
	}
}

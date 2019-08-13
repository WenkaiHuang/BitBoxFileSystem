package unimelb.bitbox;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.logging.Logger;
import java.net.*;

import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager;

public class FileSystemProcessUDP extends Thread {
	private static Logger log = Logger.getLogger(ServerMain.class.getName());
	private DatagramSocket udpSocket;
	private Thread t;
	private String host;
	private String port;
	private Document fileDescriptor;
	private PeerProtocolMessage peerProtocolMessage;
	private FileSystemManager fileSystemManager;

	private String pathName;
	private String md5;
	private long fileSize;
	private long lastModified;
	private long length;
	private long position;
	private long newLength;
	private long newLengthRp;
	private long blockSize = Long.parseLong(Configuration.getConfigurationValue("blockSize"));
	
	
	public FileSystemProcessUDP(String host, String port, DatagramSocket udpSocket, FileSystemManager fileSystemManager) {
		this.host = host;
		this.port = port;
		this.udpSocket = udpSocket;
		this.fileSystemManager = fileSystemManager;
	}

	public void start() {
		if (t == null) {
			t = new Thread(this);
			t.start();
		}
	}

	public void run() {
		try {
			int bufferSize = Integer.parseInt(Configuration.getConfigurationValue("minBuffer"));
			byte[] buffer = new byte[bufferSize];
			DatagramPacket input = new DatagramPacket(buffer, buffer.length);
			peerProtocolMessage = new PeerProtocolMessage(host, port);

			while (true) {
				
				udpSocket.receive(input);
				String json = new String(input.getData()).trim();
				if (!(json == null)) {
					this.processCommand(json);
					sleep(100);
				} else {
					udpSocket.receive(input);
					json = new String(input.getData()).trim();
				}

			}
		} catch (InterruptedException e) {
			log.warning(e.getMessage());
		} catch (UnsupportedEncodingException e) {
			log.warning(e.getMessage());
		} catch (IOException e) {
			log.warning(e.getMessage());
		} catch (NullPointerException e) {
			log.info("error in receiving command");
		}

	}

	public void processCommand(String json) {
		try {

			Document doc = Document.parse(json);
			String command = doc.getString("command");
			DatagramPacket output;

			log.info("received command [" + command + "] from " + host + ":" + port);
			log.info("received message: " + json);
			
			if (command != null && !command.isEmpty()) {
			
				switch (command) {
	
				case "FILE_CREATE_REQUEST":
	
					this.fileDescriptor = (Document) doc.get("fileDescriptor");
					this.md5 = fileDescriptor.getString("md5");
					this.lastModified = fileDescriptor.getLong("lastModified");
					this.fileSize = fileDescriptor.getLong("fileSize");
	
					this.pathName = doc.getString("pathName");
					this.position = 0;
	
					if (fileSize <= blockSize)
						this.length = fileSize;
					else if (fileSize > blockSize)
						this.length = blockSize;
	
					System.out.println("system detect FILE_CREATE_REQUEST attempt");
	
					if (fileSystemManager.isSafePathName(pathName)) {
						if ((!fileSystemManager.fileNameExists(pathName)
								&& !fileSystemManager.fileNameExists(pathName, md5))) {
							try {
								if (fileSystemManager.createFileLoader(pathName, md5, fileSize, lastModified)) {
									if (fileSystemManager.checkShortcut(pathName)) {
										
										String fileCreateResponse = peerProtocolMessage.fileCreateResponse(fileDescriptor, pathName,"file loader ready", true);
										output = new DatagramPacket(fileCreateResponse.getBytes(), fileCreateResponse.length(), InetAddress.getByName(host), Integer.parseInt(port));
										udpSocket.send(output);
										log.info("sending message: " + fileCreateResponse);
									} else {
										
										String fileCreateResponse = peerProtocolMessage.fileCreateResponse(fileDescriptor, pathName, "file loader ready", true);
										output = new DatagramPacket(fileCreateResponse.getBytes(), fileCreateResponse.length(), InetAddress.getByName(host), Integer.parseInt(port));
										udpSocket.send(output);
										log.info("sending message: " + fileCreateResponse);
	
										String fileBytesRequest = peerProtocolMessage.fileBytesRequest(fileDescriptor, pathName, position, length);
										output = new DatagramPacket(fileBytesRequest.getBytes(), fileBytesRequest.length(), InetAddress.getByName(host), Integer.parseInt(port));
										udpSocket.send(output);
										log.info("sending message: " + fileBytesRequest);
									}
	
								} else {
									if (fileSystemManager.cancelFileLoader(pathName)) {
										System.out.println("cancel the file loader");
										String fileCreateResponse = peerProtocolMessage.fileCreateResponse(fileDescriptor, pathName, "there was a problem creating the file", false);
										output = new DatagramPacket(fileCreateResponse.getBytes(), fileCreateResponse.length(), InetAddress.getByName(host), Integer.parseInt(port));
										udpSocket.send(output);
										log.info("sending message: " + fileCreateResponse);
									} else {
										String fileCreateResponse = peerProtocolMessage.fileCreateResponse(fileDescriptor, pathName, "there was a problem creating the file", false);
										output = new DatagramPacket(fileCreateResponse.getBytes(), fileCreateResponse.length(), InetAddress.getByName(host), Integer.parseInt(port));
										udpSocket.send(output);
										log.info("sending message: " + fileCreateResponse);
									}
								}
	
							} catch (NoSuchAlgorithmException e) {
								e.printStackTrace();
							} catch (IOException e) {
								e.printStackTrace();
							}
	
						} else {
							String fileCreateResponse = peerProtocolMessage.fileCreateResponse(fileDescriptor, pathName, "pathname already exists", false);
							output = new DatagramPacket(fileCreateResponse.getBytes(), fileCreateResponse.length(), InetAddress.getByName(host), Integer.parseInt(port));
							udpSocket.send(output);
							log.info("sending message: " + fileCreateResponse);
	
							if (fileSystemManager.modifyFileLoader(pathName, md5, lastModified)) {
								String fileModifyResponse = peerProtocolMessage.fileModifyResponse(fileDescriptor, pathName, "file loader ready", true);
								output = new DatagramPacket(fileModifyResponse.getBytes(), fileModifyResponse.length(), InetAddress.getByName(host), Integer.parseInt(port));
								udpSocket.send(output);
								log.info("sending message: " + fileModifyResponse);
								if (!fileSystemManager.checkShortcut(pathName)) {
									this.position = 0;
	
									if (fileSize <= blockSize)
										this.length = fileSize;
									else if (fileSize > blockSize)
										this.length = blockSize;
									
									String fileBytesRequest = peerProtocolMessage.fileBytesRequest(fileDescriptor, pathName, position, length);
									output = new DatagramPacket(fileBytesRequest.getBytes(), fileBytesRequest.length(), InetAddress.getByName(host), Integer.parseInt(port));
									udpSocket.send(output);
									log.info("sending message: " + fileBytesRequest);
								}
							} else {
								if (fileSystemManager.cancelFileLoader(pathName)) {
									System.out.println("cancel the file loader");
									String fileModifyResponse = peerProtocolMessage.fileModifyResponse(fileDescriptor, pathName, "there was a problem modifying the file", false);
									output = new DatagramPacket(fileModifyResponse.getBytes(), fileModifyResponse.length(), InetAddress.getByName(host), Integer.parseInt(port));
									udpSocket.send(output);
									log.info("sending message: " + fileModifyResponse);
								} else {
									String fileModifyResponse = peerProtocolMessage.fileModifyResponse(fileDescriptor, pathName, "there was a problem modifying the file", false);
									output = new DatagramPacket(fileModifyResponse.getBytes(), fileModifyResponse.length(), InetAddress.getByName(host), Integer.parseInt(port));
									udpSocket.send(output);
									log.info("sending message: " + fileModifyResponse);
								}
							}
						}
					} else {
						String fileCreateResponse = peerProtocolMessage.fileCreateResponse(fileDescriptor, pathName, "unsafe pathname given", false);
						output = new DatagramPacket(fileCreateResponse.getBytes(), fileCreateResponse.length(), InetAddress.getByName(host), Integer.parseInt(port));
						udpSocket.send(output);
						log.info("sending message: " + fileCreateResponse);
					}
					
					break;
					
					
				case "FILE_DELETE_REQUEST":
	
					this.fileDescriptor = (Document) doc.get("fileDescriptor");
					this.md5 = fileDescriptor.getString("md5");
					this.lastModified = fileDescriptor.getLong("lastModified");
					this.fileSize = fileDescriptor.getLong("fileSize");
	
					this.pathName = doc.getString("pathName");
					System.out.println("system detect FILE_DELETE_REQUEST attempt");
					try {
						System.out.println("isSafePathName: " + fileSystemManager.isSafePathName(pathName));
						System.out.println("fileNameExists: " + fileSystemManager.fileNameExists(pathName));
	
						System.out.println("deleteFile: " + fileSystemManager.deleteFile(pathName, lastModified, md5));
	
						if (fileSystemManager.isSafePathName(pathName)) {
							if (fileSystemManager.fileNameExists(pathName)) {
								if (fileSystemManager.deleteFile(pathName, lastModified, md5)) {
								
									String fileDeleteResponse = peerProtocolMessage.fileDeleteResponse(fileDescriptor, pathName, "file deleted", true);
									output = new DatagramPacket(fileDeleteResponse.getBytes(), fileDeleteResponse.length(), InetAddress.getByName(host), Integer.parseInt(port));
									udpSocket.send(output);
									log.info("sending message: "+ fileDeleteResponse);
									
								} else {
									
									String fileDeleteResponse = peerProtocolMessage.fileDeleteResponse(fileDescriptor, pathName, "there was a problem deleting the file", false);
									output = new DatagramPacket(fileDeleteResponse.getBytes(), fileDeleteResponse.length(), InetAddress.getByName(host), Integer.parseInt(port));
									udpSocket.send(output);
									log.info("sending message: "+ fileDeleteResponse);
								}
	
							} else {
								String fileDeleteResponse = peerProtocolMessage.fileDeleteResponse(fileDescriptor, pathName, "pathname does not exist", false);
								output = new DatagramPacket(fileDeleteResponse.getBytes(), fileDeleteResponse.length(), InetAddress.getByName(host), Integer.parseInt(port));
								udpSocket.send(output);
								log.info("sending message: "+ fileDeleteResponse);
							}
						} else {
							String fileDeleteResponse = peerProtocolMessage.fileDeleteResponse(fileDescriptor, pathName, "unsafe pathname given" , false);
							output = new DatagramPacket(fileDeleteResponse.getBytes(), fileDeleteResponse.length(), InetAddress.getByName(host), Integer.parseInt(port));
							udpSocket.send(output);
							log.info("sending message: "+ fileDeleteResponse);
						}
	
					} catch (NullPointerException e) {
						e.printStackTrace();
					}
					
					break;				
					
					
				case "FILE_MODIFY_REQUEST":
	
					this.fileDescriptor = (Document) doc.get("fileDescriptor");
					this.md5 = fileDescriptor.getString("md5");
					this.lastModified = fileDescriptor.getLong("lastModified");
					this.fileSize = fileDescriptor.getLong("fileSize");
	
					this.pathName = doc.getString("pathName");
	
					System.out.println("system detect FILE_MODIFY_REQUEST attempt");
					try {
						if (fileSystemManager.isSafePathName(pathName)) {
							if (fileSystemManager.fileNameExists(pathName)
									|| fileSystemManager.fileNameExists(pathName, md5)) {
								if (fileSystemManager.modifyFileLoader(pathName, md5, lastModified)) {
									
									String fileModifyResponse = peerProtocolMessage.fileModifyResponse(fileDescriptor, pathName, "file loader ready", true);
									output = new DatagramPacket(fileModifyResponse.getBytes(), fileModifyResponse.length(), InetAddress.getByName(host), Integer.parseInt(port));
									udpSocket.send(output);
									log.info("sending message: " + fileModifyResponse);
									if (!fileSystemManager.checkShortcut(pathName)) {
										this.position = 0;
	
										if (fileSize <= blockSize)
											this.length = fileSize;
										else if (fileSize > blockSize)
											this.length = blockSize;
										
										String fileBytesRequest = peerProtocolMessage.fileBytesRequest(fileDescriptor, pathName, position, length);
										output = new DatagramPacket(fileBytesRequest.getBytes(), fileBytesRequest.length(), InetAddress.getByName(host), Integer.parseInt(port));
										udpSocket.send(output);
										log.info("sending message: " + fileBytesRequest);
									}
								} else {
									if (fileSystemManager.cancelFileLoader(pathName)) {
										System.out.println("cancel the file loader");
										String fileModifyResponse = peerProtocolMessage.fileModifyResponse(fileDescriptor, pathName, "there was a problem modifying the file", false);
										output = new DatagramPacket(fileModifyResponse.getBytes(), fileModifyResponse.length(), InetAddress.getByName(host), Integer.parseInt(port));
										udpSocket.send(output);
										log.info("sending message: " + fileModifyResponse);
									} else {
										String fileModifyResponse = peerProtocolMessage.fileModifyResponse(fileDescriptor, pathName, "there was a problem modifying the file", false);
										output = new DatagramPacket(fileModifyResponse.getBytes(), fileModifyResponse.length(), InetAddress.getByName(host), Integer.parseInt(port));
										udpSocket.send(output);
										log.info("sending message: " + fileModifyResponse);
									}
								}
							} else {
								String fileModifyResponse = peerProtocolMessage.fileModifyResponse(fileDescriptor, pathName, "pathname does not exist", false);
								output = new DatagramPacket(fileModifyResponse.getBytes(), fileModifyResponse.length(), InetAddress.getByName(host), Integer.parseInt(port));
								udpSocket.send(output);
								log.info("sending message: " + fileModifyResponse);
	
								if (fileSystemManager.modifyFileLoader(pathName, md5, lastModified)) {
									fileModifyResponse = peerProtocolMessage.fileModifyResponse(fileDescriptor, pathName, "file already exists with matching content", false);
									output = new DatagramPacket(fileModifyResponse.getBytes(), fileModifyResponse.length(), InetAddress.getByName(host), Integer.parseInt(port));
									udpSocket.send(output);
									log.info("sending message: " + fileModifyResponse);
								}
	
							}
						} else {
							String fileModifyResponse = peerProtocolMessage.fileModifyResponse(fileDescriptor, pathName, "unsafe pathname given", false);
							output = new DatagramPacket(fileModifyResponse.getBytes(), fileModifyResponse.length(), InetAddress.getByName(host), Integer.parseInt(port));
							udpSocket.send(output);
							log.info("sending message: " + fileModifyResponse);
							if (fileSystemManager.modifyFileLoader(pathName, md5, lastModified)) {
								fileModifyResponse = peerProtocolMessage.fileModifyResponse(fileDescriptor, pathName, "file already exists with matching content", false);
								output = new DatagramPacket(fileModifyResponse.getBytes(), fileModifyResponse.length(), InetAddress.getByName(host), Integer.parseInt(port));
								udpSocket.send(output);
								log.info("sending message: " + fileModifyResponse);
							}
						}
	
					} catch (IOException e) {
						e.printStackTrace();
					}
	
					break;
					
					
				case "DIRECTORY_CREATE_REQUEST":
					this.pathName = doc.getString("pathName");
					System.out.println("system detect DIRECTORY_CREATE_REQUEST attempt");
					if (fileSystemManager.isSafePathName(pathName)) {
						if (!fileSystemManager.dirNameExists(pathName)) {
							if (fileSystemManager.makeDirectory(pathName)) {
								String directoryCreateResponse = peerProtocolMessage.directoryCreateResponse(pathName, "directory created", true);
								output = new DatagramPacket(directoryCreateResponse.getBytes(), directoryCreateResponse.length(), InetAddress.getByName(host), Integer.parseInt(port));
								udpSocket.send(output);
								log.info("sending message: " + directoryCreateResponse);
							}
	
							else {
								String directoryCreateResponse = peerProtocolMessage.directoryCreateResponse(pathName, "there was a problem creating the directory", false);
								output = new DatagramPacket(directoryCreateResponse.getBytes(), directoryCreateResponse.length(), InetAddress.getByName(host), Integer.parseInt(port));
								udpSocket.send(output);
								log.info("sending message: " + directoryCreateResponse);
							}
						} else {
							String directoryCreateResponse = peerProtocolMessage.directoryCreateResponse(pathName, "pathname already exists", false);
							output = new DatagramPacket(directoryCreateResponse.getBytes(), directoryCreateResponse.length(), InetAddress.getByName(host), Integer.parseInt(port));
							udpSocket.send(output);
							log.info("sending message: " + directoryCreateResponse);
						}
	
					} else {
						String directoryCreateResponse = peerProtocolMessage.directoryCreateResponse(pathName, "unsafe pathname given", false);
						output = new DatagramPacket(directoryCreateResponse.getBytes(), directoryCreateResponse.length(), InetAddress.getByName(host), Integer.parseInt(port));
						udpSocket.send(output);
						log.info("sending message: " + directoryCreateResponse);
					}
					break;
					
					
				case "DIRECTORY_DELETE_REQUEST":
					this.pathName = doc.getString("pathName");
					System.out.println("system detect DIRECTORY_DELETE_REQUEST attempt");
					if (fileSystemManager.isSafePathName(pathName)) {
						if (fileSystemManager.dirNameExists(pathName)) {
							if (fileSystemManager.deleteDirectory(pathName)) {
								String directoryDeleteResponse = peerProtocolMessage.directoryDeleteResponse(pathName, "directory deleted", true);
								output = new DatagramPacket(directoryDeleteResponse.getBytes(), directoryDeleteResponse.length(), InetAddress.getByName(host), Integer.parseInt(port));
								udpSocket.send(output);
								log.info("sending message: " + directoryDeleteResponse);
							} else {
								String directoryDeleteResponse = peerProtocolMessage.directoryDeleteResponse(pathName, "there was a problem deleting the directory", false);
								output = new DatagramPacket(directoryDeleteResponse.getBytes(), directoryDeleteResponse.length(), InetAddress.getByName(host), Integer.parseInt(port));
								udpSocket.send(output);
								log.info("sending message: " + directoryDeleteResponse);
							}
						} else {
							String directoryDeleteResponse = peerProtocolMessage.directoryDeleteResponse(pathName, "pathname does not exist", false);
							output = new DatagramPacket(directoryDeleteResponse.getBytes(), directoryDeleteResponse.length(), InetAddress.getByName(host), Integer.parseInt(port));
							udpSocket.send(output);
							log.info("sending message: " + directoryDeleteResponse);
						}
					} else {
						String directoryDeleteResponse = peerProtocolMessage.directoryDeleteResponse(pathName, "unsafe pathname given", false);
						output = new DatagramPacket(directoryDeleteResponse.getBytes(), directoryDeleteResponse.length(), InetAddress.getByName(host), Integer.parseInt(port));
						udpSocket.send(output);
						log.info("sending message: " + directoryDeleteResponse);
					}
					
					break;
	
				case "FILE_BYTES_REQUEST":
	
					this.fileDescriptor = (Document) doc.get("fileDescriptor");
					this.md5 = fileDescriptor.getString("md5");
					this.lastModified = fileDescriptor.getLong("lastModified");
					this.fileSize = fileDescriptor.getLong("fileSize");
	
					this.pathName = doc.getString("pathName");
					this.position = Long.parseLong(doc.getString("position"));
					this.length = Long.parseLong(doc.getString("length"));
	
					// long newPosition = position + length;
					long remainFileSize = fileSize - position;
	
					if (remainFileSize <= blockSize)
						this.newLength = remainFileSize;
					else
						this.newLength = blockSize;
					
					System.out.println("system detect FILE_BYTES_REQUEST attempt");
			
					ByteBuffer rf = fileSystemManager.readFile(md5, position, newLength);
					if (rf != null)
					{
	
						String rf_Byte = new String(rf.array());
						String content = Base64.getEncoder().encodeToString(rf_Byte.getBytes("UTF-8"));
						String fileBytesResponse = peerProtocolMessage.fileBytesResponse(fileDescriptor, pathName, true, position, newLength, content);
						output = new DatagramPacket(fileBytesResponse.getBytes(), fileBytesResponse.length(), InetAddress.getByName(host), Integer.parseInt(port));
						udpSocket.send(output);
						log.info("sending message: " + fileBytesResponse);
					} else {
						String fileBytesResponse = peerProtocolMessage.fileBytesResponse(fileDescriptor, pathName, false, position, newLength, null);
						output = new DatagramPacket(fileBytesResponse.getBytes(), fileBytesResponse.length(), InetAddress.getByName(host), Integer.parseInt(port));
						udpSocket.send(output);
						log.info("sending message: " + fileBytesResponse);
					}
					
					break;
					
				case "FILE_BYTES_RESPONSE":
	
					this.fileDescriptor = (Document) doc.get("fileDescriptor");
					this.md5 = fileDescriptor.getString("md5");
					this.lastModified = fileDescriptor.getLong("lastModified");
					this.fileSize = fileDescriptor.getLong("fileSize");
	
					this.pathName = doc.getString("pathName");
					this.position = Long.parseLong(doc.getString("position"));
					this.length = Long.parseLong(doc.getString("length"));
	
					String con_1 = doc.getString("content");
					boolean status = Boolean.parseBoolean(doc.getString("status"));
	
					if (status) {
						System.out.println("system detect FILE_BYTES_RESPONSE attempt");
						byte[] decodeBase64 = Base64.getDecoder().decode(con_1);
						ByteBuffer src = ByteBuffer.wrap(decodeBase64);
	
						if (fileSystemManager.writeFile(pathName, src, position)) {
							if (fileSystemManager.checkWriteComplete(pathName)) {
								System.out.println("the file was completed");
								log.info("the file was completed");
							} else {
								System.out.println("the loader is still waiting for more data");
								log.info("the loader is still waiting for more data");
								long newPositionRp = position + length;
								long remainFileSizeRp = fileSize - newPositionRp;
	
								if (remainFileSizeRp <= blockSize)
									this.newLengthRp = remainFileSizeRp;
								else if (remainFileSizeRp > blockSize)
									this.newLengthRp = blockSize;
								
								String fileBytesRequest = peerProtocolMessage.fileBytesRequest(fileDescriptor, pathName, newPositionRp, newLengthRp);
								output = new DatagramPacket(fileBytesRequest.getBytes(), fileBytesRequest.length(), InetAddress.getByName(host), Integer.parseInt(port));
								udpSocket.send(output);
								log.info("sending message: " + fileBytesRequest);
							}
						} else
							log.info("there was an error writing the bytes");
					}
					
					break;
				}
			}

		} catch (SocketException e) {
			log.warning("CONNECTION_TERMINATED from " + host + ":" + port);
		} catch (IOException e) {
			log.warning(e.getMessage());
		} catch (Exception e) {
			log.warning(e.getMessage());;
		}
	}

}
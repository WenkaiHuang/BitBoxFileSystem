package unimelb.bitbox;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.logging.Logger;

import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager;

public class FileSystemProcess extends Thread {
	private static Logger log = Logger.getLogger(ServerMain.class.getName());
	private Socket socket;
	private Thread t;
	private String host;
	private String port;
	private BufferedReader inputStream;
	private BufferedWriter outputStream;
	private PeerProtocolMessage peerProtocolMessage;
	private FileSystemManager fileSystemManager;

	private Document fileDescriptor;
	private String pathName;
	private String md5;
	private long fileSize;
	private long lastModified;
	private long length;
	private long position;
	private long newLength;
	private long newLengthRp;
	private long blockSize = Long.parseLong(Configuration.getConfigurationValue("blockSize"));
	
	public FileSystemProcess(String host, String port, Socket socket, FileSystemManager fileSystemManager) {
		this.host = host;
		this.port = port;
		this.socket = socket;
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
			inputStream = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF8"));
			outputStream = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF8"));
			peerProtocolMessage = new PeerProtocolMessage(host, port);

			while (true) {
				String json = inputStream.readLine();
				if (json != null) {

					this.processCommand(json);
					sleep(100);
				}

			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	public void processCommand(String json) {
		try {

			Document doc = Document.parse(json);

			String command = doc.getString("command");

			log.info("received command [" + command + "] from " + host + ":" + port);
			log.info("received message: " + json);

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

									outputStream.write(peerProtocolMessage.fileCreateResponse(fileDescriptor, pathName,
											"file loader ready", true));
									outputStream.newLine();
									outputStream.flush();
									log.info("sending message: " + peerProtocolMessage
											.fileCreateResponse(fileDescriptor, pathName, "file loader ready", true));
								} else {
									outputStream.write(peerProtocolMessage.fileCreateResponse(fileDescriptor, pathName,
											"file loader ready", true));
									outputStream.newLine();
									outputStream.flush();
									log.info("sending message: " + peerProtocolMessage
											.fileCreateResponse(fileDescriptor, pathName, "file loader ready", true));

									outputStream.write(peerProtocolMessage.fileBytesRequest(fileDescriptor, pathName,
											position, length));
									outputStream.newLine();
									outputStream.flush();
									log.info("sending message: " + peerProtocolMessage.fileBytesRequest(fileDescriptor,
											pathName, position, length));
								}

							} else {
								if (fileSystemManager.cancelFileLoader(pathName)) {
									System.out.println("cancel the file loader");
									outputStream.write(peerProtocolMessage.fileCreateResponse(fileDescriptor, pathName,
											"there was a problem creating the file", false));
									outputStream.newLine();
									outputStream.flush();
									log.info("sending message: " + peerProtocolMessage.fileCreateResponse(
											fileDescriptor, pathName, "there was a problem creating the file", false));
								} else {
									outputStream.write(peerProtocolMessage.fileCreateResponse(fileDescriptor, pathName,
											"there was a problem creating the file", false));
									outputStream.newLine();
									outputStream.flush();
									log.info("sending message: " + peerProtocolMessage.fileCreateResponse(
											fileDescriptor, pathName, "there was a problem creating the file", false));
								}
							}
						} catch (NoSuchAlgorithmException e) {
							e.printStackTrace();
						} catch (IOException e) {
							e.printStackTrace();
						}

					} else {
						outputStream.write(peerProtocolMessage.fileCreateResponse(fileDescriptor, pathName,
								"pathname already exists", false));
						outputStream.newLine();
						outputStream.flush();
						log.info("sending message: " + peerProtocolMessage.fileCreateResponse(fileDescriptor, pathName,
								"pathname already exists", false));

						if (fileSystemManager.modifyFileLoader(pathName, md5, lastModified)) {
							outputStream.write(peerProtocolMessage.fileModifyResponse(fileDescriptor, pathName,
									"file loader ready", true));
							outputStream.newLine();
							outputStream.flush();
							log.info("sending message: " + peerProtocolMessage.fileModifyResponse(fileDescriptor,
									pathName, "file loader ready", true));
							if (!fileSystemManager.checkShortcut(pathName)) {
								this.position = 0;

								if (fileSize <= blockSize)
									this.length = fileSize;
								else if (fileSize > blockSize)
									this.length = blockSize;
								outputStream.write(peerProtocolMessage.fileBytesRequest(fileDescriptor, pathName,
										position, length));
								outputStream.newLine();
								outputStream.flush();
								log.info("sending message: " + peerProtocolMessage.fileBytesRequest(fileDescriptor,
										pathName, position, length));
							}
						} else {
							if (fileSystemManager.cancelFileLoader(pathName)) {
								System.out.println("cancel the file loader");
								outputStream.write(peerProtocolMessage.fileModifyResponse(fileDescriptor, pathName,
										"there was a problem modifying the file", false));
								outputStream.newLine();
								outputStream.flush();
								log.info("sending message: " + peerProtocolMessage.fileModifyResponse(
										fileDescriptor, pathName, "there was a problem modifying the file", false));
							} else {
								outputStream.write(peerProtocolMessage.fileModifyResponse(fileDescriptor, pathName,
										"there was a problem modifying the file", false));
								outputStream.newLine();
								outputStream.flush();
								log.info("sending message: " + peerProtocolMessage.fileModifyResponse(
										fileDescriptor, pathName, "there was a problem modifying the file", false));
							}
						}
					}
				} else {
					outputStream.write(peerProtocolMessage.fileCreateResponse(fileDescriptor, pathName,
							"unsafe pathname given", false));
					outputStream.newLine();
					outputStream.flush();
					log.info("sending message: " + peerProtocolMessage.fileCreateResponse(fileDescriptor, pathName,
							"unsafe pathname given", false));
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
						if (fileSystemManager.deleteFile(pathName, lastModified, md5)) 
							
								{
									outputStream.write(
											peerProtocolMessage.fileDeleteResponse(fileDescriptor, pathName, "file deleted", true));
									outputStream.newLine();
									outputStream.flush();
									log.info("sending message: "
											+ peerProtocolMessage.fileDeleteResponse(fileDescriptor, pathName, "file deleted", true));
									
								}
							 else {
								outputStream
										.write(peerProtocolMessage.fileDeleteResponse(fileDescriptor, pathName, "there was a problem deleting the file", false));
								outputStream.newLine();
								outputStream.flush();
								log.info("sending message: "
										+ peerProtocolMessage.fileDeleteResponse(fileDescriptor, pathName, "there was a problem deleting the file", false));
							}

						} else {
							outputStream.write(peerProtocolMessage.fileDeleteResponse(fileDescriptor, pathName, "pathname does not exist", false));
							outputStream.newLine();
							outputStream.flush();
							log.info("sending message: "
									+ peerProtocolMessage.fileDeleteResponse(fileDescriptor, pathName, "pathname does not exist", false));
						}
					} else

					{
						outputStream.write(peerProtocolMessage.fileDeleteResponse(fileDescriptor, pathName, "unsafe pathname given" , false));
						outputStream.newLine();
						outputStream.flush();
						log.info("sending message: "
								+ peerProtocolMessage.fileDeleteResponse(fileDescriptor, pathName, "unsafe pathname given", false));
					}

				} catch (NullPointerException e1) {
					e1.printStackTrace();
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
								outputStream.write(peerProtocolMessage.fileModifyResponse(fileDescriptor, pathName,
										"file loader ready", true));
								outputStream.newLine();
								outputStream.flush();
								log.info("sending message: " + peerProtocolMessage.fileModifyResponse(fileDescriptor,
										pathName, "file loader ready", true));
								if (!fileSystemManager.checkShortcut(pathName)) {
									this.position = 0;

									if (fileSize <= blockSize)
										this.length = fileSize;
									else if (fileSize > blockSize)
										this.length = blockSize;
									outputStream.write(peerProtocolMessage.fileBytesRequest(fileDescriptor, pathName,
											position, length));
									outputStream.newLine();
									outputStream.flush();
									log.info("sending message: " + peerProtocolMessage.fileBytesRequest(fileDescriptor,
											pathName, position, length));
								}
							} else {
								if (fileSystemManager.cancelFileLoader(pathName)) {
									System.out.println("cancel the file loader");
									outputStream.write(peerProtocolMessage.fileModifyResponse(fileDescriptor, pathName,
											"there was a problem modifying the file", false));
									outputStream.newLine();
									outputStream.flush();
									log.info("sending message: " + peerProtocolMessage.fileModifyResponse(
											fileDescriptor, pathName, "there was a problem modifying the file", false));
								} else {
									outputStream.write(peerProtocolMessage.fileModifyResponse(fileDescriptor, pathName,
											"there was a problem modifying the file", false));
									outputStream.newLine();
									outputStream.flush();
									log.info("sending message: " + peerProtocolMessage.fileModifyResponse(
											fileDescriptor, pathName, "there was a problem modifying the file", false));
								}
							}
						} else {
							outputStream.write(peerProtocolMessage.fileModifyResponse(fileDescriptor, pathName,
									"pathname does not exist", false));
							outputStream.newLine();
							outputStream.flush();
							log.info("sending message: " + peerProtocolMessage.fileModifyResponse(fileDescriptor,
									pathName, "pathname does not exist", false));

							if (fileSystemManager.modifyFileLoader(pathName, md5, lastModified)) {
								outputStream.write(peerProtocolMessage.fileModifyResponse(fileDescriptor, pathName,
										"file already exists with matching content", false));
								outputStream.newLine();
								outputStream.flush();
								log.info("sending message: " + peerProtocolMessage.fileModifyResponse(fileDescriptor,
										pathName, "file already exists with matching content", false));
							}

						}
					} else {
						outputStream.write(peerProtocolMessage.fileModifyResponse(fileDescriptor, pathName,
								"unsafe pathname given", false));
						outputStream.newLine();
						outputStream.flush();
						log.info("sending message: " + peerProtocolMessage.fileModifyResponse(fileDescriptor, pathName,
								"unsafe pathname given", false));
						if (fileSystemManager.modifyFileLoader(pathName, md5, lastModified)) {
							outputStream.write(peerProtocolMessage.fileModifyResponse(fileDescriptor, pathName,
									"file already exists with matching content", false));
							outputStream.newLine();
							outputStream.flush();
							log.info("sending message: " + peerProtocolMessage.fileModifyResponse(fileDescriptor,
									pathName, "file already exists with matching content", false));
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
							outputStream.write(peerProtocolMessage.directoryCreateResponse(pathName, "directory created", true));
							outputStream.newLine();
							outputStream.flush();
							log.info("sending message: " + peerProtocolMessage.directoryCreateResponse(pathName, "directory created", true));
						}

						else {
							outputStream.write(peerProtocolMessage.directoryCreateResponse(pathName, "there was a problem creating the directory", false));
							outputStream.newLine();
							outputStream.flush();
							log.info(
									"sending message: " + peerProtocolMessage.directoryCreateResponse(pathName, "there was a problem creating the directory", false));
						}
					} else {
						outputStream.write(peerProtocolMessage.directoryCreateResponse(pathName, "pathname already exists", false));
						outputStream.newLine();
						outputStream.flush();
						log.info("sending message: " + peerProtocolMessage.directoryCreateResponse(pathName, "pathname already exists", false));
					}

				} else {
					outputStream.write(peerProtocolMessage.directoryCreateResponse(pathName, "unsafe pathname given", false));
					outputStream.newLine();
					outputStream.flush();
					log.info("sending message: " + peerProtocolMessage.directoryCreateResponse(pathName, "unsafe pathname given", false));
				}
				break;

				
			case "DIRECTORY_DELETE_REQUEST":
				this.pathName = doc.getString("pathName");
				System.out.println("system detect DIRECTORY_DELETE_REQUEST attempt");
				if (fileSystemManager.isSafePathName(pathName)) {
					if (fileSystemManager.dirNameExists(pathName)) {
						if (fileSystemManager.deleteDirectory(pathName)) {
							outputStream.write(peerProtocolMessage.directoryDeleteResponse(pathName, "directory deleted", true));
							outputStream.newLine();
							outputStream.flush();
							log.info("sending message: " + peerProtocolMessage.directoryDeleteResponse(pathName, "directory deleted", true));
						}else
						{
							outputStream.write(peerProtocolMessage.directoryDeleteResponse(pathName, "there was a problem deleting the directory", false));
							outputStream.newLine();
							outputStream.flush();
							log.info("sending message: " + peerProtocolMessage.directoryDeleteResponse(pathName, "there was a problem deleting the directory" , false));
						}
					} else {
						outputStream.write(peerProtocolMessage.directoryDeleteResponse(pathName, "pathname does not exist", false));
						outputStream.newLine();
						outputStream.flush();
						log.info("sending message: " + peerProtocolMessage.directoryDeleteResponse(pathName, "pathname does not exist", false));
					}
				} else {
					outputStream.write(peerProtocolMessage.directoryDeleteResponse(pathName, "unsafe pathname given", false));
					outputStream.newLine();
					outputStream.flush();
					log.info("sending message: " + peerProtocolMessage.directoryDeleteResponse(pathName, "unsafe pathname given", false));
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
					outputStream.write(peerProtocolMessage.fileBytesResponse(fileDescriptor, pathName, true, position,
							newLength, content));
					outputStream.newLine();
					outputStream.flush();
					log.info("sending message: " + peerProtocolMessage.fileBytesResponse(fileDescriptor, pathName, true,
							position, newLength, content));
				} else

				{
					outputStream.write(peerProtocolMessage.fileBytesResponse(fileDescriptor, pathName, false, position,
							newLength, null));
					outputStream.newLine();
					outputStream.flush();
					log.info("sending message: " + peerProtocolMessage.fileBytesResponse(fileDescriptor, pathName,
							false, newLength, position, null));
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
							outputStream.write(peerProtocolMessage.fileBytesRequest(fileDescriptor, pathName,
									newPositionRp, newLengthRp));
							outputStream.newLine();
							outputStream.flush();
							log.info("sending message: " + peerProtocolMessage.fileBytesRequest(fileDescriptor,
									pathName, newPositionRp, newLengthRp));
						}
					} else
						log.info("there was an error writing the bytes");
				}
				break;
			}
		} catch (SocketException e) {
			log.warning("CONNECTION_TERMINATED from " + host + ":" + port);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
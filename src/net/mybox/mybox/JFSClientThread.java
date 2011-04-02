

package net.mybox.mybox;

import java.net.*;
import java.io.*;
import jfs.conf.*;
import jfs.server.*;
import jfs.shell.*;


public class JFSClientThread extends Thread {

  private Socket socket = null;
  private Client client = null;
  private JFSConfig config = null;

  public JFSClientThread(Client _client, Socket _socket) {
    socket = _socket;
    client = _client;

    config = JFSConfig.getInstance();
    config.clean();
    config.addDirectoryPair(new JFSDirectoryPair("/home/jono/Desktop/jfilesync/test/dirs/source", "ext://localhost"));

    start();
  }


	/**
	 * @see java.lang.Runnable#run()
	 */
	public void run() {
		JFSText t = JFSText.getInstance();

    Client.printMessage("Starting JFSClientThread " + socket);

    JFSShell.startShell(true);

		try {
			try {
				while (!interrupted()) {
					handleClient();
				}
			} catch (SocketTimeoutException e) {
				JFSLog.getOut().getStream().println(t.get("error.timeout"));
			}
//			server.getSockets().remove(socket);
			socket.shutdownInput();
			socket.shutdownOutput();
			socket.close();
		} catch (SocketException e) {

      Client.printMessage("SocketException");
      
			// Ignore socket exceptions...
		} catch (EOFException e) {

      Client.printMessage("EOFException");

			// Ignore end-of-file exceptions thrown, when the JFS client is
			// shut down before the client socket on server side terminates.
		} catch (Exception e) {
			JFSLog.getErr().getStream().println(
					t.get("error.external") + " " + e);
		}
	}

	/**
	 * Reads the contents of the input stream.
	 *
	 * @throws SocketException
	 *             Thrown in case of socket problems.
	 * @throws IOException
	 *             Thrown in case of IO problems.
	 * @throws ClassNotFoundException
	 *             Thrown if the transmission class is missing.
	 */
	private void handleClient() throws SocketException, IOException,
			ClassNotFoundException {
    
    Client.printMessage("handleClient1");


		// Translation object:
		JFSText text = JFSText.getInstance();

    Client.printMessage("handleClient2");

		// Read from client:
		InputStream in = socket.getInputStream();
		ObjectInputStream oi = new ObjectInputStream(in);
		JFSTransmission t = (JFSTransmission) oi.readObject();
		JFSFileInfo info = t.getInfo();

    Client.printMessage("handleClient3");

		// Check for authentication:
		if (!t.getPassphrase().equals(
				JFSConfig.getInstance().getServerPassPhrase())) {
			JFSLog.getOut().getStream().println(
					text.get("cmd.server.accessDenied"));

			return;
		}

		// Write to client:
		OutputStream out = socket.getOutputStream();
		ObjectOutputStream oo;
		File file;
		boolean success;

		switch (t.getCommand()) {
		case JFSTransmission.CMD_GET_INFO:
			JFSLog.getOut().getStream().println(
					text.get("cmd.server.gettingInfo") + " "
							+ info.getVirtualPath());
			info.update();
			oo = new ObjectOutputStream(out);
			oo.writeObject(info);

			break;

		case JFSTransmission.CMD_PUT_INFO:
			JFSLog.getOut().getStream().println(
					text.get("cmd.server.puttingInfo") + " "
							+ info.getVirtualPath());
			success = info.updateFileSystem();
			oo = new ObjectOutputStream(out);
			oo.writeObject(new Boolean(success));

			break;

		case JFSTransmission.CMD_GET_CONTENTS:
			JFSLog.getOut().getStream().println(
					text.get("cmd.server.gettingContents") + " "
							+ info.getVirtualPath());
			file = info.complete();

			FileInputStream inFile = new FileInputStream(file);
			JFSServer.transferContent(inFile, out, file.length());
			inFile.close();

			break;

		case JFSTransmission.CMD_PUT_CONTENTS:
			JFSLog.getOut().getStream().println(
					text.get("cmd.server.puttingContents") + " "
							+ info.getVirtualPath());
			file = info.complete();

			if (file.exists())
				file.delete();

			oo = new ObjectOutputStream(out);
			oo.writeObject(info);

			FileOutputStream outFile = new FileOutputStream(info.getPath());
			success = JFSServer.transferContent(in, outFile, info.getLength());
			outFile.close();

			// Updates the file system; that is sets last modified and can
			// write property:
			if (!success) {
				file.delete();
			} else {
				if (!info.updateFileSystem()) {
					JFSLog.getOut().getStream().println(
							text.get("error.update"));
					file.delete();
				}
			}

			break;

		case JFSTransmission.CMD_MKDIR:
			JFSLog.getOut().getStream().println(
					text.get("cmd.server.mkdir") + " " + info.getVirtualPath());
			file = info.complete();
			success = true;

			if (!file.exists())
				success = file.mkdir();

			if (success)
				info.setExists(true);

			oo = new ObjectOutputStream(out);
			oo.writeObject(info);

			break;

		case JFSTransmission.CMD_DELETE:
			JFSLog.getOut().getStream()
					.println(
							text.get("cmd.server.delete") + " "
									+ info.getVirtualPath());
			file = info.complete();
			success = true;

			if (file.exists())
				success = file.delete();

			oo = new ObjectOutputStream(out);
			oo.writeObject(new Boolean(success));

			break;

		case JFSTransmission.CMD_IS_ALIVE:
			JFSLog.getOut().getStream().println(text.get("cmd.server.isAlive"));
			oo = new ObjectOutputStream(out);
			oo.writeObject(new Boolean(true));

			break;

		case JFSTransmission.CMD_IS_SHUTDOWN:
			JFSLog.getOut().getStream()
					.println(text.get("cmd.server.shutdown"));
			interrupt();
			JFSServerFactory.getInstance().getServer().stopServer();

			break;
		}
	}


}

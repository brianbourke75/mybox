/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.mybox.mybox;

import java.net.*;
import java.io.*;
import java.util.*;
import jfs.server.*;
import jfs.conf.*;

/**
 *
 */
public class JFSServerThread extends Thread {

  private HashMap<Integer, JFSServerInstance> jfsClients = new HashMap<Integer, JFSServerInstance>();
  private ServerSocket jfsServerSocket = null;

  int port = 55202;

  private void addClient(Socket socket) {

    Server.printMessage("Attaching client to server " + socket.getPort());

    JFSServerInstance thisClient = new JFSServerInstance(this, socket, "/home/jono/Desktop/jfilesync/test/dirs/target");
    
    //JFSClient handler = new JFSClient(socket);
    jfsClients.put(socket.getPort(), thisClient);

    thisClient.start();
		//handler.start();
  }

  public JFSServerThread() {
    start();
  }


	/**
	 * @see Runnable#run()
	 */
	public void run() {
		JFSText t = JFSText.getInstance();
    /*
		PrintStream out = JFSLog.getOut().getStream();
		out.println(t.get("cmd.server.start"));
		out.println(t.get("general.appName") + " "
				+ JFSConst.getInstance().getString("jfs.version"));
*/

    Server.printMessage(t.get("cmd.server.start"));
    Server.printMessage(t.get("general.appName") + " "
				+ JFSConst.getInstance().getString("jfs.version"));

		try {
//			int port = JFSConfig.getInstance().getServerPort();
			jfsServerSocket = new ServerSocket(port);
      Server.printMessage("JFS Server started: " + jfsServerSocket);

			// Start server loop:

      while (true) {
        try {
          Server.printMessage("JFSServerThread Waiting for a client ...");
          addClient(jfsServerSocket.accept());
        } catch (IOException ioe) {
          Server.printMessage("JFSServerThread Server accept error: " + ioe);
        }
      }

			//serverSocket.close();
		} catch (SocketException e) {
			// Ignore socket exceptions...
		} catch (Exception e) {
			JFSLog.getErr().getStream().println(
					t.get("error.external") + " " + e);
		}

		//out.println(t.get("cmd.server.stop"));
	}


	/**
	 * Transfers the contents of two streams.
	 *
	 * @param in
	 *            The input stream.
	 * @param out
	 *            The output stream.
	 * @param length
	 *            The number of bytes to transfer.
	 * @return True if the operation was performed successfully.
	 */
	public static boolean transferContent(InputStream in, OutputStream out,
			long length) {
		JFSText t = JFSText.getInstance();
		boolean success = true;
		long transferedBytes = 0;

		try {
			byte[] buf = new byte[JFSConfig.getInstance().getBufferSize()];
			int len;
			int maxLen = JFSConfig.getInstance().getBufferSize();

			if (length < maxLen)
				maxLen = (int) length;

			while (transferedBytes < length
					&& (len = in.read(buf, 0, maxLen)) > 0) {
				out.write(buf, 0, len);
				transferedBytes += len;

				long r = length - transferedBytes;
				if (r < maxLen)
					maxLen = (int) r;
			}
		} catch (SocketTimeoutException e) {
			JFSLog.getOut().getStream().println(t.get("error.timeout"));
			success = false;
		} catch (SocketException e) {
			JFSLog.getOut().getStream()
					.println(t.get("error.socket") + " " + e);
			success = false;
		} catch (IOException e) {
			JFSLog.getErr().getStream().println(t.get("error.io") + " " + e);
			success = false;
		}

		if (transferedBytes != length)
			success = false;

		return success;
	}



}

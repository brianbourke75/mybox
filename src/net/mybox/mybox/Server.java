/**
    Mybox version 0.1.0
    https://github.com/mybox/mybox

    Copyright (C) 2011  Jono Finger (jono@foodnotblogs.com)

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License along
    with this program; if not it can be found here:
    http://www.gnu.org/licenses/gpl-2.0.html
 */


package net.mybox.mybox;

import java.io.*;
import java.net.*;
import java.util.*;
import org.apache.commons.io.FileUtils.*;
import org.apache.commons.cli.*;

/**
 * The server component of the system is an executable that clients talk to
 */
public class Server {

  // list of connected clients
  private HashMap<Integer, ServerClientConnection> clients = new HashMap<Integer, ServerClientConnection>();
  
  // map of userId => set of all connected clients that belong to that user
  private HashMap<String, HashSet<Integer>> multiClientMap = new HashMap<String, HashSet<Integer>>();

  private ServerSocket server = null;
  public static int maxClients = 20;  // defaults
  public static int defaultQuota = 50;  // megabytes
  public static int port = Common.defaultCommunicationPort;

  ServerDB accountsDb = null;

  public static String defaultAccountsDbFile = null;
  public static String defaultConfigFile = null;
  public static final String logFile = "/tmp/mybox_server.log";

  public static String serverBaseDir = System.getProperty("user.home") + "/mbServerSpace";

  static {
    updatePaths();
  }

  public void updateMultiMap(String id, Integer handle) {

    if (multiClientMap.containsKey(id)) {
      HashSet<Integer> thisMap = multiClientMap.get(id);
      thisMap.add(handle);
      multiClientMap.put(id, thisMap);  // should overwrite the old map
    } else {
      HashSet<Integer> thisMap = new HashSet<Integer>();
      thisMap.add(handle);
      multiClientMap.put(id, thisMap);
    }
  }

  /**
   * To be called at static init time, or after the global app home path has been changed
   */
  public static void updatePaths() {
    defaultAccountsDbFile = Common.getAppHome() + "/mybox_server_db.xml";
    defaultConfigFile = Common.getAppHome() + "/mybox_server.conf";
  }

  
  private static void log(String message) {
    // TODO: change this to be a static PrintWriter opened at construction time
    PrintWriter out = null;

    try {
      out = new PrintWriter(new FileWriter(logFile, true));
    } catch (Exception e) {
      System.out.println("Unable to open log file: " + e);
    }

    out.print(message);

    out.close();
  }

  public static void printErrorExit(String message) {
    System.err.println(message);
    log(message + "\n");
    System.exit(1);
  }

  public static void printMessage(String message){
    System.out.println(message);
    log(message + "\n");
  }

  public static void printMessage_(String message){
    System.out.print(message);
    log(message);
  }

  public static void printWarning(String message){
    System.out.println(message);
    log(message + "\n");
  }

  private void readConfig(String configFile) {
    Properties properties = new Properties();

    try {
      properties.load(new FileInputStream(configFile));
    } catch (IOException e) {
      // TODO: something
    }

    port = Integer.parseInt(properties.getProperty("port"));  // returns NULL when not found
    defaultQuota = Integer.parseInt(properties.getProperty("defaultQuota"));
    maxClients = Integer.parseInt(properties.getProperty("maxClients"));

  }

  /**
   * Constructor
   * @param configFile
   */
  public Server(String configFile, String accountsDBfile) {

    printMessage("Starting server");
    printMessage("config: " + configFile);
    printMessage("database: " + accountsDBfile);

    accountsDb = new ServerDB(accountsDBfile);

    readConfig(configFile);

    try {
      printMessage("Binding to port " + port + ", please wait  ...");
      server = new ServerSocket(port);
      printMessage("Server started: " + server);

      while (true) {
        try {
          printMessage("Waiting for a client ...");
          addClient(server.accept());
        } catch (IOException ioe) {
          printMessage("Server accept error: " + ioe);
        }
      }

    } catch (IOException ioe) {
      printErrorExit("Can not bind to port " + port + ": " + ioe.getMessage());
    }
  }

  /**
   * Send message to sibling clients to span out needed sync commands.
   * @param myHandle
   * @param accountId
   * @param operation
   * @param arg
   */
  public synchronized void spanCatchupOperation(int myHandle, String accountId, Common.Signal operation, String arg) {

//    System.out.println("spanCatchupOperation from " + myHandle + " to all account=" + accountId + " (" + operation.toString() +","+ arg +")");

    HashSet<Integer> thisMap = multiClientMap.get(accountId);

    for (Integer thisHandle : thisMap) {
      if (thisHandle == myHandle)
        continue;

      System.out.println("spanCatchupOperation from " + myHandle + " to " + thisHandle + " (" + operation.toString() +","+ arg +")");

      try {
        ServerClientConnection client = clients.get(thisHandle);
        client.sendCatchup(operation, arg);
      } catch (Exception e) {
        System.out.println("Exception in spanCatchupOperation " + e.getMessage());
        System.exit(1);
      }
    }

  }


  /**
   * Remove a client connection from the server
   * @param handle
   */
  public void removeAndTerminateConnection(int handle) {

    ServerClientConnection toTerminate = clients.get(handle);
    
    if (toTerminate.account != null) {
      printMessage("Removing client " + handle + " (" + toTerminate.account.email +")");

      // update the client map
      HashSet<Integer> thisMap = multiClientMap.get(toTerminate.account.id);
      thisMap.remove(handle);
      multiClientMap.put(toTerminate.account.id, thisMap);
    }
    else
      printMessage("Removing null client " + handle);

    try {
      // stop the thread
      toTerminate.close();
    } catch (IOException ioe) {
      printMessage("Error closing thread: " + ioe);
    }

    // remove from list
    clients.remove(handle);
  }

  /**
   * Attach a new client to the server
   * @param socket
   */
  private void addClient(Socket socket) {
    
    if (clients.size() >= maxClients) {
      printMessage("Too many clients connected");
      return;
    }
    
    try {
      clients.put(socket.getPort(), new ServerClientConnection(this, socket));
      printMessage("Client accepted: " + socket);
    }
    catch (Exception e) {
      printMessage("Error attaching client: " + e);
    }

  }

  /**
   * Handle the command line args and instantiate the Server
   * @param args
   */
  public static void main(String args[]) {

    Options options = new Options();
    options.addOption("c", "config", true, "configuration file");
    options.addOption("d", "database", true, "database file");
    options.addOption("a", "apphome", true, "application home directory");
    options.addOption("h", "help", false, "show help screen");
    options.addOption("V", "version", false, "print the Mybox version");

    CommandLineParser line = new GnuParser();
    CommandLine cmd = null;

    try {
      cmd = line.parse(options, args);
    } catch( ParseException exp ) {
      System.err.println( exp.getMessage() );
      HelpFormatter formatter = new HelpFormatter();
      formatter.printHelp( Server.class.getName(), options );
      return;
    }

    if (cmd.hasOption("h")) {
      HelpFormatter formatter = new HelpFormatter();
      formatter.printHelp( Client.class.getName(), options );
      return;
    }

    if (cmd.hasOption("V")) {
      Client.printMessage("version " + Common.appVersion);
      return;
    }

    if (cmd.hasOption("a")) {
      String appHomeDir = cmd.getOptionValue("a");
      try {
        Common.updatePaths(appHomeDir);
      } catch (FileNotFoundException e) {
        printErrorExit(e.getMessage());
      }

      updatePaths();
    }

    
    String configFile = defaultConfigFile;
    String accountsDBfile = defaultAccountsDbFile;
    
    
    if (cmd.hasOption("c")) {
      configFile = cmd.getOptionValue("c");
      File fileCheck = new File(configFile);
      if (!fileCheck.isFile())
        printErrorExit("Specified config file does not exist: " + configFile);
    } else {
      File fileCheck = new File(configFile);
      if (!fileCheck.isFile())
        printErrorExit("Default config file does not exist: " + configFile);
    }
    if (cmd.hasOption("d")){
      accountsDBfile = cmd.getOptionValue("d");
      File fileCheck = new File(accountsDBfile);
      if (!fileCheck.isFile())
        printErrorExit("Specified database file does not exist: " + accountsDBfile);
    } else {
      File fileCheck = new File(accountsDBfile);
      if (!fileCheck.isFile())
        printErrorExit("Default database file does not exist: " + accountsDBfile);
    }

    Server server = new Server(configFile, accountsDBfile);
  }
}

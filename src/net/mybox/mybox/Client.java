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
import org.apache.commons.cli.*;
import org.json.simple.*;
import org.json.simple.parser.*;


/**
 * The status of the client
 */
enum ClientStatus {
  CONNECTING,DISCONNECTED,READY,SYNCING,PAUSED,ERROR
}

/**
 * The executable client connects to a running server
 */
public class Client {
  
  // object state
  private Socket serverConnectionSocket = null;

  private Thread clientInputListenerThread = null;
  private DirectoryListener dirListener = null;
  private GatherDirectoryUpdates gatherDirectoryUpdatesThread = null;
  private boolean waiting = false;
  public static Object clientGui = null;
  private Common.Signal lastReceivedOperation = null;

  private OutputStream outStream = null;
  private DataOutputStream dataOutStream = null;
  private InputStream inStream = null;
  private DataInputStream dataInStream = null;
  
  // user settings
  private ClientAccount account = new ClientAccount();

  public static final String defaultClientDir = System.getProperty("user.home") + "/Mybox";
  public static final String defaultConfigDir = System.getProperty("user.home") + "/.mybox";

  public static final String configFileName = "mybox_client.conf";
  public static final String logFileName = "mybox_client.log";
  public static final String lastSyncFileName = "lasysync.txt";

  public static String configFile = null;
  public static String logFile = null;
  public static String lastSyncFile = null;
  
  private static String localDir = null;  // data directory

  // TODO: make this a list of only name=>action items instead of myfiles
  // actually queue should eventually only be a list of files to send
  public LinkedList<MyFile> outQueue = new LinkedList<MyFile>();
  
  private long lastSync = 0;

  public HashMap<String, MyFile> S = new HashMap<String, MyFile>();  // serverFileList
  public HashMap<String, MyFile> C = new HashMap<String, MyFile>();  // clientFileList

  // filename(s) => actions
  public HashMap<String, String> Cgather = new HashMap<String, String>();


  static {
    updatePaths();
  }


  // FILE CLIENT METHODS


  private synchronized boolean updateLastSync() {
    lastSync = (new Date()).getTime();

    try {
      FileOutputStream fos = new FileOutputStream(lastSyncFile);
      DataOutputStream dos = new DataOutputStream(fos);
      dos.writeLong(lastSync);
      dos.close();
    }
    catch (Exception e) {
      return false;
    }
    
    return true;
  }
  
  private boolean getLastSync() {

    try {
      FileInputStream fin = new FileInputStream(lastSyncFile);
      DataInputStream din = new DataInputStream(fin);
      lastSync = din.readLong();
      din.close();
    }
    catch (Exception e) {
      return false;
    }

    return true;
  }


  private void handleInput(Common.Signal operation) {

    System.out.println("input operation: " + operation.toString());

    if (operation == Common.Signal.s2c) {
      try {
        String file_name = ByteStream.toString(inStream);
        String fileTimeString = ByteStream.toString(inStream);
        long fileTime = Long.valueOf(fileTimeString);

        System.out.println("getting file: " + file_name);

        File file=new File(localDir + "/" + file_name);
        ByteStream.toFile(inStream, file);
        file.setLastModified(fileTime);

      }
      catch (Exception e){
        System.out.println("Operation failed: " + e.getMessage());
      }
    } else if (operation == Common.Signal.deleteOnClient) {  // catchup operation
      try {
        String name = ByteStream.toString(inStream);
        Common.deleteLocal(localDir +"/"+ name);  // should assess return value
      }
      catch (Exception e){
        System.out.println("Operation failed: " + e.getMessage());
      }

    } else if (operation == Common.Signal.renameOnClient) {  // catchup operation
      try {
        String arg = ByteStream.toString(inStream);
        String[] args = arg.split("->");
        System.out.println("rename: " + args[0] + " to " + args[1]);
        Common.renameLocal(localDir +"/"+ args[0], localDir +"/"+ args[1]);
      }
      catch (Exception e){
        System.out.println("Operation failed: " + e.getMessage());
      }

    } else if (operation == Common.Signal.createDirectoryOnClient) {  // catchup operation
      try {
        String name = ByteStream.toString(inStream);
        Common.createLocalDirectory(localDir +"/"+ name);
      }
      catch (Exception e){
        System.out.println("Operation failed: " + e.getMessage());
      }

    } else if (operation == Common.Signal.clientWantsToSend_response) {
      try {
        String name = ByteStream.toString(inStream);
        String response = ByteStream.toString(inStream);

        System.out.println("clientWantsToSend_response: " + name + " = " + response);

        if (response.equals("yes")) {
          outQueue.push(new MyFile(name));
          checkQueue();
        }
      }
      catch (Exception e){
        System.out.println("Operation failed: " + e.getMessage());
      }

    } else if (operation == Common.Signal.requestServerFileList_response) {

      try {
        String jsonString = ByteStream.toString(inStream);
        S = decodeFileList(jsonString);
        System.out.println("Recieved jsonarray: " + jsonString);
      } catch (Exception e) {
        System.out.println("Exception while recieving jsonArray");
      }
    } else if (operation == Common.Signal.attachaccount_response) {

      String jsonString = null;
      
      try {
        jsonString = ByteStream.toString(inStream);
      } catch (Exception e) {
        //
      }

      HashMap map = Common.jsonDecode(jsonString);

      if (!((String)map.get("status")).equals("success"))
        printWarning("Unable to attach account. Server response: "+ map.get("error"));
      else {
        account.salt= (String)map.get("salt");
        System.out.println("set account salt to: " + account.salt);
        
        // inline check of mybox versions
        if (!Common.appVersion.equals((String)map.get("serverMyboxVersion")))
          printErrorExit("Client and Server Mybox versions do not match");
      }

    }
    else {
      printMessage("unknown command from server (" + operation +")");
    }

    lastReceivedOperation = operation;
  }

  /**
   * Decode a JSON string array into a Hash file list
   * @param input
   * @return
   */
  public static HashMap<String, MyFile> decodeFileList(String input) {

    HashMap<String, MyFile> result = new HashMap<String, MyFile>();

    JSONParser parser = new JSONParser();
    ContainerFactory containerFactory = new ContainerFactory(){
      @Override
      public List creatArrayContainer() {
        return new LinkedList();
      }
      @Override
      public Map createObjectContainer() {
        return new LinkedHashMap();
      }
    };

    try {
      List thisList = (List)parser.parse(input, containerFactory);

      Iterator iter = thisList.iterator();
      while(iter.hasNext()){
        String serialized = (String)iter.next();
        MyFile myFile = MyFile.fromSerial(serialized);
        result.put(myFile.name, myFile);
      }
    }
    catch(Exception pe){
      System.out.println(pe);
    }

    return result;
  }

  /**
   * Check the outgoing queue and send files from it, if it is unempty
   */
  public synchronized void checkQueue() {

    if (outQueue.size() > 0) {
      sendFile(outQueue.pop());
      checkQueue();
    }
  }

  private synchronized void sendFile(MyFile myFile) {

    System.out.println("sending file " + myFile.name);

    File fileObj = new File(localDir + "/" + myFile.name);

    try {
      sendCommandToServer(Common.Signal.c2s);
      ByteStream.toStream(outStream, myFile.name);
      ByteStream.toStream(outStream, fileObj.lastModified() +"");
      ByteStream.toStream(outStream, fileObj);
    } catch (Exception e) {
      System.out.println("error sending file");
    }
  }


  /**
   * Delete an item on the server. A file or directory.
   * @param name
   */
  private synchronized void deleteOnServer(String name) {

    System.out.println("Telling server to delete item " + name);

    try{
      sendCommandToServer(Common.Signal.deleteOnServer);
      ByteStream.toStream(outStream, name);
    } catch (Exception e) {
      System.out.println("error requesting server item delete");
    }
  }
  
  private synchronized void renameOnServer(String oldName, String newName) {
    System.out.println("Telling server to rename file " + oldName + " to " + newName);

    try{
      sendCommandToServer(Common.Signal.renameOnServer);
      ByteStream.toStream(outStream, oldName);
      ByteStream.toStream(outStream, newName);
    } catch (Exception e) {
      System.out.println("error requesting server file rename");
    }
  }


  private synchronized void createDirectoryOnServer(String name) {
    System.out.println("Telling server to create directory " + name);

    try{
      sendCommandToServer(Common.Signal.createDirectoryOnServer);
      ByteStream.toStream(outStream, name);
    } catch (Exception e) {
      System.out.println("error requesting server directory create");
    }
  }

  private synchronized void requestFile(String name) {
    System.out.println("Requesting file " + name);

    try{
      sendCommandToServer(Common.Signal.clientWants);
      ByteStream.toStream(outStream, name);
    } catch (Exception e) {
      System.out.println("error requesting file");
    }
  }

  private void showQueue() {
    System.out.println("Showing queue");
    for (MyFile myFile : outQueue) {
      System.out.println(myFile);
    }
  }


  private void populateLocalFileList() {
    try {
      List<MyFile> files = Common.getFileListing(new File(localDir));

      JSONArray jsonArray = new JSONArray();

      for (MyFile thisFile : files) {
        System.out.println(" " + thisFile.name);
        C.put(thisFile.name, thisFile);
      }

    } catch (Exception e) {
      System.out.println("Error populating local file list " + e.getMessage());
    }

  }


  /**
   * Start a new thread for getting input from the server
   */
  private void startClientInputListenerThread() {

    //if (clientInputListenerThread != null)
    //  return;

    clientInputListenerThread =

    new Thread(new Runnable() {
      @Override
      public void run() {

        while (true) {
          try {
            System.out.println("Client waiting for command");
            handleInput(Common.Signal.get( dataInStream.readByte() ));
          } catch (IOException ioe) {
            System.out.println("Listening error in ClientInThread: " + ioe.getMessage());
            clientInputListenerThread = null;
            attemptConnection(5);
            // TODO: make this reconnection work
          }
        }

      }
    });

    clientInputListenerThread.start();
  }


  /**
   * This is the sync method that is used when the client is already running.
   */
  private synchronized void GatherSync() {
    
    setStatus(ClientStatus.SYNCING);

    printMessage("Gather sync started  " + Common.now() );
    
    for (String fname : Cgather.keySet()) {
      if (Cgather.get(fname).equals("renamed")) {
        String[] names = fname.split("->");
        renameOnServer(names[0], names[1]);
      } else if (Cgather.get(fname).equals("deleted")) {
        deleteOnServer(fname);
      } else if (Cgather.get(fname).equals("modified") || Cgather.get(fname).equals("created")) {

        if ((new File(localDir + "/" + fname)).isDirectory()) {
          System.out.println(fname + " is a directory so it will be created on the server");
          createDirectoryOnServer(fname);
        } else {

          try {
            System.out.println("clientWantsToSend "+ fname);
            sendCommandToServer(Common.Signal.clientWantsToSend);
            ByteStream.toStream(outStream, fname);
            ByteStream.toStream(outStream, (new File(localDir + "/" + fname)).lastModified() +"");
          } catch (Exception e) {
            //
          }
        }
        
        
      }
    }

    updateLastSync();

    System.out.println("Setting lasySync to " + lastSync); // actually, it is not done until all incoming finish

    Cgather.clear();
      
    printMessage("Sync finished " + Common.now() );

    setStatus(ClientStatus.READY);
  }

  public synchronized void FullSync() {

    printMessage("disabling listener");
    disableDirListener(); // hack while incoming set gets figured out

    setStatus(ClientStatus.SYNCING);

    printMessage("Full sync started  " + Common.now() );

    // TODO: if lastSysc is 0 or bogus, favoror file creations over deletions
    // TODO: update all time comparisons to respect server/client time differences

    populateLocalFileList();
    boolean listReturned = serverDiscussion(Common.Signal.requestServerFileList,
            Common.Signal.requestServerFileList_response, null);

    if (!listReturned) {
      printErrorExit("requestServerFileList did not return in time");
    }

    System.out.println("comparing C=" + C.size() + " to S="+ S.size());

    LinkedList<MyFile> SendToServer = new LinkedList<MyFile>();

    // strange case = when there is the same name item but it is a file on the client and dir on server

    for (String name : C.keySet()) {

      MyFile c = C.get(name);

      if (S.containsKey(name)) {
        MyFile s = S.get(name);

        // if it is not a directory and the times are different, compare times
        if (!(new File(localDir + "/" + name)).isDirectory() && c.modtime != s.modtime) {
          
          System.out.println(name + " "+lastSync+" c.modtime=" + c.modtime + " s.modtime=" + s.modtime);
          
          if (c.modtime > lastSync) {
            if (s.modtime > lastSync) {
              System.out.println(name + " = conflict type 1");
            } else {
              System.out.println(name + " = transfer from client to server 1");
              SendToServer.addFirst(c);
            }
          } else {
            if (s.modtime > c.modtime) {
              System.out.println(name + " = transfer from server to client 1");
              requestFile(s.name);
            } else {
              System.out.println(name + " = conflict type 2");
            }
          }
        }
        S.remove(name);
      } else {

        if (c.modtime > lastSync) {

          if ((new File(localDir + "/" + name)).isDirectory()) {
            System.out.println(name + " = create directory on server");
            createDirectoryOnServer(name);
          } else {
            System.out.println(name + " = transfer from client to server 2");
            SendToServer.addFirst(c);
          }

        } else {

          if ((new File(localDir + "/" + name)).isDirectory()) {
            System.out.println(name + " = remove directory on client");
            Common.deleteLocalDirectory(new File(localDir + "/" + name));
          } else {
            System.out.println(name + " = remove from client");
            Common.deleteLocal(localDir + "/" + name);
          }

        }

      }
    }

    for (String name : S.keySet()) {  // which is now S-C
      MyFile s = S.get(name);

      if (s.modtime > lastSync) {

        if (s.type.equals("directory")) {
          System.out.println(name + " = create local directory on client");
          Common.createLocalDirectory(localDir + "/" + name);
        } else {
          System.out.println(name + " = transfer from server to client 2");
          requestFile(s.name);
        }

      } else {

        if (s.type.equals("directory")) {
          System.out.println(name + " = remove directory on server");
          deleteOnServer(s.name);
        } else {
          System.out.println(name + " = remove from server");
          deleteOnServer(s.name);
        }

      }
    }


    System.out.println("SendToServer");
    for (MyFile item : SendToServer) {
      outQueue.addFirst(item);
      System.out.println(item);
    }

    checkQueue();

    updateLastSync(); // TODO: this shoud actually happen after all inbound have finished

    System.out.println("Setting lasySync to " + lastSync);

    C.clear();
    S.clear();

    printMessage("enableing listener since sync is done");
    enableDirListener();
    
    printMessage("Sync finished " + Common.now() );

    setStatus(ClientStatus.READY);
  }




  /**
   * Set paths to local files based on the home application path
   */
  public static void updatePaths() {

    String incPath = Common.getPathToInc();

    String osName = System.getProperty("os.name").toLowerCase();

    if (osName.equals("linux")) {
      
    } else if (osName.startsWith("windows")) {
      
    } else if (osName.startsWith("mac os x")) {
      
    } else {
      throw new RuntimeException("Unsupported operating system: " + osName);
    }

  }

  // accessors

  public String GetClientDir() {
    return account.directory;
  }
  
  public String GetEmail() {
    return account.email;
  }

  public String GetServer() {
    return account.serverName;
  }

  public int GetServerPort() {
    return account.serverPort;
  }


  // helpers


  private static void log(String message) {
    // TODO: change this to be a static PrintWriter opened at construction time
    PrintWriter out = null;

    try {
      out = new PrintWriter(new FileWriter(logFile, true));
    } catch (Exception e) {
      System.out.println("Unable to open log file: " + e);
    }

    out.println(message);

    out.close();
  }

  public static void printErrorExit(String message) {

    log(message);
    if (clientGui == null) {
      System.out.println(message);
      System.exit(1);
    }
    else if (clientGui instanceof ClientGUI)
      ((ClientGUI)clientGui).printErrorExit(message);
  }

  public static void printMessage_(String message) {
    log(message);
    if (clientGui == null)
      System.out.print(message);
    else if (clientGui instanceof ClientGUI)
      ((ClientGUI)clientGui).printMessage_(message);
  }

  public static void printMessage(String message) {
    log(message);
    if (clientGui == null)
      System.out.println(message);
    else if (clientGui instanceof ClientGUI)
      ((ClientGUI)clientGui).printMessage(message);
  }

  public static void printWarning(String message) {
    log(message);
    System.out.println(message);
  }

  // methods

  /**
   * Set the status of the client. Mostly used for GUIs
   * @param status
   */
  private void setStatus(ClientStatus status) {
    if (clientGui == null)
      System.out.println("STATUS: "+ status);
    else if (clientGui instanceof ClientGUI)
      ((ClientGUI)clientGui).setStatus(status);
  }

  /**
   * Read the client config file and set this class's members accordingly
   * @param configFile
   */
  private void readConfig(String configFile) {

    Properties properties = new Properties();

    try {
      properties.load(new FileInputStream(configFile));
    } catch (IOException e) {
      // do something
    }

    // get values

    account.serverName = properties.getProperty("serverName");  // returns NULL when not found
    account.serverPort = Integer.parseInt(properties.getProperty("serverPort"));
    account.email = properties.getProperty("email");
    account.directory = properties.getProperty("directory");
    account.salt = properties.getProperty("salt");

    // check values

    if (account.serverName == null || account.serverName.isEmpty())
      printErrorExit("Unable to determine host from settings file");

    if (account.email == null || account.email.isEmpty())
      printErrorExit("Unable to determine email");

    if (account.directory == null)
      account.directory = defaultClientDir;

    File dirTest = new File(account.directory);
    if (!dirTest.exists())
      printErrorExit("Directory " + account.directory +" does not exist");

    localDir = account.directory;
  }

  /**
   * Pause the directory listener and synchronization
   */
  public void pause() {
    printMessage("pausing sync listeners/timers");
    disableDirListener();
    setStatus(ClientStatus.PAUSED);
  }

  /**
   * Unpause the directory listener and synchronization
   */
  public void unpause() {
    enableDirListener();
    FullSync();
  }

  /**
   * Enable the directory listener
   */
  public void enableDirListener() {
    printMessage("Listening on directory " + account.directory);
    if (dirListener == null)
      dirListener = new DirectoryListener(account.directory, this);
    else
      dirListener.listen();
  }

  /**
   * Disable the directory listener
   */
  public void disableDirListener() {
    if (dirListener != null)
      dirListener.pause();
  }


  /**
   * Send a message to the server
   * @param command
   */
  public void sendCommandToServer(Common.Signal signal) {
    try {
      dataOutStream.writeByte(signal.index());
      //dataOutStream.flush();
    } catch (Exception e) {
      printWarning("Sending error for ("+ signal.toString() +"): " + e.getMessage());
    }
  }

  /**
   * Called by the WaitGather thread to indicate the waiting has finished and a sync can begin.
   */
  public void waitHasFinished() {
    waiting = false;

    Client.printMessage("GatherDirectoryUpdates has finished");

    GatherSync();
  }
  
  /**
   * Called whenever a file in the directory is updated
   * @param action
   * @param items
   */
  public void directoryUpdate(String action, String items) {
    printMessage("DirectoryUpdate " + action + " " + items);

    if (items.charAt(items.length()-1) == '/') { // hack: remove trailing slash from jnotify?
      items = items.substring(0, items.length()-1);
      System.out.println("chop result: " + items);
    }
    if (Cgather.containsKey(items) && Cgather.get(items).equals(action))
      ; // dont bother adding it to the list since it is already there
    else
      Cgather.put(items, action); // this should overwrite items that area already in the list

    if (gatherDirectoryUpdatesThread != null && waiting)
      gatherDirectoryUpdatesThread.deactivate();

    gatherDirectoryUpdatesThread = new GatherDirectoryUpdates(this, 2);
    waiting = true;
  }

  /**
   * This is a helper function for ClientSetup that makes a quick connection to the server
   * just so the account can be determined.
   * @param serverName
   * @param serverPort
   * @param email
   * @return The account
   */
  public ClientAccount startGetAccountMode(String serverName, int serverPort, String email, String dataDir) {

    if (serverName == null) {
      System.err.println("Client not configured");
      System.exit(1);
    }

    account.serverName = serverName;
    account.serverPort = serverPort;
    account.email = email;
    account.directory = dataDir;

    account.salt = "0"; // temp hack

    System.out.println("Establishing connection to port "+ serverPort +". Please wait ...");

    try {
      serverConnectionSocket = new Socket(account.serverName, account.serverPort);
      System.out.println("Connected: " + serverConnectionSocket);
      dataOutStream = new DataOutputStream(serverConnectionSocket.getOutputStream());
    } catch (UnknownHostException uhe) {
      System.err.println("Host unknown: " + uhe.getMessage());
      System.exit(1);
    } catch (IOException ioe) {
      System.err.println("Unexpected exception: " + ioe.getMessage());
      System.exit(1);
    }

    JSONObject jsonOut = new JSONObject();
    jsonOut.put("email", account.email);
//    jsonOut.put("password", password);

//    client = new ClientServerReceiver(this, serverConnectionSocket);

    return account;
  }

  /**
   * Send a message to the server and wait for its response. Waiting polls once a second for 10 seconds.
   * Note that this does not handle the response, it only waits for it.
   * @param messageToServer
   * @return false if no response
   */
  public boolean serverDiscussion(Common.Signal messageToServer, Common.Signal expectedReturnCommand, String argument) {

    int pollseconds = 1;
    int pollcount = 20;

    System.out.println("serverDiscussion starting with expected return: " + expectedReturnCommand.toString());
    System.out.println("serverDiscussion sending message to server: " + messageToServer.toString());

    sendCommandToServer(messageToServer);
    if (argument != null) {
      try {
        ByteStream.toStream(outStream, argument);
      }
      catch (Exception e) {
        //
      }
    }

    for (int i=0; i<pollcount; i++){
      try{Thread.sleep(pollseconds * 1000);}catch(Exception ee){}
      if (expectedReturnCommand == (lastReceivedOperation)) {
        System.out.println("serverDiscussion returning true with: " + lastReceivedOperation);
        lastReceivedOperation = null;
        return true;
      }
    }

    System.out.println("serverDiscussion returning false");
    lastReceivedOperation = null;

    return false;
  }




  /**
   * Attempt to connect to the server port and attach the user
   * @param pollInterval The amount of seconds between socket connection retries
   */
  public void attemptConnection(int pollInterval) {

    if (account.serverName == null)
      printErrorExit("Client not configured");

    setStatus(ClientStatus.CONNECTING);

    printMessage("Establishing connection to "+ account.serverName +":"+ account.serverPort+" ...");

    // repeatedly attempt to connect to the server
    while (true) {
      try {
        serverConnectionSocket = new Socket(account.serverName, account.serverPort);

        outStream = serverConnectionSocket.getOutputStream();
        inStream = serverConnectionSocket.getInputStream();

        dataOutStream = new DataOutputStream(outStream);
        dataInStream = new DataInputStream(inStream);

        break; // reachable if there is no exception thrown
      } catch (IOException ioe) {
        printMessage("There was an error reaching server. Will try again in " + pollInterval + " seconds...");
        try{Thread.sleep(1000*pollInterval);}catch(Exception ee){}
      }
    }
    
    printMessage("Connected: " + serverConnectionSocket);

    startClientInputListenerThread();

    JSONObject jsonOut = new JSONObject();
    jsonOut.put("email", account.email);
//    jsonOut.put("password", password);
    
    // TODO: make sure if the attachAccount fails, no files can be transfered
    
    if (!serverDiscussion(Common.Signal.attachaccount,
            Common.Signal.attachaccount_response, jsonOut.toString()))
      printErrorExit("Unable to attach account");

    // checking that the mybox versions are the same happens in handleMessageFromServer

    loadNativeLibs();

    getLastSync();

    enableDirListener();
    
    printMessage("Client ready. Startup sync.");
    setStatus(ClientStatus.READY);

    // perform an initial sync to catch all the files that have changed while the client was off
    FullSync();
  }

  public void start() {
    attemptConnection(5);
  }

  public void stop() {

//    printMessage("Stopping client");
    setStatus(ClientStatus.DISCONNECTED);

    try {
/*
      if (client != null) {
        client.close();
        client = null;
      }
  */
      if (serverConnectionSocket != null) {
        serverConnectionSocket.close();
        serverConnectionSocket = null;
      }
      
      if (dataOutStream != null) {
        dataOutStream.close();
      }
    } catch (IOException ioe) {
      printWarning("Error closing ...");
    }

  }

  /**
   * Load the native libraries. Most notably JNotify.
   */
  private void loadNativeLibs() {

    String incPath = Common.getPathToInc();
    if (incPath == null)
      printErrorExit("Unable to open the include directory: " + incPath);
    
    java.lang.System.setProperty("java.library.path", incPath);

    try {
      java.lang.reflect.Field fieldSysPath = ClassLoader.class.getDeclaredField( "sys_paths" );
      fieldSysPath.setAccessible( true );
      fieldSysPath.set( null, null );
    } catch (Exception e) {
      printErrorExit("There was an error setting the java library path");
    }

  }

  /**
   * Constructor
   */
  public Client() {
    setStatus(ClientStatus.DISCONNECTED);
  }

  public void config(String configFile) {
    readConfig(configFile);
  }

  
  public static void setConfigDir(String configDir) {
    
    if (!(new File(configDir)).isDirectory()) {
      System.err.println("Specified config directory does not exist: " + configDir);
      System.exit(1);
    }
    configFile = configDir + "/" + configFileName;
    logFile = configDir + "/" + logFileName;
    lastSyncFile = configDir + "/" + lastSyncFileName;

    System.out.println("Set configFile to " + configFile);

  }

  /**
   * Handle the command line args and instantiate the Client
   * @param args
   */
  public static void main(String args[]) {

    Options options = new Options();
    options.addOption("c", "config", true, "configuration directory (default=~/.mybox)");
    options.addOption("a", "apphome", true, "application home directory");
    options.addOption("h", "help", false, "show help screen");
    options.addOption("V", "version", false, "print the Mybox version");

    CommandLineParser line = new GnuParser();
    CommandLine cmd = null;

    String configDir = defaultConfigDir;

    try {
      cmd = line.parse(options, args);
    } catch( Exception exp ) {
      System.err.println( exp.getMessage() );
      HelpFormatter formatter = new HelpFormatter();
      formatter.printHelp( Client.class.getName(), options );
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

    if (cmd.hasOption("c")) {
      configDir = cmd.getOptionValue("c");
    }
    
    setConfigDir(configDir);

    File fileCheck = new File(configFile);
    if (!fileCheck.isFile())
      System.err.println("Config file does not exist: " + configFile);

    Client client = new Client();
    client.config(configFile);
    client.start();
  }


}

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

import com.jcraft.jsch.*;
import java.io.*;
import java.net.*;
import java.util.*;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.cli.*;
import org.json.simple.*;

/*
enum WaitStatus {
  FINISHED,WAITING
}
 */

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
  private DataOutputStream streamToServer = null;
  private ClientServerReceiver client = null; // server listener thread
  private DirectoryListener dirListener = null;
  private GatherDirectoryUpdates gatherDirectoryUpdatesThread = null;
  private boolean waiting = false;
  public static Object clientGui = null;
  private String receivedResponseCommand = null;

  // user settings
  private ClientAccount account = new ClientAccount();

  // static system specific settings
  private final String ServerDir = "Mybox";
  public static final String defaultClientDir = System.getProperty("user.home") + "/Mybox";
  public static final String defaultConfigFile = System.getProperty("user.home") + "/mybox_client.conf";

  public String serverUnisonCommand = null;
  public static String clientUnisonCommand;
  public static String sshCommand;
  
  static {
    updatePaths();
  }

  /**
   * Set paths to local files based on the home application path
   */
  public static void updatePaths() {

    String incPath = Common.getPathToInc();

    String osName = System.getProperty("os.name").toLowerCase();

		if (osName.equals("linux")){
      clientUnisonCommand = incPath + "/unison-2.27.57-linux";
      sshCommand = incPath + "/ssh-ident.bash";
		}
		else if (osName.startsWith("windows")){
      clientUnisonCommand = incPath + "/unison-2.27.57-win_gui.exe";
//      sshCommand = "inc" + "\\ssh-ident.bat";
      sshCommand = incPath + System.getProperty("file.separator") + "ssh-ident.bat";
        // TODO: make this path absolute. might be a unison argument parsing bug
		}
    else if (osName.startsWith("mac os x")){
      clientUnisonCommand = incPath + "/unison-2.40.61-macXX";
      sshCommand = incPath + "/ssh-ident.bash";
		}
    else {
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
  public static void printErrorExit(String message) {
    if (clientGui == null) {
      System.out.println(message);
      System.exit(1);
    }
    else if (clientGui instanceof ClientGUI)
      ((ClientGUI)clientGui).printErrorExit(message);
  }

  public static void printMessage_(String message) {
    if (clientGui == null)
      System.out.print(message);
    else if (clientGui instanceof ClientGUI)
      ((ClientGUI)clientGui).printMessage_(message);
  }

  public static void printMessage(String message) {
    if (clientGui == null)
      System.out.println(message);
    else if (clientGui instanceof ClientGUI)
      ((ClientGUI)clientGui).printMessage(message);
  }

  public static void printWarning(String message) {
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
    sync();
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
   * Perform a directory sync from this client to the server
   */
  public void sync() {
    setStatus(ClientStatus.SYNCING);
    printMessage("Syncing client");

    printMessage("Sync started  " + Common.now() );

    String[] commandArr = new String[]{clientUnisonCommand, "-auto","-batch",
            //"-debug","verbose",
            "-sshcmd", sshCommand, "-perms", "0", "-servercmd",serverUnisonCommand,
            account.directory, "ssh://"+ account.serverPOSIXaccount +"@"+ account.serverName +"/" + ServerDir};

    Common.syscommand(commandArr);

    printMessage("Sync finished " + Common.now() );

    setStatus(ClientStatus.READY);
  }

  /**
   * Send a message to the server
   * @param msg
   */
  public void sendMessageToServer(String msg) {
    try {
      streamToServer.writeUTF(msg);
      streamToServer.flush();
    } catch (Exception e) {
      printWarning("Sending error: " + e.getMessage());
    }
  }

  /**
   * Handle message sent from the server to this client
   * @param msg
   */
  public void handleMessageFromServer(String msg) {

    printMessage("(" + Common.now() + ") Server: " + msg);

    String[] iArgs = msg.split(" ");

    String command = iArgs[0];
    String args = StringUtils.join(iArgs, " ", 1, iArgs.length);

    if (command.endsWith("_response")) {
      receivedResponseCommand = command;
    }

    if (command.equals("attachaccount_response")) {

      HashMap map = Common.jsonDecode(args);

      if (!((String)map.get("status")).equals("success"))
        printWarning("Unable to attach account. Server response: "+ map.get("error"));
      else {
        account.serverPOSIXaccount = (String)map.get("serverPOSIXaccount");
        account.salt= (String)map.get("salt");
        serverUnisonCommand = (String)map.get("serverUnisonCommand");
        printMessage("Set POSIXaccount to " + account.serverPOSIXaccount);
      }

    } else if (msg.equals("sync")) {
      printMessage("disabling listener");
      disableDirListener();
      sync();
      printMessage("enableing listener since sync is done");
      enableDirListener();
    }
    else {
      printMessage("unknown command from server");
    }

  }

  /**
   * Called by the WaitGather thread to indicate the waiting has finished and a sync can begin.
   */
  public void waitHasFinished() {
    waiting = false;
    sync();

    // this happens when the sync is done since sync() waits
    sendMessageToServer("syncfinished");
  }
  
  /**
   * Called whenever a file in the directory is updated
   * @param msg
   */
  public void directoryUpdate(String msg) {
    printMessage("DirectoryUpdate " + msg);

    if (gatherDirectoryUpdatesThread != null && waiting)
      gatherDirectoryUpdatesThread.deactivate();

    gatherDirectoryUpdatesThread = new GatherDirectoryUpdates(this, 2);
    waiting = true;
  }

  /**
   * This is a helper function for ClientSetup that makes a quick connection to the server
   * just so the ssh user can be determined.
   * @param serverName
   * @param serverPort
   * @param email
   * @return The account
   */
  public ClientAccount startGetAccountMode(String serverName, int serverPort, String email) {

    if (serverName == null)
      printErrorExit("Client not configured");
    
    account.serverName = serverName;
    account.serverPort = serverPort;
    account.email = email;

    printMessage("Establishing connection to port "+ serverPort +". Please wait ...");

    try {
      serverConnectionSocket = new Socket(account.serverName, account.serverPort);
      printMessage("Connected: " + serverConnectionSocket);
      streamToServer = new DataOutputStream(serverConnectionSocket.getOutputStream());
    } catch (UnknownHostException uhe) {
      printErrorExit("Host unknown: " + uhe.getMessage());
    } catch (IOException ioe) {
      printErrorExit("Unexpected exception: " + ioe.getMessage());
    }

    JSONObject jsonOut = new JSONObject();
    jsonOut.put("email", account.email);
//    jsonOut.put("password", password);

    client = new ClientServerReceiver(this, serverConnectionSocket);

    if (!serverDiscussion("attachaccount " + jsonOut) || account.serverPOSIXaccount == null)
      printErrorExit("Unable to determine server POSIX account");

    return account;
  }

  /**
   * Send a message to the server and wait for its response. Waiting polls once a second for 10 seconds.
   * Note that this does not handle the response, it only waits for it.
   * @param messageToServer
   * @return false if no response
   */
  public boolean serverDiscussion(String messageToServer) {

    int pollseconds = 1;
    int pollcount = 10;

    String[] iArgs = messageToServer.split(" ");
    String expectedReturnCommand = iArgs[0] + "_response";

    sendMessageToServer(messageToServer);

    for (int i=0; i<pollcount; i++){
      try{Thread.sleep(pollseconds * 1000);}catch(Exception ee){}
      if (expectedReturnCommand.equals(receivedResponseCommand)) {
        receivedResponseCommand = null;
        return true;
      }
    }

    receivedResponseCommand = null;

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
        streamToServer = new DataOutputStream(serverConnectionSocket.getOutputStream());
        break; // reachable if there is no exception thrown
      } catch (IOException ioe) {
        printMessage("There was an error reaching server. Will try again in " + pollInterval + " seconds...");
        try{Thread.sleep(1000*pollInterval);}catch(Exception ee){}
      }
    }
    
    printMessage("Connected: " + serverConnectionSocket);
    
    client = new ClientServerReceiver(this, serverConnectionSocket);

    JSONObject jsonOut = new JSONObject();
    jsonOut.put("email", account.email);
//    jsonOut.put("password", password);

    if (!serverDiscussion("attachaccount " + jsonOut) || account.serverPOSIXaccount == null)
      printErrorExit("Unable to determine ssh account");
    
    // check that the unison versions are the same. this also makes sure that the SSH keys work and that unison is installed

    SysResult runLocalUnisonChech = Common.syscommand(new String[]{Client.clientUnisonCommand, "-version"});
    if (runLocalUnisonChech.returnCode != 0)
        printErrorExit("Unable to determine local unison version");

    SysResult runServerUnisonCheck = Common.syscommand(new String[]{sshCommand, account.serverPOSIXaccount +
            "@" + account.serverName, serverUnisonCommand, "-version"});
    //sshCommand + " " + SSHuser + "@" + ServerName
    //        + " " + serverUnisonCommand + " -version");
    if (runServerUnisonCheck.returnCode != 0)
        printErrorExit("Unable to contact server to detect unison version");

    if (!runServerUnisonCheck.output.equalsIgnoreCase(runLocalUnisonChech.output)) {
      printErrorExit("The server and client are running different versions of unison");
    }

    loadNativeLibs();

    enableDirListener();
    
    printMessage("Client ready. Startup sync.");
    setStatus(ClientStatus.READY);

    // perform an initial sync to catch all the files that have changed while the client was off
    sync();
  }

  public void start() {
    attemptConnection(5);
  }

  public void stop() {

    printMessage("Stopping client");
    setStatus(ClientStatus.DISCONNECTED);

    try {

      if (client != null) {
        client.close();
        client = null;
      }
      
      if (serverConnectionSocket != null) {
        serverConnectionSocket.close();
        serverConnectionSocket = null;
      }
      
      if (streamToServer != null) {
        streamToServer.close();
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


  /**
   * Handle the command line args and instantiate the Client
   * @param args
   */
  public static void main(String args[]) {

    Options options = new Options();
    options.addOption("c", "config", true, "configuration file");
    options.addOption("a", "apphome", true, "application home directory");
    options.addOption("h", "help", false, "show help screen");
    options.addOption("V", "version", false, "print the Mybox version");

    CommandLineParser line = new GnuParser();
    CommandLine cmd = null;

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

    String configFile = defaultConfigFile;

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

    Client client = new Client();
    client.config(configFile);
    client.start();
  }

  
  // Public static functions for everyone!


  /**
   * Read a file into a string
   * @param aFile
   * @return
   */
  static public String getContents(File aFile) {
    //...checks on aFile are elided
    StringBuilder contents = new StringBuilder();

    try {
      //use buffering, reading one line at a time
      //FileReader always assumes default encoding is OK!
      BufferedReader input =  new BufferedReader(new FileReader(aFile));
      try {
        String line = null; //not declared within while loop
        /*
        * readLine is a bit quirky :
        * it returns the content of a line MINUS the newline.
        * it returns null only for the END of the stream.
        * it returns an empty String if two newlines appear in a row.
        */
        while (( line = input.readLine()) != null){
          contents.append(line);
          contents.append(System.getProperty("line.separator"));
        }
      }
      finally {
        input.close();
      }
    }
    catch (IOException ex){
      // do something
    }

    return contents.toString();
  }


  /**
   * Transfer a public key file to a remote machine.
   * @param host, where the remote file is to go
   * @param serverPOSIXaccount, account on remote system
   * @param password, raw version of the password on the remote accout
   * @param pubkeyfilename, the local filename of the public key
   * @return
   */
  public static SysResult transferPublicKey(String host, String serverPOSIXaccount, String password, String pubkeyfilename) {

    File pubkeyfile = new File(pubkeyfilename); // TODO: check that the file exists or is not expty
    String pubkey = Client.getContents(pubkeyfile).trim();

    // TODO: make sure .ssh directory exists on server. mkdir -m 700 .ssh

    // note that the password does not have to be encrypted because SSH should be doing that itself

    SysResult result = Client.runSshCommand(host, serverPOSIXaccount, password, "echo \""+ pubkey +"\" >> ~/.ssh/authorized_keys");
    return result;
  }

  public static SysResult keyGen() {
    String sshDir = System.getProperty("user.home") + "/.ssh";
    //CommCommon.syscommand("mkdir -p "+sshDir);
    //CommCommon.syscommand("chmod 700 "+sshDir);

    return keyGen(sshDir + "/mybox_rsa");
  }

  /**
   * Create a public/private keypair with a given path/basename
   * @param filename
   * @return a SysResult where output is the input filename
   */
  public static SysResult keyGen(String filename) {

    SysResult result = new SysResult();

    Common.syscommand(new String[]{"rm", "-f", filename + ".pub"}); // pseudo non-deterministic ubuntu hack
    Common.syscommand(new String[]{"ssh-add","-l"}); // this is stupid

    // generating with the below method gives "Agent admitted failure to sign using the key." when logging in

    int type=KeyPair.RSA;
    String comment="generated by mybox";
    String passphrase="";

    result.output = filename;

    JSch jsch=new JSch();

    try{
//      KeyPair kpair=KeyPair.genKeyPair(jsch, type);
      KeyPair kpair=KeyPair.genKeyPair(jsch, type, 2048);
      kpair.setPassphrase(passphrase);
      kpair.writePrivateKey(filename);
      kpair.writePublicKey(filename + ".pub", comment);
      //System.out.println(kpair.getPublicKeyBlob().toString());
      //System.out.println("Finger print: "+kpair.getFingerPrint());
      kpair.dispose();
      result.worked = true;

      Common.syscommand(new String[]{"chmod", "600", filename});
    }
    catch(Exception e){
      System.err.println("keygen: " + e);
      result.worked = false;
    }

    return result;
  }

  public static boolean setKnownHosts(String host) {
    try {
      JSch jsch=new JSch();

      // use bogus account because we dont want to log into the server with a real account.
      // we only want to generate the local known_hosts file
      String serverPOSIXaccount = "bogus";
      String password = "bogus";

      System.out.println("setting known hosts file");

      // TODO: add mkdir .ssh and touch known_hosts for OS X
      // also chmod 600 .ssh/mybox_rsa

      Session session=jsch.getSession(serverPOSIXaccount, host, 22);

      java.util.Properties config = new java.util.Properties();
      config.setProperty("StrictHostKeyChecking", "no");
      session.setConfig(config);

      UserInfo ui=new SshUserInfo();
      session.setUserInfo(ui);
      session.setPassword(password);

      session.setConfig("HashKnownHosts",  "yes");

      jsch.setKnownHosts(System.getProperty("user.home") + "/.ssh/known_hosts");

      session.connect();

      // TODO: get return values from SshUserInfo for wrong passwords etc.
      // System.out.println("Connect returned "+returned);

      session.disconnect();

      System.out.println("known hosts set");
    }
    catch(Exception e) {
      System.out.println(e);
      return false;
    }
    return true;
  }

  /**
   * Run remote command over SSH using the JSch library
   *
   * @param host
   * @param serverPOSIXaccount
   * @param passwordOrIdentFile
   * @param command
   * @return
   */
  public static SysResult runSshCommand(String host, String serverPOSIXaccount,
          String passwordOrIdentFile, String command) {

    SysResult result = new SysResult();
    try{
      JSch jsch=new JSch();

      System.out.println("sshCommand "+ serverPOSIXaccount + ":" + passwordOrIdentFile + "@" + host + " "+ command);

      Session session=jsch.getSession(serverPOSIXaccount, host, 22);

      UserInfo ui=new SshUserInfo();
      session.setUserInfo(ui);
      
      File identfile = new File(passwordOrIdentFile);
      
      if (identfile.exists()) // if it is a private key file, use it
        jsch.addIdentity(passwordOrIdentFile);
      else  // else treat the string as a password
        session.setPassword(passwordOrIdentFile);
      
      jsch.setKnownHosts(System.getProperty("user.home") + "/.ssh/known_hosts");
      session.connect();

      Channel channel=session.openChannel("exec");
      ((ChannelExec)channel).setCommand(command);

      //channel.setInputStream(System.in);
      channel.setInputStream(null);

      //channel.setOutputStream(System.out);

      //FileOutputStream fos=new FileOutputStream("/tmp/stderr");
      //((ChannelExec)channel).setErrStream(fos);
      ((ChannelExec)channel).setErrStream(System.err);

      InputStream in=channel.getInputStream();

      channel.connect();

      byte[] tmp=new byte[1024];
      while(true){
        while(in.available()>0){
          int i=in.read(tmp, 0, 1024);
          if(i<0)break;
          result.output += new String(tmp, 0, i);
        }
        if(channel.isClosed()){
          result.returnCode = channel.getExitStatus();
          break;
        }
        try{Thread.sleep(1000);}catch(Exception ee){}
      }

      result.worked = true;
      result.output = result.output.trim();
      channel.disconnect();
      session.disconnect();
    }
    catch(Exception e){
      result.worked = false;
      System.out.println(e);
    }
    return result;
  }


  /**
   * Class needed for SSH authentication via Jsch
   */
  public static class SshUserInfo implements UserInfo {
    public String getPassword(){ return null; }
    public boolean promptYesNo(String message){

      printMessage("Prompt yes/no: " + message); // perhaps printWarning
      //Client.quitError("Prompt yes/no: " + message);

      return false; // implies NO
    }

    public String getPassphrase(){ return null; }
    public boolean promptPassphrase(String message){ return true; }
    public boolean promptPassword(String message){
//      Client.quitError("ssh password prompt failure: " + message);
      printMessage("ssh password prompt failure: " + message);
      return false; // implies error?
    }
    public void showMessage(String message){
      printMessage("ssh show message: " + message);
    }

  }

}

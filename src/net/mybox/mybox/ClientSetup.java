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
import java.net.Socket;
import java.util.Properties;
import org.apache.commons.cli.*;

/**
 * Command line setup wizard for Mybox. Creates config file and sets up local files.
 */
public class ClientSetup {

  private ClientAccount account = null;

  private String password = null;

  private String configFile = Client.defaultConfigFile;
  
    

  /**
   * Get the initial input from the user
   */
  private void gatherInput() {
    
    BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

    String input = null;
    
    Client.printMessage_("Server name ["+ account.serverName +"]: ");
    
    try {
      input = br.readLine();
    }
    catch (Exception e) {
      
    }

    if (!input.isEmpty())  account.serverName = input;

    Client.printMessage_("Server port ["+ account.serverPort +"]: ");

    try {
      input = br.readLine();
    }
    catch (Exception e) {
      
    }

    if (!input.isEmpty()) {
      account.serverPort = Integer.parseInt(input);  //catch
    }

    // ping the server to make sure it is up
    Socket socket = null;
    try {
      socket = new Socket(account.serverName, account.serverPort);
    }
    catch(IOException e){
      Client.printErrorExit("Unable to contact server");
    }

    Client.printMessage_("Email ["+ account.email +"]: ");

    try {
      input = br.readLine();
    }
    catch (Exception e) {
      
    }

    if (!input.isEmpty())  account.email = input;

    Console console = System.console();

    if (console != null)
      input = new String( console.readPassword("Password ["+ password +"]: ") );
    else
      System.err.println("Unable to get password since not in console");

    System.out.println("pw input : " + input);

    if (!input.isEmpty()) password = input;
  }
  
  
  
  /**
   * Generate the config file after everything has been setup
   */
  private boolean saveConfig() {
    
    // TODO: handle existing file
    
    Properties config = new Properties();
    config.setProperty("serverName", account.serverName);
    config.setProperty("serverPort", Integer.toString(account.serverPort));
    config.setProperty("email", account.email);
    config.setProperty("salt", account.salt);

    if (!account.directory.equals(Client.defaultClientDir))
      config.setProperty("directory", account.directory);

    try {
      FileOutputStream MyOutputStream = new FileOutputStream(configFile);
      config.store(MyOutputStream, "Mybox client configuration file");
    } catch (Exception e) {
      Server.printErrorExit(e.getMessage());
    }
    
    Client.printMessage("Config file written: " + configFile);

    return true;
  }


  public ClientSetup() {

    // set up the defaults
    account = new ClientAccount();
    account.serverName = "localhost";
    account.serverPort = Common.defaultCommunicationPort;
    account.email = "bill@gates.com";
    password = "bill";  // only used for POSIX login
    
        // app settings directory
    File dirCheck = new File(Client.defaultConfigDir);
    if (!dirCheck.exists()) {
//      Client.printMessage("Creating directory since it does not exist...");
      if (dirCheck.mkdir())
        Client.printMessage("Created directory " + Client.defaultConfigDir);
      else
        Client.printMessage("Unable to create directory"); // error?
    }
    
    
    Client.printMessage("Welcome to the Mybox setup wizard");
    
    gatherInput();
    
    // attach the account to the server to get the ssh user name
    Client client = new Client();
    account = client.startGetAccountMode(account.serverName, account.serverPort, account.email);
    client.stop();



    // data directory
    Client.printMessage("testing directory " + account.directory);

    dirCheck = new File(account.directory);
    if (!dirCheck.exists()) {
//      Client.printMessage("Creating directory since it does not exist...");
      if (dirCheck.mkdir())
        Client.printMessage("Created directory " + account.directory);
      else
        Client.printMessage("Unable to create directory"); // error?
    }

    saveConfig();

    Client.printMessage("Setup finished successfully");

  }

  /**
   * Handle command line arguments
   * @param args
   */
  public static void main(String[] args) {

    Options options = new Options();
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
        Client.printErrorExit(e.getMessage());
      }

      Client.updatePaths();
    }

    ClientSetup setup = new ClientSetup();

  }

}

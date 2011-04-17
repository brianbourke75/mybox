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
import org.apache.commons.cli.*;

/**
 * Command line server administative program
 */
public class ServerAdmin {

  private ServerDB serverDb = null;


  /**
   * Delete POSIX account and update user database
   */
  private void deleteAccount(){

    BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
    serverDb.showAccounts();

    Server.printMessage_("Which account would you like to delete?\naccount number> ");

    String input = null;
    try { input = br.readLine(); } catch (Exception e) {}

    ServerDB.Account thisAccount = serverDb.getAccountByID(input);

    if (thisAccount == null) {
      System.out.println("account " + input + " does not exist.");
      return;
    }

    System.out.println("Are you sure you want to delete " + thisAccount);
    System.out.print("y/n> ");

    try { input = br.readLine(); } catch (Exception e) {}

    if (input.equals("y")) {

      // delete the data directory
      File userDir = new File(thisAccount.serverdir);

      if (!userDir.delete())  // TODO: make recursive
        Server.printWarning("There was a problem when deleting the user directory");

      else if(serverDb.deleteAccount(thisAccount.id)) // update the database
        System.out.println("Account deleted");
      else
        System.out.println("Unable to delete account");
    }

  }

  /**
   * Add POSIX account and update database
   */
  private void addAccount() {

    // gather user input
    BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

    String email = null;
    String password = null;

    Server.printMessage("Add a new account.");
    Server.printMessage_("email> ");

    try {  email = br.readLine();    } catch (Exception e) { }
    
    Server.printMessage_("password> ");
    try {  password = br.readLine();    } catch (Exception e) { }
    

    // validate the entered fields
    String id = serverDb.getNewID();
    
    if (!serverDb.validateNewAccount(id, email, password)){
      Server.printWarning("New account is invalid or conflicts with an existing one.");
      return;
    }
    
    // create the data directory
    
    File userDir = new File(Server.serverBaseDir + "/" + id);

    if (userDir.exists())
      Server.printWarning("Error: user directory already exists");
    else if (!userDir.mkdir())
      Server.printWarning("There was a problem when creating the user directory");
      
    else {

    // update the database
      String salt = null, encryptedPassword = null;

      try{
        salt = Common.generateSalt();
        encryptedPassword = Common.encryptPassword(password, salt);
      } catch (Exception e) {
        Server.printWarning("Password encryption error " + e.getMessage());
      }

      serverDb.addAccount(id, email, encryptedPassword, salt);
      
    }
  }
  
  
  /**
   * Delete POSIX account and update user database
   */
  private void showEncryptedPassword(){

    BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

    String inputPassword = null, inputSalt = null, encryptedPassword = null;

    Server.printMessage_("Enter a password to encrypt> ");
    try { inputPassword = br.readLine(); } catch (Exception e) {}
    
    Server.printMessage_("Enter salt> ");
    try { inputSalt = br.readLine(); } catch (Exception e) {}
    
    try {
      encryptedPassword = Common.encryptPassword(inputPassword, inputSalt);
    } catch (Exception e) {}

    Server.printMessage("Encrypted password: " + encryptedPassword);

  }

  /**
   * Constructor. Main loop and menu items.
   * @param dbFile
   */
  public ServerAdmin(String dbFile) {

    serverDb = new ServerDB(dbFile);

    BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
    
    Server.printMessage("Starting ServerAdmin command line utility...");
    
    char choice=0;
    String input=null;
    
    // menu
    while (choice != 'q') {
      Server.printMessage("  l) List accounts");
      //Server.printMessage("  p) Show encyrpted password");
      Server.printMessage("  a) Add account");
      Server.printMessage("  d) Delete account");
      Server.printMessage("  q) Quit");
      Server.printMessage_("  > ");
      
      try {
        input = br.readLine();
      } catch (Exception e) {
        System.err.println(e);
      }

      if (input.length() != 1)
        continue;

      choice = input.charAt(0);

      switch (choice) {
        case 'l':
          serverDb.showAccounts();
          break;
        case 'd':
          deleteAccount();
          break;
        case 'a':
          addAccount();
          break;
        case 'p':
          showEncryptedPassword();
          break;
      }
    }

  }

  /**
   * Handle command line args
   * @param args
   */
  public static void main(String[] args) {

    Options options = new Options();
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
      formatter.printHelp(ServerAdmin.class.getName(), options );
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

      Server.updatePaths();
    }

    String dbFile = Server.defaultDbFile;

    if (cmd.hasOption("d")){
      dbFile = cmd.getOptionValue("d");
      File fileCheck = new File(dbFile);
      if (!fileCheck.isFile())
        Server.printErrorExit("Specified database file does not exist: " + dbFile);
    } else {
      File fileCheck = new File(dbFile);
      if (!fileCheck.isFile())
        Server.printErrorExit("Default database file does not exist: " + dbFile);
    }

    ServerAdmin server = new ServerAdmin(dbFile);

  }

}

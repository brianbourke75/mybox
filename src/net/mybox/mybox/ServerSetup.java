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
import java.util.Properties;
import org.apache.commons.cli.*;

/**
 * Command line executable to generate a config file and setup necessarry files.
 */
public class ServerSetup {

  private int port = Server.port;
  private int defaultQuota = Server.defaultQuota;
  private int maxClients = Server.maxClients;

  private String configFile = Server.defaultConfigFile;
  
  /**
   * Get the initial input from the user
   */
  private void gatherInput() {
    
    BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

    String input = null;
    
    Server.printMessage_("Port ["+port+"]: ");
    
    try {   input = br.readLine();   }    catch (Exception e) {   }
    if (!input.isEmpty())  
      port = Integer.parseInt(input); // catch

    Server.printMessage_("Quota in megabytes ["+defaultQuota+"]: ");

    try {   input = br.readLine();   }    catch (Exception e) {   }

    if (!input.isEmpty())
      defaultQuota = Integer.parseInt(input);  //catch

    Server.printMessage_("Config file ["+configFile+"]: ");

    try {   input = br.readLine();   }    catch (Exception e) {   }
    if (!input.isEmpty())
      configFile = input; //catch
  }

  private boolean setupDirectories() {

    File baseDir = new File(Server.serverBaseDir);

    if (!baseDir.exists())
      if (!baseDir.mkdir())
        return false;

    return true;
  }

  /**
   * Save the configuration file to the filesystem
   * @return true if the file saved
   */
  private boolean saveConfig() {

    // TODO: handle existing file
    
    Properties config = new Properties();
    config.setProperty("port", Integer.toString(port));
    config.setProperty("maxClients", Integer.toString(maxClients));
    config.setProperty("defaultQuota", Integer.toString(defaultQuota));

    try {
      FileOutputStream MyOutputStream = new FileOutputStream(configFile);
      config.store(MyOutputStream, "Mybox server configuration file");
    } catch (Exception e) {
      Server.printErrorExit(e.getMessage());
    }

    return true;
  }

  /**
   * Check various files and their permissions
   * @return true if the check passed
   */
  private boolean checkFiles() {

    // check for the unison command and make sure it can be executed

    File unisonCommand = new File(Server.serverUnisonCommand);

    if (!unisonCommand.exists())
      return false;

    if (!unisonCommand.canExecute())
      unisonCommand.setExecutable(true);

    return true;
  }

  private boolean createNewDB() {

    try {
      ServerDB.createNewDB();
    } catch (IOException e) {
      return false;
    }
    return true;
  }

  /**
   * Constructor
   */
  private ServerSetup() {

    Server.printMessage("Welcome to the Mybox server setup wizard");
    // TODO: make sure they are the superuser
    
    // TODO: add facility to create a new database

    gatherInput();

    if (!checkFiles())
      Server.printErrorExit("Unable to setup needed files.");
    
    if (!setupDirectories())
      Server.printErrorExit("Unable to setup directories. Make sure you run as super user.");
    
    saveConfig();

    createNewDB();
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
      formatter.printHelp( Server.class.getName(), options );
      return;
    }

    if (cmd.hasOption("h")) {
      HelpFormatter formatter = new HelpFormatter();
      formatter.printHelp( Server.class.getName(), options );
      return;
    }

    if (cmd.hasOption("V")) {
      Server.printMessage("version " + Common.appVersion);
      return;
    }

    if (cmd.hasOption("a")) {
      String appHomeDir = cmd.getOptionValue("a");
      try {
        Common.updatePaths(appHomeDir);
      } catch (FileNotFoundException e) {
        Server.printErrorExit(e.getMessage());
      }

      Server.updatePaths();
    }

    ServerSetup setup = new ServerSetup();
  }

}

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

import net.contentobjects.jnotify.*;

/**
 * Directory update listener made possible from J/inotify
 */
public class DirectoryListener {

  public static final int FILE_CREATED = 0x1;
  public static final int FILE_DELETED = 0x2;
  public static final int FILE_MODIFIED = 0x4;
  public static final int FILE_RENAMED = 0x8;
  public static final int FILE_ANY = FILE_CREATED | FILE_DELETED | FILE_MODIFIED | FILE_RENAMED;
  private IJNotify _instance;
  private Client client = null; // calling program
  private String Dir = null;
  
  private JNotifyListener jListener = new JNotifyListener() {

      public void fileRenamed(int wd, String rootPath, String oldName, String newName) {
        client.directoryUpdate("renamed " + rootPath + " : " + oldName + " -> " + newName);
      }

      public void fileModified(int wd, String rootPath, String name) {
        client.directoryUpdate("modified " + rootPath + " : " + name);
      }

      public void fileDeleted(int wd, String rootPath, String name) {
        client.directoryUpdate("deleted " + rootPath + " : " + name);
      }

      public void fileCreated(int wd, String rootPath, String name) {
        client.directoryUpdate("created " + rootPath + " : " + name);
      }
    };
    
  private int watchId = -1;

  public DirectoryListener(String dir, Client _client) {
    client = _client;
    Dir = dir;

    String osName = System.getProperty("os.name").toLowerCase();
    if (osName.equals("linux")) {
      try {
        _instance = (IJNotify) Class.forName("net.contentobjects.jnotify.linux.JNotifyAdapterLinux").newInstance();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    } else if (osName.startsWith("windows")) {
      try {
        _instance = (IJNotify) Class.forName("net.contentobjects.jnotify.win32.JNotifyAdapterWin32").newInstance();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    } else if (osName.startsWith("mac os x")) {
      try {
        _instance = (IJNotify) Class.forName("net.contentobjects.jnotify.macosx.JNotifyAdapterMacOSX").newInstance();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    } else {
      throw new RuntimeException("Unsupported OS : " + osName);
    }

    listen();
  }
  
  public void listen() {
    if (watchId == -1) {
      try {
        watchId = addWatch(Dir, FILE_ANY, true, jListener);
      } catch (Exception e) {
        System.out.println(e.getMessage());
      }
    }
  }

  public void pause() {
    if (watchId != -1) {
      try {
        removeWatch(watchId);
      }
      catch (Exception e) {
        System.out.println(e.getMessage());
      }
      watchId = -1;
    }
  }

  private int addWatch(String path, int mask, boolean watchSubtree, JNotifyListener listener) throws JNotifyException {
    return _instance.addWatch(path, mask, watchSubtree, listener);
  }

  private boolean removeWatch(int watchId) throws JNotifyException {
    return _instance.removeWatch(watchId);
  }
}

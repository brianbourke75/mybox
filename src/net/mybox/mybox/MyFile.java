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


public class MyFile {

		public String name;
		public long modtime;
		public String type;
		//public long size;

    public String action; // send, recieve, delete, conflict, clientWants ?

    // TODO overload > functions to compare times?

    public MyFile(String name) {
      this.name = name;
    }

    public MyFile(String name, long modtime, String type) {
      this.name = name;
      this.modtime = modtime;
      this.type = type;
    }

    public MyFile(String name, long modtime, String type, String action) {
      this.name = name;
      this.modtime = modtime;
      this.type = type;
      this.action = action;
    }
    
    public String serialize() {
      return name + ";" + modtime + ";" + type;
    }

    public char getTypeChar() {
      if (type.equals("directory"))
        return 'd';
      if (type.equals("link"))
        return 'l';

      return 'f';
    }
    
    public static MyFile fromSerial(String input) {
      String[] split = input.split(";");

      if (split.length == 2)
        return (new MyFile(split[0], Long.valueOf(split[1]), "file"));
      else if (split.length == 3)
        return (new MyFile(split[0], Long.valueOf(split[1]), split[2]));
      else
        return null;
    }

  @Override
    public String toString() {
      return type + " " + name + " (" + modtime + ")  action=" + action;
    }
}

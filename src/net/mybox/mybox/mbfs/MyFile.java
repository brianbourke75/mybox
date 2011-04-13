package net.mybox.mybox.mbfs;

public class MyFile {

		public String name;
		public int modtime;
		public String type;
		public long size;

    public String action; // send, recieve, delete, conflict, clientWants ?

    // TODO overload > functions to compare times?

    public MyFile(String name, int modtime, String type, long size) {
      this.name = name;
      this.modtime = modtime;
      this.type = type;
      this.size = size;
    }

    public MyFile(String name, int modtime, String type, long size, String action) {
      this.name = name;
      this.modtime = modtime;
      this.type = type;
      this.size = size;
      this.action = action;
    }

  @Override
    public String toString() {
      return type + " " + name + " (" + modtime + ")  action=" + action;
    }
}

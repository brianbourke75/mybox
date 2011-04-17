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

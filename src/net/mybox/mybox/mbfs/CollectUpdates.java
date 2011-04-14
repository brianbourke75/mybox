package net.mybox.mybox.mbfs;

import java.util.*;

public class CollectUpdates {

  // input
	private HashMap<String, MyFile> C = new HashMap<String, MyFile>();
	private HashMap<String, MyFile> S = new HashMap<String, MyFile>();
	private int lastSync;

  // output
	//delete file on server
	//delete file on client
	//conflict
	//delete folder on server
	//delete folder on client
	//create folder on server
	//create folder on client
	//transfer to client
	//transfer to server

  // for these queues we put the fast operations (ie - delete) and the long operations
  //  on the bottom (ie - transfer and conflict). then we process from top to bottom
  private LinkedList<MyFile> DoLocal = new LinkedList<MyFile>();
  private LinkedList<MyFile> TellServer = new LinkedList<MyFile>();
  private LinkedList<MyFile> SendToServer = new LinkedList<MyFile>();

	public CollectUpdates(HashMap<String, MyFile> C, HashMap<String, MyFile> S, int lastSync) {
		this.C = C;
		this.S = S;
		this.lastSync = lastSync;
	}
  
  public void Collect() {

    System.out.println("collecting updates");

    // TODO: handle directories
    // strange case = when there is the same name item but it is a file on the client and dir on server

    for (String name : C.keySet()) {

      MyFile c = C.get(name);

      if (S.containsKey(name)) {
        MyFile s = S.get(name);

        if (c.modtime != s.modtime) {
          if (c.modtime > lastSync) {
            if (s.modtime > lastSync) {
              System.out.println(name + " = conflict type 1");
            } else {
              System.out.println(name + " = transfer from client to server");
              SendToServer.addFirst(c);
            }
          } else {
            if (s.modtime > c.modtime) {
              System.out.println(name + " = transfer from server to client");
              s.action = "clientWants";
              TellServer.addFirst(s);
            } else {
              System.out.println(name + " = conflict type 2");
            }
          }
        }
//        System.out.println("removing " + name + " from S");
        S.remove(name);
      } else {
        if (c.modtime > lastSync) {
          System.out.println(name + " = transfer from client to server");
          SendToServer.addFirst(c);
        }
        else {
          System.out.println(name + " = remove from client");
          c.action = "delete";
          DoLocal.addFirst(c);
        }
      }
    }

//    System.out.println("now processing S-C");

    for (String name : S.keySet()) {  // which is now S-C
      MyFile s = S.get(name);
      
      if (s.modtime > lastSync) {
        System.out.println(name + " = transfer from server to client");
        s.action = "clientWants";
        TellServer.addFirst(s);
      } else {
        System.out.println(name + " = remove from server");
        s.action = "delete";
        TellServer.addFirst(s);
      }
    }
    

    // debug output


    System.out.println("DoLocal");
    for(MyFile item : DoLocal)  System.out.println(item);

    System.out.println("TellServer");
    for(MyFile item : TellServer)  System.out.println(item);

    System.out.println("SendToServer");
    for(MyFile item : SendToServer)  System.out.println(item);
  }

	// testing function
	public static void main(String[] args) {
    HashMap<String, MyFile> C = new HashMap<String, MyFile>();
    HashMap<String, MyFile> S = new HashMap<String, MyFile>();

    C.put("e", new MyFile("e", 1, "file"));
    C.put("a", new MyFile("a", 10, "file"));
    C.put("b", new MyFile("b", 10, "file"));
    C.put("c", new MyFile("c", 10, "file"));
    C.put("g", new MyFile("g", 10, "file"));
//    C.put("adir", new MyFile("adir", 10, "dir", 1));
//    C.put("adir/1", new MyFile("adir/1", 10, "file", 1));
//    C.put("bdir", new MyFile("bdir", 10, "dir", 1));

    S.put("a", new MyFile("a", 10, "file"));
    S.put("b", new MyFile("b", 11, "file"));
    S.put("c", new MyFile("c", 9, "file"));
    S.put("d", new MyFile("d", 9, "file"));
    S.put("f", new MyFile("f", 6, "file"));

    int _lastSync = 8;

    CollectUpdates updater = new CollectUpdates(C, S, _lastSync);
    updater.Collect();

	}

}

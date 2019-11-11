package AllStoresServer;
import java.rmi.registry.LocateRegistry;
import AllStoresServer.Interfaces.AllStoresServerInterface;
import java.rmi.registry.Registry;


public class Server {
	
	private static final int ALLSTORES_PORT = 1099;

	public static void main(String[] args) throws Exception {
		String databaseHost = "127.0.0.1";
		int databasePort = 1100;

		if (args.length == 2) {
			databaseHost = args[0];
			databasePort = Integer.parseInt(args[1]);
		}

		AllStoresServerInterface testing = new AllStoresServerImp(databaseHost, databasePort);
		
		Registry registry =  null;
		try {
			registry = LocateRegistry.createRegistry(ALLSTORES_PORT);
			registry.rebind("ClientServerInterface", testing);
			System.out.println("Server ready!");
		}
		catch (Exception e) {
			System.err.println("Server exception: Error trying to start.");
			System.exit(0);
		}
		
		// getting the server address
		String address = null;
		try {
			address = System.getProperty("java.rmi.server.hostname");
			address = address == null ? "127.0.0.1" : address;
		} catch (Exception e) {
			System.out.println("Can't get inet address.");
			System.exit(0);
		}

		String myID = address + ":" + "ClientServerInterface";
		System.out.println(myID);
	}

}

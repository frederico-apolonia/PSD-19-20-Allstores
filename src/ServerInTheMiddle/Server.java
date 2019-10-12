package ServerInTheMiddle;
import java.rmi.registry.LocateRegistry;
import ManInTheMiddleClient.ClientServerInterface;
import java.rmi.registry.Registry;


public class Server {
	
	private static final int INTERFACE_PORT = 2000;

	public static void main(String[] args) throws Exception {
		ClientServerInterface testing = new ClientServerInterfaceImp();
		
		
		 System.setProperty( "java.rmi.server.hostname", "10.101.148.179" );
		
		Registry registry = null;
		try {
			registry = LocateRegistry.createRegistry(INTERFACE_PORT);
			registry.rebind("ClientServerInterface", testing);
			System.out.println("Server ready!");
		}
		catch (Exception e) {
			System.err.println("Server exception: Error trying to start.");
			System.exit(0);
		}
		
		// getting the server address
//		String address = null;
//		try {
//			address = System.getProperty("java.rmi.server.hostname");
//			address = address == null ? "10.101.148.179" : address;
//		} catch (Exception e) {
//			System.out.println("Can't get inet address.");
//			System.exit(0);
//		}
		
//		String myID = new String(address + ":" + "ClientServerInterface");
//		System.out.println(myID);
	}

}

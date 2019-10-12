package ServerInTheMiddle;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import ManInTheMiddleClient.ClientServerInterface;
import DatabaseServer.Reservation;


public class ClientServerInterfaceImp extends UnicastRemoteObject implements ClientServerInterface {

		//idcliente,
	HashMap<Integer,List<Reservation>> mapReservas;
	//storeID
	HashMap<Integer,List<Product>> mapLojas;


	
	
	protected ClientServerInterfaceImp() throws RemoteException {
		super();
		mapReservas = new HashMap<Integer,List<Reservation>>();
		//map.put(key, value)
	
	}
	
	public List<String> getReservation(int storeID, int productID, int quantitym, int clientID) throws RemoteException {
		
		
	}
	
	
	public List<String> getList(int storeID) throws RemoteException {
		// get the products list of the store containing the storeID written by the client
		HashMap<Integer, ArrayList<String>> listing = new HashMap<Integer, ArrayList<String>>();
		ArrayList<String> productInfo = new ArrayList<String>();
		String productID[] = {"A","B","C","D","E","F","G","H","I","J","K","L","M","N","O","P","Q","R","S","T"};
		
		for(int i = 0; i <20; i++) {
			Product productExmp = new Product(productID[i],10,0);
			productInfo.add(productExmp.toString());
		}
		
		listing.put(storeID,productInfo);
		return listing.get(storeID);
	}

}

package AllStoresServer.Interfaces;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface ClientServerInterface extends Remote {
	List<String> getList(int storeID) throws RemoteException;
	
	String addReservation(int storeID, int productID, int quantitym, int clientID) throws RemoteException;
	
	String buy(int clienID, int storeID, int productID, int quantity) throws RemoteException;

	List<String> buyAll(int clientID) throws RemoteException;
	
	List<String> cancel(int clientID);
}

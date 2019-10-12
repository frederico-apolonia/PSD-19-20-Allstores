package ManInTheMiddleClient;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface ClientServerInterface extends Remote {
	List<String> getList(int storeID) throws RemoteException;
}

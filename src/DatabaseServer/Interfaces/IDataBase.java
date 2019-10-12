package DatabaseServer.Interfaces;

import DatabaseServer.Product;
import DatabaseServer.Reservation;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface IDataBase extends Remote {

    // metodos list get addreserve removereserve... etc
    public List<Product> getShopProducts(int shopID) throws RemoteException;
    public String addReservation(int shopID, int productID, int quantity, int clientID) throws RemoteException;
    public String buyProduct(int shopID, int productID, int quantity, int clientID) throws RemoteException;
    public boolean removeReservation(int clientID, int shopID, int productID) throws RemoteException;
    public List<Reservation> getClientReservations(int clientID) throws RemoteException;
    public boolean cancelAllReservations(int clientID) throws RemoteException;

}

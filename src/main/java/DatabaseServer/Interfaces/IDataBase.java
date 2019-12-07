package DatabaseServer.Interfaces;

import DatabaseServer.Product;
import DatabaseServer.Reservation;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.List;

public interface IDataBase extends Remote {

    /**
     * Adds a new reservation R to the server
     * @param r reservation to be added
     * @return True if reservation added successfully
     */
    public boolean addReservation(Reservation r) throws RemoteException;

    /**
     * Get all reservations from a client
     * @param clientID
     * @return List containing all reservations from a client
     * @throws RemoteException
     */
    public List<Reservation> getClientReservations(int clientID) throws RemoteException;

    /**
     * Cancels all reservations from a client
     * @param clientID
     * @return True if all reservations were removed with success
     * @throws RemoteException
     */
    public boolean cancelAllReservations(int clientID) throws RemoteException;

    /**
     * Finds a client reservation
     * @param clientID
     * @param shopID
     * @param productID
     * @return Client reservation R if found, null if not found
     * @throws RemoteException
     */
    public Reservation findClientReservation(int clientID, int shopID, int productID)throws RemoteException;

    /**
     * Updates an existing client reservation
     * @param reservation
     * @param updateQuantity
     * @return true if successfully updated the reservation, false if doesn't exist (or something happened)
     * @throws RemoteException
     */
    public boolean updateClientReservation(Reservation reservation, int updateQuantity) throws RemoteException;

    /**
     * Lists all products of a shop
     * @param shopID
     * @return List containing all products of a shop
     * @throws RemoteException
     */
    public List<Product> getShopProducts(int shopID) throws RemoteException;

    /**
     * Buys a product from a shop
     * @param shopID
     * @param productID
     * @param quantity
     * @param clientID
     * @return true if everything went OK
     * @throws RemoteException
     */
    public boolean buyProduct(int shopID, int productID, int quantity, int clientID) throws RemoteException;

    /**
     *
     * @param storeID
     * @param productID
     * @param quantity
     * @return
     */
    boolean sendBuyToReplica(int shopID, int productID, int quantity) throws RemoteException;

    /**
     *
     * @param shops
     */
    void updateDatabase(HashMap<Integer, List<Product>> shops) throws RemoteException;
}

package DatabaseServer.Interfaces;

import DatabaseServer.Product;
import DatabaseServer.Reservation;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface IDataBase extends Remote {

    /**
     * Adds a new reservation R to the server
     * @param r reservation to be added
     * @return True if reservation added successfully
     */
    public boolean addReservation(Reservation r) throws RemoteException;

    /**
     * Removes a reservation R (if it exists)
     * @param r reservation to be removed
     * @return true if reservation was removed with success, false if it doesn't exist (or something happened)
     * @throws RemoteException
     */
    public boolean removeReservation(Reservation r) throws RemoteException;

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
     * Updates shop stock and reservation parameters
     * @param shopID
     * @param productID
     * @param reserveQuantity
     * @param increaseReservation if the reservation quantity should be increased (true) or decreased (false)
     * @return true if everything went OK
     * @throws RemoteException
     */
    public boolean productUpdateReservation(int shopID, int productID, int reserveQuantity, boolean increaseReservation)
            throws RemoteException;

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
}

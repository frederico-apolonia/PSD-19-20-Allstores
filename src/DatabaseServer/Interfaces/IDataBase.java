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
    //acrescentei todos metodos em baixo ------------------------------------------------------------------------------
    
    public Reservation findClientReservation(int clientID, int shopID, int productID)throws RemoteException;
    
    //parecido a este mas que faça o update automatico pode devolverum boolean a dizer se fez tudo bem ou nao
    public int productUpdateReservation(int shopID, int productID, int reserveQuantity, boolean increaseReservation) throws RemoteException;
    
    //faz update a uma reserva  da uma olhadela na chamada deste metodo pois dava jeito um r.resetTimer()
    public boolean updateReservation(Reservation r) throws RemoteException;
    //adiciona uma nova reserva 
    public boolean addReservation(Reservation r) throws RemoteException;
    //delete a todas as reservas feitas por um cliente
    public boolean deleteClientReservations(int clientID) throws RemoteException;
    
    //Adiciona mais argumentos se te der mais jeito
}

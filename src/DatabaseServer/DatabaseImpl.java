package DatabaseServer;

import DatabaseServer.Interfaces.IDataBase;

import java.io.File;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashMap;

public class DatabaseImpl extends UnicastRemoteObject implements IDataBase {

    // tem de ser set
    private File serverPath = null;

    private HashMap<String, Product> shopProducts = new HashMap<>();
    private ArrayList<Reservation> reservations = new ArrayList<>();

    public DatabaseImpl() throws RemoteException {
        initDataBase();
    }

    private void initDataBase() {
        // todo
    }
}

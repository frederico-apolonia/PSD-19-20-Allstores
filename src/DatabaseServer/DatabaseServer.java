package DatabaseServer;

import DatabaseServer.Interfaces.IDataBase;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class DatabaseServer {

    private static final int DATABASE_PORT = 1100;

    public static void main(String[] args) throws Exception {
        IDataBase database = new DatabaseImpl();

        /* Create registry and rebind it to port DATABASE_PORT */
        Registry registry = null;
        try {
            registry = LocateRegistry.createRegistry(DATABASE_PORT);
            registry.rebind("AllstoresDatabaseServer", database);
        } catch (Exception e) {
            System.err.println("DataBase: ERROR trying to start server");
            e.printStackTrace();
            System.exit(0);
        }

        /* Get server address */
        String address = null;
        try {
            address = System.getProperty("java.rmi.server.hostname");
            // is address null? if so then it is 127.0.0.1 (localhost), else it still is address
            address = address == null ? "127.0.0.1" : address;
        } catch (Exception e) {
            System.out.println("Can't get inet address.");
            System.exit(0);
        }

        String myID = address + ":" + "AllstoresDatabaseServer";
        System.out.println(myID);
    }

}

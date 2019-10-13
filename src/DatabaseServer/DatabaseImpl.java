package DatabaseServer;

import DatabaseServer.Interfaces.IDataBase;

import java.io.*;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;

public class DatabaseImpl extends UnicastRemoteObject implements IDataBase {

    // product ids are the first 20 letters of the alphabet
    private static final int NUM_PRODUCT_IDS = 20;
    private static final int NUMBER_OF_STORES = 600;

    private static final String FILE_SEPARATOR = File.separator;
    // property fetches the home path
    private static final String SERVER_PATH = System.getProperty("user.home")
            + FILE_SEPARATOR + "AllstoresDB" + FILE_SEPARATOR;

    private HashMap<Integer, List<Product>> shops = new HashMap<>();
    private HashMap<Integer, List<Reservation>> reservations = new HashMap<>();

    public DatabaseImpl() throws Exception {
        try {
            initDataBase();
        } catch (IOException e) {
            e.getStackTrace();
        }
    }

    /**
     * Loads the shop products to memory.
     * Checks first if there is a previous state of the database then loads it, else
     * creates a new one from scratch (600 stores, 20 products ea)
     */
    private void initDataBase() throws IOException {

        File serverDir = new File(SERVER_PATH);
        // check if there is a previous version of the server
        System.out.println("Checking if there is a previous state of the database...");
        if (serverDir.exists()) {
            System.out.println("Version found! Loading the latest saved state");
            loadShops();
        } else {
            System.out.println("No version found, starting from scratch...");
            for (int shopID = 0; shopID < NUMBER_OF_STORES; shopID++) {
                generateNewShop(shopID);
            }
            System.out.println("All shops generated! Writing stores to disk...");
            writeStoresToDisk();
        }

    }

    private void loadShops() {
        // todo
        // carregar o estado das lojas para memória

        // ir ao log e repôr o estado correto

    }

    /**
     * Writes all stores to disk
     */
    private void writeStoresToDisk() throws IOException {
        for (int shop : this.shops.keySet()) {
            writeStoreToDisk(shop, this.shops.get(shop));
        }
    }

    /**
     * Write a store to disk on file <storeID>.shop
     * @param shopID
     * @param products
     */
    private void writeStoreToDisk(int shopID, List<Product> products) throws IOException {
        String shopPath = SERVER_PATH + shopID + ".shop";
        BufferedWriter writer = new BufferedWriter(new FileWriter(shopPath));
        for(Product p : products) {
            writer.write(String.format("%d %d %d\n", p.getProductID(), p.getAvailable(), p.getSold()));
        }
    }

    private void generateNewShop(int shopID) {
        List<Product> currShopProds = new ArrayList<>();

        for(int productID = 0; productID < NUM_PRODUCT_IDS; productID++) {
            Product p = new Product(shopID, productID);
            currShopProds.add(p);
            System.out.println(String.format("Added product %s to " +
                            "shop %d with quantity %d", p.getProductID(), shopID, p.getAvailable()));
        }

        this.shops.put(shopID, currShopProds);
    }

    @Override
    public List<Product> getShopProducts(int shopID) throws RemoteException {
        return shops.get(shopID);
    }

    @Override
    public String addReservation(int shopID, int productID, int quantity, int clientID) throws RemoteException {
        /* Reservations don't go to logs, they don't matter if the system goes down and back up again */
        /* Reservations require some kind of thread "sleeping" for 15s to remove the reservation
        *  if the reservation goes thru then this thread should be killed or something */


        return null;
    }

    private void superviseReservation(Reservation reservation) {
        Timer timer = new Timer();
        reservation.timer = timer;
        reservation.timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    removeReservation(reservation.getClientID(), reservation.getShopID(), reservation.getProductID());
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }, 15);
    }

    @Override
    public String buyProduct(int shopID, int productID, int quantity, int clientID) throws RemoteException {
        return null;
    }

    @Override
    public boolean removeReservation(int clientID, int shopID, int productID) throws RemoteException {
        int counter = 0;
        List<Reservation> clientReservations = this.reservations.get(clientID);
        Reservation res = clientReservations.get(counter);

        while(!(res.getShopID() == shopID) && !(res.getProductID() == productID) ) {
            counter++;
            if(counter > clientReservations.size()) {
                res = null;
                break;
            }
            res = clientReservations.get(counter);
        }

        if(res != null) {
            // cancel the timer
            res.timer.cancel();
            clientReservations.remove(counter);
            return true;
        }
        return false;
    }

    @Override
    public List<Reservation> getClientReservations(int clientID) throws RemoteException {
        return this.reservations.get(clientID);
    }

    @Override
    public boolean cancelAllReservations(int clientID) throws RemoteException {

        List<Reservation> clientReservations = this.reservations.get(clientID);

        if(clientReservations != null) {
            for(Reservation r : clientReservations) {
                // cancel all timers
                r.timer.cancel();
            }
            this.reservations.remove(clientID);
            return true;
        }

        return false;
    }
}

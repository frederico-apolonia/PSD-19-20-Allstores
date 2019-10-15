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

    private void writeBuyToLog(int shopID, int productID, int quantity) {
        // todo
    }

    @Override
    public List<Product> getShopProducts(int shopID) throws RemoteException {
        return shops.get(shopID);
    }

    @Override
    public String addReservation(int shopID, int productID, int quantity, int clientID) throws RemoteException {
        /* Reservations don't go to logs, they don't matter if the system goes down and back up again */

        Reservation r = new Reservation(clientID, shopID, productID, quantity);

        // update product information to match this new reservation
        if(!productUpdateReservation(shopID, productID, quantity, true)) {
            return "<ERRO> AO ALTERAR QUANTIDADE PRODUTO";
        };
        //
        List<Reservation> clientReservations = this.reservations.get(clientID);
        if(clientReservations != null) {
            clientReservations.add(r);
        } else {
            clientReservations = new ArrayList<>();
            clientReservations.add(r);
            this.reservations.put(clientID, clientReservations);
        }
        // arm the 15s clock
        superviseReservation(r);

        return "<RESERVED>";
    }

    public  Reservation findClientReservation(int clientID, int shopID, int productID) {
        Reservation result = null;
        for(Reservation r : this.reservations.get(clientID)) {
            if(r.getShopID() == shopID && r.getProductID() == productID) {
                result = r;
                break;
            }
        }
        return result;
    }

    private void superviseReservation(Reservation reservation) {
        Timer timer = new Timer();
        reservation.timer = timer;
        // set a schedule to remove the reservation in 15 seconds
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

        // first, check if the client already has a reservation for this product
        Reservation r = findClientReservation(clientID, shopID, productID);
        if(r != null) {
            // if so, disarm the clock, subtract the qty, if > 0, arm the clock again, else remove it. save the
            // qty difference (reserve.qty - quantity)
            int remainingReservationQuantity = r.getQuantity() - quantity;
            removeReservation(r.getClientID(), r.getShopID(), r.getProductID());

            if(remainingReservationQuantity > 0) {
                addReservation(shopID, productID, remainingReservationQuantity, clientID);
            }

        }
        // update the product information (reservation space is already taken care of)
        List<Product> shopProducts = this.shops.get(shopID);
        int remainingAvailable = 0;
        for(Product p : shopProducts) {
            if(p.getProductID() == productID && p.getShopID() == shopID) {
            	remainingAvailable=p.getAvailable() - quantity;
            	p.setAvailable(remainingAvailable);
                p.setSold(quantity);
                break;
            }
        }
        // write to log
        writeBuyToLog(shopID, productID, quantity);

        // return the value to the middle server
        return String.format("<SOLD> %d", remainingAvailable);
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
            productUpdateReservation(res.getShopID(), res.getProductID(), res.getQuantity(), false);
            clientReservations.remove(counter);
            return true;
        }
        return false;
    }

    @Override
    public List<Reservation> getClientReservations(int clientID) throws RemoteException {
        return this.reservations.get(clientID);
    }

    /**
     * Updates product available and reserved quantities
     * @param shopID
     * @param productID
     * @param reserveQuantity
     * @param increaseReservation - tells if the reservation quantity increases or not (if someone is making a reservation)
     * @return remaining quantity available
     */
    public boolean productUpdateReservation(int shopID, int productID, int reserveQuantity, boolean increaseReservation) throws RemoteException {

        int result = -1;

        for(Product p : this.shops.get(shopID)) {
            if(p.getProductID() == productID) {
                if(increaseReservation) {
                    p.setAvailable(p.getAvailable() - reserveQuantity);
                    p.setReserved(p.getReserved() + reserveQuantity);
                    result = p.getAvailable();
                } else {
                    p.setAvailable(p.getAvailable() + reserveQuantity);
                    p.setReserved(p.getReserved() - reserveQuantity);
                    result = p.getAvailable();
                }
                break;
            }
        }
        return result != -1;
    }

    @Override
    public boolean cancelAllReservations(int clientID) throws RemoteException {

        List<Reservation> clientReservations = this.reservations.get(clientID);

        if(clientReservations != null) {
            for(Reservation r : clientReservations) {
                // cancel all timers
                r.timer.cancel();
                productUpdateReservation(r.getShopID(), r.getProductID(), r.getQuantity(), false);
            }
            this.reservations.remove(clientID);
            return true;
        }

        return false;
    }

	@Override
	public boolean updateReservation(Reservation r) throws RemoteException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean addReservation(Reservation r) throws RemoteException {
		// TODO Auto-generated method stub
		return false;
	}
}

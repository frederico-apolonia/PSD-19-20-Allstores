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

    /**
     * Loads shops from files
     * @throws IOException
     */
    private void loadShops() throws IOException {
        // todo
        // carregar o estado das lojas para memória
        for(int shopID = 0; shopID < NUMBER_OF_STORES; shopID++) {
            BufferedReader fileReader = new BufferedReader(new FileReader(SERVER_PATH + shopID + ".shop"));
            List<Product> shopProducts = new ArrayList<>();

            for(int fileLine = 0; fileLine < NUM_PRODUCT_IDS; fileLine++) {
                String line = fileReader.readLine();
                //productID available sold
                String[] shopQuantities = line.split(" ");

                int productID = Integer.parseInt(shopQuantities[0]);
                int available = Integer.parseInt(shopQuantities[1]);
                int sold = Integer.parseInt(shopQuantities[0]);

                shopProducts.add(new Product(shopID, productID, available, sold));
            }

            fileReader.close();
            shops.put(shopID, shopProducts);
        }
        // ir ao log e repôr o estado correto
        File logFile = new File(SERVER_PATH + "sales.log");
        if(logFile.exists()) {
            updateProductsFromLogs();
        }
    }

    /**
     * Updates files from logs
     */
    private void updateProductsFromLogs() throws IOException {
        String logPath = SERVER_PATH + "sales.log";
        BufferedReader fileReader = new BufferedReader(new FileReader(logPath));
        String line = fileReader.readLine();
        while(line != null) {
            String[] productDetails = line.split(" ");
            int shopID = Integer.parseInt(productDetails[0]);
            int productID = Integer.parseInt(productDetails[1]);
            int quantity = Integer.parseInt(productDetails[2]);

            updateBuyProduct(shopID, productID, quantity);
            line = fileReader.readLine();
        }
        fileReader.close();
        FileWriter fileWriter = new FileWriter(logPath, false);
        // guarantee that it is now empty
        fileWriter.write("");
        fileWriter.flush();
        fileWriter.close();
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
        BufferedWriter writer = new BufferedWriter(new FileWriter(shopPath, false));
        for(Product p : products) {
            writer.write(String.format("%d %d %d\n", p.getProductID(), p.getAvailable(), p.getSold()));
        }
    }

    private void writeBuyToLog(int shopID, int productID, int quantity) throws IOException {
        String logPath = SERVER_PATH + "sales.log";

        File logFile = new File(logPath);
        FileWriter fileWriter;
        if(logFile.exists()) {
            double fileSize = logFile.length();
            // if file size is larger than 1000000 bytes (1MB) then it's time to write the stores to disk
            if(fileSize > 1000000) {
                writeStoresToDisk();
                fileWriter = new FileWriter(logPath, false);
                // guarantee that it is now empty
                fileWriter.write("");
            } else {
                fileWriter = new FileWriter(logPath, true);
            }
        } else {
            fileWriter = new FileWriter(logPath);
        }

        fileWriter.append(String.format("%d %d %d", shopID, productID, quantity));
        fileWriter.flush();
        fileWriter.close();
    }

    @Override
    public synchronized List<Product> getShopProducts(int shopID) throws RemoteException {
        return shops.get(shopID);
    }

    @Override
    public synchronized boolean addReservation(Reservation reservation) throws RemoteException {
        /* Reservations don't go to logs, they don't matter if the system goes down and back up again */

        // update product information to match this new reservation
        if(!productUpdateReservation(reservation.getShopID(), reservation.getProductID(), reservation.getQuantity(), true)) {
            return false;
        }
        //
        List<Reservation> clientReservations = this.reservations.get(reservation.getClientID());
        if(clientReservations != null) {
            clientReservations.add(reservation);
        } else {
            clientReservations = new ArrayList<>();
            clientReservations.add(reservation);
            this.reservations.put(reservation.getClientID(), clientReservations);
        }
        // arm the 15s clock
        superviseReservation(reservation);

        return true;
    }

    public synchronized Reservation findClientReservation(int clientID, int shopID, int productID) {
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
                    removeReservation(reservation);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }, 15);
    }

    @Override
    public synchronized boolean buyProduct(int shopID, int productID, int quantity, int clientID) throws RemoteException {

        boolean result = false;
        // first, check if the client already has a reservation for this product
        Reservation existingReservation = findClientReservation(clientID, shopID, productID);
        int remainingReservationQuantity = -1;
        if(existingReservation != null) {
            // if so, disarm the clock, subtract the qty, if > 0, arm the clock again, else remove it. save the
            // qty difference (reserve.qty - quantity)
            remainingReservationQuantity = existingReservation.getQuantity() - quantity;
            result = removeReservation(existingReservation);
        }

        // update the product information (reservation space is already taken care of)
        updateBuyProduct(shopID, productID, quantity);
        // write to log
        try {
            writeBuyToLog(shopID, productID, quantity);
        } catch (IOException e) {
            // todo
            e.printStackTrace();
        }
        // check if a new reservation should be added (if client had a previous reservation and didn't buy everything)
        if (existingReservation != null) {
           if(remainingReservationQuantity > 0) {
               result = addReservation(new Reservation(clientID, shopID, productID, remainingReservationQuantity));
           }
        }
        return result;
    }

    /**
     * Updates the instance of the product that is being bought (or updated with logs)
     * @param shopID
     * @param productID
     * @param quantity
     */
    private void updateBuyProduct(int shopID, int productID, int quantity) {
        List<Product> shopProducts = shops.get(shopID);
        // find product and update it
        for(Product p : shopProducts) {
            if(p.getProductID() == productID) {
                p.setAvailable(p.getAvailable() - quantity);
                p.setSold(p.getSold() + quantity);
                break;
            }
        }
    }

    @Override
    public synchronized boolean removeReservation(Reservation reservation) throws RemoteException {
        int counter = 0;
        List<Reservation> clientReservations = this.reservations.get(reservation.getClientID());
        Reservation res = clientReservations.get(counter);

        while(!reservation.equals(res)) {
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

    @Override
    public synchronized boolean productUpdateReservation
            (int shopID, int productID, int reserveQuantity, boolean increaseReservation) throws RemoteException {

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
    public synchronized boolean cancelAllReservations(int clientID) throws RemoteException {

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
}

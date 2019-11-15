package DatabaseServer;

import DatabaseServer.Interfaces.IDataBase;
import org.apache.zookeeper.ZooKeeper;

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
    private static final String ALLSTORES_DB_PATH = System.getProperty("user.home")
            + FILE_SEPARATOR + "AllstoresDB" + FILE_SEPARATOR;
    private String serverPath;

    private HashMap<Integer, List<Product>> shops = new HashMap<>();
    private HashMap<Integer, List<Reservation>> reservations = new HashMap<>();

    private ZooKeeper zooKeeper;
    private int zooKeeperId;
    private int numberClients = 0;

    public DatabaseImpl(ZooKeeper zooKeeper, int zooKeeperId) throws Exception {
        this.zooKeeper = zooKeeper;
        this.zooKeeperId = zooKeeperId;
        this.serverPath = ALLSTORES_DB_PATH + zooKeeperId + FILE_SEPARATOR;
        initDataBase();
    }

    /**
     * Loads the shop products to memory.
     * Checks first if there is a previous state of the database then loads it, else
     * creates a new one from scratch (600 stores, 20 products ea)
     */
    private void initDataBase() throws IOException {

        List<String> clients;
        try {
            clients = this.zooKeeper.getChildren("/db/clients", false);
            this.numberClients = clients.size();
        } catch (Exception e) {
            System.err.println("Something happened while trying to fetch clients");
            e.printStackTrace();
            System.exit(0);
        }

        if (this.numberClients != 1) {
            // todo
            return;
        } else {
            System.out.println("Server path: " + ALLSTORES_DB_PATH);
            File serverDir = new File(ALLSTORES_DB_PATH);
            // check if there is a previous version of the server
            System.out.println("Checking if there is a previous state of the database...");
            if (serverDir.exists()) {
                System.out.println("Version found! Loading the latest saved state");
                loadShops(serverDir);
            } else {
                System.out.println("No version found, starting from scratch...");
                for (int shopID = 1; shopID <= NUMBER_OF_STORES; shopID++) {
                    generateNewShop(shopID);
                }
                System.out.println("All shops generated!");
                new File(ALLSTORES_DB_PATH).mkdir();
            }
        }
        // creates server folder
        new File(serverPath).mkdirs();
        // creates sales log file
        new File(serverPath + "sales.log").createNewFile();
        writeStoresToDisk();

    }

    /**
     * Loads shops from files
     * @throws IOException
     */
    private void loadShops(File serverDir) throws IOException {
        // we need to see if there is any "leftover" stores from previous runs
        File[] directories = serverDir.listFiles(File::isDirectory);
        assert directories != null;

        if (directories.length != 0) {

            // each directory is a previous run of a server shard
            for (File directory : directories) {
                File[] shops = directory.listFiles((file, name) -> !name.equals("sales.log"));
                assert shops != null;

                // go tru each SHOP FILE and create a new shop with their items
                for(File shop : shops) {
                    int shopID = Integer.parseInt(shop.getName().split(".")[0]);
                    BufferedReader fileReader = new BufferedReader(new FileReader(shop));
                    List<Product> shopProducts = new ArrayList<>();

                    for(int line = 0; line < NUM_PRODUCT_IDS; line++) {
                        String[] shopQuantities = fileReader.readLine().split(" ");

                        int productID = Integer.parseInt(shopQuantities[0]);
                        int available = Integer.parseInt(shopQuantities[1]);
                        int sold = Integer.parseInt(shopQuantities[2]);

                        shopProducts.add(new Product(shopID, productID, available, sold));
                    }
                    fileReader.close();
                    shop.delete();
                    this.shops.put(shopID, shopProducts);
                }
                // go to log and recover correct state
                File logFile = Objects.requireNonNull(directory.listFiles((file, name) -> name.equals("sales.log")))[0];
                updateProductsFromLogs(logFile);
                logFile.delete();
                directory.delete();
            }
        }
    }

    /**
     * Updates files from logs
     */
    private void updateProductsFromLogs(File logFile) throws IOException {
        BufferedReader fileReader = new BufferedReader(new FileReader(logFile));
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
    }

    private void generateNewShop(int shopID) {
        List<Product> currShopProds = new ArrayList<>();

        for(int productID = 1; productID <= NUM_PRODUCT_IDS; productID++) {
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
        String shopPath = this.serverPath + shopID + ".shop";
        BufferedWriter writer = new BufferedWriter(new FileWriter(shopPath, false));
        for(Product p : products) {
            writer.write(String.format("%d %d %d\n", p.getProductID(), p.getAvailable(), p.getSold()));
        }
        writer.flush();
        writer.close();
    }

    private boolean writeBuyToLog(int shopID, int productID, int quantity) throws IOException {
        String logPath = ALLSTORES_DB_PATH + "sales.log";

        File logFile = new File(logPath);
        FileWriter fileWriter;
        try {
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
            fileWriter.append(String.format("%d %d %d\n", shopID, productID, quantity));
            fileWriter.flush();
            fileWriter.close();
        } catch (IOException e) {
            return false;
        }
        return true;
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
        Reservation r = getClientReservation(clientID, shopID, productID);
        if (r != null) {
            return new Reservation(r.getClientID(), r.getShopID(), r.getProductID(), r.getQuantity());
        } else {
            return null;
        }
    }

    private Reservation getClientReservation(int clientID, int shopID, int productID) {
        Reservation result = null;
        List<Reservation> clientReservations = this.reservations.get(clientID);
        if (clientReservations != null) {
            for(Reservation r : this.reservations.get(clientID)) {
                if(r.getShopID() == shopID && r.getProductID() == productID) {
                    result = r;
                    break;
                }
            }
        }
        return result;
    }

    @Override
    public boolean updateClientReservation(Reservation reservation, int updateQuantity) throws RemoteException {
        boolean result = false;
        List<Reservation> clientReservations = this.reservations.get(reservation.getClientID());
        if(clientReservations != null) {
            for (Reservation r : clientReservations) {
                if(reservation.equals(r)) {
                    r.timer.cancel();
                    r.timer.purge();
                    int reserveQuantityIncrement = updateQuantity - r.getQuantity();
                    r.setQuantity(updateQuantity);
                    result = productUpdateReservation(r.getShopID(), r.getProductID(), reserveQuantityIncrement, true);
                    superviseReservation(r);
                }
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
        }, 15*1000);
    }

    @Override
    public synchronized boolean buyProduct(int shopID, int productID, int quantity, int clientID) throws RemoteException {

        boolean result = false;
        // first, check if the client already has a reservation for this product
        Reservation existingReservation = getClientReservation(clientID, shopID, productID);
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
            result = writeBuyToLog(shopID, productID, quantity);
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
        List<Reservation> result = null;
        if(this.reservations.containsKey(clientID)) {
            result = new ArrayList<>();
            for(Reservation r : this.reservations.get(clientID)) {
                result.add(new Reservation(r.getClientID(), r.getShopID(), r.getProductID(), r.getQuantity()));
            }

        }
        return result;
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

package DatabaseServer;

import DatabaseServer.Interfaces.IDataBase;
import org.apache.zookeeper.*;

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
    private static final String SHARED_PATH = ALLSTORES_DB_PATH + "shared" + FILE_SEPARATOR;
    private String serverPath;
    private String logPath;

    private HashMap<Integer, List<Product>> shops = new HashMap<>();
    private HashMap<Integer, List<Reservation>> reservations = new HashMap<>();

    private ZooKeeper zooKeeper;
    private int zooKeeperId;
    private int numberClients = 0;
    private Watcher dbsChangedWatcher;

    public DatabaseImpl(ZooKeeper zooKeeper, int zooKeeperId) throws Exception {
        this.zooKeeper = zooKeeper;
        this.zooKeeperId = zooKeeperId;
        this.serverPath = ALLSTORES_DB_PATH + zooKeeperId + FILE_SEPARATOR;
        this.logPath = serverPath + "sales.log";
        initDataBase();
    }

    /**
     * Loads the shop products to memory.
     * Checks first if there is a previous state of the database then loads it, else
     * creates a new one from scratch (600 stores, 20 products ea)
     */
    private void initDataBase() throws Exception {

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
            /*
             * If number of clients is different than 1, it means that there are more clients already alive
             * and controlling the data. We need to wait for them to prepare our data to be able to
             * set and run our shard
             */
            waitAndReadStores();
        } else {
            System.out.println("Server path: " + ALLSTORES_DB_PATH);
            File serverDir = new File(ALLSTORES_DB_PATH);
            // check if there is a previous version of the server
            System.out.println("Checking if there is a previous state of the database...");
            if (Objects.requireNonNull(serverDir.listFiles(File::isDirectory)).length > 1) {
                System.out.println("Version found! Loading the latest saved state");
                loadMultipleDBShops(serverDir);
            } else {
                System.out.println("No version found, starting from scratch...");
                for (int shopID = 1; shopID <= NUMBER_OF_STORES; shopID++) {
                    generateNewShop(shopID);
                }
                System.out.println("All shops generated!");
            }
            new File(SHARED_PATH).mkdirs();
        }
        // creates server folder
        new File(serverPath).mkdirs();
        // creates sales log file
        new File(logPath).createNewFile();
        writeStoresToDisk(this.shops, this.serverPath);

        setDbsChangedWatcher();
    }

    private void waitAndReadStores() throws Exception {
        // sleep 2 seconds to let the other servers notice us and start working on our data
        Thread.sleep(1000);
        String myNode = this.zooKeeper.create(String.format("/db/shared/read-%d", this.zooKeeperId), "".getBytes(),
                ZooDefs.Ids.READ_ACL_UNSAFE, CreateMode.EPHEMERAL);

        List<String> currentWaiters = this.zooKeeper.getChildren("/db/shared", false);
        currentWaiters.removeIf(s -> s.contains("read"));
        // order waiters list; WARNING: will work with numbers from 1 to 9, above it things might go wrong!
        File myStores = new File(SHARED_PATH);
        if (currentWaiters.size() == 0) {
            loadShops(myStores);
        } else {
            currentWaiters.sort(String::compareTo);
            String lastWriter = "/db/shared/" + currentWaiters.get(currentWaiters.size() - 1);

            zooKeeper.exists(Objects.requireNonNull(lastWriter), event -> {
                assert lastWriter.equals(event.getPath());

                if (event.getType() == Watcher.Event.EventType.NodeDeleted) {
                    try {
                        loadShops(myStores);
                        this.zooKeeper.delete(myNode, this.zooKeeper.exists(myNode, false).getVersion());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        }
    }

    private void setDbsChangedWatcher() throws Exception {
        System.out.println("Setting dbs changed watcher...");
        this.dbsChangedWatcher = watchedEvent -> {
            System.out.println("Nodes changed!");
            if (watchedEvent.getType() == Watcher.Event.EventType.NodeChildrenChanged) {
                assert watchedEvent.getPath().equals("/db/clients");

                try {
                    List<String> currentWaiters = zooKeeper.getChildren(watchedEvent.getPath(), null);
                    if (currentWaiters.size() > this.numberClients) {
                        this.numberClients = currentWaiters.size();
                        updateDbServers();
                    } /*else {
                        // next phase
                    }*/
                    setDbsChangedWatcher();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        zooKeeper.getChildren("/db/clients", this.dbsChangedWatcher);
    }

    private void updateDbServers() throws KeeperException, InterruptedException, IOException {
        String myNode = this.zooKeeper.create(String.format("/db/shared/write-%d", this.zooKeeperId), "".getBytes(),
                ZooDefs.Ids.READ_ACL_UNSAFE, CreateMode.EPHEMERAL);

        List<String> currentWaiters = this.zooKeeper.getChildren("/db/shared", false);

        List<String> readWaiters = new ArrayList<>(currentWaiters);
        readWaiters.removeIf(s -> !s.contains("read"));
        if (readWaiters.size() > 1) Thread.sleep(1000);

        currentWaiters.removeIf(s -> s.contains("read"));
        // order waiters list; WARNING: will work with numbers from 1 to 9, above it things might go wrong!
        currentWaiters.sort(String::compareTo);

        // how many shop should each db have now?
        int shopsPerStore = NUMBER_OF_STORES / this.numberClients;

        if (currentWaiters.get(0).equals(String.format("write-%d", this.zooKeeperId))) {
            // I am the first node, so I must check what I need to leave for the next node
            // from shops per store + 1 to the end of the stores that I own must go
            shareStoresFromDatabase(shopsPerStore);
            this.zooKeeper.delete(myNode, this.zooKeeper.exists(myNode, false).getVersion());

            deleteStoresOnDisk(this.serverPath);
            // update files on disk
            writeStoresToDisk(this.shops, this.serverPath);

            // delete logs
            BufferedWriter fileWriter = new BufferedWriter(new FileWriter(this.logPath));
            fileWriter.write("");
            fileWriter.close();
        } else {
            String beforeMyNode = null;
            // find the path of who is behind me
            for (int i = 1; i < currentWaiters.size(); i++) {
                if (currentWaiters.get(i).equals(String.format("write-%d", this.zooKeeperId))) {
                    beforeMyNode = "/db/shared/" + currentWaiters.get(i - 1);
                    break;
                }
            }

            String finalBeforeMyNode = beforeMyNode;
            zooKeeper.exists(Objects.requireNonNull(beforeMyNode), event -> {
                assert finalBeforeMyNode.equals(event.getPath());

                if (event.getType() == Watcher.Event.EventType.NodeDeleted) {
                    try {
                        loadShops(new File(SHARED_PATH));

                        shareStoresFromDatabase(shopsPerStore);
                        this.zooKeeper.delete(myNode, this.zooKeeper.exists(myNode, false).getVersion());
                        deleteStoresOnDisk(this.serverPath);
                        // update files on disk
                        writeStoresToDisk(this.shops, this.serverPath);
                        // delete logs
                        BufferedWriter fileWriter = new BufferedWriter(new FileWriter(this.logPath));
                        fileWriter.write("");
                        fileWriter.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });

        }

    }

    private void deleteStoresOnDisk(String serverPath) {
        File[] shops = new File(serverPath).listFiles();
        for (int i = shops.length - 1; i >= 0; i--) {
            if(!shops[i].getName().contains("sales.log"))
                shops[i].delete();
        }
    }

    private void shareStoresFromDatabase(int startingPosition) throws IOException {
        HashMap<Integer, List<Product>> dumpStores = new HashMap<>();
        /*
         * We need to get the shop ids because they won't always match the ids, for instance:
         * Our server is in charge of shops 301 to 600, and we need to deliver 50 shops, our i
         * will start at 250, but the ids start at 550. There's a difference between the
         * iteration of the shopIds and the iteration variable
         */
        List<Integer> serverShopIds = new ArrayList<>(this.shops.keySet());
        serverShopIds.sort(Integer::compareTo);

        for (int i = startingPosition; i < serverShopIds.size(); i++) {
            // gets the shopID at index i
            int shopId = serverShopIds.get(i);

            List<Product> shopProductsCopy = new ArrayList<>(this.shops.get(shopId));
            dumpStores.put(shopId, shopProductsCopy);
        }
        writeStoresToDisk(dumpStores, SHARED_PATH);

        // remove shops that are no longer owned by the server
        for (int i = serverShopIds.size() - 1; i >= startingPosition; i--) {
            // gets the shopID at index i
            int shopId = serverShopIds.get(i);
            // remove shop with the ID at index i
            this.shops.remove(shopId);
        }
    }

    private void loadMultipleDBShops(File serverDir) throws IOException {
        // we need to see if there is any "leftover" stores from previous runs
        File[] directories = serverDir.listFiles(File::isDirectory);
        assert directories != null;

        if (directories.length != 0) {

            // each directory is a previous run of a server shard
            for (File directory : directories) {
                if (!directory.getName().equals("shared")) {
                    loadShops(directory);
                    // go to log and recover correct state
                    File logFile = directory.listFiles()[0];
                    updateProductsFromLogs(logFile);
                    logFile.delete();
                    directory.delete();
                }
            }
        }
    }

    /**
     * Loads shops from files
     * @throws IOException
     */
    private void loadShops(File directory) throws IOException {
        File[] shops = directory.listFiles();
        if (shops != null) {
            // go tru each SHOP FILE and create a new shop with their items
            for(File shop : shops) {
                if (!shop.getName().contains("sales.log")) {
                    String shopFile = shop.getName();
                    String[] shopFileSplit = shopFile.split(".shop");
                    int shopID = Integer.parseInt(shopFileSplit[0]);
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
                    this.shops.put(shopID, shopProducts);
                }
            }
            for (int i = shops.length - 1; i >= 0; i--) {
                if(!shops[i].getName().contains("sales.log"))
                    shops[i].delete();
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
    private void writeStoresToDisk(HashMap<Integer, List<Product>> shops, String path) throws IOException {
        for (int shop : shops.keySet()) {
            writeStoreToDisk(shop, this.shops.get(shop), path);
        }
    }

    /**
     * Write a store to disk on file <storeID>.shop
     * @param shopID
     * @param products
     */
    private void writeStoreToDisk(int shopID, List<Product> products, String path) throws IOException {
        String shopPath = path + shopID + ".shop";
        BufferedWriter writer = new BufferedWriter(new FileWriter(shopPath, false));
        for(Product p : products) {
            writer.write(String.format("%d %d %d\n", p.getProductID(), p.getAvailable(), p.getSold()));
        }
        writer.flush();
        writer.close();
    }

    private boolean writeBuyToLog(int shopID, int productID, int quantity) throws IOException {

        File logFile = new File(logPath);
        FileWriter fileWriter;
        try {
            if(logFile.exists()) {
                double fileSize = logFile.length();
                // if file size is larger than 1000000 bytes (1MB) then it's time to write the stores to disk
                if(fileSize > 1000000) {
                    writeStoresToDisk(this.shops, this.serverPath);
                    fileWriter = new FileWriter(this.logPath, false);
                    // guarantee that it is now empty
                    fileWriter.write("");
                    fileWriter.close();
                }
            }
            LogWriter logWriter = new LogWriter(this.logPath, shopID, productID, quantity);
            logWriter.start();
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
        Product product = getShopProduct(reservation.getShopID(), reservation.getProductID());
        if(!product.reserve(reservation.getQuantity())) {
            return false;
        }

        Reservation newReservation = new Reservation(
                reservation.getClientID(),
                reservation.getShopID(),
                reservation.getProductID(),
                reservation.getQuantity()
        );
        //
        List<Reservation> clientReservations = this.reservations.get(newReservation.getClientID());
        if(clientReservations != null) {
            clientReservations.add(newReservation);
        } else {
            clientReservations = new ArrayList<>();
            clientReservations.add(newReservation);
            this.reservations.put(newReservation.getClientID(), clientReservations);
        }
        // arm the 15s clock
        superviseReservation(newReservation);

        return true;
    }

    /**
     * Get client reservation associated with a product
     * @param clientID
     * @param shopID
     * @param productID
     * @return Reservation if it has been placed, null if not
     */
    public Reservation getClientReservation(int clientID, int shopID, int productID) {
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
        // get reservation object
        Reservation r = getClientReservation(reservation.getClientID(), reservation.getShopID(), reservation.getProductID());
        // cancel reservation
        r.timer.cancel();
        r.timer.purge();

        // update product quantity
        int reserveQuantityIncrement = updateQuantity - r.getQuantity();
        Product product = getShopProduct(r.getShopID(), r.getProductID());
        if(product.reserve(reserveQuantityIncrement)) {
            r.setQuantity(updateQuantity);
            superviseReservation(r);
            return true;
        } else {
            // if it fails, we need to completely remove this reservation
            cancelReservation(r);
            return false;
        }
    }

    private void superviseReservation(Reservation reservation) {
        Timer timer = new Timer();
        reservation.timer = timer;
        // set a schedule to remove the reservation in 15 seconds
        reservation.timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    cancelReservation(reservation);
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
        int remainingReservationQuantity = 0;
        if(existingReservation != null) {
            // if so, disarm the clock, subtract the qty, if > 0, arm the clock again, else remove it. save the
            // qty difference (reserve.qty - quantity)
            remainingReservationQuantity = existingReservation.getQuantity() - quantity;
            result = cancelReservation(existingReservation);
        }

        Product product = getShopProduct(shopID, productID);
        // update the product information (reservation space is already taken care of)
        if(!product.sale(quantity)) {
            // buy fails because there's no sufficient products for buy
            if (existingReservation != null) {
                addReservation(new Reservation(clientID, shopID, productID, existingReservation.getQuantity()));
            }
            return false;
        }
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
     * Gets a product from a shop
     * @param shopID
     * @param productID
     * @return Product if product exists, else null
     */
    private Product getShopProduct(int shopID, int productID) {
        Product result = null;

        for(Product p : this.shops.get(shopID)) {
            if(p.getProductID() == productID) {
                result = p;
                break;
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

    /**
     * Removes (cancel) a reservation R (if it exists)
     * @param reservation
     * @return true if reservation was removed with success, false if it doesn't exist (or something happened)
     * @throws RemoteException
     */
    private boolean cancelReservation(Reservation reservation) throws RemoteException {
        if(reservation == null) {
            return false;
        }

        List<Reservation> clientReservations;

        if((clientReservations = getClientReservations(reservation.getClientID())) == null) {
            // check if client has reservations
            return false;
        }

        // remove reservation from reservation list
        if(!clientReservations.remove(reservation)) {
            return false;
        }

        // get product associated with this reservation
        Product product = getShopProduct(reservation.getShopID(), reservation.getProductID());
        // remove reservation placement
        return product.removeReservation(reservation.getQuantity());

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
        return this.reservations.get(clientID);
    }

    @Override
    public synchronized boolean cancelAllReservations(int clientID) throws RemoteException {

        List<Reservation> clientReservations = this.reservations.get(clientID);
        Product product;

        if(clientReservations != null) {
            for(Reservation r : clientReservations) {
                // cancel timer
                r.timer.cancel();
                // update product availability
                product = getShopProduct(r.getShopID(), r.getProductID());
                product.removeReservation(r.getQuantity());
            }
            this.reservations.remove(clientID);
            return true;
        }

        return false;
    }
}

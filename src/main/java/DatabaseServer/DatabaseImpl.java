package DatabaseServer;

import DatabaseServer.Interfaces.IDataBase;
import org.apache.log4j.pattern.LogEvent;
import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;

import java.io.*;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.logging.*;

public class DatabaseImpl extends UnicastRemoteObject implements IDataBase {

    private static final Logger LOGGER = Logger.getLogger( DatabaseImpl.class.getName() );

    // product ids are the first 20 letters of the alphabet
    private static final int NUM_PRODUCT_IDS = 20;
    private static final int NUMBER_OF_SHOPS = 600;

    private static final String FILE_SEPARATOR = File.separator;
    // property fetches the home path
    private static final String ALLSTORES_DB_PATH = System.getProperty("user.home")
            + FILE_SEPARATOR + "AllstoresDB" + FILE_SEPARATOR;
    private String serverPath;
    private String logPath;

    private HashMap<Integer, List<Product>> shops = new HashMap<>();
    private HashMap<Integer, List<Reservation>> reservations = new HashMap<>();

    // Track other DB servers <serverid, ip:port>
    private HashMap<String, String> dbServers = new HashMap<>();

    private ZooKeeper zooKeeper;
    private int zooKeeperId;
    private Watcher dbsChangedWatcher;

    private int firstShop;
    private int lastShop;

    public DatabaseImpl(ZooKeeper zooKeeper, int zooKeeperId) throws Exception {
        StreamHandler sh = new StreamHandler(System.out, new SimpleFormatter());
        sh.setLevel(Level.FINER);
        LOGGER.addHandler(sh);
        LOGGER.setLevel(Level.ALL);

        this.zooKeeper = zooKeeper;
        this.zooKeeperId = zooKeeperId;
        this.serverPath = ALLSTORES_DB_PATH + zooKeeperId + FILE_SEPARATOR;
        this.logPath = serverPath + "sales.log";
        this.dbServers = getDbServers();
        LOGGER.log(Level.FINE, String.format("Starting new Database instance\n" +
                        "Zookeeper ID (parsed): %d\n" +
                        "Server path: %s\n" +
                        "Server log path: %s\n",
                this.zooKeeperId, this.serverPath, this.logPath));
        initDataBase();
    }

    /**
     * Get all dbservers currently running
     * @return hashmap with <zookeeper id, ip:port></zookeeper>
     */
    private HashMap<String, String> getDbServers() {
        LOGGER.log(Level.FINE, "Getting all Database servers available on ZooKeeper...");
        HashMap<String, String> result = null;
        List<String> dbClients;
        try {
            dbClients = this.zooKeeper.getChildren("/db/clients", false);
            result = new HashMap<>();
            for(String dbServer: dbClients) {
                String dbServerPath =  "/db/clients/" + dbServer;
                LOGGER.log(Level.FINER, String.format("Getting zNode %s", dbServerPath));
                Stat currentDatabase = this.zooKeeper.exists(dbServerPath, false);
                byte[] zNodeData = this.zooKeeper.getData(dbServerPath, false, currentDatabase);
                String serverHost = new String(zNodeData);
                LOGGER.log(Level.FINER, String.format("zNode data (host): %s", serverHost));

                result.put(dbServer, serverHost);
            }
            LOGGER.log(Level.FINE, "Complete! All servers fetch!");
            return result;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Something happened while trying to fetch clients");
            e.printStackTrace();
            System.exit(0);
        }
        return result;
    }

    /**
     * Loads the shop products to memory.
     * Checks first if there is a previous state of the database then loads it, else
     * creates a new one from scratch (600 stores, 20 products ea)
     */
    private void initDataBase() throws Exception {

        if (this.dbServers.size() == 1)  {
            System.out.println("Server path: " + ALLSTORES_DB_PATH);
            File serverDir = new File(ALLSTORES_DB_PATH);
            // check if there is a previous version of the server
            LOGGER.log(Level.FINE, "Checking if there is a previous state of the database...");
            if (Objects.requireNonNull(serverDir.listFiles(File::isDirectory)).length > 0) {
                LOGGER.log(Level.FINE, "Version found! Loading the latest saved state");
                loadMultipleDBShops(serverDir);
            } else {
                LOGGER.log(Level.FINE, "No version found, starting from scratch...");
                for (int shopID = 1; shopID <= NUMBER_OF_SHOPS; shopID++) {
                    generateNewShop(shopID);
                }
                LOGGER.log(Level.FINE, "All shops generated!");
            }
        }
        // creates server folder
        LOGGER.log(Level.FINE, "Creating server folder");
        new File(serverPath).mkdirs();
        // creates sales log file
        LOGGER.log(Level.FINE, "Creating log file");
        new File(logPath).createNewFile();
        writeStoresToDisk(this.shops, this.serverPath);

        updateFirstLastShop();

        setDbsChangedWatcher();
    }

    private void setDbsChangedWatcher() throws Exception {
        LOGGER.log(Level.FINE, "Setting dbs changed watcher...");
        this.dbsChangedWatcher = watchedEvent -> {
            LOGGER.log(Level.FINE, "Nodes changed!");
            if (watchedEvent.getType() == Watcher.Event.EventType.NodeChildrenChanged) {
                assert watchedEvent.getPath().equals("/db/clients");
                LOGGER.log(Level.FINE, "Sleeping for 500ms to let the new node finish his setup");
                try {
                    Thread.sleep(500);
                }
                catch(InterruptedException ex) {
                    // https://www.javaspecialists.eu/archive/Issue056.html
                    Thread.currentThread().interrupt();
                }
                LOGGER.log(Level.FINE, "Woke up. Checking if servers increased or decreased");
                try {
                    HashMap<String, String> updatedDbServers = getDbServers();
                    if (updatedDbServers.size() > this.dbServers.size()) {
                        LOGGER.log(Level.FINE, "New DB server joined!");
                        /*
                         * 1. Send the stores that I'm controlling to the new server
                         * 2. Calculate the shops that I am now controlling
                         */
                        LOGGER.log(Level.FINE, "Preparing and sending my shops to the new server");
                        sendStoresToNewDbServer(updatedDbServers);
                        LOGGER.log(Level.FINE, "Complete! All shops sent!");
                    }

                    this.dbServers = updatedDbServers;
                    updateFirstLastShop();
                    setDbsChangedWatcher();
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Something happened while sending the shops!");
                    e.printStackTrace();
                }
            }
        };
        zooKeeper.getChildren("/db/clients", this.dbsChangedWatcher);
    }

    /**
     * Set first and last shop that my db server is controlling
     */
    private void updateFirstLastShop() {

        LOGGER.log(Level.FINE, "Updating the first and last shop boundaries");
        int myPosition = 0;
        List<String> dbServers = new ArrayList<>(this.dbServers.keySet());
        // convert zookeeper ids to integer
        List<Integer> zookeeperIdsParsed = new ArrayList<>();
        for (String zkId : dbServers) {
            zookeeperIdsParsed.add(Integer.parseInt(zkId.replaceFirst("^0+(?!$)", "")));
        }
        // sort dbservers by zookeeper id
        zookeeperIdsParsed.sort(Integer::compareTo);

        for (int zooKeeperId: zookeeperIdsParsed) {
            if(zooKeeperId == this.zooKeeperId) {
                break;
            }
            myPosition++;
        }
        LOGGER.log(Level.FINER, String.format("This server position is %d of %d servers", myPosition + 1, this.dbServers.size()));

        int shopsPerServer = NUMBER_OF_SHOPS / this.dbServers.size();
        LOGGER.log(Level.FINER, String.format("Number of servers:%d -> number of shops per server: %d",
                this.dbServers.size(), shopsPerServer));

        /*
        3 servers
        shopsPerServer=600/3
        0*200+1=1
        1+200-1=200

        1*200+1=201
        201+200-1=400

        2*200+1=401
        401+200-1=600
        */
        this.firstShop = (myPosition * shopsPerServer) + 1;
        this.lastShop = this.firstShop + shopsPerServer - 1;
        LOGGER.log(Level.FINE, String.format("First shop %d\nLast shop: %d", this.firstShop, this.lastShop));
    }

    /**
     *
     * @param updatedDbServers
     */
    private void sendStoresToNewDbServer(HashMap<String, String> updatedDbServers) {

        String newServerZKId = getZooKeeperId(updatedDbServers, updatedDbServers.size() - 1);
        String newServerHost = updatedDbServers.get(newServerZKId);

        LOGGER.log(Level.FINE, "Copying the stores that this server is responsible");
        HashMap<Integer, List<Product>> myShops = copyMyShops();
        LOGGER.log(Level.FINE, "Complete!");
        // connect to the new DB
        LOGGER.log(Level.FINE, "Getting a connection with the new Database...");
        IDataBase newDbConnection = connectToDatabaseServer(newServerHost);
        LOGGER.log(Level.FINE, "Connecting successful!");
        // send
        try {
            assert newDbConnection != null;
            LOGGER.log(Level.FINE, "Sending shops to the new server...");
            newDbConnection.updateDatabase(myShops);
            LOGGER.log(Level.FINE, "Complete! Shops sent!");
        } catch (RemoteException e) {
            LOGGER.log(Level.SEVERE, "There was an error while sending my shops to the new server!");
            e.printStackTrace();
        }

    }

    private String getZooKeeperId(HashMap<String, String> updatedDbServers, int position) {
        List<String> zooKeeperIds = new ArrayList<>(updatedDbServers.keySet());
        List<Integer> parsedIds = new ArrayList<>();

        for (String zkId: zooKeeperIds) {
            parsedIds.add(Integer.parseInt(zkId.replaceFirst("^0+(?!$)", "")));
        }
        parsedIds.sort(Integer::compareTo);

        int zooKeeperIdParsed = parsedIds.get(position);
        String result = null;
        for (String zkId : zooKeeperIds) {
            if(Integer.parseInt(zkId.replaceFirst("^0+(?!$)", "")) == zooKeeperIdParsed) {
                result = zkId;
            }
        }

        return result;
    }

    private IDataBase connectToDatabaseServer(String address) {
        try {
            LOGGER.log(Level.FINE, String.format("Connecting to %s", address));
            String[] data = address.split(":");
            assert data.length == 2;

            String databaseHost = data[0];
            int databasePort = Integer.parseInt(data[1]);

            Registry registry = LocateRegistry.getRegistry(databaseHost, databasePort);
            return (IDataBase) registry.lookup("AllstoresDatabaseServer");

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Creates a copy of the shops that the current DB is managing
     * @return copy of the shops that the current DB is managing
     */
    private HashMap<Integer, List<Product>> copyMyShops() {
        HashMap<Integer, List<Product>> result = new HashMap<>();
        int firstShop = this.firstShop;
        int lastShop = this.lastShop;
        for (int i = firstShop; i <= lastShop; i++) {
            LOGGER.log(Level.FINEST, String.format("Adding shop %d to the copy HashMap", i));
            result.put(i, this.shops.get(i));
        }
        return result;
    }

    private void loadMultipleDBShops(File serverDir) throws IOException {
        // we need to see if there is any "leftover" stores from previous runs
        File[] directories = serverDir.listFiles(File::isDirectory);
        assert directories != null;

        if (directories.length != 0) {
            LOGGER.log(Level.FINE, "Picking a previous server ran on this machine to load the state...");
            // each directory is a previous run of a server shard
            for (File directory : directories) {
                if (!directory.getName().equals("shared")) {
                    loadShops(directory);
                    // go to log and recover correct state
                    File logFile = directory.listFiles()[0];
                    updateProductsFromLogs(logFile);
                    logFile.delete();
                    directory.delete();
                    break;
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
        LOGGER.log(Level.FINE, String.format("Loading stores from %s", directory));
        if (shops != null) {
            // go tru each SHOP FILE and create a new shop with their items
            for(File shop : shops) {
                if (!shop.getName().contains("sales.log")) {
                    String shopFile = shop.getName();
                    String[] shopFileSplit = shopFile.split(".shop");
                    int shopID = Integer.parseInt(shopFileSplit[0]);
                    LOGGER.log(Level.FINER, String.format("Loading shop %d", shopID));
                    BufferedReader fileReader = new BufferedReader(new FileReader(shop));
                    List<Product> shopProducts = new ArrayList<>();

                    for(int line = 0; line < NUM_PRODUCT_IDS; line++) {
                        String[] shopQuantities = fileReader.readLine().split(" ");

                        int productID = Integer.parseInt(shopQuantities[0]);
                        int available = Integer.parseInt(shopQuantities[1]);
                        int sold = Integer.parseInt(shopQuantities[2]);
                        LOGGER.log(Level.FINEST, String.format("Adding product %d, available %d, sold %d",
                                productID, available, sold));
                        shopProducts.add(new Product(shopID, productID, available, sold));
                    }
                    fileReader.close();
                    this.shops.put(shopID, shopProducts);
                    LOGGER.log(Level.FINER, String.format("Shop %d complete", shopID));
                }
            }
            LOGGER.log(Level.FINER, "Deleting previous shop files...");
            for (int i = shops.length - 1; i >= 0; i--) {
                if(!shops[i].getName().contains("sales.log"))
                    shops[i].delete();
            }
            LOGGER.log(Level.FINER, "Complete! Previous shop files deleted!");
        }
    }

    /**
     * Updates files from logs
     */
    private void updateProductsFromLogs(File logFile) throws IOException {
        LOGGER.log(Level.FINE, "Updating current shops from a previous log file");
        BufferedReader fileReader = new BufferedReader(new FileReader(logFile));
        String line = fileReader.readLine();
        while(line != null) {
            String[] productDetails = line.split(" ");
            int shopID = Integer.parseInt(productDetails[0]);
            int productID = Integer.parseInt(productDetails[1]);
            int quantity = Integer.parseInt(productDetails[2]);
            LOGGER.log(Level.FINER, String.format("Updating product %d from shop %d with buy quantity %d",
                    productID, shopID, quantity));
            updateBuyProduct(shopID, productID, quantity);
            line = fileReader.readLine();
        }
        fileReader.close();
        LOGGER.log(Level.FINE, "Done, all shops updated from logger");
    }

    private void generateNewShop(int shopID) {
        List<Product> currShopProds = new ArrayList<>();

        for(int productID = 1; productID <= NUM_PRODUCT_IDS; productID++) {
            Product p = new Product(shopID, productID);
            currShopProds.add(p);
            LOGGER.log(Level.FINEST, String.format("Added product %s to " +
                    "shop %d with quantity %d", p.getProductID(), shopID, p.getAvailable()));
        }
        LOGGER.log(Level.FINER, String.format("Adding shop %d to shops", shopID));
        this.shops.put(shopID, currShopProds);
    }

    /**
     * Writes all stores to disk
     */
    private void writeStoresToDisk(HashMap<Integer, List<Product>> shops, String path) throws IOException {
        LOGGER.log(Level.FINER, "Writing shops to disk...");
        for (int shop : shops.keySet()) {
            writeStoreToDisk(shop, this.shops.get(shop), path);
        }
        LOGGER.log(Level.FINER, "Complete! All shops written to disk!");
    }

    /**
     * Write a store to disk on file <storeID>.shop
     * @param shopID
     * @param products
     */
    private void writeStoreToDisk(int shopID, List<Product> products, String path) throws IOException {
        LOGGER.log(Level.FINER, String.format("Writing shop %d to disk", shopID));
        String shopPath = path + shopID + ".shop";
        BufferedWriter writer = new BufferedWriter(new FileWriter(shopPath, false));
        for(Product p : products) {
            LOGGER.log(Level.FINEST, String.format("Writing product %d with %d available and %d sold",
                    p.getProductID(), p.getAvailable(), p.getSold()));
            writer.write(String.format("%d %d %d\n", p.getProductID(), p.getAvailable(), p.getSold()));
        }
        writer.flush();
        writer.close();
        LOGGER.log(Level.FINER, String.format("Complete! Wrote shop %d to disk", shopID));
    }

    private boolean writeBuyToLog(int shopID, int productID, int quantity) throws IOException {

        File logFile = new File(logPath);
        FileWriter fileWriter;
        try {
            if(logFile.exists()) {
                double fileSize = logFile.length();
                // if file size is larger than 100000 bytes (100KB) then it's time to write the stores to disk
                if(fileSize > 100000) {
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
        LOGGER.log(Level.FINER, String.format("Request to get all products for shop %d", shopID));
        if(shopID > this.lastShop || shopID < this.firstShop) {
            LOGGER.log(Level.WARNING, String.format("Received a request to get a shop that is out of bounds!\n" +
                    "Requested shop: %d; Min shop: %d; Max shop: %d", shopID, this.firstShop, this.lastShop));
            return null;
        }
        return shops.get(shopID);
    }

    @Override
    public synchronized boolean addReservation(Reservation reservation) throws RemoteException {
        /* Reservations don't go to logs, they don't matter if the system goes down and back up again */
        LOGGER.log(Level.FINER, "Received a request to add a new reservation");
        LOGGER.log(Level.FINER, String.format("Reservation contents:\nCID: %d; SID: %d; PID: %d; Qty: %d",
                reservation.getClientID(), reservation.getShopID(), reservation.getProductID(), reservation.getQuantity()));

        // update product information to match this new reservation
        LOGGER.log(Level.FINEST, "Getting product");
        Product product = getShopProduct(reservation.getShopID(), reservation.getProductID());
        LOGGER.log(Level.FINEST, "Updating product to reserve the items");
        if(!product.reserve(reservation.getQuantity())) {
            LOGGER.log(Level.WARNING, "Received a request to reserve with a larger quantity than available");
            return false;
        }
        LOGGER.log(Level.FINEST, "Product updated! Creating the new reservation...");
        Reservation newReservation = new Reservation(
                reservation.getClientID(),
                reservation.getShopID(),
                reservation.getProductID(),
                reservation.getQuantity()
        );
        //
        LOGGER.log(Level.FINEST, String.format("Finding if client %d already had reservations",
                reservation.getClientID()));
        List<Reservation> clientReservations = this.reservations.get(newReservation.getClientID());
        if(clientReservations != null) {
            LOGGER.log(Level.FINEST, "Client already has reservations, adding a new one");
            clientReservations.add(newReservation);
        } else {
            LOGGER.log(Level.FINEST, "Client doesn't have existing reservations. Creating a new list");
            clientReservations = new ArrayList<>();
            LOGGER.log(Level.FINEST, "Adding the reservation to the new list");
            clientReservations.add(newReservation);
            LOGGER.log(Level.FINEST, "Adding reservation list to reservations map.");
            this.reservations.put(newReservation.getClientID(), clientReservations);
        }
        LOGGER.log(Level.FINEST,"Reservation added, arming the alarm...");
        // arm the 15s clock
        superviseReservation(newReservation);
        LOGGER.log(Level.FINEST, "Clock armed! Add reservation complete");
        return true;
    }

    /**
     * Get client reservation associated with a product
     * @param clientID
     * @param shopID
     * @param productID
     * @return Reservation if it has been placed, null if not
     */
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
        LOGGER.log(Level.FINER, String.format("Received a request to update a previous client reservation with new quantity %d",
                updateQuantity));
        // get reservation object
        Reservation r = getClientReservation(reservation.getClientID(), reservation.getShopID(), reservation.getProductID());
        LOGGER.log(Level.FINER, "Canceling previous reservation timer");
        // cancel reservation
        r.timer.cancel();
        r.timer.purge();

        // update product quantity
        LOGGER.log(Level.FINER, "Updating reservation quantity on product");
        int reserveQuantityIncrement = updateQuantity - r.getQuantity();
        Product product = getShopProduct(r.getShopID(), r.getProductID());
        if(product.reserve(reserveQuantityIncrement)) {
            r.setQuantity(updateQuantity);
            LOGGER.log(Level.FINER, "Reservation updated successfully, setting the new timer");
            superviseReservation(r);
            return true;
        } else {
            LOGGER.log(Level.WARNING, "Update reservation failed! Received more quantity than available" +
                    "Removing the reservation.");
            // if it fails, we need to completely remove this reservation
            cancelReservation(r);
            return false;
        }
    }

    private void superviseReservation(Reservation reservation) {
        LOGGER.log(Level.FINER, String.format("Setting a new timer for reservation of CID %d and PID %d",
                reservation.getClientID(), reservation.getProductID()));
        Timer timer = new Timer();
        reservation.timer = timer;
        // set a schedule to remove the reservation in 15 seconds
        reservation.timer.schedule(new TimerTask() {
            @Override
            public void run() {
                LOGGER.log(Level.FINEST, String.format("Reservation CID %d and PID %d with quantity %d expired!\nUpdating product",
                        reservation.getClientID(), reservation.getProductID(), reservation.getQuantity()));
                try {
                    cancelReservation(reservation);
                } catch (RemoteException e) {
                    LOGGER.log(Level.WARNING, "Something happened while removing a reservation!");
                    e.printStackTrace();
                }
                LOGGER.log(Level.FINEST, "Reservation successfully canceled!");
            }
        }, 15*1000);
    }

    @Override
    public synchronized boolean buyProduct(int shopID, int productID, int quantity, int clientID) throws RemoteException {
        if(shopID > this.lastShop || shopID < this.firstShop) {
            LOGGER.log(Level.WARNING, String.format("Received a request to buy a product from a shop that is out of bounds!\n" +
                    "Requested shop: %d; Min shop: %d; Max shop: %d", shopID, this.firstShop, this.lastShop));
            return false;
        }
        boolean result = false;
        // first, check if the client already has a reservation for this product
        LOGGER.log(Level.FINEST, String.format("Received a request to buy a PID %d of SID %d with qty %d from CID %d",
                productID, shopID, quantity, clientID));
        LOGGER.log(Level.FINEST, "Checking if client has an existing reservation for this product");
        Reservation existingReservation = getClientReservation(clientID, shopID, productID);
        int remainingReservationQuantity = 0;
        if(existingReservation != null) {
            LOGGER.log(Level.FINEST, "Reservation found, disarming the clock");
            // if so, disarm the clock, subtract the qty, if > 0, arm the clock again, else remove it. save the
            // qty difference (reserve.qty - quantity)
            existingReservation.timer.cancel();
            existingReservation.timer.purge();

            remainingReservationQuantity = existingReservation.getQuantity() - quantity;
            LOGGER.log(Level.FINEST, String.format("Reservation clock canceled. Remaining quantity for this reservation: %d",
                    remainingReservationQuantity));
            result = cancelReservation(existingReservation);
        }

        Product product = getShopProduct(shopID, productID);
        // update the product information (reservation space is already taken care of)
        LOGGER.log(Level.FINEST,"Updating product information (updating the available and sold values)");
        if(!product.sale(quantity)) {
            // buy fails because there's no sufficient products for buy
            LOGGER.log(Level.WARNING, "Received a request to buy a product that doesn't contain the required amount of quantity!");
            LOGGER.log(Level.WARNING, String.format("Available quantity: %d Requested %d", product.getAvailable(), quantity));
            if (existingReservation != null) {
                LOGGER.log(Level.FINEST, "Updating reservation");
                addReservation(new Reservation(clientID, shopID, productID, existingReservation.getQuantity()));
            }
            return false;
        }
        // write to log
        LOGGER.log(Level.FINEST, String.format("Writing buy to log: SID %d PID %d Qty %d", shopID, productID, quantity));
        try {
            result = writeBuyToLog(shopID, productID, quantity);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "ERROR while writing buy to log! Proceeding...");
            e.printStackTrace();
        }
        LOGGER.log(Level.FINEST, "Successfully wrote buy to log");
        // check if a new reservation should be added (if client had a previous reservation and didn't buy everything)
        if (existingReservation != null) {
           if(remainingReservationQuantity > 0) {
                LOGGER.log(Level.FINEST, String.format("Adding a new reservation for the remaining quantity %d",
                        remainingReservationQuantity));
               result = addReservation(new Reservation(clientID, shopID, productID, remainingReservationQuantity));
           }
        }
        LOGGER.log(Level.FINEST, "Sending buy to replicas");
        sendBuyToAllReplicas(shopID, productID, quantity);

        LOGGER.log(Level.FINEST, "Purchase complete!");
        return result;
    }

    /**
     * Send the product bought to all replicas
     * @param shopID
     * @param productID
     * @param quantity
     */
    private void sendBuyToAllReplicas(int shopID, int productID, int quantity) {
        LOGGER.log(Level.FINEST, String.format("Sending buy SID %d PID %d QTY %d to all %d replicas",
                shopID, productID, quantity, this.dbServers.size() - 1));
        for (String dbKey: this.dbServers.keySet()) {
            if(Integer.parseInt(dbKey.replaceFirst("^0+(?!$)", "")) != this.zooKeeperId) {
                try {
                    LOGGER.log(Level.FINEST, String.format("Sending to db %s", dbKey));
                    IDataBase replica = connectToDatabaseServer(this.dbServers.get(dbKey));
                    assert replica != null;

                    replica.sendBuyToReplica(shopID, productID, quantity);
                    LOGGER.log(Level.FINEST, "Sent!");
                } catch (RemoteException e) {
                    LOGGER.log(Level.WARNING, String.format("ERROR while sending buy SID %d PID %d QTY %d to replica %s",
                            shopID, productID, quantity, dbKey));
                    e.printStackTrace();
                }
            }
        }
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
        Product product = getShopProduct(shopID, productID);
        product.sale(quantity);
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
        LOGGER.log(Level.FINEST, String.format("Removing reservation CID %d, PID %d",
                reservation.getClientID(), reservation.getProductID()));
        List<Reservation> clientReservations;

        if((clientReservations = this.reservations.get(reservation.getClientID())) == null) {
            LOGGER.log(Level.WARNING, String.format("Request to remove a reservation that doesn't exist! PID %d CID %d",
                    reservation.getProductID(), reservation.getClientID()));
            // check if client has reservations
            return false;
        }

        LOGGER.log(Level.FINEST, "Client has reservation in place");
        // remove reservation from reservation list
        if(!clientReservations.remove(reservation)) {
            LOGGER.log(Level.WARNING, String.format("Request to remove a reservation that doesn't exist! PID %d CID %d",
                    reservation.getProductID(), reservation.getClientID()));
            return false;
        }

        // get product associated with this reservation
        Product product = getShopProduct(reservation.getShopID(), reservation.getProductID());
        // remove reservation placement
        LOGGER.log(Level.FINEST, "Updating product info and returning");
        return product.removeReservation(reservation.getQuantity());

    }

    @Override
    public List<Reservation> getClientReservations(int clientID) throws RemoteException {
        LOGGER.log(Level.FINER, String.format("Request for all client %d reservations", clientID));
        List<Reservation> result = new ArrayList<>();
        if(this.reservations.containsKey(clientID)) {
            LOGGER.log(Level.FINER, String.format("Found reservations for client %d", clientID));
            LOGGER.log(Level.FINER, "Copying reservations to an ArrayList...");
            for(Reservation r : this.reservations.get(clientID)) {
                LOGGER.log(Level.FINEST, String.format("Copying reservation for shop %d, product %d and quantity %d",
                        r.getShopID(), r.getProductID(), r.getQuantity()));
                result.add(new Reservation(r.getClientID(), r.getShopID(), r.getProductID(), r.getQuantity()));
            }

        }
        LOGGER.log(Level.FINER, String.format("Complete! Found %d reservations for client %d",
                result.size() ,clientID));
        return result;
    }

    @Override
    public synchronized boolean cancelAllReservations(int clientID) throws RemoteException {
        LOGGER.log(Level.FINER, String.format("Request to cancel all reservations associated with client %d", clientID));
        List<Reservation> clientReservations = this.reservations.get(clientID);
        Product product;

        if(clientReservations != null) {
            LOGGER.log(Level.FINER, "Reservations found! Canceling...");
            for(Reservation r : clientReservations) {
                LOGGER.log(Level.FINEST, "Canceling reservation for product %d", r.getProductID());
                LOGGER.log(Level.FINEST, "Canceling timer");
                // cancel timer
                r.timer.cancel();
                // update product availability
                LOGGER.log(Level.FINEST, String.format("Removing reservation for product %d", r.getProductID()));
                product = getShopProduct(r.getShopID(), r.getProductID());
                product.removeReservation(r.getQuantity());
                LOGGER.log(Level.FINEST, String.format("Reservation with %d quantity removed", r.getQuantity()));
            }
            LOGGER.log(Level.FINER, "All reservations removed, removing client reservation list");
            this.reservations.remove(clientID);
            LOGGER.log(Level.FINER, "Done! All all client reservations are now removed.");
            return true;
        }
        LOGGER.log(Level.FINER, String.format("Client %d has no active reservations", clientID));
        return false;
    }

    @Override
    public Reservation findClientReservation(int clientID, int shopID, int productID) throws RemoteException {
        LOGGER.log(Level.FINER, String.format("Request to find client reservation, CID: %d, SID: %d, PID: %d",
                clientID, shopID, productID));
        Reservation r = getClientReservation(clientID, shopID, productID);
        if(r != null) {
            LOGGER.log(Level.FINER, "Found! Creating a copy and sending it back!");
            return new Reservation(r.getClientID(), r.getShopID(), r.getProductID(), r.getQuantity());
        } else {
            LOGGER.log(Level.FINER, "Not found, this reservation doesn't exist!");
            return null;
        }
    }

    @Override
    public boolean sendBuyToReplica(int shopID, int productID, int quantity) {
        LOGGER.log(Level.FINEST, String.format("Received a buy from the main replica for shop %d, product %d, quantity %d",
                shopID, productID, quantity));
        LOGGER.log(Level.FINEST, "Getting product from shop");
        Product product = getShopProduct(shopID, productID);
        LOGGER.log(Level.FINEST, "Updating product");
        boolean result = product.sale(quantity);
        try {
            LOGGER.log(Level.FINEST, "Writing update to disk (logging...)");
            result = result && writeBuyToLog(shopID, productID, quantity);
            LOGGER.log(Level.FINEST, "Done!");
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error while writing to logger!");
            e.printStackTrace();
        }
        return result;
    }

    @Override
    public synchronized void updateDatabase(HashMap<Integer, List<Product>> shops) {
        LOGGER.log(Level.FINE, String.format("Received a HashMap with %d shops", shops.size()));
        this.shops.putAll(shops);
        try {
            LOGGER.log(Level.FINE, "Writing new shops to disk!");
            writeStoresToDisk(this.shops, this.serverPath);
            LOGGER.log(Level.FINE, "Complete! All stores written");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

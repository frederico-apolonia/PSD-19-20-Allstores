package DatabaseServer;

import DatabaseServer.Interfaces.IDataBase;

import java.io.File;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashMap;

public class DatabaseImpl extends UnicastRemoteObject implements IDataBase {

    // product ids are the first 20 letters of the alphabet
    private static final String PRODUCT_IDS = "abcdefgijklmnopqrstu";
    private static final int NUM_PRODUCT_IDS = PRODUCT_IDS.length();
    private static final int NUMBER_OF_STORES = 600;

    private static final String FILE_SEPARATOR = File.separator;
    // property fetches the home path
    private static final String SERVER_PATH = System.getProperty("user.home")
            + FILE_SEPARATOR + "AllstoresDB" + FILE_SEPARATOR;

    private HashMap<String, Product> shopProducts = new HashMap<>();
    private ArrayList<Reservation> reservations = new ArrayList<>();

    public DatabaseImpl() throws RemoteException {
        initDataBase();
    }

    /**
     * Loads the shop products to memory.
     * Checks first if there is a previous state of the database then loads it, else
     * creates a new one from scratch (600 stores, 20 products ea)
     */
    private void initDataBase() {
        // initialize products
        shopProducts = new HashMap<>();

        File serverDir = new File(SERVER_PATH);
        // check if there is a previous version of the server
        System.out.println("Checking if there is a previous state of the database...");
        if (serverDir.exists()) {
            // load files
        } else {
            System.out.println("No version found, starting from scratch...");
            for (int shopID = 0; shopID < NUMBER_OF_STORES; shopID++) {
                Product[] currShopProds = new Product[NUM_PRODUCT_IDS];

                for(int j = 0; j < NUM_PRODUCT_IDS; j++) {
                    Product p = new Product(shopID, String.format("%s", PRODUCT_IDS.charAt(j)));
                    String shopProductsKey = p.getProductsKey();
                    shopProducts.put(shopProductsKey, p);
                    System.out.println(String.format("Added product %s to " +
                            "shop %d with quantity %d", p.getProductID(), shopID,
                            p.getAvailable()));
                    currShopProds[j] = p;
                }
                // initialize shop file
            }
        }

    }
}

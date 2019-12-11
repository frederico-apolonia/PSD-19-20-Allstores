package DatabaseServer;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;

public class ShopWriter extends Thread {

    private String path;
    private HashMap<Integer, List<Product>> shops;

    public ShopWriter(String path, HashMap<Integer, List<Product>> shops) {
        this.path = path;
        this.shops = shops;
    }

    /**
     * Writes all stores to disk
     */
    private void writeStoresToDisk() throws IOException {
        for (int shop : shops.keySet()) {
            writeStoreToDisk(shop, this.shops.get(shop));
        }
    }

    /**
     * Write a store to disk on file <storeID>.shop
     * @param shopID
     * @param products
     */
    private void writeStoreToDisk(int shopID, List<Product> products) throws IOException {
        String shopPath = path + shopID + ".shop";
        BufferedWriter writer = new BufferedWriter(new FileWriter(shopPath, false));
        for(Product p : products) {
            writer.write(String.format("%d %d %d\n", p.getProductID(), p.getAvailable(), p.getSold()));
        }
        writer.flush();
        writer.close();
    }

    @Override
    public void run() {
        try {
            writeStoresToDisk();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

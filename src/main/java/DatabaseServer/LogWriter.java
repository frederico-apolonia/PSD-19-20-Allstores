package DatabaseServer;

import java.io.FileWriter;
import java.io.IOException;

public class LogWriter extends Thread {

    private String logPath;
    private int shopID, productID, quantity;

    public LogWriter(String logPath, int shopID, int productID, int quantity) {
        this.logPath = logPath;
        this.shopID = shopID;
        this.productID = productID;
        this.quantity = quantity;
    }

    @Override
    public void run() {
        try {
            FileWriter fileWriter = new FileWriter(logPath, true);
            fileWriter.append(String.format("%d %d %d\n", shopID, productID, quantity));
            fileWriter.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

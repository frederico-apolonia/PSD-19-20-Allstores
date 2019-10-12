package DatabaseServer;

import java.util.Timer;

public class Reservation {

    private int clientID;
    private int shopID;
    private int productID;
    private int quantity;
    public Timer timer;

    public Reservation(int clientID, int shopID, int productID, int quantity) {
        this.clientID = clientID;
        this.shopID = shopID;
        this.productID = productID;
        this.quantity = quantity;
    }

    public int getClientID() {
        return clientID;
    }

    public void setClientID(int clientID) {
        this.clientID = clientID;
    }

    public int getShopID() {
        return shopID;
    }

    public void setShopID(int shopID) {
        this.shopID = shopID;
    }

    public int getProductID() {
        return productID;
    }

    public void setProductID(int productID) {
        this.productID = productID;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    @Override
    public boolean equals(Object obj) {
        if(obj instanceof Reservation)
            if(this.clientID == ((Reservation) obj).getClientID())
                if(this.shopID == ((Reservation) obj).getShopID())
                    if(this.productID == ((Reservation) obj).getProductID())
                        return this.quantity == ((Reservation) obj).getQuantity();

        return false;
    }
}

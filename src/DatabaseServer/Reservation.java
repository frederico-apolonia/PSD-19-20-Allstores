package DatabaseServer;

public class Reservation {

    private int clientID;
    private int shopID;
    private int productID;
    private int quantity;

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
}

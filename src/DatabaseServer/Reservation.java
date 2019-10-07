package DatabaseServer;

public class Reservation {

    private int clientID;
    private String productsKey;
    private int shopID;
    private String productID;
    private int quantity;

    public Reservation(int clientID, String productsKey, int shopID, String productID, int quantity) {
        this.clientID = clientID;
        this.productsKey = productsKey;
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

    public String getProductsKey() {
        return productsKey;
    }

    public void setProductsKey(String productsKey) {
        this.productsKey = productsKey;
    }

    public int getShopID() {
        return shopID;
    }

    public void setShopID(int shopID) {
        this.shopID = shopID;
    }

    public String getProductID() {
        return productID;
    }

    public void setProductID(String productID) {
        this.productID = productID;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}

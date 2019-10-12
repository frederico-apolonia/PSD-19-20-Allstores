package DatabaseServer;

public class Product {

    private static final int DEFAULT_STARTING_STOCK = 10;

    private int shopID;
    private int productID;
    private int available;
    private int sold;
    private int reserved;

    public Product(int shopID, int productID) {
        this.shopID = shopID;
        this.productID = productID;

        // o stock default
        this.available = DEFAULT_STARTING_STOCK;
        this.sold = 0;
        this.reserved = 0;
    }

    public Product(int shopID, int productID, int available, int sold) {
        this.shopID = shopID;
        this.productID = productID;
        this.available = available;
        this.sold = sold;

        this.reserved = 0;
    }

    @Override
    public String toString() {
        return "ProductID: " + this.productID + " ShopID: " +
                this.shopID + " Num Available: " + this.available +
                " Num Sold: " + this.sold + " Num reserved: " +
                this.reserved;
    }

    public int getShopID() {
        return shopID;
    }

    /**
     * @return unique key for each map entry
     */
    public int getProductsKey() {
        return this.shopID + this.productID;
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

    public int getAvailable() {
        return available;
    }

    public void setAvailable(int available) {
        this.available = available;
    }

    public int getSold() {
        return sold;
    }

    public void setSold(int sold) {
        this.sold = sold;
    }

    public int getReserved() {
        return reserved;
    }

    public void setReserved(int reserved) {
        this.reserved = reserved;
    }
}

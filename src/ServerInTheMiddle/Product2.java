package ServerInTheMiddle;
import java.io.Serializable;

public class Product2 implements Serializable {

	private String productID; //{A,B,C,D,E,F,G,G,I,J,K,L,M,N,O,P,Q,R,S,T}
	private int availableQnt;
	private int soldQnt;
	
	public Product2(String productID, int availableQnt, int soldQnt) {
		this.productID = productID;
		this.availableQnt = availableQnt;
		this.soldQnt = soldQnt;
	}
	
	public String getProductID() {
		return this.productID;
	}
	
	public int getAvailableQnt() {
		return this.availableQnt;
	}
	
	public int getSoldQnt() {
		return this.soldQnt;
	}
	
	public void setSoldQnt(int sold) {
		this.soldQnt = sold;
		this.availableQnt = this.availableQnt - sold;
	}
	
	public void setAvailableQnt(int available) {
		this.availableQnt = available;
	}
	
	public String toString() {
		return "Product " + this.productID + "; Available: " + this.availableQnt + "; Sold: " + this.soldQnt + ".\n";
	}
}

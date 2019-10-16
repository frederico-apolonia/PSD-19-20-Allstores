package Client;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;
import java.util.Scanner;
import ManInTheMiddleClient.ClientServerInterface;

public class Client {

	private static final int INTERFACE_PORT = 2000;

	public static void main(String[] args) throws Exception {

		String host = "10.101.148.179"; // default host
		ClientServerInterface CSIstub = null;
		int clientID = 0, storeID = 0, productID = 0, quantity = 0;

		try {
			while(true) {
				System.out.println("Welcome to Allstores, a place where you can buy products from 600 stores!\n"
						+ "Tell us what you want to do (List, Reserve, Buy, Buyall, Cancel):");

				Scanner input = new Scanner(System.in);
				String cmdline= input.nextLine();

				if(!tryParseInt(cmdline)) {
					if("exit".compareTo(cmdline)==0)
						break;
				}

				// gives details about the methods the client can call
				if(cmdline.equals("man")) {
					System.out.println("Type List <storeID> to get a list of the products from a specific store.\n <storeID> int 1-600.\n\n"
							+ "Type Reserve <clientID> <storeID> <productID> <quantity> to make a reservation of a quantity of a specific product from a specific store.\n <clientID> int, <storeID> int 1-600, <productID> int 1-20, <quantity> int.\n\n"
							+ "Type Buy <clientID> <storeID> <productID> <quantity> to buy a quantity of a specific product from a specific store, it checks if it was reserved previously.\n <clientID> int, <storeID> int 1-600, <productID> int 1-20, <quantity> int.\n\n"
							+ "Type Buyall <clientID> to buy all the products reserved with that client ID.\n <lientID> int.\n\n"
							+ "Type Cancel <clientID> to cancel all the reservations made with that client ID.\n <clientID> int.");
				}

				
				// getting the registry and looking up the registry for the remote object
				Registry registry = LocateRegistry.getRegistry(host,INTERFACE_PORT);
				CSIstub = (ClientServerInterface) registry.lookup("ClientServerInterface");


				// "List storeID"
				if(cmdline.toUpperCase().startsWith("LIST")) {
					String[] splitInput = cmdline.split(" ");
					
					if (splitInput.length != 2) {
						System.out.println("Error: check if you introduced all (2) the parameters right!");
						break;
					} else {
						storeID = Integer.parseInt(splitInput[1]);
					}

					if (storeID == 0) {
						System.out.println("Error: check if the number introduced is between 1 and 600!");
						break;
					}

					List<String> resultList = CSIstub.getList(storeID);

					System.out.println("Available products on store number " + storeID + ":\n");

					for (String s : resultList) {
						System.out.println(s); // <stock> message, listing the products of the store required by the client
					}
				}

				// "Reserve clientID storeID productID quantity"
				if(cmdline.toUpperCase().startsWith("RESERVE")) {
					String[] splitInput = cmdline.split(" ");

					if (splitInput.length != 5) {
						System.out.println("Error: check if you introduced all (5) the parameters right!");
						break;
					} else {
						clientID = Integer.parseInt(splitInput[1]);
						storeID = Integer.parseInt(splitInput[2]);
						productID = Integer.parseInt(splitInput[3]);
						quantity = Integer.parseInt(splitInput[4]);
					}

					if (clientID == 0 || storeID == 0 || productID == 0 || quantity == 0) {
						System.out.println("Error: check if the numbers introduced match the parameters!");
						break;
					} else {
						//String resultReserve = CSIstub.getReservation(clientID, storeID, productID, quantity);

						// System.out.println(); // <reserved> or <unavailable> message
					}
				}

				// "Buy clientID storeID productID quantity"
				if(cmdline.toUpperCase().startsWith("BUY ")) {
					String[] splitInput = cmdline.split(" ");

					if (splitInput.length != 5) {
						System.out.println("Error: check if you introduced all (5) the parameters right!");
						break;
					} else {
						clientID = Integer.parseInt(splitInput[1]);
						storeID = Integer.parseInt(splitInput[2]);
						productID = Integer.parseInt(splitInput[3]);
						quantity = Integer.parseInt(splitInput[4]);
					}

					if (clientID == 0 || storeID == 0 || productID == 0 || quantity == 0) {
						System.out.println("Error: check if the numbers introduced match the parameters!");
						break;
					} else {
						String resultBuy = CSIstub.buy(clientID, storeID, productID, quantity);

						System.out.println(resultBuy); // <sold> or <unavailable> message
					}
				}

				// "Buyall clientID"
				if(cmdline.toUpperCase().startsWith("BUYALL")) {
					String[] splitInput = cmdline.split(" ");
					
					if (splitInput.length != 2) {
						System.out.println("Error: check if you introduced all (2) the parameters right!");
						break;
					} else {
						clientID = Integer.parseInt(splitInput[1]);
					}

					if (clientID == 0) {
						System.out.println("Error: check if you introduced the right clientID!");
						break;
					} else {
						List<String> resultBuyall = CSIstub.buyAll(clientID);

						// System.out.println(); // <cart> message, listing all the product bought by the client
					}
				}

				// "Cancel clientID"
				if(cmdline.toUpperCase().startsWith("CANCEL")) {
					String[] splitInput = cmdline.split(" ");
					
					if (splitInput.length != 2) {
						System.out.println("Error: check if you introduced all (2) the parameters right!");
						break;
					} else {
						clientID = Integer.parseInt(splitInput[1]);
					}

					if (clientID == 0) {
						System.out.println("Error: check if you introduced the right clientID!");
						break;
					} else {
						//List<String> resultCancel = CSIstub.cancel(clientID);

						// System.out.println(); // <cancelled> message, listing the freed products
					}
				}
			}

			//System.out.println("Remote method invoked.");
		}
		catch (Exception e){
			System.err.println("Client exception: " + e.toString());
			e.printStackTrace();
		}
	}

	private static boolean tryParseInt(String n) {
		try {
			Integer.parseInt(n);
			return true;
		} catch(NumberFormatException e) {
			return false;
		}
	}
}

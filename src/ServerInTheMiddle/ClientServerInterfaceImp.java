package ServerInTheMiddle;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import ManInTheMiddleClient.ClientServerInterface;
import DatabaseServer.DatabaseImpl;
import DatabaseServer.Product;
import DatabaseServer.Reservation;
import DatabaseServer.Interfaces.*;

public class ClientServerInterfaceImp extends UnicastRemoteObject implements ClientServerInterface {

	// idcliente,
	HashMap<Integer, List<Reservation>> mapReservas;
	// storeID
	HashMap<Integer, List<Product>> mapLojas;

	protected ClientServerInterfaceImp() throws RemoteException {
		super();
		mapReservas = new HashMap<Integer, List<Reservation>>();
		// map.put(key, value)

	}

	public String getReservation(int storeID, int productID, int quantitym, int clientID) throws RemoteException {

		IDataBase conectionBD;
		StringBuilder message = new StringBuilder();
		try {
			conectionBD = new DatabaseImpl();

		List<Product> listaProd = conectionBD.getShopProducts(storeID);

		//verifica se esse produto existe
		for (Product p : listaProd) {
			if (productID == p.getProductID()) {
				// falta ver se ja existe uma reserva DESTE CLIENTE
					Reservation reserve =conectionBD.findClientReservation(clientID,storeID,productID);		
				if(reserve!=null) 
				{
					reserve.setQuantity(reserve.getQuantity() + quantitym);
					reserve.resetTimer();//um metodo que faz reset do timer
					
					conectionBD.updateReservation(reserve);	
					
				}
				else 
				{
					//a reserva nao exite sera que exite stock disponivel
					if (p.getAvailable() >= quantitym) {
						reserve = new Reservation(clientID, storeID, productID, quantitym);
						
						conectionBD.addReservation(storeID, productID, quantitym, clientID);
						 
						//mudar pois o return vai ser um boolean											NAO PERCEBI ESTE BOOLEAN
						int remainingQty = conectionBD.productUpdateReservation(storeID, productID, quantitym, true);
						//falta so vericar se os metodos retornam true
						
						
						
						conectionBD.addReservation(reserve);
						
						return message.append("Nova reserva efetuada com sucesso " + p.getAvailable()
						+ "para o produto " + productID).toString();
						
					} else {
						//nao exite uma reserva nem stock disponivel
						return message.append("Isto não é a Amazon só temos de disponivel " + p.getAvailable()
						+ "para o produto " + productID).toString();
					}
					
				}
				

			}
		}
		
		return message.append("Bro esse produto não existe!!").toString();
		
		} catch (Exception e) {
			System.err.println("Erro na conexão entre o ServerInTheMiddle e a BD");
			e.printStackTrace();
		}
		return message.append("Server is Down!!!").toString();
	}
	public String cancel(int clientID) 
	{
		IDataBase conectionBD;
		StringBuilder message = new StringBuilder();
		
		
		try {
			conectionBD = new DatabaseImpl();
			
			//obtem todas as reservas
			List<Reservation> listreserves= conectionBD.getClientReservations(clientID);
			
			for(Reservation r :listreserves) 
			{
				//ahhhhhh ja percebi false ele diminui
				conectionBD.productUpdateReservation(r.getShopID(), r.getProductID(),r.getQuantity(), false);
			}
				
			boolean res= conectionBD.cancelAllReservations(clientID) ;
			if(res)
				return message.append("Todas as reservas foram deletadas com sucesso !!!").toString();
			else
				return message.append("Erro ao deletar todas as reservas").toString();
			
			
			
			
			
			
			
		} catch (Exception e) {
			System.err.println("Erro na conexão entre o ServerInTheMiddle e a BD");
			e.printStackTrace();
		}
		return message.append("Server is Down!!!").toString();
		
	}

	public List<String> getList(int storeID) throws RemoteException {
		// get the products list of the store containing the storeID written by the
		// client
		HashMap<Integer, ArrayList<String>> listing = new HashMap<Integer, ArrayList<String>>();
		ArrayList<String> productInfo = new ArrayList<String>();
		String productID[] = { "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R",
				"S", "T" };

		for (int i = 0; i < 20; i++) {
			Product productExmp = new Product(productID[i], 10, 0);
			productInfo.add(productExmp.toString());
		}

		listing.put(storeID, productInfo);
		return listing.get(storeID);
	}

}

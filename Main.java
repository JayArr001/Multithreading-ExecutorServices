import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/*
 * This application is made to demonstrate proficiency and ability to deploy a multi-threaded solution.
 * Specifically, this is a producer-consumer relationship.
 * This solution uses ExecutorServices to automatically manage the threadpool.
 *
 * The intent is to simulate a relationship between a "producer", which might be a storefront that produces orders,
 * and a "consumer", the warehouse; which will "take in" the orders (consume) to fulfill them.
 *
 * Producer code generates orders and sends them to the consumer in first-in-first-out order.
 * Again, this is deployed in asynchronous fashion. The producer and consumer are working in non-linear time.
 * While the performance gain is negative or negligible given the small amount of data in this set,
 * given hundreds of thousands to millions of operations, there would be a significant gain in performance.
* */

//pojo to handle order details
record Order(int orderID, String orderType, int orderQuantity)
{
	public Order(Order o)
	{
		this(o.orderID, o.orderType, o.orderQuantity);
	}
}

class Warehouse
{
	//for example-sake there are only 3 types of shoes to pick from
	public static final List<String> productList = new ArrayList<>(List.of("hiking", "sneakers", "running"));

	private volatile static List<Order> orderList;

	//arbitrary number to simulate the warehouse being at capacity
	//ie can't take any more orders until some are filled
	private static final int CAPACITY = 3;

	private ExecutorService multiExecutor; //this is the consumer's ExecutorService
	private static int ordersFilled = 0;

	public Warehouse()
	{
		//this must be a linked list, or some collection that helps track insertion order
		//since a requirement is FIFO
		orderList = new LinkedList<Order>();

		//this could be a cachedpool instead for better performance
		//however, the decision was made to only use a single thread in combination with sleep() calls
		//it can be clearly seen that the producer and consumer are working in parallel and independently
		multiExecutor = Executors.newSingleThreadExecutor();

		//this object will be used by the consumer
	}

	//called by producer X times, where X = 15 for this example
	public void receiveOrder(Order newOrder)
	{
		//while the warehouse is at capacity for orders
		while(orderList.size() >= CAPACITY)
		{
			System.out.println("warehouse is at capacity, waiting");
			try
			{
				//simulation of waiting on data transfer, calculation or something else
				Thread.sleep(450);
			}
			catch (InterruptedException e)
			{
				e.printStackTrace();
			}
		}

		if(orderList.size() < CAPACITY)
		{
			orderList.add(newOrder);
			System.out.println("Producer added " + newOrder + " - size: " + orderList.size());
		}
		else
		{
			System.out.println("orderList is full");
		}
		fulfillOrder();
	}

	public void fulfillOrder() //called by consumer
	{
		while(orderList.size() < 1) //list is empty and we are waiting for new orders
		{
			System.out.println("consumer sees no orders, waiting");
			try
			{
				//more simulation on waiting for something
				Thread.sleep(100);
			}
			catch (InterruptedException e)
			{
				e.printStackTrace();
			}
		}
		multiExecutor.execute(fillOrder);
	}

	//the runnable eventually called by the consumer, managed automatically by ExecutorService thread
	//we must go in FIFO order
	private Runnable fillOrder = () ->
	{
		//first check if the list isn't empty
		if(orderList.size() > 0)
		{
			Order o = new Order(orderList.remove(0)); //pop the first element
			int sleepTime = 100 + (o.orderQuantity() * 20); //simulating some time spent calculating or loading
			System.out.println("consumer starting orderID " + o.orderID() + ", expecting " + sleepTime + "ms");
			try
			{
				Thread.sleep(sleepTime); //sleep the thread for the randomly decided time
			}
			catch (InterruptedException e)
			{
				throw new RuntimeException(e);
			}
			ordersFilled++;
			System.out.println("consumer fulfilled orderID " + o.orderID() + " ordersFilled: " + ordersFilled);
		}
		else
		{
			System.out.println("orderList is empty");
		}

		//when the consumer finishes, shut down the thread since we won't need it anymore
		if(ordersFilled >= Main.orderCount)
		{
			System.out.println("consumer is finished, shutting down");
			multiExecutor.shutdown();
		}
	};
}

public class Main
{
	private static int orderID = 0; //static order tracker
	public static final int orderCount = 15; //number of orders to complete, chosen arbitrarily
	public static void main(String[] args)
	{
		Warehouse shoehouse = new Warehouse();

		List<Order> orderGens = Stream.generate(Main::generateRandomOrder)
				.limit(orderCount)
				.toList();

		//runnable that will attempt to add orders to the consumer, but will need to wait if it's at capacity
		//the sleep delays added are such that there will be some waiting every time
		Runnable producerRunnable = () ->
		{
			for(Order o : orderGens)
			{
				shoehouse.receiveOrder(o);
			}
		};

		//using a single thread for the producer for the same reason explained for the consumer
		//a single thread with sleep() calls clearly shows work being done in parallel and independently
		//for larger scales, cachedThreadPool would probably be preferable
		var multiExecutor = Executors.newSingleThreadExecutor();

		multiExecutor.execute(producerRunnable);
		multiExecutor.shutdown();

	}

	//helper method to easily generate orders
	//will pick a random shoe type and quantity between 0 and 100, not including 0
	public static Order generateRandomOrder()
	{
		orderID++;
		Random random = new Random();
		int randomShoeType = random.nextInt(0, Warehouse.productList.size());
		String randomShoe = Warehouse.productList.get(randomShoeType);
		int newQty = random.nextInt(0, 100);
		return new Order(orderID, randomShoe, newQty);
	}
}

package test;

public class MultiThreadedTest {
	public void testcase () {
		FinTrans ft = new FinTrans ();
		TransThread tt1 = new TransThread (ft, "Deposit Thread");
		TransThread tt2 = new TransThread (ft, "Withdrawal Thread");
		tt1.start ();
		tt2.start ();
	}
}

class FinTrans {
	public static String transName;
	public static double amount;
}

class TransThread extends Thread {
	private FinTrans ft;
	TransThread (FinTrans ft, String name)
	{
		super (name); // Save thread's name
		this.ft = ft; // Save reference to financial transaction object
	}
	public void run () {	
	    if (getName ().equals ("Deposit Thread"))
	    {
	        // Start of deposit thread's critical code section
	        ft.transName = "Deposit";
	        ft.amount = 2000.0;
	        System.out.println (ft.transName + " " + ft.amount);
	        // End of deposit thread's critical code section
	    }
	    else
	    {
	        // Start of withdrawal thread's critical code section
	        ft.transName = "Withdrawal";
	        ft.amount = 250.0;
	        System.out.println (ft.transName + " " + ft.amount);
	        // End of withdrawal thread's critical code section
	    }
	}
}
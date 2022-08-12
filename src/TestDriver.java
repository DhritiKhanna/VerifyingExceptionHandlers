public class TestDriver {
	public static void main(String[] args) {
		 try {
			 test.LOGJTest e = new test.LOGJTest();
			 e.testcase1();
		 } catch (RuntimeException e1) {
			 e1.printStackTrace();
		 }
		 catch (Exception e1) {
			 e1.printStackTrace();
		 }
	 }
}
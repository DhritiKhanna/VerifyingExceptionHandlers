package test;

import org.apache.commons.pool.PoolableObjectFactory;
/*
 * Copyright (C) 2014, United States Government, as represented by the
 * Administrator of the National Aeronautics and Space Administration.
 * All rights reserved.
 *
 * Symbolic Pathfinder (jpf-symbc) is licensed under the Apache License, 
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 *        http://www.apache.org/licenses/LICENSE-2.0. 
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 */

import java.util.LinkedList;

class Point {
	int p;
}

class Example {
	int a;
	String b;
	Point point;
	Example() {
		
	}
	Example(int a) {
		this.a = a;
	}
}

public class Test {
	private static int z;
	LinkedList<Example> allocationQueue;

	Test() {
		allocationQueue = new LinkedList<Example>();
	}

	public void foo() throws Exception {
		PoolableObjectFactory p = new PoolableObjectFactory() {
			
			@Override
			public boolean validateObject(Object arg0) {
				// TODO Auto-generated method stub
				return false;
			}
			
			@Override
			public void passivateObject(Object arg0) throws Exception {
				// TODO Auto-generated method stub
				
			}
			
			@Override
			public Object makeObject() throws Exception {
				// TODO Auto-generated method stub
				return null;
			}
			
			@Override
			public void destroyObject(Object arg0) throws Exception {
				// TODO Auto-generated method stub
				
			}
			
			@Override
			public void activateObject(Object arg0) throws Exception {
				// TODO Auto-generated method stub
				
			}
		};
		p.makeObject();
		throw new ArithmeticException();
	}
	
	public int bar(int param) {
		try {
			int [] a = new int[param];
			a[2] = param;
			wait();
			try {
				foo();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return 10 / param;
		}
		catch(InterruptedException i) {
			return -1;
		}
		catch(ArrayIndexOutOfBoundsException a) {
			return -1;
		}
		catch(ArithmeticException n) {
			return -1;
		}
	}

	public void test(int x, int y) throws InterruptedException {
		z = x-y;
		int w;
		w = x+10;
		allocationQueue = new LinkedList<Example>();
		Example example = new Example();
		Example ex = new Example();
		example = ex;
		example.point = new Point();
		ex.point = example.point;
		Point p = example.point;
		System.out.println(ex);
		allocationQueue.add(example);
		
		if (z > 1) {
	        if ((2*(z-y)) > 0) {
	        	int sdf = x-y;
	        } else {
	        	synchronized(this) {
	        		throw new InterruptedException();
	        		//wait();
	        	}
	        }
		}
		else {
			synchronized(this) {
	        	notify();
	        	assert false: "point to be hit";
	        }
		}
		/*int i = 0;
		while(i<x) {
			i++;
			allocationQueue.add(example); // The Example object created above may escape out of the method by being added into allocationQueue
		}
		*/
	}
}
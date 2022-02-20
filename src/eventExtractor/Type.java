package eventExtractor;

import gov.nasa.jpf.vm.ElementInfo;
import gov.nasa.jpf.vm.Types;
import gov.nasa.jpf.vm.VM;

public enum Type {

	INT(1),
	REAL(2),
	STRING(3),
	BOOLEAN(4),
	SHORT(5),
	BYTE(6),
	LONG(7),
	CHAR(8),
	FLOAT(9),
	REF(10),
	REFERENCE(0),
	SYMINT(-1),
	SYMREAL(-2),
	SYMSTRING(-3),
	SYMREF(-4);
	
	private int code;
	
	Type(int c){
		code = c;
	}
	
	int getCode(){
		return code;
	}
	
	public static Type typeToInteger(String type){
		if ((type.compareTo("java.lang.Integer") == 0) || (type.compareTo("int") == 0) || (type.equals("int[]"))){
			return Type.INT;
		}else if ((type.compareTo("java.lang.Boolean") == 0) || (type.compareTo("boolean") == 0)){
			return Type.BOOLEAN;
		}else if ((type.compareTo("java.lang.Short") == 0) || (type.compareTo("short") == 0) || (type.equals("short[]"))){
			return Type.SHORT;
		}else if ((type.compareTo("java.lang.Byte") == 0) || (type.compareTo("byte") == 0) || (type.equals("byte[]"))){
			return Type.BYTE;
		}else if ((type.compareTo("java.lang.Long") == 0) || (type.compareTo("long") == 0) || (type.equals("long[]"))){
			return Type.LONG;
		}else if ((type.compareTo("java.lang.Character") == 0) || (type.compareTo("char") == 0) || (type.equals("char[]"))){
			return Type.CHAR;
		}else if ((type.compareTo("java.lang.Double") == 0) || (type.compareTo("double") == 0) || (type.equals("double[]"))){
			return Type.REAL;
		}else if ((type.compareTo("java.lang.Float") == 0) || (type.compareTo("float") == 0) || (type.equals("float[]"))){
			return Type.FLOAT;
		}else if ((type.compareTo("java.lang.String") == 0)){
			return Type.STRING;
		}else{
			return Type.REFERENCE;
		}
	}
	
	
public static Object transformValueFromLong(VM vm, long value, String type){
		
		if (type.compareTo("int") == 0){
			return (int)value;
		}else if (type.compareTo("java.lang.Integer") == 0){
			ElementInfo obj = vm.getElementInfo((int)value);
			return (Integer) obj.getFieldValueObject("value");
		
		}else if (type.compareTo("boolean") == 0){
			// 0 : false
			// 1 : true
			return  (int)value;
		
		}else if (type.compareTo("java.lang.Boolean") == 0){
			ElementInfo obj = vm.getElementInfo((int)value);
			return Types.booleanToInt((Boolean) obj.getFieldValueObject("value"));
		
		}else if (type.compareTo("short") == 0){
			return (int) value;
		
		}else if((type.compareTo("java.lang.Short") == 0)){
			ElementInfo obj = vm.getElementInfo((int)value);
			return (Integer) obj.getFieldValueObject("value");
		
		}else if (type.compareTo("byte") == 0){
			return (int) value;
		
		}else if(type.compareTo("java.lang.Byte") == 0){
			ElementInfo obj = vm.getElementInfo((int)value);
			return (Integer) obj.getFieldValueObject("value");
		
		}else if (type.compareTo("long")==0){
			if (value>Long.MAX_VALUE){
				System.out.println("ERROR: Integer value out of range (MAX)");
				return null;
			}else if (value<Long.MIN_VALUE){
				System.out.println("ERROR: Integer value out of range (MIN)");
				return null;
			}
			return (long) value;
		
		}else if (type.compareTo("java.lang.Long") == 0){
			ElementInfo obj = vm.getElementInfo((int)value);
			int v = (Integer) obj.getFieldValueObject("value");

			if (v>Integer.MAX_VALUE){
				System.out.println("ERROR: Integer value out of range (MAX)");
				return null;
			}else if (v<Integer.MIN_VALUE){
				System.out.println("ERROR: Integer value out of range (MIN)");
				return null;
			}
			return v;
		
		}else if (type.compareTo("char") == 0){
			return (int) value;
		
		}else if(type.compareTo("java.lang.Character") == 0){
			ElementInfo obj = vm.getElementInfo((int)value);
			return Character.getNumericValue((Character) obj.getFieldValueObject("value"));
		
		}else if  (type.compareTo("double") == 0){
			return Types.longToDouble(value);
		
		}else if (type.compareTo("java.lang.Double") == 0){
			ElementInfo obj = vm.getElementInfo((int)value);
			return (Double) obj.getFieldValueObject("value");
		
		}else if (type.compareTo("float") == 0){
			return (Double) ((Float)Types.intToFloat((int)value)).doubleValue();
		
		}else if(type.compareTo("java.lang.Float") == 0){
			ElementInfo obj = vm.getElementInfo((int)value);
			return (Double) obj.getFieldValueObject("value");
		
		}else if ((type.compareTo("java.lang.String") == 0)){
			ElementInfo obj = vm.getElementInfo((int)value);
			String v = obj.asString();
			//if(v.isEmpty()) // DK: I don't know why isEmpty() is not working
			if(v.length() == 0)
				v = "0";
			else if(v.equals("\n")){
				System.out.println(" (Utilities) string is '\\n', so store its numerical value instead.");
				v = String.valueOf(value);
			}
			System.out.println(" (Utilities) WARNING: String: "+v);
			return v;
		
		}else{
			if ((Long)value>Integer.MAX_VALUE){
				System.out.println("ERROR: Integer value out of range (MAX). When creating a reference");
				return null;
			}else if ((Long)value<Integer.MIN_VALUE){
				System.out.println("ERROR: Integer value out of range (MIN). When creating a reference");
				return null;
			}
			System.out.println("WARNING: The value written is not a basic reference. I do not know what to do with it.");
			
			return (int)value;
		}
	}
}
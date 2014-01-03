package ch.ethz.twimight.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;

public class Serialization {
	/**
	 * Serializes the given object.
	 * 
	 * @param object
	 *            the object to serialize
	 * @return a byte array containing the serialized input or null if something
	 *         goes wrong
	 */
	public static byte[] serialize(Object object) {
		byte[] serializedObject = null;
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		ObjectOutput out = null;
		try {
			out = new ObjectOutputStream(bos);
			out.writeObject(object);
			serializedObject = bos.toByteArray();
		} catch (IOException e) {
			// IOExceptions, IOExceptions..
		} finally {
			try {
				if (out != null) {
					out.close();
				}
			} catch (IOException e) {
				// .. whatcha gonna do?
			}
			try {
				bos.close();
			} catch (IOException e) {
				// ...watcha gonna do when they come for you?
			}
		}
		return serializedObject;
	}

	public static <T> T deserialize(byte[] bytes) {
		ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
		ObjectInput in = null;
		T output = null;
		try {
			in = new ObjectInputStream(bis);
			output = (T) in.readObject();
		} catch (ClassNotFoundException e) {
			// You chuck it on that one...
		} catch (IOException e) {
			// .. you chuck it on this one...
		} finally {
			try {
				bis.close();
			} catch (IOException e) {
				// ...you chuck it on that one...
			}
			try {
				if (in != null) {
					in.close();
				}
			} catch (IOException e) {
				// ...and you chuck it on me
			}
		}
		return output;
	}
}

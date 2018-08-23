package com.lioryamin.tcp.server;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TCPServer {
	/**
	 * Application method to run the server runs in an infinite loop listening
	 * on port 9898. When a connection is requested, it spawns a new thread to
	 * do the servicing and immediately returns to listening.
	 */
	public static void main(String[] args) throws Exception {
		System.out.println("Api server is up and running.");
		int clientNumber = 0;
		ServerSocket listener = new ServerSocket(9898);
		try {
			while (true) {
				new TcpServer(listener.accept(), clientNumber++).start();
			}
		} finally {
			listener.close();
		}
	}

	private static class TcpServer extends Thread {
		private Socket socket;
		private int clientNumber;
		private Properties disk = new Properties();
		private Map<String, LinkedList<String>> cache = new LinkedHashMap<String, LinkedList<String>>(10);

		public TcpServer(Socket socket, int clientNumber) {
			this.socket = socket;
			this.clientNumber = clientNumber;
			Map<String, LinkedList<String>> savedData = loadFromDisk();
			if (savedData == null) {
				saveToDisk(new LinkedHashMap<String, LinkedList<String>>());
			}

			log("New connection with client# " + clientNumber + " at " + socket);
		}

	
		public void run() {
			try {

				BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

				// Send a welcome message to the client.
				out.println("Hello, you are client #" + clientNumber + " , using this api.");
				out.println("Enter a line with only a period to quit or use the api\n");

				while (true) {
					String input = in.readLine();
					if (input == null || input.equals(".")) {
						break;
					}

					out.println(this.invokeMethod(input));
				}
			} catch (Exception e) {
				log("Error handling client# " + clientNumber + ": " + e);
			} finally {
				try {
					socket.close();
				} catch (IOException e) {
					log("Couldn't close a socket, what's going on?");
				}
				log("Connection with client# " + clientNumber + " closed");
			}
		}

		private String invokeMethod(String method) {
			String methodName = null;
			String param1 = null;
			String param2 = null;
			Object o = null;
			boolean isGetMethod = false;

			try {
				methodName = method.substring(0, method.indexOf("("));

				if (!(method.indexOf(",") > -1)) { // get methods
					param1 = method.substring(method.indexOf("(") + 1, method.length() - 1);
					isGetMethod = true;
				} else {
					param1 = method.substring(method.indexOf("(") + 1, method.indexOf(","));
					param2 = method.substring(method.indexOf(",") + 1, method.length() - 1);
				}

				Method m = null;

				Class c = this.getClass();

				if (isGetMethod) {
					m = c.getDeclaredMethod(methodName, String.class);
					o = m.invoke(this, param1);
				} else {
					m = c.getDeclaredMethod(methodName, new Class[] { String.class, String.class });
					o = m.invoke(this, param1, param2);
				}

			} catch (Exception e) {
				return "General Error!";

			}
			return o.toString();
		}

		public String getAllKeys(String pattern) {// – Returns all keys matching
													// pattern.
			Map<String, LinkedList<String>> allData = loadFromDisk();
			StringBuilder sb = new StringBuilder();

			Pattern myPattern = Pattern.compile(pattern);

			Matcher matcher = null;
			for (Map.Entry<String, LinkedList<String>> entry : allData.entrySet()) {
				matcher = myPattern.matcher(entry.getKey());
				if (matcher.matches()) {
					sb.append(entry.getKey()).append(",");
				}

			}
			if (sb.length() > 0) {
				sb.deleteCharAt(sb.length() - 1); 
			}
			return sb.length() > 0 ? sb.toString() : "Nothing Found";
		}

		public String rightAdd(String key, String value) {// – adds a value to// key K, from the// right
			LinkedList<String> values = null;
			Map<String, LinkedList<String>> allData = null;
			allData = loadFromDisk();
			if (cache.get(key) != null) { // if in memory then getValues its
											// faster
				values = cache.get(key);
				values.addLast(value);
			} else {
				if (allData.get(key) != null) {
					try {
						values = allData.get(key);
					} catch (Exception e) {
						e.printStackTrace();
					}
					values.addLast(value);
					allData.put(key, values);
				} else {
					return "No Such Key!";
				}
			}
			removeKeyFromCacheAndSaveNewValues(key, values, allData);

			return "value was right added to key";

		}

		private void removeKeyFromCacheAndSaveNewValues(String key, LinkedList<String> values,
				Map<String, LinkedList<String>> allData) {
			// clear old item from cache
			cache.remove(key);
			// add new values to key cache
			cache.put(key, values);
			// save to disk
			saveToDisk(allData);
		}

		public String leftAdd(String key, String value) {// – adds a value to key K, from the left
			LinkedList<String> values = null;
			Map<String, LinkedList<String>> allData;
			allData = loadFromDisk();
			if (cache.get(key) != null) { // if in memory then getValues its faster
				values = (LinkedList<String>) cache.get(key);
				values.addFirst(value);
			} else {
				if (allData.get(key) != null) {
					values = (LinkedList<String>) allData.get(key);
					values.addFirst(value);
					allData.put(key, values);
				} else {
					return "No Such Key!";
				}
			}
			removeKeyFromCacheAndSaveNewValues(key, values, allData);
			return "value was right added to key";
		}

		public String set(String key, String values) {
			LinkedList<String> val = new LinkedList<String>();
			for (String s : values.split(",")){
				val.add(s);
			}
			return set(key, val);
		}

		public String set(String key, LinkedList<String> values) {
			Map<String, LinkedList<String>> allData = null;
			if (cache.get(key) != null) { // if in memory then remove
				cache.remove(key);
			}
			allData = loadFromDisk();
			allData.put(key, values); // save to disk
			cache.put(key, values); // save to cache
			saveToDisk(allData);
			return "Done Adding Key: " + key;
		}

		public String get(String key) {
			Map<String, LinkedList<String>> allData = null;
			StringBuilder sb = new StringBuilder();
			List<String> result = null;
			if (cache.get(key) != null) { // check if key in cache
				result = cache.get(key);
			} else {
				allData = loadFromDisk();
				result = allData.get(key);
			}
			if (result == null){
				return "no such key";
			}
			for (String s : result) {
				sb.append(s).append(",");
			}
			
			if (sb.length() > 0) {
				sb.deleteCharAt(sb.length() - 1); 
			}
			return sb.toString();
		}

		private void saveToDisk(Map<String, LinkedList<String>> dataToSave) {
			try {
				FileOutputStream fileOut = new FileOutputStream("data.txt");
				ObjectOutputStream out = new ObjectOutputStream(fileOut);
				out.writeObject(dataToSave);
				out.close();
				fileOut.close();
				System.out.printf("Serialized data is saved in data.txt");
			} catch (IOException i) {
				i.printStackTrace();
			}

		}

		private Map<String, LinkedList<String>> loadFromDisk() {
			Map<String, LinkedList<String>> result = null;
			try {
				FileInputStream fileIn = new FileInputStream("data.txt");
				ObjectInputStream in = new ObjectInputStream(fileIn);
				result = (Map<String, LinkedList<String>>) in.readObject();
				in.close();
				fileIn.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
			return result;
		}

		/**
		 * Logs a simple message. In this case we just write the message to the
		 * server applications standard output.
		 */
		private void log(String message) {
			System.out.println(message);
		}

	}
}
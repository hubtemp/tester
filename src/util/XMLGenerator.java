package util;

import javax.xml.datatype.DatatypeFactory;
import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.LinkedHashMap;
import java.util.UUID;

import static util.Utilities.logError;
import static util.Utilities.logInfo;

public class XMLGenerator {

	private final static boolean CHANGE_AMOUNT = true;
	private final static String CSV_DELIMITER = ";";

	public static void generatePaymentFiles(File directory) {
		int fileCount = 0;
		File[] files = directory.listFiles();
		if (files != null && files.length > 0) {
			for (File file : files) {
				try {
					if (!file.isFile() || !file.getName().toLowerCase().endsWith(".csv")) {
						continue;
					}
					ArrayList<LinkedHashMap<String, String>> rows = loadFileData(file);
					Xml xml = createXMLObject(rows.get(0));
					for (LinkedHashMap<String, String> row : rows) {
						addXMLPaymentBlock(xml, row);
					}
					Files.write(file.toPath().resolveSibling(file.getName().replace(".csv", ".xml")), xml.getBytes());
					logInfo(file.getName() + " payment count = " + rows.size());
					fileCount++;
				} catch (Exception e) {
					logError("Failed to generate XML from file %s! %s", file.getName(), e.getMessage());
					e.printStackTrace();
				}
			}
		}
		logInfo("%d XML files generated", fileCount);
	}

	private static ArrayList<LinkedHashMap<String, String>> loadFileData(File file) throws Exception {
		ArrayList<LinkedHashMap<String, String>> rows = new ArrayList<>();
		try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
			String line = br.readLine();
			String[] columnNames = line.split(CSV_DELIMITER, -1);
			while ((line = br.readLine()) != null) {
				if (!line.trim().isEmpty()) {
					String[] rowData = line.split(CSV_DELIMITER, -1);
					LinkedHashMap<String, String> row = new LinkedHashMap<>();
					for (int i = 0; i < columnNames.length; i++) {
						row.put(columnNames[i], rowData[i]);
					}
					rows.add(row);
				}
			}
		}
		return rows;
	}

}

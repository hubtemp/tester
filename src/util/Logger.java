package util;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static util.Utilities.logError;

public class Logger extends Thread {

	private final BlockingQueue<String> queue = new ArrayBlockingQueue<>(100);
	private static final String FILE_EXTENSION = "csv";
	private volatile boolean active = true;
	private Thread runningThread;
	private final String fileName;
	private final Path filePath;

	public Logger(Path filePath, String[] columns) {
		fileName = filePath.getFileName().toString() + "-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")) + "." + FILE_EXTENSION;
		this.filePath = filePath.resolveSibling(fileName);
		addData(columns);
	}

	@Override public void run() {
		runningThread = Thread.currentThread();
		try (FileWriter fw = new FileWriter(filePath.toFile())) {
			while (active) {
				try {
					String data = queue.take();
					if (data != null) {
						fw.write(data + System.lineSeparator());
						fw.flush();
					}
				} catch (InterruptedException ignore) {
				}
			}
		} catch (IOException e) {
			logError("Logger failed (filename=" + fileName + ")! " + e.getMessage());
			e.printStackTrace();
		}
	}


	public boolean addData(String data) {
		boolean ret = false;
		try {
			ret = queue.offer(data, 3, TimeUnit.SECONDS);
		} catch (Exception ignore) {
		}
		return ret;
	}

	public boolean addData(String... data) {
		return addData(String.join(";", data));
	}

	public void close() {
		active = false;
		runningThread.interrupt();
	}

}

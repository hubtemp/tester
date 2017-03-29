package tests;

import util.*;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static util.Utilities.*;

public class PaymentTest implements Runnable {

	public static final String RESULT_LOG_FILENAME = "test-results";
	public static final String[] RESULT_LOG_COLUMNS = new String[] {
			"Payment XML file name",
			"Payment procedure name",
			"Start date and time",
			"Finish date and time",
			"Execution time (ms)",
			"Portion/call count",
			"Portion size",
			"Payments per second",
			"Overall payments",
			"Successful payments",
			"Failed payments",
			"Pending payments" };
	public static final String FAILED_PAYMENTS_LOG_FILENAME = "failed-payments";
	public static final String[] FAILED_PAYMENTS_LOG_COLUMNS = new String[] {
			"Payment XML file name",
			"Payment docId",
			"Error code" };

	private final String contractId;

	public static final int DEFAULT_PORTION_SIZE = 50;
	private int portionSize;

	private final Path filePath;
	private final String fileName;
	private final String procedureName;

	public static Logger resultLog;
	public static Logger failedPaymentLog;

	public PaymentTest(String file, String procedureName, String portionSize) {
		this.procedureName = procedureName;
		filePath = Paths.get(file);
		fileName = filePath.getFileName().toString();
		try {
			this.portionSize = Integer.parseInt(portionSize);
		} catch (Exception ignore) {
		}
		this.portionSize = this.portionSize > 0 ? this.portionSize : DEFAULT_PORTION_SIZE;
		contractId = Services.registerClientContract();
	}

	@Override public void run() {
		if (isEmpty(contractId)) {
			logError("Test %s can't be executed - no client contract id!", fileName);
			return;
		}
		String[] resultData = new String[RESULT_LOG_COLUMNS.length];
		Arrays.fill(resultData, "0");
		resultData[0] = filePath.getFileName().toString();
		resultData[1] = procedureName;
		resultData[2] = "=\"" + getCurrentDateTime() + "\""; // to satisfy Excel
		logInfo("Test " + fileName + " started at " + getCurrentDateTime());
		int successCount = 0;
		int failedCount = 0;
		int pendingCount = 0;
		try (Connection con = new Connection(true)) {
			long start = System.nanoTime();
			ArrayList<SepaPayment> payments = loadPaymentsFromXML();
			logInfo("XML %s parsed in %sms", fileName, Utilities.getDuration(start));
			int fromIndex = 0;
			int toIndex = payments.size() < portionSize ? payments.size() : portionSize;
			int callCount = payments.size() < portionSize ? 1 : ((payments.size() / portionSize) + (payments.size() % portionSize == 0 ? 0 : 1));
			long overallDuration = 0;
			int currentCallCount = 0;
			int sentPaymentCount = 0;
			int prevCallPaymentCount = 0;
			while (currentCallCount < callCount && !Thread.interrupted()) {
				try {
					currentCallCount++;
					logInfo("Test %s portion %d/%d started. Last call duration %sms, %.1ftrx/sec, progress %d%%", fileName, currentCallCount, callCount, con.getDuration(),
							1000000000 / ((double) con.getDurationNano() / prevCallPaymentCount), (fromIndex * 100) / payments.size());
					PaymentRequest request = new PaymentRequest();
					request.tt_sepa = payments.subList(fromIndex, toIndex);
					prevCallPaymentCount = request.tt_sepa.size();
					sentPaymentCount += request.tt_sepa.size();
					con.call(request, procedureName);
					overallDuration += con.getDurationNano();
					for (PaymentStatus ps : request.paymentStatus) {
						if ("PEND".equalsIgnoreCase(ps.state)) {
							pendingCount++;
						} else if (ps.errno == 0) {
							successCount++;
						} else {
							failedCount++;
							failedPaymentLog.addData(fileName, ps.docID, String.valueOf(ps.errno));
						}
					}
				} catch (InterruptedException e) {
					logError("Test %s payment execution interrupted! %s", fileName, e.getMessage());
					break;
				} catch (Exception e) {
					logError("Test %s error while calling payment procedure! %s", fileName, e.getMessage());
					e.printStackTrace();
				} finally {
					fromIndex = toIndex;
					toIndex += portionSize;
					if (toIndex > payments.size()) {
						toIndex = payments.size();
					}
				}
			}
			resultData[3] = "=\"" + getCurrentDateTime() + "\""; // ="" to satisfy Excel
			resultData[4] = Utilities.formatDuration(overallDuration);
			resultData[5] = String.valueOf(currentCallCount);
			resultData[6] = String.valueOf(portionSize);
			resultData[7] = String.format(Utilities.LOCALE, "%.1f", 1000000000 / ((double) overallDuration / sentPaymentCount));
			resultData[8] = String.valueOf(sentPaymentCount);
			resultData[9] = String.valueOf(successCount);
			resultData[10] = String.valueOf(failedCount);
			resultData[11] = String.valueOf(pendingCount);
		} catch (Exception e) {
			logError("Error while performing payment test %s! %s", fileName, e.getMessage());
			e.printStackTrace();
		}
		Services.closeClientContract(contractId);
		resultLog.addData(resultData);
		logInfo("Test %s finished at %s. Payment status: OK=%d, PENDING=%d, FAILED=%d", fileName, getCurrentDateTime(), successCount, pendingCount, failedCount);
	}
}

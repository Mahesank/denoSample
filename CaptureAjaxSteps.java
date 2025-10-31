import io.cucumber.java.en.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.v112.network.Network;
import org.openqa.selenium.devtools.v112.network.model.Request;
import org.openqa.selenium.devtools.v112.network.model.Response;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.*;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class CaptureAjaxSteps {

    Workbook inputWorkbook;
    Workbook outputWorkbook;
    Sheet outputSheet;
    int rowNum = 0;

    @Given("I have a list of URLs in an Excel file")
    public void readUrlsFromExcel() throws Exception {
        FileInputStream fis = new FileInputStream("urls.xlsx");
        inputWorkbook = new XSSFWorkbook(fis);
        outputWorkbook = new XSSFWorkbook();
        outputSheet = outputWorkbook.createSheet("Results");

        // Create header row
        String[] headers = {"URL", "Action", "Product ID", "From Date", "To Date", "Nonce", "Availability_ID Present", "Spots_Open Present"};
        Row headerRow = outputSheet.createRow(rowNum++);
        for (int i = 0; i < headers.length; i++) {
            headerRow.createCell(i).setCellValue(headers[i]);
        }
    }

    @When("I load each URL and capture admin-ajax.php request and response")
    public void processUrls() throws Exception {
        for (Row row : inputWorkbook.getSheetAt(0)) {
            String url = row.getCell(0).getStringCellValue();
            System.out.println("Processing URL: " + url);

            ChromeDriver driver = new ChromeDriver();
            DevTools devTools = driver.getDevTools();
            devTools.createSession();
            devTools.send(Network.enable(Optional.empty(), Optional.empty(), Optional.empty()));

            Map<String, Map<String, String>> requestMap = new ConcurrentHashMap<>();

            // Capture request payload
            devTools.addListener(Network.requestWillBeSent(), request -> {
                Request req = request.getRequest();
                if (req.getUrl().contains("admin-ajax.php")) {
                    String payload = req.getPostData().orElse("");
                    Map<String, String> params = parsePayload(payload);
                    requestMap.put(request.getRequestId().toString(), params);
                }
            });

            // Capture response and validate
            devTools.addListener(Network.responseReceived(), response -> {
                Response res = response.getResponse();
                if (res.getUrl().contains("admin-ajax.php")) {
                    String requestId = response.getRequestId().toString();
                    Map<String, String> params = requestMap.getOrDefault(requestId, new HashMap<>());

                    String body = devTools.send(Network.getResponseBody(response.getRequestId())).getBody();
                    JsonObject jsonResponse = JsonParser.parseString(body).getAsJsonObject();

                    String availabilityIdStatus = "No";
                    String spotsOpenStatus = "No";

                    if (jsonResponse.has("availability")) {
                        JsonObject availability = jsonResponse.getAsJsonObject("availability");
                        String productId = params.getOrDefault("product_id", "");
                        if (availability.has(productId)) {
                            JsonObject productData = availability.getAsJsonObject(productId);
                            if (productData.has("availability_id") && !productData.get("availability_id").getAsString().isEmpty()) {
                                availabilityIdStatus = "Yes";
                            }
                            if (productData.has("spots_open") && !productData.get("spots_open").getAsString().isEmpty()) {
                                spotsOpenStatus = "Yes";
                            }
                        }
                    }

                    // Write to Excel
                    Row outRow = outputSheet.createRow(rowNum++);
                    outRow.createCell(0).setCellValue(url);
                    outRow.createCell(1).setCellValue(params.getOrDefault("action", ""));
                    outRow.createCell(2).setCellValue(params.getOrDefault("product_id", ""));
                    outRow.createCell(3).setCellValue(params.getOrDefault("from_date", ""));
                    outRow.createCell(4).setCellValue(params.getOrDefault("to_date", ""));
                    outRow.createCell(5).setCellValue(params.getOrDefault("nonce", ""));
                    outRow.createCell(6).setCellValue(availabilityIdStatus);
                    outRow.createCell(7).setCellValue(spotsOpenStatus);
                }
            });

            driver.get(url);
            Thread.sleep(15000); // Wait for requests
            driver.quit();
        }
    }

    @Then("I validate availability_id and spots_open and record results in Excel")
    public void writeResultsToExcel() throws Exception {
        try (FileOutputStream fos = new FileOutputStream("ajax_results.xlsx")) {
            outputWorkbook.write(fos);
        }
        inputWorkbook.close();
        outputWorkbook.close();
    }

    private Map<String, String> parsePayload(String payload) {
        Map<String, String> map = new HashMap<>();
        String[] pairs = payload.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=");
            if (keyValue.length == 2) {
                map.put(keyValue[0], keyValue[1]);
            }
        }
        return map;
    }
}
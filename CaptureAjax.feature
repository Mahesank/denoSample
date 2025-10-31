Feature: Capture admin-ajax.php payload and response for multiple URLs

  Scenario: Validate availability and spots for multiple URLs
    Given I have a list of URLs in an Excel file
    When I load each URL and capture admin-ajax.php request and response
    Then I validate availability_id and spots_open and record results in Excel
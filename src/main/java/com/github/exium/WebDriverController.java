package com.github.exium;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FileUtils;
import org.openqa.selenium.*;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.firefox.FirefoxProfile;
import org.openqa.selenium.ie.InternetExplorerDriver;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.safari.SafariDriver;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.UnexpectedTagNameException;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Execute command with Selenium web driver
 */
class WebDriverController {

    private Logger logger;
    private WebDriver driver;
    private HashMap<String, WebDriver> drivers;
    private Configurator conf;

    WebDriverController()
    {
        logger = Exium.logger;
        drivers = new HashMap<>();
        conf = Exium.conf;
    }

    /**
     * Execute "Open" command
     * @param listParameter parameter list with its values.
     * @param strCaseNum string value of case number.
     * @return result of this command (if true, execute success("OK"). if false, execute error("NG").)
     */
    boolean executeOpen(ArrayList<String[]> listParameter, String strCaseNum) {

        // get parameter value of "browser"
        String browser = getParameterValue(listParameter, strCaseNum, "browser");
        // get parameter value of "url"
        String url = getParameterValue(listParameter, strCaseNum, "url");
        if (url.equals("")) {
            url = conf.getStringProperty("webdriver.common.default.url");
        }
        // get parameter value of "device"
        String device = getParameterValue(listParameter, strCaseNum, "device");
        if (!device.equals("")) {
            if (!browser.equalsIgnoreCase("")
					&& !browser.equalsIgnoreCase("Chrome")
					&& !browser.equalsIgnoreCase("FireFox")) {
                logger.log("message.warn.ignore_device", strCaseNum, "Open");
            }
        }
        // get browser properties(width, height, userAgent) from device list
        int width = 0;
        int height = 0;
        String userAgent = "";
        String deviceNameOnChrome = "";
        if (!device.equals("")) {
            // search device from Configurator
            for (int i = 0; i < 16; i++) {
                String prop = "browser.common.device." + i;
                String title = conf.getStringProperty(prop + ".title");
                if (device.equalsIgnoreCase(title)) {
                    width = conf.getIntProperty(prop + ".width");
                    height = conf.getIntProperty(prop + ".height");
                    userAgent = conf.getStringProperty(prop + ".useragent");
                    deviceNameOnChrome = conf.getStringProperty(prop + ".chrome");
                    break;
                }
            }
        }

        // Open each browser
        switch (browser.toLowerCase()) {
            case "firefox" :
            	String geckodriver ="";
                try {
					// get path of gecko driver and set to system properties
					geckodriver = conf.getStringProperty("webdriver.firefox.driver");
					if (isWindows()) {
						if (!geckodriver.toLowerCase().contains(".exe")) {
							geckodriver += ".exe";
						}
					}
					System.setProperty("webdriver.gecko.driver", geckodriver);
					FirefoxOptions options = new FirefoxOptions();
					options.setCapability("marionette", true);
					if (!device.equals("") && !userAgent.equals("")) {
						// apply userAgent
						FirefoxProfile profile = new FirefoxProfile();
						profile.setPreference("general.useragent.override", userAgent);
						options.setProfile(profile);
					}
					int retry_num = conf.getIntProperty("webdriver.common.retry");
					for (int i = 0; i < retry_num + 1; i++) {
						try {
							driver = new FirefoxDriver(options);
							break;
						} catch (SessionNotCreatedException snce) {
							// note: firefox driver can't open multiple windows so need wait for previous session closed.
							int retry_interval = conf.getIntProperty("webdriver.common.retry_interval");
							logger.log("message.warn.session_not_closed", strCaseNum, "Open", Integer.toString(i + 1) + "/" + Integer.toString(retry_num), Integer.toString(retry_interval));
							Thread.sleep(retry_interval);
						}
					}
				} catch (Exception e) {
                    logger.log("message.warn.cant_init_firefox", e, strCaseNum, "Open", geckodriver);
                    return false;
                }
                break;
            case "ie" :
                // note: don't apply userAgent even if user defined
                try {
                    driver = new InternetExplorerDriver();
                } catch (Exception e) {
                    logger.log("message.warn.cant_init_ie", e, strCaseNum, "Open");
                    return false;
                }
                break;
            case "chrome" :
                String chromedriver = "";
                try {
                    // get path of Chrome driver and set to system properties
                    chromedriver = conf.getStringProperty("webdriver.chrome.driver");
                    if (isWindows()) {
                        if (!chromedriver.toLowerCase().contains(".exe")) {
                            chromedriver += ".exe";
                        }
                    }
                    System.setProperty("webdriver.chrome.driver", chromedriver);
                    // if defined device name on Chrome, open Chrome with the device name (using mobile emulation)
                    // if don't defined device name on Chrome, open Chrome and set userAgent (if defined)
                    if (!deviceNameOnChrome.equals("")) {
                        Map<String, String> mobileEmulation = new HashMap<>();
                        mobileEmulation.put("deviceName", deviceNameOnChrome);
                        ChromeOptions options = new ChromeOptions();
                        options.setExperimentalOption("mobileEmulation", mobileEmulation);
                        driver = new ChromeDriver(options);
                    } else if (!device.equals("") && !userAgent.equals("")) {
                        ChromeOptions options = new ChromeOptions();
                        options.addArguments("--user-agent=" +  userAgent);
                        driver = new ChromeDriver(options);
                    } else {
                        driver = new ChromeDriver();
                    }
                } catch (Exception e) {
                    logger.log("message.warn.cant_init_chrome", e, strCaseNum, "Open", chromedriver);
                    return false;
                }
                break;
            case "safari" :
                // note: don't apply userAgent even if user defined
                try {
                	int retry_num = conf.getIntProperty("webdriver.common.retry");
                	for (int i = 0; i < retry_num + 1; i++) {
                		try {
							driver = new SafariDriver();
							break;
						} catch (SessionNotCreatedException snce) {
                			// note: safari driver can't open multiple windows so need wait for previous session closed.
                			int retry_interval = conf.getIntProperty("webdriver.common.retry_interval");
							logger.log("message.warn.session_not_closed", strCaseNum, "Open", Integer.toString(i + 1) + "/" + Integer.toString(retry_num), Integer.toString(retry_interval));
                			Thread.sleep(retry_interval);
						}
					}
                } catch (Exception e) {
                    logger.log("message.warn.cant_init_safari", e, strCaseNum, "Open");
                    return false;
                }
                break;
			case "":
				// in case of re-open
				break;
			default :
				logger.log("message.warn.invalid_browser", strCaseNum, "Open", browser);
				return false;
        }
        // driver check
        if (driver == null) {
            logger.log("message.warn.cant_open_browser", strCaseNum, "Open");
            return false;
        }
        // set window size
        if (width == 0) {
            width = driver.manage().window().getSize().width;
        }
        if (height == 0) {
            height = driver.manage().window().getSize().height;
        }
        driver.manage().window().setSize(new Dimension(width, height));
        // set browser timeout second
        int timeout = conf.getIntProperty("webdriver.common.timeout");
        driver.manage().timeouts().implicitlyWait(timeout, TimeUnit.MILLISECONDS);

        // open url in the browser
        try {
            driver.get(url);
        } catch (TimeoutException te) {
            logger.log("message.warn.driver_timeout", strCaseNum, "Open", url, Integer.toString(timeout));
            return false;
        }

        return true;
    }

    /**
     * Execute "SwitchBrowser" command
     * @param listParameter parameter list with its values.
     * @param strCaseNum string value of case number.
     * @return result of this command (if true, execute success("OK"). if false, execute error("NG").)
     */
    boolean executeSwitchBrowser(ArrayList<String[]> listParameter, String strCaseNum) {

        // get target case number
        String openedBy = getParameterValue(listParameter, strCaseNum, "opened_by");
        if (openedBy.equals("")) {
            logger.log("message.warn.invalid_value", strCaseNum, "SwitchBrowser", "opened_by");
            return false;
        }
        // get used driver by case number
        this.driver = drivers.get(openedBy);
        if (this.driver == null) {
            logger.log("message.warn.cant_find_browser", strCaseNum, "SwitchBrowser");
            return false;
        }

        return true;
    }

    /**
     * Execute some commands needs analyze WebElement
     * @param listParameter parameter list with its values.
     * @param strCaseNum string value of case number.
     * @param command target command.
     * @return result of this command (if true, execute success("OK"). if false, execute error("NG").)
     */
    boolean executeToElement(ArrayList<String[]> listParameter, String strCaseNum, String command) {

		// check driver
		if (this.driver == null) {
			logger.log("message.warn.cant_find_driver", strCaseNum, command);
			return false;
		}

        // get target elements
        List<WebElement> elements = getElements(listParameter, strCaseNum, command);
        if ((elements == null) || (elements.size() == 0)) {
            // error, but don't need output log (already output)
            return false;
        }

        // execute each command
        switch(command.toLowerCase()) {
            case "input":
                // arrow multiple "text" parameters
				boolean isExistInputText = false;
                for(String[] param : listParameter) {
                    if (param[0].equalsIgnoreCase("text")) {
						String textInput = (param[1] != null) ? param[1] : "";
                        try {
							elements.get(0).sendKeys(textInput);
						} catch (Exception e) {
							logger.log("message.warn.cant_input_text", strCaseNum, command, textInput);
							return false;
						}
						isExistInputText = true;
                    }
                }
                if (!isExistInputText) {
					logger.log("message.warn.lack_param", strCaseNum, command);
					return false;
				}
                break;
            case "submit":
                // arrow multiple "text" parameters
				boolean isExistSubmitText = false;
                for(String[] param : listParameter) {
                    if (param[0].equalsIgnoreCase("text")) {
                        String textSubmit = (param[1] != null) ? param[1] : "";
						try {
							elements.get(0).sendKeys(textSubmit);
						} catch (Exception e) {
							logger.log("message.warn.cant_input_text", strCaseNum, command, textSubmit);
							return false;
						}
						isExistSubmitText = true;
                    }
                }
				if (!isExistSubmitText) {
					logger.log("message.warn.lack_param", strCaseNum, command);
					return false;
				}

				try {
					elements.get(0).submit();
				} catch (Exception e) {
					logger.log("message.warn.cant_submit", e, strCaseNum, command);
					return false;
				}
                break;
            case "click":
                String typeClick = elements.get(0).getAttribute("type");
                // if the type is "submit", not using click()
				try {
					elements.get(0).submit();
				} catch (Exception ignore) {
					// Ignore exception
					elements.get(0).click();
				}
                break;
            case "select":
                try {
                    Select select = new Select(elements.get(0));
                    if (select.isMultiple()) {
                        select.deselectAll();
                    }
                    boolean isExistSelectText = false;
                    for (String[] param : listParameter) {
                        if (param[0].equalsIgnoreCase("text")) {
							String textSelect = (param[1] != null) ? param[1] : "";
                            select.selectByVisibleText(textSelect);
                            isExistSelectText = true;
                        }
                    }
					if (!isExistSelectText) {
						logger.log("message.warn.lack_param", strCaseNum, command);
						return false;
					}
                } catch (UnexpectedTagNameException utne) {
                    logger.log("message.warn.cant_conv_select", strCaseNum, command);
                    return false;
                } catch (NoSuchElementException nsee) {
                    logger.log("message.warn.cant_select_by_txt", strCaseNum, command);
                    return false;
                }
                break;
            case "mouserover":
                Actions builder = new Actions(driver);
                builder.moveToElement(elements.get(0)).build().perform();
                break;
            case "log":
                boolean isAttributeLog = false;
                for(String[] param : listParameter) {
                    if (param[0].equalsIgnoreCase("attribute")) {
                        logger.log("message.info.log.attribute", strCaseNum, command, param[1], elements.get(0).getAttribute(param[1]));
                        isAttributeLog = true;
                        break;
                    }
                }
                if (!isAttributeLog) {
                    logger.log("message.info.log.text", strCaseNum, command, elements.get(0).getText());
                }
                break;
            case "comparetext":
                // get regex string
                String regex = "";
                for(String[] param : listParameter) {
                    if (param[0].equalsIgnoreCase("regex")) {
                        regex = param[1];
                        break;
                    }
                }
				if (regex.equals("")) {
					logger.log("message.warn.lack_param", strCaseNum, command);
					return false;
				}
                regex = Pattern.quote(regex);

                // get target string to regex
                String target = "";
                boolean isAttributeCompareText = false;
                for(String[] param : listParameter) {
                    if (param[0].equalsIgnoreCase("attribute")) {
                        target = param[1];
                        isAttributeCompareText = true;
                        break;
                    }
                }
                if (!isAttributeCompareText) {
                    target = elements.get(0).getText();
                }

                // compare
                boolean isMatch;
                try {
                    isMatch = target.matches(regex);
                } catch (PatternSyntaxException pse) {
                    logger.log("message.warn.invalid_regex", strCaseNum, command, regex);
                    return false;
                }
                return isMatch;
            default:
                logger.log("message.warn.unexpected", strCaseNum, command);
                return false;
        }

        return true;
    }

    /**
     * Execute "SendKeys" command
     * @param listParameter parameter list with its values.
     * @param strCaseNum string value of case number.
     * @return result of this command (if true, execute success("OK"). if false, execute error("NG").)
     */
    boolean executeSendKeys(ArrayList<String[]> listParameter, String strCaseNum) {

		// check driver
		if (this.driver == null) {
			logger.log("message.warn.cant_find_driver", strCaseNum, "SendKeys");
			return false;
		}

		// get target elements
        List<WebElement> elements = getElements(listParameter, strCaseNum, "SendKeys");
        if ((elements == null) || (elements.size() == 0)) {
            // error, but don't need output log (already output)
            return false;
        }

        // send keys
        for(String[] param : listParameter) {
            if (param[0].equalsIgnoreCase("keys")) {
                if (param[1] == null) {
                    continue;
                }
                try {
                    StringBuilder buff = new StringBuilder();
                    CSVParser parser = CSVParser.parse(param[1], CSVFormat.EXCEL);
                    for (CSVRecord record : parser) {
                        for (SpecialKeys tag : SpecialKeys.values()) {
                            // if key is special key (ex: "<DEL>", "<ENTER>", etc...)
                            if (record.toString().equalsIgnoreCase(tag.getTag())) {
                                buff.append(tag.getKey());
                                break;
                            }
                            // if normal keys
                            buff.append(record);
                        }
                    }
                    elements.get(0).sendKeys(Keys.chord(buff));
                } catch (Exception e) {
                    logger.log("message.warn.cant_conv_keys", "strCaseNum", strCaseNum);
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Execute "BrowserOperation" command
     * @param listParameter parameter list with its values.
     * @param strCaseNum string value of case number.
     * @return result of this command (if true, execute success("OK"). if false, execute error("NG").)
     */
    boolean executeBrowserOperation(ArrayList<String[]> listParameter, String strCaseNum) {

        // check driver
        if (this.driver == null) {
            logger.log("message.warn.cant_find_driver", strCaseNum, "Close");
            return false;
        }

        // execute operation
        for (String[] parameter : listParameter) {
            String param = parameter[0].toLowerCase();
            String value = parameter[1].toLowerCase();
            if (!param.equals("operation")) {
                continue;
            }
            switch (value) {
                case "forward" :
                    driver.navigate().forward();
                    break;
                case "back" :
                    driver.navigate().back();
                    break;
                case "refresh" :
                    driver.navigate().refresh();
                    break;
                case "close" :
                    // in some browser(firefox, safari), close() doesn't work.
                    //driver.close();
                    driver.quit();
                    driver = null;
                    break;
                default:
                    break;
            }
        }

        return true;
    }

    /**
     * Execute "Capture" command
     * @param listParameter parameter list with its values.
     * @param strCaseNum string value of case number.
     * @param strID string value of ID (need for filename)
     * @param strTitle string value of Title (need for filename)
     * @return result of this command (if true, execute success("OK"). if false, execute error("NG").)
     */
    boolean executeCapture(ArrayList<String[]> listParameter, String strCaseNum, String strID, String strTitle) {

		// check driver
		if (this.driver == null) {
			logger.log("message.warn.cant_find_driver", strCaseNum, "Capture");
			return false;
		}

		String filename = "";
        for(String[] param : listParameter) {
            if (param[0].equalsIgnoreCase("filename")) {
                if (param[1] != null) {
                    filename = param[1];
                    break;
                }
            }
        }
        if (filename.equals("")) {
            logger.log("message.warn.no_filename", strCaseNum, "Capture");
            return false;
        }
        filename = createFilename(filename, strCaseNum, strID, strTitle);
        String ext = filename.substring(filename.length() - 4);
        if (!ext.equalsIgnoreCase(".png")) {
        	filename = filename + ".png";
		}

		// get screen capture
		try {
        	// need to loading has finished
        	int intervalBefore = conf.getIntProperty("webdriver.common.ss_before_interval");
        	Thread.sleep(intervalBefore);
			File scrFile = ((TakesScreenshot)driver).getScreenshotAs(OutputType.FILE);
			FileUtils.copyFile(scrFile, new File(filename));
			// need to save file has finished
			// (It seems to be doing parallel processing.)
			int intervalAfter = conf.getIntProperty("webdriver.common.ss_after_interval");
			Thread.sleep(intervalAfter);
		} catch (Exception e) {
			logger.log("message.error.cant_write_file", filename);
			return false;
		}

        return true;
    }

    /**
     * Execute "Wait" command
     * @param listParameter parameter list with its values.
     * @param strCaseNum string value of case number.
     * @return result of this command (if true, execute success("OK"). if false, execute error("NG").)
     */
    boolean executeWait(ArrayList<String[]> listParameter, String strCaseNum) {
        String strMilliseconds = getParameterValue(listParameter, strCaseNum, "milliseconds");
        try {
            int milliseconds = Integer.parseInt(strMilliseconds);
            if (milliseconds > (15 * 60 * 1000)) {
                logger.log("message.warn.too_long_wait", "Wait", strCaseNum, strMilliseconds);
                return false;
            }
            Thread.sleep(milliseconds);
        } catch (Exception e) {
            logger.log("message.warn.cant_conv_param", "Wait", strCaseNum, strMilliseconds);
            return false;
        }
        return true;
    }

    /**
     * Terminate test case
     * @param strCaseNum string value of case number.
     */
    void terminateTestCase(String strCaseNum) {
        if (this.driver != null) {
            drivers.put(strCaseNum, this.driver);
        }
    }

    /**
     * Terminate process
     */
    void terminate() {
        try {
            for (String key : drivers.keySet()) {
                drivers.get(key).quit();
            }
            if (driver != null) {
                driver.quit();
                driver = null;
            }
        } catch (Exception e) {
            // do nothing
        }
    }

    /**
     * filtering WebElement by parameters
     * @param listParameter parameter list with its values.
     * @param strCaseNum string value of case number.
     * @return filtered element
     */
    private List<WebElement> getElements(ArrayList<String[]> listParameter, String strCaseNum, String command) {

    	// check parameter
		boolean isValidParameter = false;
		for (String[] str: listParameter) {
			switch (str[0]) {
				case ("by_id"):
				case ("by_name"):
				case ("by_class"):
				case ("by_xpath"):
				case ("by_link_text"):
					if (str[1].equals("")) {
						logger.log("message.warn.lack_value", "Wait", strCaseNum, str[0]);
						return null;
					}
					isValidParameter = true;
					break;
				default:
					break;
			}
		}
		if (!isValidParameter) {
			logger.log("message.warn.lack_param", strCaseNum, command);
			return null;
		}

		// find elements
		boolean isParent = true;
        List<WebElement> elements = null;
        String param = "";
        String value = "";
        for (String[] str: listParameter) {
            // set filtering by
			By by;
			switch (str[0]) {
                case ("by_id"):
                    by = By.id(str[1]);
                    break;
                case ("by_name"):
                    by = By.name(str[1]);
                    break;
                case ("by_class"):
                    by = By.className(str[1]);
                    break;
                case ("by_xpath"):
                    by = By.xpath(str[1]);
                    break;
                case ("by_link_text"):
                    by = By.linkText(str[1]);
                    break;
                default:
                    continue;
            }

            // fine element by value
            switch (str[0]) {
                case ("by_id"):
                case ("by_name"):
                case ("by_class"):
                case ("by_xpath"):
                case ("by_link_text"):
					param = str[0];
					value = str[1] ;
					if (isParent) {
                        elements = driver.findElements(by);
                        isParent = false;
                    } else {
                        List<WebElement> tmp = new ArrayList<>();
                        for (WebElement element: elements) {
                            try {
                                tmp.addAll(element.findElements(by));
                            } catch (NoSuchElementException e) {
                                // do nothing
                            }
                        }
                        elements = tmp;
                    }
                    break;
                default:
					continue;
            }
            if ((elements == null) || (elements.size() == 0)) {
                logger.log("message.warn.cant_find_item", strCaseNum, command, param, value);
                return null;
            }
        }
		// if couldn't identify as one (use only top level element)
		if(elements != null && elements.size() > 1) {
			logger.log("message.warn.too_mach_elements", strCaseNum, command, Integer.toString(elements.size()), param, value);
		}

		return elements;
    }


    /**
     * filtering WebElement by parameters
     * @param listParameter parameter list with its values.
     * @param strCaseNum string value of case number.
     * @param key target parameter string
     * @return value of parameter
     */
    private String getParameterValue(ArrayList<String[]> listParameter, String strCaseNum, String key) {
        String value = "";
        for (String[] param: listParameter) {
            if (key.equalsIgnoreCase(param[0])) {
                value = param[1];
            }
        }
        if (value == null) {
            logger.log("message.warn.cant_find_param", strCaseNum, key);
            value = "";
        }
        return value;
    }

    /**
     * running on windows or not
     * @return if true, runnning on windows.
     */
    private boolean isWindows() {
        return System.getProperty("os.name").contains("Windows");
    }

    /**
     * create filename for capture
     * @param origin original filename include special tags
     * @param strCaseNum string value of case number.
     * @param strID string of ID
     * @param strTitle string of Title
     * @return filename (after replacement by tags)
     */
    private String createFilename(String origin, String strCaseNum, String strID, String strTitle) {

        String filename = origin;

        //Date, Time
        Date date = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String strDate = sdf.format(date);
        filename = Pattern.compile("<DATE>", Pattern.CASE_INSENSITIVE).matcher(filename).replaceAll(strDate);
        sdf = new SimpleDateFormat("HH:mm:ss");
        String strTime = sdf.format(date);
        filename = Pattern.compile("<TIME>", Pattern.CASE_INSENSITIVE).matcher(filename).replaceAll(strTime);

        //ID
        filename = Pattern.compile("<ID>", Pattern.CASE_INSENSITIVE).matcher(filename).replaceAll(strID);

        // Title
        filename = Pattern.compile("<TITLE>", Pattern.CASE_INSENSITIVE).matcher(filename).replaceAll(strTitle);

        // CaseNumber
        filename = Pattern.compile("<CASENO>", Pattern.CASE_INSENSITIVE).matcher(filename).replaceAll(strCaseNum);

        return filename;
    }

    /**
     * enum for "SendKeys" command
     */
    private enum SpecialKeys {
        ENT("<ENTER>"       , Keys.ENTER),
        BKS("<BS>"          , Keys.BACK_SPACE),
        DEL("<DEL>"         , Keys.DELETE),
        CTL("<CTRL>"        , Keys.CONTROL),
        CON("<CONTROL>"     , Keys.CONTROL),
        ALT("<ALT>"         , Keys.ALT),
        SFT("<SHIFT>"       , Keys.SHIFT),
        TAB("<TAB>"         , Keys.TAB),
        ESC("<ESC>"         , Keys.ESCAPE),
        HOM("<HOME>"        , Keys.HOME),
        END("<END>"         , Keys.END),
        PGU("<PAGEUP>"      , Keys.PAGE_UP),
        PGD("<PAGEDOWN>"    , Keys.PAGE_DOWN),
        AUP("<UP>"          , Keys.ARROW_UP),
        ADO("<DOWN>"        , Keys.ARROW_DOWN),
        ALE("<LEFT>"        , Keys.ARROW_LEFT),
        ARI("<RIGHT>"       , Keys.ARROW_RIGHT),
        F01("<F1>"          , Keys.F1),
        F02("<F2>"          , Keys.F2),
        F03("<F3>"          , Keys.F3),
        F04("<F4>"          , Keys.F4),
        F05("<F5>"          , Keys.F5),
        F06("<F6>"          , Keys.F6),
        F07("<F7>"          , Keys.F7),
        F08("<F8>"          , Keys.F8),
        F09("<F9>"          , Keys.F9),
        F10("<F10>"         , Keys.F10),
        F11("<F11>"         , Keys.F11),
        F12("<F12>"         , Keys.F12);

        String tag;
        Keys key;

        SpecialKeys(String tag, Keys key) {
            this.tag = tag;
            this.key = key;
        }

        String getTag() {
            return this.tag;
        }

        Keys getKey() {
            return this.key;
        }

    }

}

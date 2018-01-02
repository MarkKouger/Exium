package com.github.exium;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.*;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Name;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellReference;

/**
 * Parse excel file and execute command
 */
class ExcelParser {

    private String testFilename;
    private String resultFilename;
    private FileInputStream in = null;
    private Workbook wb = null;
    private Logger logger;
    private Cell cellNumberHeader;
    private Cell cellCommandHeader;
    private Cell cellParameterHeader;
    private Cell cellValueHeader;
    private Cell cellCaseNumberHeader;
    private String strID;
    private String strTitle;
    private WebDriverController wdc;

    /**
     * Constructor of ExcelParse class.
     * @param testFilename checklist file name (argument of  "-c" option)
     * @param resultFilename result file name (argument of "-r" option)
     */
    ExcelParser(String testFilename, String resultFilename) {
        this.testFilename = testFilename;
        this.resultFilename = resultFilename;
        this.logger = Exium.logger;
        wdc = new WebDriverController();
    }

    /**
     * To open the test file and create result file.
     * @return true: success, false: error(ex. can't open, can't create, etc...)
     */
    boolean openTestFile() {
        // existing check
        File testFile = new File(testFilename);
        if (!testFile.exists()) {
            logger.log("message.error.not_exist_file", testFilename);
            return false;
        }

        // open workbook
        try {
			in = new FileInputStream(testFile);
			wb = WorkbookFactory.create(in);
        } catch (Exception e) {
            // cannot open, invalid file type
            logger.log("message.error.invalid_file", e, testFilename);
            return false;
        }

        // create result file
        try {
            FileOutputStream out = new FileOutputStream(resultFilename);
            wb.write(out);
            out.close();
        } catch (Exception e) {
            logger.log("message.error.cant_write_file", e, resultFilename);
            return false;
        }

        return true;
    }

    /**
     * execute test cases by each sheet (without "MasterData" sheet).
     * @return true: success, false: An error preventing continuation occurred
     */
    boolean execute() {
        // iterate by sheet
        Iterator<Sheet> sheets = wb.sheetIterator();
        while(sheets.hasNext()) {
            Sheet sheet = sheets.next();

            // skip MasterData sheet
            if (sheet.getSheetName().equalsIgnoreCase("MasterData")) {
                continue;
            }
            logger.log("message.info.start_sheet", sheet.getSheetName());

            // validation existing named cell
            String tags[] = {"ID", "TITLE", "NO", "COMMAND", "PARAMETER", "VALUE", "CASENUMBER"};
            boolean rc = true;
            for (String tag: tags) {
                rc &= checkNamedCell(sheet, tag);
            }
            if (!rc) {
                // skip this sheet (because of invalid format)
                continue;
            }

            // checking for extended format check (need to align these named cells).
            if (cellCommandHeader.getRow() != cellParameterHeader.getRow() ||
                    cellCommandHeader.getRow() != cellValueHeader.getRow()) {
                // invalid format(not aligned these rows) therefor skip this sheet
                logger.log("message.error.invalid_rows");
                continue;
            }

            // parse Test Scenario and get command list. (The reference cell is "CASENUMBER")
            int rowHeader = cellCaseNumberHeader.getRowIndex() + 1;
            List<HashMap> listCommand = parseTestScenario(sheet, rowHeader);

            // execute test cases / iterate by case number
            int col = cellCaseNumberHeader.getColumnIndex();
            while(true) {
                // terminate condition
                Cell cellCaseNumber = getCell(sheet, rowHeader, col);
                String strCaseNumber = getCellValue(cellCaseNumber, true);
                if (strCaseNumber.equals("")) {
                    // complete execute test case in this sheet
                    break;
                }

                // execute test case
                logger.log("message.info.start_case", strCaseNumber);
                executeTestCase(sheet, listCommand, rowHeader + 1, col, strCaseNumber);
                wdc.terminateTestCase(strCaseNumber);
                logger.log("message.info.complete_case", strCaseNumber);

                // next column
                col++;
            }

            wdc.terminate();
            logger.log("message.info.complete_sheet", sheet.getSheetName());
        }

        return true;
    }

    /**
     * To parse test scenario (parse each commands, parameters, values).
     * @param sheet Target sheet.
     * @param rowHeader Row of header. Starting parse is from header + 1.
     * @return list of command, parameter, value (commands and parameters are satisfied).
     */
    private List<HashMap> parseTestScenario(Sheet sheet, int rowHeader) {

        // initialize
        int row = rowHeader + 1;
        List<HashMap> list = new ArrayList<HashMap>();
        int numCommandID = 0;
        String strCommand = "";
        String strParameter = "";

        // parse scenario
        while(true) {
            // Termination condition
            Cell cellNumber = getCell(sheet, row, cellNumberHeader.getColumnIndex());
            String strNumber = getCellValue(cellNumber);
            if (strNumber.equals("")) {
                // Terminate when the number (row index) breaks.
                break;
            }

            // prepare HashMap to store Command, Parameter, Value, etc...
            HashMap<String, String> map = new HashMap<String, String>();

            // get command str
            Cell cellCommand = getCell(sheet, row, cellCommandHeader.getColumnIndex());
            String strCommandTmp = getCellValue(cellCommand);
            if (!strCommandTmp.equals("")) {
                strCommand = strCommandTmp;
                numCommandID++;
            }
            map.put("COMMAND", strCommand.toLowerCase());
            map.put("COMMAND_ID", String.valueOf(numCommandID));

            // get parameter str
            Cell cellParameter = getCell(sheet, row, cellParameterHeader.getColumnIndex());
            String strParameterTmp = getCellValue(cellParameter);
            if (!strParameterTmp.equals("")) {
                strParameter = strParameterTmp;
            }
            map.put("PARAMETER", strParameter.toLowerCase());

            // get value str
            Cell cellValue = getCell(sheet, row, cellValueHeader.getColumnIndex());
            String strValue = getCellValue(cellValue);
            map.put("VALUE", strValue);

            list.add(map);
            row++;
        }

        return list;
    }


    /**
     * Execute each command using checked parameter.
     * @param sheet Target sheet
     * @param listCommand Command list. It's result of parse.
     * @param rowStart Row of the first command.
     * @param col Target column.
     * @param strCaseNum The string of the case number (related with the target column).
     */
    private void executeTestCase(Sheet sheet, List<HashMap> listCommand, int rowStart, int col, String strCaseNum) {

        int cnt = 0;
        String strCommand;
        String strCommandId;
        boolean rc = false;

        // loop in test scenario (rows).
        while (true) {
            // terminate condition
            if (cnt >= listCommand.size()) {
                break;
            }

            // execute command
            ArrayList<String[]> listCommandParameter = new ArrayList<>();
            ArrayList<Cell> cells = new ArrayList<>();
            while (true) { // loop in each command
                //
                HashMap command = listCommand.get(cnt);
                strCommandId = (String)command.get("COMMAND_ID");
                strCommand = (String)command.get("COMMAND");

                // if command or command id is blank, skip this row
                if ((strCommandId == null) || (strCommand == null)) {
                    // skip this command (invalid case)
                    strCommand = "";
                    cnt++;
                    break;
                }

                // read and create parameters and values to use
                Cell cell = getCell(sheet, rowStart + cnt, col);
                String check = getCellValue(cell);
                switch (check) {
                    case ("Y"):
                    case ("y"):
                    case ("o"):
                    case ("O"):
                    case ("*"):
                    case ("●"):
                    case ("✓"):
                    case ("☑"):
                    case ("✅"):
                        String[] str = {(String)command.get("PARAMETER"), (String)command.get("VALUE")};
                        listCommandParameter.add(str);
                        cells.add(cell);
                        break;
                    default:
                        break;
                }
                cnt++;

                // terminate condition (1)
                if (cnt == listCommand.size()) {
                    // If end of the command list, goto terminate parent loop (execute last command)
                    break;
                }

                // terminate condition (2)
                HashMap mapNextCommand = listCommand.get(cnt);
                String strNextCommandSeq = (String) mapNextCommand.get("COMMAND_ID");
                if (!strCommandId.equals(strNextCommandSeq)) {
                    // execute this command and goto next command
                    break;
                }
            }
            if (listCommandParameter.size() == 0) {
                // not need execute command
                continue;
            }

            // execute command
            try {
                switch (strCommand) {
                    case "open":
                        rc = wdc.executeOpen(listCommandParameter, strCaseNum);
                        break;
                    case "switchbrowser":
                        rc = wdc.executeSwitchBrowser(listCommandParameter, strCaseNum);
                        break;
                    case "input":
                        rc = wdc.executeToElement(listCommandParameter, strCaseNum, "Input");
                        break;
                    case "submit":
                        rc = wdc.executeToElement(listCommandParameter, strCaseNum, "Submit");
                        break;
                    case "click":
                        rc = wdc.executeToElement(listCommandParameter, strCaseNum, "Click");
                        break;
                    case "select":
                        rc = wdc.executeToElement(listCommandParameter, strCaseNum, "Select");
                        break;
                    case "sendkey":
                        rc = wdc.executeSendKeys(listCommandParameter, strCaseNum);
                        break;
                    case "mouseover":
                        rc = wdc.executeToElement(listCommandParameter, strCaseNum, "MouserOver");
                        break;
                    case "browseroperation":
                        rc = wdc.executeBrowserOperation(listCommandParameter, strCaseNum);
                        break;
                    case "log":
                        rc = wdc.executeToElement(listCommandParameter, strCaseNum, "Log");
                        break;
                    case "capture":
                        rc = wdc.executeCapture(listCommandParameter, strCaseNum, strID, strTitle);
                        break;
                    case "comparetext":
                        rc = wdc.executeToElement(listCommandParameter, strCaseNum, "CompareText");
                        break;
                    case "wait":
                        rc = wdc.executeWait(listCommandParameter, strCaseNum);
                        break;
                    default:
                        // If unknown command, skip this command
                        logger.log("message.warn.unknown_command", strCaseNum, strCommand);
                        break;
                }
            } catch (Exception e) {
                logger.log("message.warn.command_exec_fail", e, strCaseNum, strCommand);
                rc = false;
            }

            // write the result to result file
            for (Cell cell : cells) {
                String check = getCellValue(cell);
                switch (check) {
                    case ("●"): // Japanese type
                        cell.setCellValue(rc ? "○" : "×");
                        break;
                    default: // Others
                        cell.setCellValue(rc ? "OK" : "NG");
                        break;
                }
            }
        }
        // save the wb(workbook) to file
        save();
    }

    /**
     * Get cell value.
     * @param cell Target cell.
     * @param zeroSuppress Need to fill by "0" for Integer.
     * @return The value in string.
     */
    private String getCellValue(Cell cell, boolean zeroSuppress) {
        String ret = "";

        // null check
        if (cell == null) {
            return ret;
        }

        // change getting function by cell type.
        switch(cell.getCellTypeEnum()) {
            case STRING:
                ret = cell.getRichStringCellValue().getString();
                ret = ret.trim();
                break;
            case BOOLEAN:
                ret = String.valueOf(cell.getBooleanCellValue());
                break;
            case NUMERIC:
                // change getting function between date and numeric.
                if(DateUtil.isCellDateFormatted(cell)) {
                    // if date
                    Date value = cell.getDateCellValue();
                    ret = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(value);
                } else {
                    // if numeric
                    if (zeroSuppress) {
                        ret = String.format("%03d", (int)cell.getNumericCellValue());
                    } else {
                        ret = Integer.toString((int)cell.getNumericCellValue());
                    }
                }
                break;
            case FORMULA:
                CreationHelper crateHelper = wb.getCreationHelper();
                FormulaEvaluator evaluator = crateHelper.createFormulaEvaluator();
                ret = getCellValue(evaluator.evaluateInCell(cell), zeroSuppress);
                break;
            case _NONE:
            case BLANK:
            case ERROR:
            default:
                return "";
        }
        if (ret == null) {
            return "";
        }
        return ret;
    }

    /**
     * Get cell value (without zero suppress, it always "false").
     * @param cell Target cell.
     * @return The value in string.
     */
    private String getCellValue(Cell cell) {
        return getCellValue(cell, false);
    }


    /**
     * Get cell object by row, column
     * @param sheet Target sheet
     * @param row Row of target cell
     * @param col Column of target cell
     * @return cell object
     */
    private Cell getCell(Sheet sheet, int row, int col) {
        CellReference cellref = new CellReference(row, col);
        Row rows = sheet.getRow(cellref.getRow());
        if (rows == null) {
            return null;
        }
        return rows.getCell(cellref.getCol());
    }


    /**
     * To check existing named cells in sheet. Some named cells need for parse test file.
     * @param sheet A sheet of test file.
     * @param tag The name of named cell.
     * @return true: success, false: error(Not found named cell)
     */
    private boolean checkNamedCell(Sheet sheet, String tag) {
        Name name = wb.getName(tag);
        if (name == null) {
            // Not found tag
            logger.log("message.error.invalid_format", sheet.getSheetName(), tag);
            return false;
        }
        CellReference cellref = new CellReference(name.getRefersToFormula());
        Row row = sheet.getRow(cellref.getRow());
        Cell cell = row.getCell(cellref.getCol());
        switch (tag) {
            case "ID" :
                strID = getCellValue(cell);
                break;
            case "TITLE" :
                strTitle = getCellValue(cell);
                break;
            case "NO" :
                cellNumberHeader = cell;
                break;
            case "COMMAND" :
                cellCommandHeader = cell;
                break;
            case "PARAMETER" :
                cellParameterHeader = cell;
                break;
            case "VALUE" :
                cellValueHeader = cell;
                break;
            case "CASENUMBER":
                cellCaseNumberHeader = cell;
                String value = getCellValue(cell);
                if (!value.equalsIgnoreCase("CaseNo")) {
                    logger.log("message.error.cant_find_case");
                    return false;
                }
                break;
            default:
                logger.log("message.error.unexpected");
                return false;
        }
        return true;
    }

    /**
     * Save(overwrite) the workbook to result file.
     */
    private void save() {
        try {
            FileOutputStream out = new FileOutputStream(resultFilename);
            wb.write(out);
            out.close();
        } catch (Exception e) {
            logger.log("message.error.unexpected", e);
        }
    }

    /**
     * Terminate process of ExcelParser
     */
    void terminate() {
        save();
        try {
            // workbook close
            if (wb != null) {
                wb.close();
            }
            // file input stream
			if (in != null) {
            	in.close();
			}
        } catch (Exception e) {
            logger.log("message.error.unexpected", e);
        }
    }

}

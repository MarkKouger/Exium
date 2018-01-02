package com.github.exium;

import org.apache.commons.cli.*;
import java.util.ResourceBundle;

/**
 * Master class of Exium
 */
public class Exium {

    // common objects
    static Logger logger;       // logger
    static ResourceBundle rb;   // message resource bundle
    static Configurator conf;   // config(using for Constants.properties and user properties)
    private static String messages = "Messages";  // for message resource bundle

    public static void main(String[] args) {
        new Exium().start(args);
    }

    /**
     * Starting Exium
     * @param args Java command args.
     */
    private void start(String[] args) {

        // Read resource bundle
        rb = ResourceBundle.getBundle(Exium.messages);

        // Argument check
        Options opts = new Options();

        Option checklist = new Option("c", "checklist", true, rb.getString("constant.option_desc.f"));
        checklist.setArgName("filename");
        opts.addOption(checklist);

        Option result = new Option("r", "result", true, rb.getString("constant.option_desc.o"));
        result.setArgName("filename");
        opts.addOption(result);

        Option properties = new Option("p", "properties", true, rb.getString("constant.option_desc.p"));
        properties.setArgName("filename");
        opts.addOption(properties);

        Option log = new Option("l", "log", true, rb.getString("constant.option_desc.l"));
        log.setArgName("filename");
        opts.addOption(log);

        CommandLineParser parser = new DefaultParser();
        String testFilename = "";
        String resultFilename = "";
        try {
            CommandLine cl = parser.parse(opts, args);
            testFilename = cl.getOptionValue("c");
            if ((testFilename == null) || (testFilename.equals(""))) {
                throw new ParseException("");
            }
            resultFilename = cl.getOptionValue("r");
            if ((resultFilename == null) || (resultFilename.equals(""))) {
                throw new ParseException("");
            }
            String propertiesFilename = cl.getOptionValue("p");
            if ((propertiesFilename == null) || (propertiesFilename.equals(""))) {
                propertiesFilename = "Constants.properties";
            }
            conf = new Configurator(propertiesFilename);
            String logFilename = cl.getOptionValue("l");
            if (logFilename == null) {
                logFilename = "";
            }
            logger = new Logger(logFilename);
        } catch (ParseException e) {
            HelpFormatter help = new HelpFormatter();
            help.setOptionComparator(null);
            help.printHelp("java <java options> -jar Exium.jar", opts, true);
            System.exit(-1);
        }

        // read Excel file
        ExcelParser ep = new ExcelParser(testFilename, resultFilename);
        boolean rc = ep.openTestFile();
        if (!rc) {
            ep.terminate();
            System.exit(-1);
        }

        // parse and execute cases
        rc = ep.execute();
        if (!rc) {
            ep.terminate();
            System.exit(-1);
        }

        // terminate
        ep.terminate();

    }
}

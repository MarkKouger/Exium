package com.github.exium;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ResourceBundle;

class Logger {

    private ResourceBundle rb;
    private File file;
    private String lineCd;
    private boolean append = false;

    Logger(String filename) {
        rb = Exium.rb;
        if (filename.equals("")) {
            file = null;
        } else {
			file = new File(filename);
	    }
        lineCd = System.getProperty("line.separator");
		log("message.info.start_excelenium");
    }

    void log(String msg_id, String... args) {

        Date now = new Date();
        String str = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").format(now);
        MessageFormat mf = new MessageFormat(rb.getString(msg_id));
        output(str + "  " + mf.format(args));
    }


    void log(String msg_id, Exception e, String... args) {

        log(msg_id, args);

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        String trace = sw.toString();
        output(trace);
        try {
            sw.close();
            pw.close();
        } catch (Exception ignore) {
            // do nothing
        }

    }


    private void output(String str) {
		if (file == null) {
			System.out.println(str);
		} else {
			try {
				FileWriter filewriter = new FileWriter(file, append);
				filewriter.write(str + lineCd);
				filewriter.close();
			} catch (Exception e) {
				file = null;
				log("message.warn.cant_open_logfile", e);
			}
			if (!append) {
				append = true;
			}
		}
	}

}

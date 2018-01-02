package com.github.exium;

import java.util.ResourceBundle;

class Configurator {

    private static ResourceBundle ext = null;
    private static ResourceBundle def = null;
    private Logger logger;

    Configurator(String filename) {

        logger = Exium.logger;

        if ((filename != null) && (filename.equals(""))) {
            try {
                ext = ResourceBundle.getBundle(filename);
            } catch (Exception e) {
                logger.log("message.warn.cant_open_prop");
            }
        }
        def = ResourceBundle.getBundle("Constants");

    }


    String getStringProperty(String key) {
        String val = null;
        if (ext != null) {
            try {
                val = ext.getString(key).trim();
            } catch (Exception e) {
                ; // do nothing
            }
        }
        if ((val == null) || (val.equals(""))) {
            val = def.getString(key).trim();
        }

        return val;
    }

    int getIntProperty(String key) {
        String val = null;
        if (ext != null) {
            try {
                val = ext.getString(key).trim();
            } catch (Exception e) {
                ; // do nothing
            }
        }
        if ((val == null) || (val.equals(""))) {
            val = def.getString(key).trim();
        }
        int num = 0;
        if (!val.equals("")) {
            try {
                num = Integer.parseInt(val);
            } catch (Exception e) {
                logger.log("message.warn.cant_conv_prop_num", val);
            }
        }

        return num;
    }

}

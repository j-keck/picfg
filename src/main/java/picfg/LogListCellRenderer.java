package picfg;

import javax.swing.*;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class LogListCellRenderer extends DefaultListCellRenderer {

    private SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        Component comp = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

        String logPrefix = sdf.format(((picfg.log.Log) value).ts()) + " | ";

        if (value instanceof picfg.log.Info) {
            comp.setForeground(Color.BLACK);
            setText(logPrefix + ((picfg.log.Info) value).msg());
        } else if (value instanceof picfg.log.Error) {
            comp.setForeground(Color.RED);
            String text = ((picfg.log.Error) value).msg() + ": " + ((picfg.log.Error) value).e().getMessage();
            setText(logPrefix + text);
        } else {
            System.err.println("Unexpected 'Log' Type: " + value);
        }

        return comp;
    }
}

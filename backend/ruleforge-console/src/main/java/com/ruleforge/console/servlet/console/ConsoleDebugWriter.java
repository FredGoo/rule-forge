package com.ruleforge.console.servlet.console;

import com.ruleforge.debug.DebugWriter;
import com.ruleforge.debug.MessageItem;

import java.io.IOException;
import java.util.List;

public class ConsoleDebugWriter implements DebugWriter {
    private DebugMessageHolder debugMessageHolder;

    @Override
    public void write(List<MessageItem> items) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (MessageItem item : items) {
            sb.append(item.toHtml());
        }
        String key = debugMessageHolder.generateKey();
        debugMessageHolder.putDebugMessage(key, sb.toString());
    }

    public void setDebugMessageHolder(DebugMessageHolder debugMessageHolder) {
        this.debugMessageHolder = debugMessageHolder;
    }
}
